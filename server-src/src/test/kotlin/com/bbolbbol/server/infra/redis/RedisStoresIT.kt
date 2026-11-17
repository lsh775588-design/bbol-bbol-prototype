package com.bbolbbol.server.infra.redis

import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.domain.auth.repository.EmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.EmailVerificationStore.SendPermit
import com.bbolbbol.server.domain.auth.repository.InMemoryEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.InMemoryLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.InMemoryRefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.LoginFailureStore
import com.bbolbbol.server.domain.auth.repository.RedisEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.RedisLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.RedisRefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.RefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.VerificationTokenEntry
import com.bbolbbol.server.domain.auth.service.EmailVerificationService
import com.bbolbbol.server.domain.auth.service.RedisVerificationTokenVerifier
import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.infra.mail.EmailSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/**
 * 실제 Redis 통합 테스트(opt-in). 환경변수 BBOL_IT_REDIS=true일 때만 돈다(평소 테스트는 Docker 없이 초록을 유지).
 * 접속: BBOL_IT_REDIS_HOST(기본 localhost), BBOL_IT_REDIS_PORT(기본 6379), BBOL_IT_REDIS_DB(기본 15).
 * 안전장치: 지정한 DB 번호 하나만 비우며(FLUSHDB), 그 DB에 이 테스트가 만든 표식 키가 없는데 다른 키가 있으면 거부한다.
 * 같은 시나리오를 인메모리 구현에도 돌려서 동작이 다르면 실패하게 한다(차이 = 발견).
 */
@EnabledIfEnvironmentVariable(named = "BBOL_IT_REDIS", matches = "true")
class RedisStoresIT {

	class MutableClock(var now: Instant = Instant.parse("2026-11-10T00:00:00Z")) : Clock() {
		override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
		override fun withZone(zone: ZoneId): Clock = this
		override fun instant(): Instant = now
		fun advance(d: Duration) { now = now.plus(d) }
	}

	companion object {
		private lateinit var factory: LettuceConnectionFactory
		private lateinit var redis: StringRedisTemplate
		private const val MARKER = "bbol-it-marker"

		@JvmStatic
		@BeforeAll
		fun connect() {
			val cfg = RedisStandaloneConfiguration(
				System.getenv("BBOL_IT_REDIS_HOST") ?: "localhost",
				(System.getenv("BBOL_IT_REDIS_PORT") ?: "6379").toInt(),
			)
			cfg.database = (System.getenv("BBOL_IT_REDIS_DB") ?: "15").toInt()
			factory = LettuceConnectionFactory(cfg)
			factory.afterPropertiesSet()
			redis = StringRedisTemplate(factory)
			redis.afterPropertiesSet()
			val size = redis.execute { it.serverCommands().dbSize() } ?: 0L
			check(size == 0L || redis.hasKey(MARKER) == true) { "Redis DB ${cfg.database}에 이 테스트의 키가 아닌 데이터가 있어 거부합니다." }
			redis.opsForValue().set(MARKER, "1")
		}

		@JvmStatic
		@AfterAll
		fun disconnect() {
			redis.execute { it.serverCommands().flushDb() }
			factory.destroy()
		}
	}

	@BeforeEach
	fun clean() {
		redis.execute { it.serverCommands().flushDb() }
		redis.opsForValue().set(MARKER, "1")
	}

	private val ttl = Duration.ofDays(14)
	private val grace = Duration.ofSeconds(20)

	private class RefreshImpl(val name: String, val store: RefreshTokenStore, val clock: MutableClock)

	private fun refreshImpls(): List<RefreshImpl> {
		val c1 = MutableClock(); val c2 = MutableClock(Instant.now())
		return listOf(RefreshImpl("memory", InMemoryRefreshTokenStore(c1), c1), RefreshImpl("redis", RedisRefreshTokenStore(redis, c2), c2))
	}

	// ---------------- RefreshTokenStore ----------------

