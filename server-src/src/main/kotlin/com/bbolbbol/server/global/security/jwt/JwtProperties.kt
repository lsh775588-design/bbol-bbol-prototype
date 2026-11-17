package com.bbolbbol.server.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * application.yml의 jwt.* 값
 *
 * @param secret HMAC 서명 키
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
	val secret: String,
	val accessTokenExpiration: Duration = Duration.ofHours(1),
	val refreshTokenExpiration: Duration = Duration.ofDays(14),
) {
	// JWT_SECRET이 없거나 짧으면 알아보기 쉬운 메시지와 함께 서버 시작을 막음
	init {
		require(!secret.startsWith("\${") && secret.toByteArray().size >= MIN_SECRET_BYTES) {
			"JWT_SECRET이 설정되지 않았거나 너무 짧음. .env에 ${MIN_SECRET_BYTES}자 이상의 임의 문자열을 넣어야 함!"
		}
	}

	companion object {
		private const val MIN_SECRET_BYTES = 32
	}
}
