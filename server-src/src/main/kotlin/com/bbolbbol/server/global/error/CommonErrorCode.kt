package com.bbolbbol.server.global.error

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH401", "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH403", "접근 권한이 없습니다."),

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON400", "입력값을 다시 확인해 주세요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 경로를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "지원하지 않는 HTTP 메서드입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON415", "지원하지 않는 Content-Type입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
}
