package com.bbolbbol.server.global.infra.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * storage.type=local 일 때만 있는 다운로드 라우트(개발용). 배포(S3)에서는 CDN이 대신한다.
 *
 * 보안 트레이드오프: 인증된 사용자(anyRequest().authenticated())만 받을 수 있고, 주소의 key가 UUID 난수라
 * 추측할 수 없다. 즉 "로그인한 사용자가 URL을 알아야" 볼 수 있다. 앱의 이미지 로더가 Authorization 헤더를
 * 붙여야 한다. 헤더 없이 쓰려면 files 경로 전체를 공개로 풀어야 하는데, 그러면 URL 유출 = 사진 공개가 된다.
 * 이미지는 항상 JPEG로 다시 인코딩해 저장하므로 Content-Type은 image/jpeg로 고정하고 nosniff를 붙인다.
 */
@RestController
@ConditionalOnProperty(name = ["storage.type"], havingValue = "local")
class LocalFileController(private val storage: LocalFileStorage) {

	@GetMapping("/files/{directory}/{name}")
	fun download(@PathVariable directory: String, @PathVariable name: String): ResponseEntity<Resource> {
		if (!KEY_PATTERN.matches("$directory/$name")) return ResponseEntity.notFound().build()
		val path = storage.find("$directory/$name") ?: return ResponseEntity.notFound().build()
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(FileSystemResource(path))
	}

	private companion object {
		val KEY_PATTERN = Regex("^[a-z]+/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.]jpg$")
	}
}
