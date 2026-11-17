package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.dto.PasswordPolicy
import com.bbolbbol.server.domain.auth.repository.InMemoryRefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.RedisRefreshTokenStore
import com.bbolbbol.server.domain.auth.service.RefreshTokenService
import com.bbolbbol.server.global.security.jwt.JwtProperties
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Profiles
import org.springframework.core.env.StandardEnvironment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class RefreshTokenStoreTest {

	class MutableClock(var now: Instant = Instant.parse("2026-11-10T00:00:00Z")) : Clock() {
		override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
		override fun withZone(zone: ZoneId): Clock = this
		override fun instant(): Instant = now
	}

	private val properties = JwtProperties(secret = "dev-only-FAKE-unit-test-secret-0123456789abcdef")

	@Test
	@DisplayName("인메모리: 저장/조회/삭제, 사용자당 1개(덮어쓰기), TTL 지나면 만료")
	fun inMemory() {
		val clock = MutableClock()
		val store = InMemoryRefreshTokenStore(clock)
		store.save(1L, "h1", Duration.ofDays(14))
		assertThat(store.find(1L)).isEqualTo("h1")
		store.save(1L, "h2", Duration.ofDays(14))
		assertThat(store.find(1L)).isEqualTo("h2")
		assertThat(store.find(2L)).isNull()
		clock.now = clock.now.plus(Duration.ofDays(14)).minusSeconds(1)
		assertThat(store.find(1L)).isNotNull()
		clock.now = clock.now.plusSeconds(1)
		assertThat(store.find(1L)).isNull()
		store.save(3L, "h3", Duration.ofDays(14))
		store.delete(3L)
		assertThat(store.find(3L)).isNull()
	}

	@Test
	@DisplayName("발급: JWT(type=REFRESH, 매번 다름), 저장소에는 SHA-256 해시만, 14일 TTL")
	fun issue() {
		val clock = MutableClock(Instant.now())
		val store = InMemoryRefreshTokenStore(clock)
		val service = RefreshTokenService(store, JwtProvider(properties, clock), properties)
		val seen = (1..50).map { service.issue(7L) }.toSet()
		assertThat(seen).hasSize(50)
		val token = service.issue(7L)
		assertThat(token.split(".")).hasSize(3)
		val provider = JwtProvider(properties, clock)
		assertThat(provider.getUserIdFromRefreshToken(token)).isEqualTo(7L)
		assertThat(provider.getUserIdFromAccessToken(token)).isNull()
		val stored = store.find(7L)!!
		assertThat(stored).isNotEqualTo(token).doesNotContain(token).matches("[0-9a-f]{64}")
		assertThat(stored).isEqualTo(RefreshTokenService.hash(token))
		assertThat(service.matches(7L, token)).isTrue()
		assertThat(service.matches(7L, token + "x")).isFalse()
		assertThat(service.matches(8L, token)).isFalse()
		assertThat(service.matches(7L, null)).isFalse()
		// 새로 발급하면 이전 토큰은 무효(사용자당 1개)
		val second = service.issue(7L)
		assertThat(service.matches(7L, token)).isFalse()
		assertThat(service.matches(7L, second)).isTrue()
		// TTL = 14일(jwt.refresh-token-expiration 기본값)
		assertThat(properties.refreshTokenExpiration).isEqualTo(Duration.ofDays(14))
		clock.now = clock.now.plus(Duration.ofDays(14)).plusSeconds(1)
		assertThat(service.matches(7L, second)).isFalse()
		// delete
		service.issue(9L)
		service.delete(9L)
		assertThat(store.find(9L)).isNull()
	}

	@Test
	@Suppress("UNCHECKED_CAST")
	@DisplayName("Redis 구현: 키 refresh:{userId}, TTL 전달, 조회, 삭제")
	fun redis() {
		val redis = mock(StringRedisTemplate::class.java)
		val ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
		`when`(redis.opsForValue()).thenReturn(ops)
		`when`(ops.get("refresh:5")).thenReturn("hash5")
		val store = RedisRefreshTokenStore(redis, MutableClock())

		store.save(5L, "hash5", Duration.ofDays(14))
		verify(ops).set("refresh:5", "hash5", Duration.ofDays(14))
		verify(redis).delete("refresh-recent:5") // 로그인으로 교체하면 유예 중인 옛 토큰도 버린다
		assertThat(store.find(5L)).isEqualTo("hash5")
		assertThat(store.find(6L)).isNull()
		store.delete(5L)
		verify(redis).delete(listOf("refresh:5", "refresh-recent:5"))
	}

	@Test
	@DisplayName("프로파일 선택: Redis는 local/test 제외 전부, 인메모리는 local/test만")
	fun profileExpressions() {
		val redisProfile = Profiles.of("!local & !test")
		val memoryProfile = Profiles.of("local | test")
		for (p in listOf("mysql", "prod", "")) {
			val env = StandardEnvironment()
			if (p.isNotEmpty()) env.setActiveProfiles(p)
			assertThat(env.acceptsProfiles(redisProfile)).isTrue()
			assertThat(env.acceptsProfiles(memoryProfile)).isFalse()
		}
		for (p in listOf("local", "test")) {
			val env = StandardEnvironment()
			env.setActiveProfiles(p)
			assertThat(env.acceptsProfiles(redisProfile)).isFalse()
			assertThat(env.acceptsProfiles(memoryProfile)).isTrue()
		}
		// 어노테이션이 위 표현식을 쓰는지 고정
		assertThat(RedisRefreshTokenStore::class.java.getAnnotation(Profile::class.java).value)
			.containsExactly("!local & !test")
		assertThat(InMemoryRefreshTokenStore::class.java.getAnnotation(Profile::class.java).value)
			.containsExactly("local | test")
	}

	@Test
	@DisplayName("비밀번호: BCrypt 해시는 원문과 다르고 솔트로 매번 다르며 matches로 검증된다")
	fun passwordHashing() {
		val enc = BCryptPasswordEncoder()
		val h1 = enc.encode("bbol1234!")!!
		val h2 = enc.encode("bbol1234!")!!
		assertThat(h1).startsWith("\$2").isNotEqualTo("bbol1234!").isNotEqualTo(h2)
		assertThat(enc.matches("bbol1234!", h1)).isTrue()
		assertThat(enc.matches("bbol1234?", h1)).isFalse()
	}

	@Test
	@DisplayName("비밀번호 규칙: 8~20자, 영문+숫자+특수문자 각 1개 이상")
	fun passwordPolicy() {
		val p = Regex(PasswordPolicy.REGEX)
		for (ok in listOf("bbol1234!", "a1!aaaaa", "Zz9~Zz9~Zz9~Zz9~Zz9~")) {
			assertThat(p.matches(ok)).`as`(ok).isTrue()
		}
		for (bad in listOf(
			"bbol12!", "bbolbbol!!", "12345678!", "bbol12345", "bbol 1234!", "bbol1234!bbol1234!bbb", "bbol1234!한글",
		)) {
			assertThat(p.matches(bad)).`as`(bad).isFalse()
		}
	}
}
