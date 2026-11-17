package com.bbolbbol.server.domain.auth.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

/** 번호와 설명은 노션 명세(이메일 인증 코드 발송/확인, 비밀번호 재설정)를 그대로 따름. */
enum class EmailErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {
	CODE_MISMATCH(HttpStatus.BAD_REQUEST, "EMAIL4001", "인증 코드가 일치하지 않음"),
	CODE_EXPIRED(HttpStatus.BAD_REQUEST, "EMAIL4002", "인증 코드가 만료되었거나 5번 틀려서 무효됨. 다시 발송해야 함"),
	INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "EMAIL4003", "verificationToken이 없거나 만료됨, 또는 다른 용도의 토큰"),
	RESEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "EMAIL429", "재발송 제한(1분에 1번, 하루 5번)"),
	SEND_FAILED(HttpStatus.BAD_GATEWAY, "EMAIL502", "메일 발송 실패"),
}
