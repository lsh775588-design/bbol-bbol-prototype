package com.bbolbbol.server.global.error

import org.springframework.http.HttpStatus

/**
 * 파일 업로드 에러 코드
 *
 * 파일 누락, 용량 초과는 GlobalExceptionHandler가 자동으로 처리함
 * 지원하지 않는 형식(이미지가 아님 등)은 서비스에서 확인해서 직접 던지면 됨
 * ex) throw BusinessException(FileErrorCode.INVALID_FILE)
 */
enum class FileErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {
	INVALID_FILE(HttpStatus.BAD_REQUEST, "FILE4001", "파일이 없거나 지원하지 않는 형식입니다."),
	FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE413", "파일 용량은 10MB를 넘을 수 없습니다."),
}
