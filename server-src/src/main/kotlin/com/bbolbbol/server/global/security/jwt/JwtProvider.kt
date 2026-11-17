package com.bbolbbol.server.global.security.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * 서버 전용 JWT 발급과 검증 담당
 * - subject = userId (이메일 가입자는 socialId가 없기 때문)
 * - type 클레임으로 access / refresh 토큰 구분
 */
@Component
class JwtProvider(
	private val properties: JwtProperties,
	private val clock: Clock,
) {
	private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray())

	fun createAccessToken(userId: Long): String =
		createToken(userId, TokenType.ACCESS, properties.accessTokenExpiration.toMillis())

	fun createRefreshToken(userId: Long): String =
		createToken(userId, TokenType.REFRESH, properties.refreshTokenExpiration.toMillis())

	/** 유효한 access token이면 userId, 아니면(만료·위조·refresh token) null */
	fun getUserIdFromAccessToken(token: String): Long? = parseUserId(token, TokenType.ACCESS)

	/** 유효한 refresh token이면 userId, 아니면 null */
	fun getUserIdFromRefreshToken(token: String): Long? = parseUserId(token, TokenType.REFRESH)

	private fun createToken(userId: Long, type: TokenType, expirationMillis: Long): String {
		val now = clock.millis()
		return Jwts.builder()
			.id(UUID.randomUUID().toString()) // 같은 초에 발급해도 토큰이 겹치지 않도록
			.subject(userId.toString())
			.claim(TYPE_CLAIM, type.name)
			.issuedAt(Date(now))
			.expiration(Date(now + expirationMillis))
			.signWith(key)
			.compact()
	}

	private fun parseUserId(token: String, expectedType: TokenType): Long? =
		try {
			val claims = Jwts.parser()
				.verifyWith(key)
				.clock { Date(clock.millis()) }
				.build()
				.parseSignedClaims(token)
				.payload
			if (claims[TYPE_CLAIM] == expectedType.name) claims.subject.toLongOrNull() else null
		} catch (e: JwtException) {
			null
		} catch (e: IllegalArgumentException) {
			null
		}

	private enum class TokenType { ACCESS, REFRESH }

	companion object {
		private const val TYPE_CLAIM = "type"
	}
}
