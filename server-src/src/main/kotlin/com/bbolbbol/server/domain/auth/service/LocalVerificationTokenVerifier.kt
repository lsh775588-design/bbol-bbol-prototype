package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * !!! DEV ONLY !!! local 프로파일에서만 만들어짐. 실제 인증 흐름(RedisVerificationTokenVerifier)에 더해,
 * 이메일 인증 없이 가입을 확인하기 위한 고정 토큰도 받아 주는 추가 경로다.
 * 토큰 형식은 "{고정토큰}:{이메일}"이며, 고정토큰은 bbolbbol.local.verification-token(application-local.yml)에서 읽음
 * 이 형식이 아니면 실제 구현에 맡긴다. 다른 프로파일에는 이 프로퍼티도 이 빈도 없음.
 */
@Component
@Primary
@Profile("local")
class LocalVerificationTokenVerifier(
	@Value("\${bbolbbol.local.verification-token}") private val fixedToken: String,
	private val real: RedisVerificationTokenVerifier,
) : VerificationTokenVerifier {

	override fun verifySignupToken(token: String?): String {
		val devEmail = devTokenEmail(token)
		return devEmail ?: real.verifySignupToken(token)
	}

	override fun consume(token: String) {
		// 고정 토큰은 재사용 가능(개발용). 실제 토큰이면 실제 구현이 지운다.
		if (devTokenEmail(token) == null) real.consume(token)
	}

	/** 고정 토큰 형식이면 이메일, 아니면 null(고정 토큰으로 시작하는데 이메일 형식이 틀리면 EMAIL4003). */
	private fun devTokenEmail(token: String?): String? {
		if (token == null || fixedToken.isBlank()) return null
		val sep = token.indexOf(':')
		if (sep <= 0) return null
		val head = token.substring(0, sep)
		val same = MessageDigest.isEqual(head.toByteArray(Charsets.UTF_8), fixedToken.toByteArray(Charsets.UTF_8))
		if (!same) return null
		val email = token.substring(sep + 1).trim()
		if (!SIMPLE_EMAIL.matches(email)) throw BusinessException(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
		return email
	}

	companion object {
		private val SIMPLE_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
	}
}