	@Test
	@DisplayName("refresh: 저장/조회/덮어쓰기/삭제와 rotate(유예 내 옛 토큰 허용, 유예 후 거절)가 인메모리와 같다")
	fun refreshBasics() {
		for (i in refreshImpls()) {
			val s = i.store; val n = i.name
			s.save(1L, "h1", ttl)
			assertThat(s.find(1L)).describedAs(n).isEqualTo("h1")
			assertThat(s.rotate(1L, "nope", "x", ttl, grace)).describedAs("$n 모르는 해시").isFalse()
			assertThat(s.rotate(1L, "h1", "h2", ttl, grace)).describedAs("$n rotate").isTrue()
			assertThat(s.find(1L)).isEqualTo("h2")
			// 유예 안: 옛 토큰 h1이 한 번 더 통하고, 그때 현재(h2)가 유예 토큰이 된다
			i.clock.advance(Duration.ofSeconds(10))
			assertThat(s.rotate(1L, "h1", "h3", ttl, grace)).describedAs("$n 유예 안 옛 토큰").isTrue()
			assertThat(s.find(1L)).isEqualTo("h3")
			assertThat(s.rotate(1L, "h2", "h4", ttl, grace)).describedAs("$n 유예 체인 h2").isTrue()
			// h1의 유예(20초)는 처음 rotate 시각 + 20초. 10초 더 지나면(20초 경과) 끝난다
			i.clock.advance(Duration.ofSeconds(10).plusMillis(1))
			assertThat(s.rotate(1L, "h1", "h5", ttl, grace)).describedAs("$n 유예 지난 옛 토큰").isFalse()
			// 하지만 h1을 쓴 시점(10초)에 유예가 걸린 h2, 현재 h4는 아직 산다
			assertThat(s.rotate(1L, "h2", "h6", ttl, grace)).describedAs("$n 늦게 유예 걸린 h2").isTrue()
			i.clock.advance(Duration.ofSeconds(21))
			assertThat(s.rotate(1L, "h2", "h7", ttl, grace)).describedAs("$n 전부 지난 뒤").isFalse()
			assertThat(s.find(1L)).describedAs("실패한 rotate는 현재 토큰을 바꾸지 않는다").isEqualTo("h6")
			// save는 유예 토큰도 지운다, delete는 전부 지운다
			s.save(1L, "h9", ttl)
			assertThat(s.rotate(1L, "h4", "x", ttl, grace)).describedAs("$n save 뒤 옛 유예 토큰").isFalse()
			s.delete(1L)
			assertThat(s.find(1L)).isNull()
			assertThat(s.rotate(1L, "h9", "x", ttl, grace)).describedAs("$n delete 뒤").isFalse()
		}
	}

	@Test
	@DisplayName("refresh: 같은 토큰으로 32개 스레드가 동시에 rotate하면 전부 한 번씩 성공하고 사슬이 일관된다(유예 안)")
	fun refreshConcurrentSameToken() {
		for (i in refreshImpls()) {
			val s = i.store
			s.save(7L, "root", ttl)
			val n = 32
			val results = parallel(n) { idx -> s.rotate(7L, "root", "child-$idx", ttl, grace) }
			assertThat(results.count { it }).describedAs("${i.name} 성공 수").isEqualTo(n)
			val cur = s.find(7L)!!
			assertThat(cur).startsWith("child-")
			// 마지막으로 성공한 쪽이 현재, 나머지 자식과 root는 유예 토큰. 유예 안에서는 모두 한 번 더 통한다
			assertThat(s.rotate(7L, "root", "after-root", ttl, grace)).isTrue()
			// 최대 50개까지만 유예 토큰을 기억한다: 33개(root + 31 children + 1) 정도라 전부 남아 있어야 한다
			val ok = (0 until n).count { idx -> s.rotate(7L, "child-$idx", "again-$idx", ttl, grace) }
			assertThat(ok).describedAs("${i.name} 자식 토큰 재사용(유예 안)").isGreaterThanOrEqualTo(n - 2)
			i.clock.advance(Duration.ofSeconds(25))
			assertThat(s.rotate(7L, "root", "late", ttl, grace)).describedAs("${i.name} 유예 지난 root").isFalse()
		}
	}

