package com.bbolbbol.server.global.common

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 모든 API의 공통 응답 형식
 *
 * {
 *     "success": true,
 *     "code": "COMMON200",
 *     "message": "요청 성공",
 *     "data": { ... }
 * }
 */
data class ApiResponse<T>(
	val success: Boolean,
	val code: String,
	val message: String,
	val data: T?,
) {
	companion object {
		fun <T> ok(data: T? = null): ApiResponse<T> =
			ApiResponse(true, "COMMON200", "요청 성공", data)

		fun <T> created(data: T? = null): ApiResponse<T> =
			ApiResponse(true, "COMMON201", "생성 성공", data)

		/**
		 * 실제 HTTP 201로 응답해야 하는 생성 API(가입, 도착 인증, 사진 업로드)용
		 * created()는 body만 만들어서 HTTP 상태가 200이 되니, 컨트롤러에서는 이쪽을 반환할 것
		 */
		fun <T> createdResponse(data: T? = null, message: String = "생성 성공"): ResponseEntity<ApiResponse<T>> =
			ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse(true, "COMMON201", message, data))

		fun <T> fail(errorCode: ErrorCode, data: T? = null): ApiResponse<T> =
			ApiResponse(false, errorCode.code, errorCode.message, data)
	}
}
