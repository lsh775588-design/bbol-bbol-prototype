package com.bbolbbol.server.global.security

import com.bbolbbol.server.global.common.ApiResponse
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Security 필터 단계에서 난 에러는 @RestControllerAdvice까지 가지 않기 때문에
 * 여기서 직접 공통 응답 형식으로 써줘야 함
 */
@Component
class SecurityErrorResponder(
	private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

	// 인증 정보가 없거나 유효하지 않음 == AUTH401
	override fun commence(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authException: AuthenticationException,
	) = write(response, CommonErrorCode.UNAUTHORIZED)

	// 로그인은 했지만 권한이 없음 == AUTH403
	override fun handle(
		request: HttpServletRequest,
		response: HttpServletResponse,
		accessDeniedException: AccessDeniedException,
	) = write(response, CommonErrorCode.FORBIDDEN)

	private fun write(response: HttpServletResponse, errorCode: ErrorCode) {
		response.status = errorCode.status.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = Charsets.UTF_8.name()
		response.writer.write(jsonMapper.writeValueAsString(ApiResponse.fail<Any>(errorCode)))
	}
}