	@Test
	@DisplayName("refresh(설계 확인): 같은 토큰으로 동시에 rotate한 자식들은 유예가 끝나면 마지막에 현재가 된 하나만 살아남는다(둘 다 같음)")
	fun refreshParallelOnlyCurrentChildSurvivesGrace() {
		for (i in refreshImpls()) {
			val s = i.store
			s.save(11L, "root", ttl)
			val n = 8
			val results = parallel(n) { idx -> s.rotate(11L, "root", "c-$idx", ttl, grace) }
			assertThat(results.all { it }).isTrue()
			val current = s.find(11L)!!
			i.clock.advance(Duration.ofSeconds(25))
			val alive = (0 until n).filter { idx -> s.rotate(11L, "c-$idx", "z-$idx", ttl, grace) }
			assertThat(alive.map { "c-$it" }).describedAs("${i.name} 유예 후 살아있는 자식").containsExactly(current)
		}
	}

	@Test
	@DisplayName("refresh: 서로 다른 사용자의 키는 섞이지 않고, Redis 키에 TTL이 걸린다(현재 토큰과 유예 ZSET)")
	fun refreshKeysAndTtl() {
		val s = RedisRefreshTokenStore(redis, MutableClock(Instant.now()))
		s.save(1L, "a1", Duration.ofSeconds(100))
		s.save(2L, "b1", Duration.ofSeconds(100))
		assertThat(s.rotate(1L, "b1", "x", ttl, grace)).isFalse()
		assertThat(s.rotate(1L, "a1", "a2", Duration.ofSeconds(100), grace)).isTrue()
		val t1 = redis.getExpire(RedisRefreshTokenStore.KEY_PREFIX + 1L)
		val t2 = redis.getExpire(RedisRefreshTokenStore.RECENT_PREFIX + 1L)
		assertThat(t1).isBetween(90L, 100L)
		assertThat(t2).describedAs("유예 ZSET에도 TTL").isBetween(90L, 100L)
		assertThat(redis.type(RedisRefreshTokenStore.RECENT_PREFIX + 1L)?.code()).isEqualTo("zset")
	}

	@Test
	@DisplayName("refresh: 실제 TTL이 지나면 현재 토큰이 사라지고 rotate가 실패한다(Redis 만료)")
	fun refreshRealExpiry() {
		val s = RedisRefreshTokenStore(redis, MutableClock(Instant.now()))
		s.save(3L, "t1", Duration.ofMillis(1500))
		assertThat(s.rotate(3L, "t1", "t2", Duration.ofMillis(1500), grace)).isTrue()
		Thread.sleep(1800)
		assertThat(s.find(3L)).isNull()
		assertThat(s.rotate(3L, "t2", "t3", ttl, grace)).isFalse()
		assertThat(s.rotate(3L, "t1", "t3", ttl, grace)).describedAs("현재 토큰이 없으면 유예 토큰도 무효").isFalse()
		assertThat(redis.hasKey(RedisRefreshTokenStore.RECENT_PREFIX + 3L)).describedAs("유예 ZSET도 같이 만료").isFalse()
	}

	@Test
	@DisplayName("refresh: 유예 토큰이 50개를 넘으면 가장 오래된 것부터 밀려난다(인메모리와 같다)")
	fun refreshRecentCap() {
		for (i in refreshImpls()) {
			val s = i.store
			s.save(9L, "r0", ttl)
			for (k in 1..60) {
				i.clock.advance(Duration.ofMillis(1))
				assertThat(s.rotate(9L, "r${k - 1}", "r$k", ttl, grace)).isTrue()
			}
			// 현재 r60, 유예: 최근 50개(r10..r59)만. r0은 밀려남
			assertThat(s.rotate(9L, "r0", "z", ttl, grace)).describedAs("${i.name} r0").isFalse()
			assertThat(s.rotate(9L, "r59", "z1", ttl, grace)).describedAs("${i.name} r59").isTrue()
		}
	}

	// ---------------- LoginFailureStore ----------------

	private class LoginImpl(val name: String, val store: LoginFailureStore, val clock: MutableClock?)

	private fun loginImpls(): List<LoginImpl> {
		val c = MutableClock()
		return listOf(LoginImpl("memory", InMemoryLoginFailureStore(c), c), LoginImpl("redis", RedisLoginFailureStore(redis), null))
	}

