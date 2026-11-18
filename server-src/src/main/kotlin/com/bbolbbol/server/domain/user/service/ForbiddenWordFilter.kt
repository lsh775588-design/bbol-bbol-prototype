package com.bbolbbol.server.domain.user.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.Locale

/**
 * 닉네임 금칙어 필터. 목록은 리소스 파일(기본 classpath:forbidden-words.txt)에서 읽음
 * 공백/특수문자를 제거하고 소문자로 맞춘 뒤 포함 여부를 검사해 우회 입력을 막음
 */
@Component
class ForbiddenWordFilter(words: List<String>) {

	@Autowired
	constructor(
		@Value("\${bbolbbol.nickname.forbidden-words-location:classpath:forbidden-words.txt}") resource: Resource,
	) : this(load(resource))

	private val words: List<String> = words.map(::normalize).filter { it.isNotEmpty() }

	fun containsForbiddenWord(text: String?): Boolean {
		if (text == null) return false
		val n = normalize(text)
		return words.any { n.contains(it) }
	}

	companion object {
		private val NON_WORD = Regex("[^\\p{L}\\p{N}]")

		internal fun normalize(s: String): String =
			NON_WORD.replace(Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT), "")

		private fun load(resource: Resource): List<String> =
			resource.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
				.lines()
				.map { it.trim() }
				.filter { it.isNotEmpty() && !it.startsWith("#") }
	}
}
