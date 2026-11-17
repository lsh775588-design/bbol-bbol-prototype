package com.bbolbbol.server.global.security.jwt

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class JwtProviderTest {

	private val properties = JwtProperties(
		secret = "test-secret-key-for-unit-tests-0123456789abcdef",
		accessTokenExpiration = Duration.ofHours(1),
		refreshTokenExpiration = Duration.ofDays(14),
	)
	private val issuedAt = Instant.parse("2026-11-09T00:00:00Z")

	private fun providerAt(instant: Instant) =
		JwtProvider(properties, Clock.fixed(instant, ZoneId.of("Asia/Seoul")))

	@Test
	fun `access token에서 userId를 꺼낼 수 있다`() {
		val provider = providerAt(issuedAt)
		val token = provider.createAccessToken(42L)

		assertEquals(42L, provider.getUserIdFromAccessToken(token))
	}

	@Test
	fun `refresh token은 access token 자리에 쓸 수 없고 그 반대도 마찬가지다`() {
		val provider = providerAt(issuedAt)

		assertNull(provider.getUserIdFromAccessToken(provider.createRefreshToken(42L)))
		assertNull(provider.getUserIdFromRefreshToken(provider.createAccessToken(42L)))
		assertEquals(42L, provider.getUserIdFromRefreshToken(provider.createRefreshToken(42L)))
	}

	@Test
	fun `access token은 1시간이 지나면 만료된다`() {
		val token = providerAt(issuedAt).createAccessToken(42L)

		assertEquals(42L, providerAt(issuedAt.plus(Duration.ofMinutes(59))).getUserIdFromAccessToken(token))
		assertNull(providerAt(issuedAt.plus(Duration.ofMinutes(61))).getUserIdFromAccessToken(token))
	}

	@Test
	fun `다른 키로 서명했거나 형식이 깨진 토큰은 거절한다`() {
		val other = JwtProvider(
			properties.copy(secret = "another-secret-key-for-unit-tests-9876543210"),
			Clock.fixed(issuedAt, ZoneId.of("Asia/Seoul")),
		)
		val provider = providerAt(issuedAt)

		assertNull(provider.getUserIdFromAccessToken(other.createAccessToken(42L)))
		assertNull(provider.getUserIdFromAccessToken("not-a-jwt"))
		assertNull(provider.getUserIdFromAccessToken(""))
	}

	@Test
	fun `같은 시각에 발급해도 토큰 문자열은 서로 다르다`() {
		val provider = providerAt(issuedAt)

		assertNotEquals(provider.createRefreshToken(42L), provider.createRefreshToken(42L))
	}
}
