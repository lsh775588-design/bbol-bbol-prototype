package com.bbolbbol.server.domain.completion.service

import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * 정제한 바이트를 FileStorage 포트(MultipartFile을 받는다)에 넘기기 위한 어댑터.
 * 파일 이름과 타입을 서버가 정하므로 LocalFileStorage가 클라이언트 파일 이름에서 확장자를 가져오지 못한다.
 */
class BytesMultipartFile(
	private val bytes: ByteArray,
	private val fileName: String,
	private val type: String,
) : MultipartFile {
	override fun getName(): String = "photo"
	override fun getOriginalFilename(): String = fileName
	override fun getContentType(): String = type
	override fun isEmpty(): Boolean = bytes.isEmpty()
	override fun getSize(): Long = bytes.size.toLong()
	override fun getBytes(): ByteArray = bytes
	override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
	override fun transferTo(dest: File) = dest.writeBytes(bytes)
}
