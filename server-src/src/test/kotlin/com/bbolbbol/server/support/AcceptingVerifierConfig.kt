package com.bbolbbol.server.support

import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.domain.auth.service.VerificationTokenVerifier
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap

/** 통합 테스트용: "ok:{email}" 토큰을 허용하고 consume으로 폐기한다. 그 밖의 토큰은 EMAIL4003. */
@TestConfiguration
class AcceptingVerifierConfig {

	class FakeVerifier : VerificationTokenVerifier {
		val consumed: MutableSet<String> = ConcurrentHashMap.newKeySet()

		override fun verifySignupToken(token: String?): String {
			if (token == null || !token.startsWith("ok:") || token in consumed) {
				throw BusinessException(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
			}
			return token.substring(3)
		}

		override fun consume(token: String) {
			consumed.add(token)
		}
	}

	@Bean
	@Primary
	fun fakeVerificationTokenVerifier(): FakeVerifier = FakeVerifier()
}
