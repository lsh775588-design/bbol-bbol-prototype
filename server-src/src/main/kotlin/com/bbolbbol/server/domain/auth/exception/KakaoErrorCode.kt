package com.bbolbbol.server.domain.auth.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class KakaoErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {
	INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "KAKAO401", "카카오 토큰이 만료되었거나 유효하지 않음"),
	KAKAO_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "KAKAO502", "카카오 서버 응답 실패"),
}
