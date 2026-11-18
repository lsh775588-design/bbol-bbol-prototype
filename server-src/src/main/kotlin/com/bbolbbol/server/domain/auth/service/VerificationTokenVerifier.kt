package com.bbolbbol.server.domain.auth.service

/**
 * 가입용 이메일 인증 토큰(verificationToken) 검증 포트. 토큰 방식은 쟁점 2(미결정)라 인터페이스로 분리한다.
 * 구현은 토큰이 없거나 만료되었거나 다른 용도(SIGNUP이 아님)면 EMAIL4003(BusinessException)을 던져야 한다.
 */
interface VerificationTokenVerifier {

	/** SIGNUP 용도 토큰을 검증하고 토큰에 묶인 이메일을 돌려준다. 실패하면 EMAIL4003. */
	fun verifySignupToken(token: String?): String

	/** 가입이 끝난 뒤 토큰을 폐기해 재사용을 막는다. */
	fun consume(token: String)
}
