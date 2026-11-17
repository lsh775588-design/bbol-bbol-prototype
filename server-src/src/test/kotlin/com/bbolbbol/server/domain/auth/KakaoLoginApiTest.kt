package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.support.AuthFlowTest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KakaoLoginApiTest : AuthFlowTest() {

	@Test
	@DisplayName("새 카카오 사용자: 200, 자동 가입(닉네임 null, 온보딩 전, 비밀번호 없음), isNewUser=true, suggestedNickname은 다듬은 카카오 닉네임")
	fun newUser() {
		val res = kakaoLogin("DEV-KAKAO|1001|kakao1@Example.com|뽈뽈 탐험가!")
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
			.andExpect(jsonPath("$.data.userId").isNumber)
			.andExpect(jsonPath("$.data.isNewUser").value(true))
			.andExpect(jsonPath("$.data.suggestedNickname").value("뽈뽈탐험가"))
		val data = tree(res).get("data")
		assertThat(keysOf(data)).containsExactlyInAnyOrder("userId", "accessToken", "refreshToken", "isNewUser", "suggestedNickname")
		val row = jdbc.queryForMap("SELECT email, password_hash, login_type, kakao_id, nickname, status, onboarding_completed FROM users")
		assertThat(row["login_type"]).isEqualTo("KAKAO")
		assertThat(row["kakao_id"]).isEqualTo("1001")
		assertThat(row["email"]).isEqualTo("kakao1@example.com")
		assertThat(row["nickname"]).isNull()
		assertThat(row["password_hash"]).isNull()
		assertThat(row["status"]).isEqualTo("ACTIVE")
		assertThat(row["onboarding_completed"]).isEqualTo(false)
		// 토큰은 실제로 쓸 수 있다
		val access = data.get("accessToken").asString()
		getAuth("/users/me", "Bearer $access").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.loginType").value("KAKAO"))
			.andExpect(jsonPath("$.data.nickname").value(nullValue()))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(false))
		postJson("/auth/reissue", mapOf("refreshToken" to data.get("refreshToken").asString())).andExpect(status().isOk)
	}

	@Test
	@DisplayName("같은 카카오 계정으로 다시 로그인하면 같은 사용자이고, 온보딩 전까지 isNewUser=true, 완료 뒤 false")
	fun existingUser() {
		val first = kakaoSession("DEV-KAKAO|1002|k2@example.com|카카오둘")
		val again = tree(kakaoLogin("DEV-KAKAO|1002|k2@example.com|카카오둘").andExpect(status().isOk)).get("data")
		assertThat(again.get("userId").asLong()).isEqualTo(first.userId)
		assertThat(again.get("isNewUser").asBoolean()).isTrue()
		assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1)
		patchJson("/users/me/onboarding", mapOf("nickname" to "카카오둘이", "ageGroup" to "THIRTIES", "gender" to "MALE"), first.bearer)
			.andExpect(status().isOk)
		val done = tree(kakaoLogin("DEV-KAKAO|1002|k2@example.com|카카오둘").andExpect(status().isOk)).get("data")
		assertThat(done.get("isNewUser").asBoolean()).isFalse()
		assertThat(done.get("suggestedNickname").isNull).isTrue() // 이미 닉네임이 있으면 추천하지 않는다
	}

	@Test
	@DisplayName("suggestedNickname이 null인 경우: 다듬고 나면 2자 미만, 기호뿐, 금칙어, 이미 쓰는 닉네임, 카카오 닉네임 없음")
	fun suggestedNicknameNull() {
		registerEmailUser("owner@example.com", "겹침닉네임")
		val cases = mapOf(
			"DEV-KAKAO|2001||가" to "한 글자",
			"DEV-KAKAO|2002||!!!" to "기호뿐",
			"DEV-KAKAO|2003||관리자" to "금칙어",
			"DEV-KAKAO|2004||겹침 닉네임" to "중복",
			"DEV-KAKAO|2005||" to "닉네임 없음",
		)
		for ((token, why) in cases) {
			kakaoLogin(token).andExpect(status().isOk).andExpect(jsonPath("$.data.suggestedNickname").value(nullValue()))
				.also { assertThat(true).`as`(why).isTrue() }
		}
	}

	@Test
	@DisplayName("긴 카카오 닉네임은 10자로 자르고, 영문과 숫자는 살린다")
	fun suggestedNicknameTruncated() {
		kakaoLogin("DEV-KAKAO|2101||가나다라마바사아자차카타").andExpect(jsonPath("$.data.suggestedNickname").value("가나다라마바사아자차"))
		kakaoLogin("DEV-KAKAO|2102||Bbol_Bbol 99").andExpect(jsonPath("$.data.suggestedNickname").value("BbolBbol99"))
	}

	@Test
	@DisplayName("USER4093: 카카오 이메일이 이미 이메일 가입 계정이면 409이고 카카오 사용자를 만들지 않는다(대소문자 무시)")
	fun emailAccountConflict() {
		registerEmailUser("conflict@example.com", "이메일계정")
		kakaoLogin("DEV-KAKAO|3001|Conflict@Example.com|카카오").andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER4093"))
			.andExpect(jsonPath("$.success").value(false))
		assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM users WHERE kakao_id IS NOT NULL")).isZero()
	}

	@Test
	@DisplayName("이메일 가입 계정과 이메일이 같아도 이미 가입한 카카오 사용자는 로그인된다(충돌 검사는 새 가입에만)")
	fun conflictOnlyForNewSignup() {
		val k = kakaoSession("DEV-KAKAO|3002|later@example.com|카카오")
		// 같은 이메일로 이메일 계정을 만들 수는 없다(USER409) -> 충돌이 생길 수 없는 구조
		sendCode("later@example.com").andExpect(status().isConflict)
		kakaoLogin("DEV-KAKAO|3002|later@example.com|카카오").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.userId").value(k.userId))
	}

	@Test
	@DisplayName("탈퇴한 이메일 계정의 이메일은 카카오 가입에 쓸 수 있다")
	fun withdrawnEmailAllowed() {
		val old = registerEmailUser("freed@example.com", "곧탈퇴")
		deleteAuth("/users/me", old.bearer).andExpect(status().isOk)
		kakaoLogin("DEV-KAKAO|3003|freed@example.com|카카오").andExpect(status().isOk)
		assertThat(jdbc.queryForObject("SELECT email FROM users WHERE kakao_id = '3003'", String::class.java)).isEqualTo("freed@example.com")
	}

	@Test
	@DisplayName("이메일 제공에 동의하지 않으면 이메일 없이 가입하고 중복 검사는 하지 않는다(이메일 없는 사용자가 여럿이어도 된다)")
	fun noEmail() {
		kakaoLogin("DEV-KAKAO|4001||이메일없음일").andExpect(status().isOk).andExpect(jsonPath("$.data.isNewUser").value(true))
		kakaoLogin("DEV-KAKAO|4002||이메일없음이").andExpect(status().isOk)
		assertThat(count("SELECT COUNT(*) FROM users WHERE email IS NULL")).isEqualTo(2)
	}

	@Test
	@DisplayName("다른 카카오 계정이 이미 쓰는 이메일이면 이메일 없이 가입시킨다(유니크 위반으로 실패하지 않는다)")
	fun emailUsedByOtherKakaoAccount() {
		kakaoLogin("DEV-KAKAO|5001|shared@example.com|카카오하나").andExpect(status().isOk)
		kakaoLogin("DEV-KAKAO|5002|shared@example.com|카카오둘").andExpect(status().isOk)
		assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(2)
		assertThat(jdbc.queryForObject("SELECT email FROM users WHERE kakao_id = '5002'", String::class.java)).isNull()
	}

	@Test
	@DisplayName("KAKAO401: 만료, 무효, 형식이 틀린 카카오 토큰")
	fun invalidKakaoToken() {
		for (t in listOf("DEV-KAKAO-EXPIRED", "garbage", "DEV-KAKAO|", "DEV-KAKAO||a@example.com|x")) {
			kakaoLogin(t).andExpect(status().isUnauthorized)
				.andExpect(jsonPath("$.code").value("KAKAO401"))
				.andExpect(jsonPath("$.success").value(false))
		}
		assertThat(count("SELECT COUNT(*) FROM users")).isZero()
	}

	@Test
	@DisplayName("KAKAO502: 카카오 서버 응답 실패")
	fun kakaoDown() {
		kakaoLogin("DEV-KAKAO-DOWN").andExpect(status().isBadGateway).andExpect(jsonPath("$.code").value("KAKAO502"))
	}

	@Test
	@DisplayName("COMMON400: kakaoAccessToken 누락과 빈 값")
	fun validation() {
		postJson("/auth/kakao", emptyMap()).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("kakaoAccessToken"))
		postJson("/auth/kakao", mapOf("kakaoAccessToken" to " ")).andExpect(status().isBadRequest)
	}

	@Test
	@DisplayName("카카오 사용자는 이메일 로그인이 안 된다(비밀번호가 없음, AUTH4011)")
	fun noPasswordLogin() {
		kakaoSession("DEV-KAKAO|6001|kpw@example.com|카카오")
		postJson("/auth/login", mapOf("email" to "kpw@example.com", "password" to PW)).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011"))
		// 이메일 중복 확인은 카카오 가입 이메일임을 알려 준다
		mvc.perform(
			org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/email-availability").param("email", "kpw@example.com"),
		).andExpect(jsonPath("$.data.reason").value("REGISTERED_WITH_KAKAO"))
	}

	@Test
	@DisplayName("같은 카카오 계정의 첫 로그인이 동시에 와도 사용자는 1명이고 두 요청 모두 성공한다")
	fun concurrentFirstLogin() {
		val pool = Executors.newFixedThreadPool(2)
		try {
			val barrier = CyclicBarrier(2)
			val results = (1..2).map {
				pool.submit<Pair<Int, Long>> {
					barrier.await(5, TimeUnit.SECONDS)
					val res = kakaoLogin("DEV-KAKAO|9001|race@example.com|경합").andReturn().response
					res.status to (if (res.status == 200) json.readTree(res.contentAsString).get("data").get("userId").asLong() else -1L)
				}
			}.map { it.get(10, TimeUnit.SECONDS) }
			assertThat(results.map { it.first }).containsExactly(200, 200)
			assertThat(results.map { it.second }.toSet()).hasSize(1)
			assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1)
		} finally {
			pool.shutdownNow()
		}
	}
}
