package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.support.AuthFlowTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

class LoginRateLimitTest : AuthFlowTest() {

	private fun login(email: String, password: String, ip: String) =
		postJson("/auth/login", mapOf("email" to email, "password" to password), ip = ip)

	private fun failTimes(n: Int, ip: String, email: String = "rl@example.com") =
		repeat(n) { login(email, "wrong1234!", ip).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4011")) }

	@Test
	@DisplayName("같은 IP에서 1분에 10번 실패하면 11번째부터 429 AUTH429(맞는 비밀번호도 거절)")
	fun blockedAfterTenFailures() {
		registerEmailUser("rl@example.com", "제한맨")
		failTimes(10, "10.0.0.1")
		login("rl@example.com", PW, "10.0.0.1").andExpect(status().isTooManyRequests)
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("AUTH429"))
	}

	@Test
	@DisplayName("동시에 40개의 틀린 로그인이 와도 비밀번호 검사까지 가는 것은 10개뿐이고 나머지는 429다(BCrypt가 도는 동안 우회 불가)")
	fun concurrentFailuresCannotBypass() {
		registerEmailUser("rl@example.com", "제한맨")
		val n = 40
		val pool = java.util.concurrent.Executors.newFixedThreadPool(n)
		try {
			val barrier = java.util.concurrent.CyclicBarrier(n)
			val codes = (1..n).map {
				pool.submit<Int> {
					barrier.await(10, java.util.concurrent.TimeUnit.SECONDS)
					login("rl@example.com", "wrong1234!", "10.9.9.9").andReturn().response.status
				}
			}.map { it.get(60, java.util.concurrent.TimeUnit.SECONDS) }
			org.assertj.core.api.Assertions.assertThat(codes.count { it == 401 }).isEqualTo(10)
			org.assertj.core.api.Assertions.assertThat(codes.count { it == 429 }).isEqualTo(30)
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	@DisplayName("9번 실패까지는 막지 않는다")
	fun nineFailuresAllowed() {
		registerEmailUser("rl@example.com", "제한맨")
		failTimes(9, "10.0.0.2")
		login("rl@example.com", PW, "10.0.0.2").andExpect(status().isOk)
	}

	@Test
	@DisplayName("IP마다 따로 센다: 한 IP가 막혀도 다른 IP는 로그인할 수 있다")
	fun perIp() {
		registerEmailUser("rl@example.com", "제한맨")
		failTimes(10, "10.0.0.3")
		login("rl@example.com", PW, "10.0.0.3").andExpect(status().isTooManyRequests)
		login("rl@example.com", PW, "10.0.0.4").andExpect(status().isOk)
	}

	@Test
	@DisplayName("1분 창이 지나면 다시 시도할 수 있다(59초까지는 막힘)")
	fun windowReset() {
		registerEmailUser("rl@example.com", "제한맨")
		failTimes(10, "10.0.0.5")
		advance(Duration.ofSeconds(59))
		login("rl@example.com", PW, "10.0.0.5").andExpect(status().isTooManyRequests)
		advance(Duration.ofSeconds(1))
		login("rl@example.com", PW, "10.0.0.5").andExpect(status().isOk)
	}

	@Test
	@DisplayName("성공하면 카운터가 초기화된다(9번 실패 -> 성공 -> 9번 실패해도 안 막힘)")
	fun successResets() {
		registerEmailUser("rl@example.com", "제한맨")
		failTimes(9, "10.0.0.6")
		login("rl@example.com", PW, "10.0.0.6").andExpect(status().isOk)
		failTimes(9, "10.0.0.6")
		login("rl@example.com", PW, "10.0.0.6").andExpect(status().isOk)
	}

	@Test
	@DisplayName("존재하지 않는 이메일 실패도 센다(이메일 존재 여부와 상관없이 같은 제한)")
	fun unknownEmailCounts() {
		failTimes(10, "10.0.0.7", email = "nobody@example.com")
		login("nobody@example.com", PW, "10.0.0.7").andExpect(status().isTooManyRequests)
	}

	@Test
	@DisplayName("입력 검증 실패(COMMON400)는 시도로 세지 않는다")
	fun validationErrorsNotCounted() {
		repeat(15) { postJson("/auth/login", mapOf("email" to "rl@example.com"), ip = "10.0.0.8").andExpect(status().isBadRequest) }
		registerEmailUser("rl@example.com", "제한맨")
		login("rl@example.com", PW, "10.0.0.8").andExpect(status().isOk)
	}

	@Test
	@DisplayName("IP는 연결의 remoteAddr만 쓴다: X-Forwarded-For를 바꿔 보내도 제한을 피할 수 없고, 다른 사용자의 IP로 위장해 그 사람을 막을 수도 없다")
	fun forwardedHeaderIgnored() {
		registerEmailUser("rl@example.com", "제한맨")
		repeat(10) { i ->
			mvc.perform(
				post("/auth/login").contentType(MediaType.APPLICATION_JSON)
					.header("X-Forwarded-For", "203.0.113.$i")
					.content("""{"email":"rl@example.com","password":"wrong1234!"}""")
					.with { it.remoteAddr = "10.0.0.9"; it },
			).andExpect(status().isUnauthorized)
		}
		mvc.perform(
			post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "203.0.113.200")
				.content("""{"email":"rl@example.com","password":"$PW"}""")
				.with { it.remoteAddr = "10.0.0.9"; it },
		).andExpect(status().isTooManyRequests)
		// 위장한 헤더의 값(203.0.113.0)은 실제로는 한 번도 접속하지 않은 주소라 막히지 않는다
		login("rl@example.com", PW, "203.0.113.0").andExpect(status().isOk)
	}
}
