package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.repository.RedisEmailVerificationStore
import com.bbolbbol.server.support.AuthFlowTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 코드 확인의 시도 횟수는 비교 전에 원자적으로 선점해야 한다(동시 추측으로 5번 제한을 넘기지 못함). */
class EmailAttemptSecurityTest : AuthFlowTest() {

	private fun <T> parallel(n: Int, task: (Int) -> T): List<T> {
		val pool = Executors.newFixedThreadPool(n)
		try {
			val barrier = CyclicBarrier(n)
			return (0 until n).map { i -> pool.submit<T> { barrier.await(10, TimeUnit.SECONDS); task(i) } }
				.map { it.get(30, TimeUnit.SECONDS) }
		} finally {
			pool.shutdownNow()
		}
	}

	private fun wrongFor(email: String): String = if (mail.lastCode(email) == "000000") "000001" else "000000"

	private fun confirmFrom(email: String, code: String, ip: String): Pair<Int, String> {
		val res = postJson(
			"/auth/email-verifications/confirm",
			mapOf("email" to email, "purpose" to "SIGNUP", "code" to code), ip = ip,
		).andReturn().response
		return res.status to json.readTree(res.contentAsString).get("code").asString()
	}

	@Test
	@DisplayName("동시에 40개의 틀린 추측이 와도 비교는 5번뿐이다(EMAIL4001 정확히 4번, 나머지는 EMAIL4002)")
	fun parallelWrongGuessesNeverExceedFive() {
		sendCode("storm@example.com").andExpect(status().isOk)
		val wrong = wrongFor("storm@example.com")
		val results = parallel(40) { i -> confirmFrom("storm@example.com", wrong, "10.1.0.$i") }
		assertThat(results.count { it.second == "EMAIL4001" }).isEqualTo(4)
		assertThat(results.count { it.second == "EMAIL4002" }).isEqualTo(36)
		assertThat(results.count { it.first == 200 }).isZero()
		// 소진된 뒤에는 맞는 코드도 거절한다
		confirmCode("storm@example.com", mail.lastCode("storm@example.com")).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
	}

	@Test
	@DisplayName("동시에 맞는 코드 1개와 틀린 추측 9개: 토큰은 최대 1개이고 선점한 비교 횟수(성공+EMAIL4001)는 5를 넘지 않는다")
	fun parallelCorrectAndWrong() {
		repeat(5) { round ->
			val email = "mix$round@example.com"
			sendCode(email).andExpect(status().isOk)
			val right = mail.lastCode(email)
			val wrong = wrongFor(email)
			val results = parallel(10) { i -> confirmFrom(email, if (i == 0) right else wrong, "10.2.$round.$i") }
			val ok = results.count { it.first == 200 }
			assertThat(ok).isLessThanOrEqualTo(1)
			assertThat(ok + results.count { it.second == "EMAIL4001" }).isLessThanOrEqualTo(5)
			// 성공했다면 코드는 이미 소비되어 다시 쓸 수 없다
			if (ok == 1) confirmFrom(email, right, "10.2.$round.99").also { assertThat(it.second).isEqualTo("EMAIL4002") }
			advance(Duration.ofSeconds(61))
		}
	}

	@Test
	@DisplayName("한 IP가 10분에 30번을 넘게 확인을 시도하면 EMAIL429(여러 메일함을 돌아가며 찍어도 마찬가지), 다른 IP는 영향 없고 창이 지나면 풀린다")
	fun perIpConfirmLimit() {
		repeat(30) { i ->
			confirmFrom("victim$i@example.com", "123456", "10.3.0.1").also { assertThat(it.second).isEqualTo("EMAIL4002") }
		}
		confirmFrom("victim99@example.com", "123456", "10.3.0.1").also {
			assertThat(it.first).isEqualTo(429)
			assertThat(it.second).isEqualTo("EMAIL429")
		}
		confirmFrom("victim99@example.com", "123456", "10.3.0.2").also { assertThat(it.second).isEqualTo("EMAIL4002") }
		advance(Duration.ofMinutes(10))
		confirmFrom("victim99@example.com", "123456", "10.3.0.1").also { assertThat(it.second).isEqualTo("EMAIL4002") }
	}

	@Test
	@DisplayName("IP는 remoteAddr만 쓴다: X-Forwarded-For를 바꿔도 IP 상한을 피할 수 없다")
	fun forwardedHeaderIgnored() {
		repeat(30) { i ->
			mvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/email-verifications/confirm")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.header("X-Forwarded-For", "203.0.113.$i")
					.content("""{"email":"x$i@example.com","purpose":"SIGNUP","code":"123456"}""")
					.with { it.remoteAddr = "10.4.0.1"; it },
			)
		}
		confirmFrom("x99@example.com", "123456", "10.4.0.1").also { assertThat(it.second).isEqualTo("EMAIL429") }
	}

	@Test
	@DisplayName("Redis 저장소: 시도 선점은 코드 키와 횟수 키를 함께 Lua로 처리하고(PEXPIRE로 코드 TTL을 따름), 저장할 때 횟수 키를 코드보다 먼저 만든다")
	fun redisCounterCarriesCodeTtl() {
		@Suppress("UNCHECKED_CAST")
		val ops = mock(ValueOperations::class.java) as ValueOperations<String, String>
		var script: String? = null
		var keys: List<String>? = null
		val redis = mock(StringRedisTemplate::class.java) { inv ->
			when (inv.method.name) {
				"opsForValue" -> ops
				"execute" -> {
					script = (inv.arguments[0] as RedisScript<*>).scriptAsString
					@Suppress("UNCHECKED_CAST")
					keys = inv.arguments[1] as List<String>
					3L
				}
				else -> null
			}
		}
		val store = RedisEmailVerificationStore(redis, Clock.systemUTC())
		val p = com.bbolbbol.server.domain.auth.service.VerificationPurpose.SIGNUP

		assertThat(store.reserveAttempt(p, "a@x.com")).isEqualTo(3L)
		assertThat(script).contains("PEXPIRE").contains("PTTL").contains("INCR")
		assertThat(keys).containsExactly("email-verify:SIGNUP:a@x.com", "email-verify-attempts:SIGNUP:a@x.com")
		store.saveCode(p, "a@x.com", "123456", Duration.ofMinutes(10))
		val order = inOrder(ops)
		order.verify(ops).set("email-verify-attempts:SIGNUP:a@x.com", "0", Duration.ofMinutes(10))
		order.verify(ops).set("email-verify:SIGNUP:a@x.com", "123456", Duration.ofMinutes(10))
	}
}
