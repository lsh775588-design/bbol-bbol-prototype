package com.bbolbbol.server.domain.auth.service

import org.springframework.stereotype.Component

/**
 * 모든 프로파일의 실제 구현. 이메일 인증 확인 API가 발급해서 저장소(운영 Redis, local/test 인메모리)에 둔 토큰만 통과시킨다.
 * SIGNUP 용도 토큰만 허용하고(비밀번호 재설정 토큰은 EMAIL4003), 가입이 끝나면 consume으로 지운다.
 * 이름은 운영 저장소 기준이다. 저장소 구현은 EmailVerificationStore가 고른다.
 */
@Component
class RedisVerificationTokenVerifier(
	private val emailVerificationService: EmailVerificationService,
) : VerificationTokenVerifier {

	override fun verifySignupToken(token: String?): String =
		emailVerificationService.peekToken(token, VerificationPurpose.SIGNUP)

	override fun consume(token: String) {
		// 동시에 두 번 소비되어도 가입은 이미 끝났으므로 실패를 무시한다(이메일 유니크 제약이 중복 가입을 막는다).
		runCatching { emailVerificationService.consumeToken(token, VerificationPurpose.SIGNUP) }
	}
}
