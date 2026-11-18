package com.bbolbbol.server.global.error

/**
 * 서비스 로직에서 예상된 실패를 알릴 때 던지는 예외
 * GlobalExceptionHandler가 잡아서 공통 응답 형식으로 바꿔 줌
 *
 * ex) throw BusinessException(UserErrorCode.USER_NOT_FOUND)
 *     throw BusinessException(UserErrorCode.NICKNAME_CHANGE_LIMIT, mapOf("nextChangeableAt" to date))
 *
 * @param data 에러 응답의 data에 함께 내려줄 값 (ex. remainingAttempts, nextChangeableAt)
 */
class BusinessException(
	val errorCode: ErrorCode,
	val data: Any? = null,
) : RuntimeException(errorCode.message)
