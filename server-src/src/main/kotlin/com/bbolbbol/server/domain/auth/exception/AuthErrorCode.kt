package com.bbolbbol.server.domain.auth.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH4011", "이메일 또는 비밀번호가 일치하지 않음"),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4012", "refresh token이 만료, 위조되었거나 이미 사용됨"),

	/** NOT IN SPEC: 명세에 없는 코드. 로그인 무차별 대입을 막으려고 최소로 추가함(A8). 이름은 AUTH401, AUTH403 계열을 따름. */
	TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "AUTH429", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
}
