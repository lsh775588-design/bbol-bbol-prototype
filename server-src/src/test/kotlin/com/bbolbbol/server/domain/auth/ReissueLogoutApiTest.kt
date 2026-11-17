package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.repository.RefreshTokenStore
import com.bbolbbol.server.domain.auth.service.RefreshTokenService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AuthFlowTest
import com.bbolbbol.server.support.AuthTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReissueLogoutApiTest : AuthFlowTest() {

	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var store: RefreshTokenStore
	@Value("\${jwt.secret}") lateinit var secret: String

	private fun reissue(refreshToken: String?): ResultActions =
		postJson("/auth/reissue", if (refreshToken == null) emptyMap() else mapOf("refreshToken" to refreshToken))

	private fun ResultActions.refreshOf(): String = tree(this).get("data").get("refreshToken").asString()
	private fun ResultActions.accessOf(): String = tree(this).get("data").get("accessToken").asString()

	@Test
	@DisplayName("재발급 성공: 200, 명세 문구, data는 accessToken과 refreshToken만, 새 access token으로 API 호출 가능, 저장소엔 새 토큰 해시")
	fun reissueSuccess() {
		val s = registerEmailUser("re@example.com", "재발급맨")
		advance(Duration.ofMinutes(5))
		val res = reissue(s.refreshToken)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("토큰이 재발급되었습니다."))
		val node = tree(res)
		assertThat(keysOf(node)).containsExactlyInAnyOrder("success", "code", "message", "data")
		assertThat(keysOf(node.get("data"))).containsExactlyInAnyOrder("accessToken", "refreshToken")
		val newRefresh = node.get("data").get("refreshToken").asString()
		val newAccess = node.get("data").get("accessToken").asString()
		assertThat(newRefresh).isNotEqualTo(s.refreshToken)
		assertThat(newAccess).isNotEqualTo(s.accessToken)
		assertThat(jwt.getUserIdFromRefreshToken(newRefresh)).isEqualTo(s.userId)
		assertThat(jwt.getUserIdFromAccessToken(newAccess)).isEqualTo(s.userId)
		assertThat(store.find(s.userId)).isEqualTo(RefreshTokenService.hash(newRefresh)).hasSize(64)
		getAuth("/users/me", "Bearer $newAccess").andExpect(status().isOk)
	}

	@Test
	@DisplayName("재발급 직후 20초 동안은 옛 토큰도 받아 준다(응답 유실 복구), 20초가 지나면 AUTH4012, 새 토큰은 계속 유효")
	fun graceWindow() {
		val s = registerEmailUser("grace@example.com", "유예맨")
		val t2 = reissue(s.refreshToken).andExpect(status().isOk).refreshOf()
		advance(Duration.ofSeconds(19))
		val t3 = reissue(s.refreshToken).andExpect(status().isOk).refreshOf() // 응답을 못 받아 같은 토큰으로 다시
		assertThat(t3).isNotEqualTo(t2)
		advance(Duration.ofSeconds(1)) // 첫 재발급으로부터 정확히 20초: 유예 끝
		reissue(s.refreshToken).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4012"))
			.andExpect(jsonPath("$.message").value("refresh token이 만료, 위조되었거나 이미 사용됨"))
		// 유예 중 다시 받은 t3도, 그 직전의 t2도(t3 발급 때 밀려났으므로 19초 뒤까지) 살아 있고, 현재 토큰은 항상 유효하다
		reissue(t2).andExpect(status().isOk)
	}

	@Test
	@DisplayName("유예가 지난 뒤 옛 토큰 재사용은 AUTH4012이고, 그래도 현재 토큰은 유효하다")
	fun reuseAfterGrace() {
		val s = registerEmailUser("reuse@example.com", "재사용맨")
		val t2 = reissue(s.refreshToken).andExpect(status().isOk).refreshOf()
		advance(Duration.ofSeconds(21))
		reissue(s.refreshToken).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		reissue(t2).andExpect(status().isOk)
	}

	@Test
	@DisplayName("유예 안의 옛 토큰으로 재발급하면 그 시점의 현재 토큰도 유예를 받아, 둘 다 쓸 수 있다(응답 두 개를 각각 받은 경우)")
	fun graceChain() {
		val s = registerEmailUser("chain@example.com", "체인맨")
		val t2 = reissue(s.refreshToken).andExpect(status().isOk).refreshOf()
		advance(Duration.ofSeconds(5))
		val t3 = reissue(s.refreshToken).andExpect(status().isOk).refreshOf()
		advance(Duration.ofSeconds(16)) // t0 + 21: 처음 토큰의 유예는 끝, t2의 유예(t0+5+20)는 남음
		reissue(s.refreshToken).andExpect(status().isUnauthorized)
		val t4 = reissue(t2).andExpect(status().isOk).refreshOf()
		reissue(t3).andExpect(status().isOk)
		assertThat(setOf(t2, t3, t4)).hasSize(3)
	}

	@Test
	@DisplayName("동시에 같은 refresh token으로 재발급하는 두 요청이 모두 성공한다(서로 다른 새 토큰), 그 뒤에도 둘 다 쓸 수 있다")
	fun concurrentReissue() {
		repeat(5) { round ->
			val s = registerEmailUser("conc$round@example.com", "동시성$round")
			val pool = Executors.newFixedThreadPool(2)
			try {
				val barrier = CyclicBarrier(2)
				val futures = (1..2).map {
					pool.submit<Pair<Int, String>> {
						barrier.await(5, TimeUnit.SECONDS)
						val res = reissue(s.refreshToken).andReturn().response
						res.status to res.contentAsString
					}
				}
				val results = futures.map { it.get(10, TimeUnit.SECONDS) }
				assertThat(results.map { it.first }).`as`("round $round: ${results.map { it.second }}").containsExactly(200, 200)
				val refreshes = results.map { json.readTree(it.second).get("data").get("refreshToken").asString() }
				assertThat(refreshes[0]).isNotEqualTo(refreshes[1])
				refreshes.forEach { reissue(it).andExpect(status().isOk) }
			} finally {
				pool.shutdownNow()
			}
		}
	}

	@Test
	@DisplayName("동시에 여러 요청(8개)이 와도 모두 성공하고 저장소는 현재 토큰 1개를 유지한다")
	fun manyConcurrentReissue() {
		val s = registerEmailUser("many@example.com", "다중동시")
		val n = 8
		val pool = Executors.newFixedThreadPool(n)
		try {
			val barrier = CyclicBarrier(n)
			val statuses = (1..n).map {
				pool.submit<Int> {
					barrier.await(5, TimeUnit.SECONDS)
					reissue(s.refreshToken).andReturn().response.status
				}
			}.map { it.get(15, TimeUnit.SECONDS) }
			assertThat(statuses).allMatch { it == 200 }
			assertThat(store.find(s.userId)).isNotNull()
		} finally {
			pool.shutdownNow()
		}
	}

	@Test
	@DisplayName("AUTH4012: 지어낸 토큰, 다른 키로 서명한 토큰, alg none, access token, 저장된 적 없는 정상 refresh JWT")
	fun invalidTokens() {
		val s = registerEmailUser("bad@example.com", "위조맨")
		val forged = listOf(
			"garbage",
			AuthTestSupport.token("another-FAKE-secret-another-FAKE-secret-0123456789", subject = s.userId.toString(), type = "REFRESH"),
			AuthTestSupport.algNoneToken(),
			s.accessToken,
			AuthTestSupport.token(secret, subject = s.userId.toString(), type = null),
			jwt.createRefreshToken(s.userId), // 서명과 type은 정상이지만 서버가 발급해 저장한 토큰이 아니다
			jwt.createRefreshToken(987654L),
		)
		for (t in forged) {
			reissue(t).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		}
		// 위조 시도가 정상 토큰을 망가뜨리지 않는다
		reissue(s.refreshToken).andExpect(status().isOk)
	}

	@Test
	@DisplayName("AUTH4012: refresh token 만료(14일)")
	fun expired() {
		val s = registerEmailUser("old@example.com", "오래된토큰")
		advance(Duration.ofDays(14).plusSeconds(1))
		reissue(s.refreshToken).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
	}

	@Test
	@DisplayName("COMMON400: refreshToken 누락과 빈 값")
	fun validation() {
		reissue(null).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("refreshToken"))
		reissue("").andExpect(status().isBadRequest)
	}

	@Test
	@DisplayName("새 기기에서 로그인하면 이전 기기의 refresh token은 AUTH4012(재발급으로 유예 중이던 토큰도 함께 끊긴다)")
	fun newLoginReplaces() {
		val first = registerEmailUser("two@example.com", "두기기")
		val rotated = reissue(first.refreshToken).andExpect(status().isOk).refreshOf()
		val second = session(
			tree(postJson("/auth/login", mapOf("email" to "two@example.com", "password" to PW)).andExpect(status().isOk)).get("data"),
		)
		reissue(first.refreshToken).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		reissue(rotated).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		reissue(second.refreshToken).andExpect(status().isOk)
	}

	@Test
	@DisplayName("탈퇴한 사용자의 refresh token은 AUTH4012")
	fun withdrawnUser() {
		val s = registerEmailUser("wd@example.com", "탈퇴할사람")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		reissue(s.refreshToken).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
	}

	@Test
	@DisplayName("로그아웃 성공: 200, 명세 문구, data null, 저장된 refresh token이 지워져 재발급은 AUTH4012")
	fun logoutSuccess() {
		val s = registerEmailUser("out@example.com", "로그아웃맨")
		postJson("/auth/logout", emptyMap(), s.bearer)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
			.andExpect(jsonPath("$.data").value(nullValue()))
		assertThat(store.find(s.userId)).isNull()
		reissue(s.refreshToken).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		// access token은 서버에 상태가 없어서 만료 전까지는 계속 통한다(알려진 한계)
		getAuth("/users/me", s.bearer).andExpect(status().isOk)
	}

	@Test
	@DisplayName("로그아웃은 유예 중인 옛 토큰까지 모두 지운다")
	fun logoutClearsGrace() {
		val s = registerEmailUser("out2@example.com", "유예로그아웃")
		val t2 = reissue(s.refreshToken).andExpect(status().isOk)
		val newAccess = t2.accessOf()
		postJson("/auth/logout", emptyMap(), "Bearer $newAccess").andExpect(status().isOk)
		reissue(s.refreshToken).andExpect(status().isUnauthorized)
	}

	@Test
	@DisplayName("AUTH401: 로그아웃은 access token이 없거나 유효하지 않으면 거절(refresh token을 Bearer로 보내도 거절)")
	fun logoutRequiresAuth() {
		postJson("/auth/logout", emptyMap()).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH401"))
		postJson("/auth/logout", emptyMap(), "Bearer garbage").andExpect(status().isUnauthorized)
		val s = registerEmailUser("out3@example.com", "토큰혼동")
		postJson("/auth/logout", emptyMap(), "Bearer ${s.refreshToken}").andExpect(status().isUnauthorized)
		assertThat(store.find(s.userId)).isNotNull()
	}

	@Test
	@DisplayName("다른 /auth 경로(reissue, login)는 인증 없이 호출할 수 있다")
	fun otherAuthPathsStayPublic() {
		reissue("garbage").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4012"))
		postJson("/auth/login", mapOf("email" to "nobody@example.com", "password" to PW))
			.andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH4011"))
	}
}