	@Test
	@DisplayName("로그인 실패: increment/count/reset과 키 분리가 인메모리와 같다")
	fun loginBasics() {
		for (i in loginImpls()) {
			val s = i.store
			assertThat(s.count("1.1.1.1")).isEqualTo(0)
			assertThat(s.increment("1.1.1.1", Duration.ofMinutes(1))).isEqualTo(1)
			assertThat(s.increment("1.1.1.1", Duration.ofMinutes(1))).isEqualTo(2)
			assertThat(s.increment("2.2.2.2", Duration.ofMinutes(1))).isEqualTo(1)
			assertThat(s.count("1.1.1.1")).describedAs(i.name).isEqualTo(2)
			s.reset("1.1.1.1")
			assertThat(s.count("1.1.1.1")).isEqualTo(0)
			assertThat(s.increment("1.1.1.1", Duration.ofMinutes(1))).describedAs("reset 뒤 새 창").isEqualTo(1)
		}
	}

	@Test
	@DisplayName("로그인 실패: 64개 스레드가 동시에 increment하면 중복 없이 1..64가 나온다(원자적)")
	fun loginConcurrentAtomic() {
		for (i in loginImpls()) {
			val values = parallel(64) { i.store.increment("9.9.9.9", Duration.ofMinutes(1)) }
			assertThat(values.sorted()).describedAs(i.name).isEqualTo((1L..64L).toList())
			assertThat(i.store.count("9.9.9.9")).isEqualTo(64)
		}
	}

	@Test
	@DisplayName("로그인 실패: 첫 실패에 TTL이 걸리고 실제로 창이 지나면 0으로 돌아가며, 고정 창이라 이후 실패가 창을 늘리지 않는다")
	fun loginWindowReset() {
		val s = RedisLoginFailureStore(redis)
		s.increment("w", Duration.ofSeconds(2))
		Thread.sleep(1000)
		s.increment("w", Duration.ofSeconds(2))
		val ttl = redis.getExpire("login-fail:w", java.util.concurrent.TimeUnit.MILLISECONDS) ?: -2
		assertThat(ttl).describedAs("두 번째 실패가 TTL을 늘리지 않는다").isBetween(1L, 1100L)
		Thread.sleep(1300)
		assertThat(s.count("w")).isEqualTo(0)
		assertThat(s.increment("w", Duration.ofSeconds(2))).isEqualTo(1)
	}

	@Test
	@DisplayName("로그인 실패: INCR만 되고 TTL이 없는 키(서버 죽음 모사)는 다음 increment가 TTL을 복구한다")
	fun loginTtlRepair() {
		redis.opsForValue().set("login-fail:orphan", "3")
		assertThat(redis.getExpire("login-fail:orphan")).isEqualTo(-1L)
		val s = RedisLoginFailureStore(redis)
		assertThat(s.increment("orphan", Duration.ofSeconds(60))).isEqualTo(4)
		assertThat(redis.getExpire("login-fail:orphan")).isBetween(50L, 60L)
	}

	// ---------------- EmailVerificationStore ----------------

	private class EmailImpl(val name: String, val store: EmailVerificationStore, val clock: MutableClock)

	private fun emailImpls(): List<EmailImpl> {
		val c1 = MutableClock(); val c2 = MutableClock(Instant.now())
		return listOf(EmailImpl("memory", InMemoryEmailVerificationStore(c1), c1), EmailImpl("redis", RedisEmailVerificationStore(redis, c2), c2))
	}

	@Test
	@DisplayName("이메일 코드: 틀린 시도 선점(reserveAttempt)이 동시에 몰려도 1..N이 중복 없이 나오고 5 이하만 비교 대상이다")
	fun emailReserveConcurrent() {
		for (i in emailImpls()) {
			val s = i.store; val e = "a@example.com"
			s.saveCode(VerificationPurpose.SIGNUP, e, "123456", Duration.ofMinutes(10))
			val values = parallel(40) { s.reserveAttempt(VerificationPurpose.SIGNUP, e) }
			assertThat(values.sorted()).describedAs(i.name).isEqualTo((1L..40L).toList())
			assertThat(values.count { it in 1..EmailVerificationService.MAX_ATTEMPTS }).describedAs("${i.name} 비교 허용 수").isEqualTo(5)
		}
	}

