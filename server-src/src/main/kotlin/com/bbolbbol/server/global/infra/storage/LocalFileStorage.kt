package com.bbolbbol.server.global.infra.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/** storage.type=local 일 때 사용하는 로컬 디스크 저장소 (개발용) */
@Component
@ConditionalOnProperty(name = ["storage.type"], havingValue = "local")
class LocalFileStorage(
	@Value("\${storage.local.base-dir}") baseDir: String,
	@Value("\${storage.public-base-url:}") private val publicBaseUrl: String,
) : FileStorage {

	private val root: Path = Paths.get(baseDir).toAbsolutePath().normalize()

	override fun upload(file: MultipartFile, directory: String): String {
		val extension = file.originalFilename
			?.substringAfterLast('.', "")
			?.lowercase()
			?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
		val fileName = UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: "")
		val key = "$directory/$fileName"

		val target = resolve(key)
		Files.createDirectories(target.parent)
		file.inputStream.use { Files.copy(it, target) }
		return key
	}

	override fun delete(key: String) {
		Files.deleteIfExists(resolve(key))
	}

	override fun url(key: String): String = publicBaseUrl.trimEnd('/') + "/files/" + key

	/** 로컬 다운로드 라우트용. 저장소 밖 경로이거나 없는 파일이면 null. */
	fun find(key: String): Path? {
		val path = runCatching { resolve(key) }.getOrNull() ?: return null
		return path.takeIf { Files.isRegularFile(it) }
	}

	// "../" 같은 경로로 저장소 밖의 파일을 건드리지 못하도록 방지
	private fun resolve(key: String): Path {
		val path = root.resolve(key).normalize()
		require(path.startsWith(root)) { "잘못된 파일 경로입니다: $key" }
		return path
	}
}
