package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.repository.EmailVerificationStore.SendPermit
import com.bbolbbol.server.domain.auth.repository.InMemoryEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.InMemoryLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.InMemoryRefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.VerificationTokenEntry
import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/** 저장소 단위 테스트(스프링 없이). 시간은 제어 가능한 Clock으로 옮긴다. */
class AuthStoresTest {

	private val ttl = Duration.ofDays(14)
	private val grace = Duration.ofSeconds(20)

	@Test
	@DisplayName("refresh rotate: 현재 토큰은 교체되고, 밀려난 토큰은 유예 동안만 한 번 더 통한다")
	fun rotateGrace() {
		val clock = RefreshTokenStoreTest.MutableClock()
		val store = InMemoryRefreshTokenStore(clock)
		store.save(1L, "h1", ttl)
		assertThat(store.rotate(1L, "h1", "h2", ttl, grace)).isTrue()
		assertThat(store.find(1L)).isEqualTo("h2")
		assertThat(store.rotate(1L, "unknown", "hx", ttl, grace)).isFalse()
		assertThat(store.find(1L)).isEqualTo("h2")
		clock.now = clock.now.plusSeconds(19)
		assertThat(store.rotate(1L, "h1", "h3", ttl, grace)).isTrue() // 유예 안의 옛 토큰
		assertThat(store.find(1L)).isEqualTo("h3")
		clock.now = clock.now.plusSeconds(1)
		assertThat(store.rotate(1L, "h1", "h4", ttl, grace)).isFalse() // h1의 유예는 끝
		assertThat(store.rotate(1L, "h2", "h4", ttl, grace)).isTrue() // h2는 h3으로 밀려난 시점부터 20초
		clock.now = clock.now.plusSeconds(21)
		assertThat(store.rotate(1L, "h2", "h5", ttl, grace)).isFalse()
		assertThat(store.rotate(1L, "h4", "h5", ttl, grace)).isTrue()
	}

	@Test
	@DisplayName("refresh rotate: save(로그인)와 delete는 유예 중인 토큰까지 모두 무효로 하고, 다른 사용자와 섞이지 않는다")
	fun saveAndDeleteClearGrace() {
		val store = InMemoryRefreshTokenStore(RefreshTokenStoreTest.MutableClock())
		store.save(1L, "a1", ttl)
		store.rotate(1L, "a1", "a2", ttl, grace)
		store.save(1L, "login", ttl)
		assertThat(store.rotate(1L, "a1", "x", ttl, grace)).isFalse()
		assertThat(store.rotate(1L, "a2", "x", ttl, grace)).isFalse()
		store.rotate(1L, "login", "b2", ttl, grace)
		store.delete(1L)
		assertThat(store.rotate(1L, "login", "x", ttl, grace)).isFalse()
		assertThat(store.find(1L)).isNull()
		store.save(2L, "u2", ttl)
		assertThat(store.rotate(1L, "u2", "x", ttl, grace)).isFalse()
	}

	@Test
	@DisplayName("refresh rotate: TTL(14일)이 지나면 현재 토큰도 유예 토큰도 무효이고, 유예 목록은 최대 개수로 제한된다")
	fun rotateExpiryAndCap() {
		val clock = RefreshTokenStoreTest.MutableClock()
		val store = InMemoryRefreshTokenStore(clock)
		store.save(1L, "h0", ttl)
		var current = "h0"
		repeat(InMemoryRefreshTokenStore.MAX_RECENT + 5) { i ->
			assertThat(store.rotate(1L, current, "h${i + 1}", ttl, grace)).isTrue()
			current = "h${i + 1}"
		}
		assertThat(store.rotate(1L, "h0", "z", ttl, grace)).isFalse() // 오래전에 밀려나 목록에서도 빠짐
		clock.now = clock.now.plus(ttl).plusSeconds(1)
		assertThat(store.rotate(1L, current, "z", ttl, grace)).isFalse()
	}