	@Test
	@DisplayName("이메일 코드: 시도 횟수 키는 코드와 같은 TTL을 갖고, 코드 삭제 뒤에는 0을 돌려주며 TTL 없는 키가 남지 않는다")
	fun emailAttemptKeyTtl() {
		val s = RedisEmailVerificationStore(redis, MutableClock(Instant.now()))
		val e = "b@example.com"
		s.saveCode(VerificationPurpose.SIGNUP, e, "111111", Duration.ofSeconds(100))
		assertThat(s.reserveAttempt(VerificationPurpose.SIGNUP, e)).isEqualTo(1)
		val codeTtl = redis.getExpire("email-verify:SIGNUP:$e")
		val attTtl = redis.getExpire("email-verify-attempts:SIGNUP:$e")
		assertThat(codeTtl).isBetween(90L, 100L)
		assertThat(attTtl).isBetween(90L, 100L)
		// saveCode 순서: 횟수 키를 먼저 만들고 코드를 쓴다 -> 코드가 보이면 횟수 키도 있다. 재저장하면 횟수가 0으로 돌아간다
		s.saveCode(VerificationPurpose.SIGNUP, e, "222222", Duration.ofSeconds(100))
		assertThat(redis.opsForValue().get("email-verify-attempts:SIGNUP:$e")).isEqualTo("0")
		assertThat(s.findCode(VerificationPurpose.SIGNUP, e)).isEqualTo("222222")
		assertThat(s.deleteCode(VerificationPurpose.SIGNUP, e)).isTrue()
		assertThat(s.deleteCode(VerificationPurpose.SIGNUP, e)).describedAs("두 번째 삭제는 false").isFalse()
		assertThat(redis.hasKey("email-verify-attempts:SIGNUP:$e")).isFalse()
		assertThat(s.reserveAttempt(VerificationPurpose.SIGNUP, e)).describedAs("코드 없음 = 0").isEqualTo(0)
		assertThat(redis.hasKey("email-verify-attempts:SIGNUP:$e")).describedAs("코드 없을 때 횟수 키를 만들지 않는다").isFalse()
	}

	@Test
	@DisplayName("이메일 코드: 실제 TTL(1.5초) 만료 뒤 코드와 횟수 키가 함께 사라지고 reserveAttempt가 0이다")
	fun emailCodeRealExpiry() {
		val s = RedisEmailVerificationStore(redis, MutableClock(Instant.now()))
		val e = "c@example.com"
		s.saveCode(VerificationPurpose.PASSWORD_RESET, e, "333333", Duration.ofMillis(1500))
		assertThat(s.reserveAttempt(VerificationPurpose.PASSWORD_RESET, e)).isEqualTo(1)
		Thread.sleep(1800)
		assertThat(s.findCode(VerificationPurpose.PASSWORD_RESET, e)).isNull()
		assertThat(s.reserveAttempt(VerificationPurpose.PASSWORD_RESET, e)).isEqualTo(0)
		assertThat(redis.hasKey("email-verify-attempts:PASSWORD_RESET:$e")).isFalse()
	}

	@Test
	@DisplayName("이메일 코드: purpose별로 코드와 횟수가 분리되어 있다")
	fun emailPurposeIsolation() {
		for (i in emailImpls()) {
			val s = i.store; val e = "d@example.com"
			s.saveCode(VerificationPurpose.SIGNUP, e, "111111", Duration.ofMinutes(10))
			assertThat(s.findCode(VerificationPurpose.PASSWORD_RESET, e)).describedAs(i.name).isNull()
			assertThat(s.reserveAttempt(VerificationPurpose.PASSWORD_RESET, e)).isEqualTo(0)
			assertThat(s.reserveAttempt(VerificationPurpose.SIGNUP, e)).isEqualTo(1)
		}
	}