	@Test
	@DisplayName("refresh rotate: 같은 토큰으로 동시에 호출해도 판단과 교체가 원자적이라 모두 성공하고 유실이 없다")
	fun rotateConcurrent() {
		val store = InMemoryRefreshTokenStore(RefreshTokenStoreTest.MutableClock())
		store.save(1L, "start", ttl)
		val n = 16
		val pool = Executors.newFixedThreadPool(n)
		try {
			val barrier = CyclicBarrier(n)
			val results = (1..n).map { i ->
				pool.submit(Callable { barrier.await(); store.rotate(1L, "start", "new$i", ttl, grace) })
			}.map { it.get() }
			assertThat(results).containsOnly(true)
			assertThat(store.find(1L)).startsWith("new")
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	@DisplayName("이메일 저장소: 발송 허가는 1분 쿨다운과 하루 5번, release는 허가를 돌려주고, 날짜는 Clock(KST) 기준")
	fun sendPermits() {
		val clock = RefreshTokenStoreTest.MutableClock()
		val store = InMemoryEmailVerificationStore(clock)
		val cooldown = Duration.ofMinutes(1)
		assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.OK)
		assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.COOLDOWN)
		assertThat(store.tryAcquireSend("b@x.com", cooldown, 5)).isEqualTo(SendPermit.OK)
		store.releaseSend("a@x.com")
		assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.OK)
		repeat(4) {
			clock.now = clock.now.plusSeconds(61)
			assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.OK)
		}
		clock.now = clock.now.plusSeconds(61)
		assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.DAILY_LIMIT)
		clock.now = clock.now.plus(Duration.ofHours(24))
		assertThat(store.tryAcquireSend("a@x.com", cooldown, 5)).isEqualTo(SendPermit.OK)
	}

	@Test
	@DisplayName("이메일 저장소: 코드 TTL, 틀린 횟수, 선점 삭제, 토큰은 읽으면서 한 번만 지워진다")
	fun codesAndTokens() {
		val clock = RefreshTokenStoreTest.MutableClock()
		val store = InMemoryEmailVerificationStore(clock)
		val p = VerificationPurpose.SIGNUP
		store.saveCode(p, "a@x.com", "123456", Duration.ofMinutes(10))
		assertThat(store.findCode(p, "a@x.com")).isEqualTo("123456")
		assertThat(store.findCode(VerificationPurpose.PASSWORD_RESET, "a@x.com")).isNull()
		assertThat(store.reserveAttempt(p, "a@x.com")).isEqualTo(1)
		assertThat(store.reserveAttempt(p, "a@x.com")).isEqualTo(2)
		store.saveCode(p, "a@x.com", "654321", Duration.ofMinutes(10)) // 다시 저장하면 횟수 초기화
		assertThat(store.reserveAttempt(p, "a@x.com")).isEqualTo(1)
		assertThat(store.deleteCode(p, "a@x.com")).isTrue()
		assertThat(store.deleteCode(p, "a@x.com")).isFalse()
		assertThat(store.reserveAttempt(p, "a@x.com")).isEqualTo(0)

		store.saveCode(p, "b@x.com", "111111", Duration.ofMinutes(10))
		clock.now = clock.now.plus(Duration.ofMinutes(10))
		assertThat(store.findCode(p, "b@x.com")).isNull()
		assertThat(store.deleteCode(p, "b@x.com")).isFalse()

		val entry = VerificationTokenEntry(p, "a@x.com")
		store.saveToken("hash", entry, Duration.ofMinutes(30))
		assertThat(store.findToken("hash")).isEqualTo(entry)
		assertThat(store.consumeToken("hash")).isEqualTo(entry)
		assertThat(store.consumeToken("hash")).isNull()
		store.saveToken("hash2", entry, Duration.ofMinutes(30))
		clock.now = clock.now.plus(Duration.ofMinutes(30))
		assertThat(store.consumeToken("hash2")).isNull()
	}

	@Test
	@DisplayName("로그인 실패 저장소: 첫 실패부터 창이 시작되고 창이 끝나면 사라지며 reset은 지운다")
	fun loginFailures() {
		val clock = RefreshTokenStoreTest.MutableClock()
		val store = InMemoryLoginFailureStore(clock)
		val window = Duration.ofMinutes(1)
		assertThat(store.count("ip")).isEqualTo(0)
		assertThat(store.increment("ip", window)).isEqualTo(1)
		clock.now = clock.now.plusSeconds(30)
		assertThat(store.increment("ip", window)).isEqualTo(2) // 창은 첫 실패 기준이라 연장되지 않는다
		clock.now = clock.now.plusSeconds(30)
		assertThat(store.count("ip")).isEqualTo(0)
		assertThat(store.increment("ip", window)).isEqualTo(1)
		store.reset("ip")
		assertThat(store.count("ip")).isEqualTo(0)
	}
}