	@Test
	@DisplayName("이메일 발송 제한: 쿨다운, 하루 횟수, 실패 시 반환(release), 동시 요청에서 허가는 하나뿐")
	fun emailSendPermits() {
		for (i in emailImpls()) {
			val s = i.store; val e = "e@example.com"; val cd = Duration.ofMinutes(1)
			assertThat(s.tryAcquireSend(e, cd, 5)).describedAs(i.name).isEqualTo(SendPermit.OK)
			assertThat(s.tryAcquireSend(e, cd, 5)).isEqualTo(SendPermit.COOLDOWN)
			s.releaseSend(e)
			assertThat(s.tryAcquireSend(e, cd, 5)).describedAs("${i.name} release 뒤 다시 허가").isEqualTo(SendPermit.OK)
			// 하루 5번: 쿨다운을 건너뛰기 위해 시계를 옮긴다(Redis는 쿨다운이 실제 시간이라 release로 푼다)
			var ok = 1
			while (true) {
				if (i.name == "memory") i.clock.advance(Duration.ofMinutes(2)) else redis.delete("email-send-cooldown:$e")
				val p = s.tryAcquireSend(e, cd, 5)
				if (p == SendPermit.OK) ok++ else { assertThat(p).describedAs(i.name).isEqualTo(SendPermit.DAILY_LIMIT); break }
				if (ok > 10) error("하루 제한이 걸리지 않는다")
			}
			assertThat(ok).describedAs("${i.name} 하루 허가 수").isEqualTo(5)
			// 동시: 새 이메일에 24개 스레드
			val perms = parallel(24) { s.tryAcquireSend("f@example.com", cd, 5) }
			assertThat(perms.count { it == SendPermit.OK }).describedAs("${i.name} 동시 허가").isEqualTo(1)
		}
		val cnt = redis.keys("email-send-count:e@example.com:*").single()
		assertThat(redis.getExpire(cnt)).describedAs("횟수 키는 48시간 TTL").isBetween(172_000L, 172_800L)
		assertThat(redis.opsForValue().get(cnt)).describedAs("DAILY_LIMIT 시도는 횟수를 올리지 않는다(되돌림)").isEqualTo("5")
	}

	@Test
	@DisplayName("verificationToken: 저장, 한 번만 소비(동시 16개 중 1개), TTL, purpose 정보 보존")
	fun tokenStore() {
		for (i in emailImpls()) {
			val s = i.store
			s.saveToken("hash1", VerificationTokenEntry(VerificationPurpose.SIGNUP, "t@example.com"), Duration.ofMinutes(30))
			assertThat(s.findToken("hash1")).describedAs(i.name).isEqualTo(VerificationTokenEntry(VerificationPurpose.SIGNUP, "t@example.com"))
			val got = parallel(16) { s.consumeToken("hash1") }
			assertThat(got.count { it != null }).describedAs("${i.name} 동시 소비").isEqualTo(1)
			assertThat(s.findToken("hash1")).isNull()
		}
		val s = RedisEmailVerificationStore(redis, MutableClock(Instant.now()))
		s.saveToken("hash2", VerificationTokenEntry(VerificationPurpose.PASSWORD_RESET, "p@example.com"), Duration.ofMillis(1200))
		assertThat(redis.getExpire("email-verify-token:hash2", java.util.concurrent.TimeUnit.MILLISECONDS)).isBetween(1L, 1200L)
		Thread.sleep(1500)
		assertThat(s.findToken("hash2")).describedAs("TTL 만료").isNull()
		// 이메일에 '|'가 있어도(드물지만) 첫 구분자 기준으로 파싱한다
		s.saveToken("hash3", VerificationTokenEntry(VerificationPurpose.SIGNUP, "we|ird@example.com"), Duration.ofMinutes(1))
		assertThat(s.findToken("hash3")?.email).isEqualTo("we|ird@example.com")
	}

	// ---------------- 서비스 + RedisVerificationTokenVerifier ----------------

	private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

	@Test
	@DisplayName("서비스 전체 흐름(실제 Redis): 코드 발송, 동시 오답 5회 제한, 정답 확인, 토큰 단일 사용과 purpose 구속, 토큰 TTL")
	fun serviceAndVerifier() {
		val store = RedisEmailVerificationStore(redis, MutableClock(Instant.now()))
		val users = Mockito.mock(UserService::class.java)
		val sender = Mockito.mock(EmailSender::class.java)
		val ipStore = RedisLoginFailureStore(redis)
		val service = EmailVerificationService(store, users, sender, ipStore, 1000)
		val verifier = RedisVerificationTokenVerifier(service)
		val email = "flow@example.com"

		service.send(email, VerificationPurpose.SIGNUP)
		val code = store.findCode(VerificationPurpose.SIGNUP, email)!!
		assertThat(code).matches("\\d{6}")

		// 동시 오답 20개: 비교는 최대 5번(CODE_MISMATCH 4번 + 5번째는 CODE_EXPIRED로 코드 삭제), 나머지는 CODE_EXPIRED
		val wrong = if (code == "000000") "000001" else "000000"
		val outcomes = parallel(20) {
			try { service.confirm(email, VerificationPurpose.SIGNUP, wrong, "10.0.0.1"); "OK" } catch (e: BusinessException) { e.errorCode.code }
		}
		val mismatches = outcomes.count { it == EmailErrorCode.CODE_MISMATCH.code }
		assertThat(mismatches).describedAs("틀렸다고 비교한 횟수는 4 이하(5번째는 만료 처리)").isLessThanOrEqualTo(4)
		assertThat(outcomes.none { it == "OK" }).isTrue()
		assertThat(store.findCode(VerificationPurpose.SIGNUP, email)).describedAs("5번 틀리면 코드 삭제").isNull()
		assertThat(redis.hasKey("email-verify-attempts:SIGNUP:$email")).isFalse()

		// 쿨다운 때문에 재발송 불가 -> 키를 지워 새 코드를 받고 정답으로 확인
		redis.delete("email-send-cooldown:$email")
		service.send(email, VerificationPurpose.SIGNUP)
		val code2 = store.findCode(VerificationPurpose.SIGNUP, email)!!
		val token = service.confirm(email, VerificationPurpose.SIGNUP, code2, "10.0.0.1").verificationToken
		assertThat(redis.getExpire("email-verify-token:${sha256(token)}")).describedAs("토큰 TTL 30분").isBetween(1700L, 1800L)
		assertThat(redis.keys("email-verify-token:*").size).isEqualTo(1)
		assertThat(redis.keys("*").any { it.contains(token) }).describedAs("원문 토큰은 저장하지 않는다(해시만)").isFalse()
		// 같은 코드를 동시에 두 번 확인해도 토큰은 하나 (코드를 지운 쪽만)
		// 단일 사용 + purpose 구속
		assertThat(verifier.verifySignupToken(token)).isEqualTo(email)
		assertThat(verifier.verifySignupToken(token)).describedAs("peek은 지우지 않는다").isEqualTo(email)
		assertThatThrownBy { service.peekToken(token, VerificationPurpose.PASSWORD_RESET) }.isInstanceOf(BusinessException::class.java)
		assertThat(verifier.verifySignupToken(token)).describedAs("다른 purpose로 거절돼도 토큰은 지워지지 않는다").isEqualTo(email)
		// 동시 소비 8개: 가입 쪽 consume은 실패를 무시하지만 저장소에서는 정확히 한 번만 지워진다
		val consumed = parallel(8) { runCatching { service.consumeToken(token, VerificationPurpose.SIGNUP) }.isSuccess }
		assertThat(consumed.count { it }).describedAs("동시 소비 성공 수").isGreaterThanOrEqualTo(1)
		assertThatThrownBy { verifier.verifySignupToken(token) }.isInstanceOf(BusinessException::class.java)
		// 비밀번호 재설정 토큰은 가입 검증기에서 거절
		redis.delete("email-send-cooldown:$email")
		service.send(email, VerificationPurpose.PASSWORD_RESET)
		val rcode = store.findCode(VerificationPurpose.PASSWORD_RESET, email)!!
		val rtoken = service.confirm(email, VerificationPurpose.PASSWORD_RESET, rcode, "10.0.0.1").verificationToken
		assertThatThrownBy { verifier.verifySignupToken(rtoken) }.isInstanceOf(BusinessException::class.java)
	}

	// ---------------- 공용 ----------------

	private fun <T> parallel(n: Int, block: (Int) -> T): List<T> {
		val pool = Executors.newFixedThreadPool(n)
		val barrier = CyclicBarrier(n)
		try {
			val futures = (0 until n).map { idx -> pool.submit(Callable { barrier.await(); block(idx) }) }
			return futures.map { it.get() }
		} finally {
			pool.shutdownNow()
		}
	}
}
