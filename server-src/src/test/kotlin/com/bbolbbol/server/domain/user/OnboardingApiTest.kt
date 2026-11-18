package com.bbolbbol.server.domain.user

import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.support.AuthFlowTest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class OnboardingApiTest : AuthFlowTest() {

	@Autowired lateinit var userService: UserService

	private val skip = mapOf("ageGroup" to "UNDISCLOSED", "gender" to "UNDISCLOSED")

	@Test
	@DisplayName("조회(온보딩 전): 200, 명세 문구, ageGroup/gender는 null, interests는 빈 배열, onboardingCompleted=false, 키는 정확히 4개")
	fun getBefore() {
		val s = registerEmailUser("ob1@example.com", "온보딩일")
		val res = getAuth("/users/me/onboarding", s.bearer)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
			.andExpect(jsonPath("$.data.ageGroup").value(nullValue()))
			.andExpect(jsonPath("$.data.gender").value(nullValue()))
			.andExpect(jsonPath("$.data.interests").isEmpty)
			.andExpect(jsonPath("$.data.onboardingCompleted").value(false))
		assertThat(keysOf(tree(res).get("data"))).containsExactlyInAnyOrder("ageGroup", "gender", "interests", "onboardingCompleted")
		assertThat(userService.isOnboardingCompleted(s.userId)).isFalse()
	}

	@Test
	@DisplayName("첫 저장(이메일 가입자): 연령대와 성별로 완료 처리, 응답 키는 5개, 조회와 로그인 isNewUser가 따라 바뀐다")
	fun firstSaveEmailUser() {
		val s = registerEmailUser("ob2@example.com", "온보딩이")
		val res = patchJson(
			"/users/me/onboarding",
			mapOf("ageGroup" to "TWENTIES", "gender" to "UNDISCLOSED", "interests" to listOf("CAFE", "LIBRARY")),
			s.bearer,
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.message").value("온보딩 정보가 저장되었습니다."))
			.andExpect(jsonPath("$.data.nickname").value("온보딩이"))
			.andExpect(jsonPath("$.data.ageGroup").value("TWENTIES"))
			.andExpect(jsonPath("$.data.gender").value("UNDISCLOSED"))
			.andExpect(jsonPath("$.data.interests[0]").value("CAFE"))
			.andExpect(jsonPath("$.data.interests[1]").value("LIBRARY"))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
		assertThat(keysOf(tree(res).get("data"))).containsExactlyInAnyOrder("nickname", "ageGroup", "gender", "interests", "onboardingCompleted")
		getAuth("/users/me/onboarding", s.bearer)
			.andExpect(jsonPath("$.data.ageGroup").value("TWENTIES"))
			.andExpect(jsonPath("$.data.interests.length()").value(2))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
		getAuth("/users/me", s.bearer).andExpect(jsonPath("$.data.onboardingCompleted").value(true))
		postJson("/auth/login", mapOf("email" to "ob2@example.com", "password" to PW)).andExpect(jsonPath("$.data.isNewUser").value(false))
		assertThat(userService.isOnboardingCompleted(s.userId)).isTrue()
	}

	@Test
	@DisplayName("건너뛰기: 연령대와 성별을 둘 다 UNDISCLOSED로 보내면 완료, interests 없이도 된다")
	fun skipOnboarding() {
		val s = registerEmailUser("ob3@example.com", "건너뛰기")
		patchJson("/users/me/onboarding", skip, s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
			.andExpect(jsonPath("$.data.interests").isEmpty)
	}

	@Test
	@DisplayName("COMMON400: 첫 온보딩에서 필수 필드(ageGroup, gender) 누락은 data.errors에 필드별로, 아무것도 저장하지 않는다")
	fun firstSaveMissingFields() {
		val s = registerEmailUser("ob4@example.com", "누락맨")
		patchJson("/users/me/onboarding", mapOf("interests" to listOf("CAFE")), s.bearer).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[?(@.field=='ageGroup')]").exists())
			.andExpect(jsonPath("$.data.errors[?(@.field=='gender')]").exists())
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "TEENS"), s.bearer).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("gender"))
		patchJson("/users/me/onboarding", emptyMap(), s.bearer).andExpect(status().isBadRequest)
		assertThat(userService.isOnboardingCompleted(s.userId)).isFalse()
		assertThat(count("SELECT COUNT(*) FROM user_interests")).isZero()
		assertThat(jdbc.queryForObject("SELECT age_group FROM users", String::class.java)).isNull()
	}

	@Test
	@DisplayName("COMMON400: 없는 ageGroup, gender 값")
	fun invalidEnumValues() {
		val s = registerEmailUser("ob5@example.com", "값검증")
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "NINETIES", "gender" to "MALE"), s.bearer)
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.data.errors[0].field").value("ageGroup"))
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "TEENS", "gender" to "OTHER"), s.bearer)
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.data.errors[0].field").value("gender"))
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "teens", "gender" to "MALE"), s.bearer).andExpect(status().isBadRequest)
	}

	@Test
	@DisplayName("모든 ageGroup, gender 값이 저장된다")
	fun allEnumValues() {
		val s = registerEmailUser("ob6@example.com", "전체값")
		for (age in listOf("TEENS", "TWENTIES", "THIRTIES", "FORTIES", "FIFTIES_PLUS", "UNDISCLOSED")) {
			patchJson("/users/me/onboarding", mapOf("ageGroup" to age, "gender" to "MALE"), s.bearer)
				.andExpect(status().isOk).andExpect(jsonPath("$.data.ageGroup").value(age))
		}
		for (g in listOf("MALE", "FEMALE", "UNDISCLOSED")) {
			patchJson("/users/me/onboarding", mapOf("gender" to g), s.bearer)
				.andExpect(status().isOk).andExpect(jsonPath("$.data.gender").value(g))
		}
	}

	@Test
	@DisplayName("interests: 최대 3개, 없는 코드는 COMMON400(저장 안 함), 빈 배열은 모두 삭제, 통째 교체, 순서 유지, 중복 제거")
	fun interests() {
		val s = registerEmailUser("ob7@example.com", "관심사맨")
		patchJson("/users/me/onboarding", skip + mapOf("interests" to listOf("CAFE", "PARK", "MUSEUM", "MOVIE")), s.bearer)
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.data.errors[0].field").value("interests"))
		patchJson("/users/me/onboarding", skip + mapOf("interests" to listOf("CAFE", "NOPE")), s.bearer)
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.data.errors[0].field").value("interests"))
		assertThat(userService.isOnboardingCompleted(s.userId)).isFalse()

		patchJson("/users/me/onboarding", skip + mapOf("interests" to listOf("LIBRARY", "CAFE", "PARK")), s.bearer)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.interests[0]").value("LIBRARY"))
			.andExpect(jsonPath("$.data.interests[2]").value("PARK"))
		getAuth("/users/me/onboarding", s.bearer).andExpect(jsonPath("$.data.interests[0]").value("LIBRARY"))
		// 같은 코드를 다시 보내도 PK 위반 없이 통째로 교체된다
		patchJson("/users/me/onboarding", mapOf("interests" to listOf("PARK", "PARK", "CAFE")), s.bearer)
			.andExpect(status().isOk).andExpect(jsonPath("$.data.interests.length()").value(2))
		assertThat(count("SELECT COUNT(*) FROM user_interests")).isEqualTo(2)
		// interests를 보내지 않으면 그대로
		patchJson("/users/me/onboarding", mapOf("gender" to "FEMALE"), s.bearer)
			.andExpect(jsonPath("$.data.interests.length()").value(2))
		patchJson("/users/me/onboarding", mapOf("interests" to emptyList<String>()), s.bearer)
			.andExpect(status().isOk).andExpect(jsonPath("$.data.interests").isEmpty)
		assertThat(count("SELECT COUNT(*) FROM user_interests")).isZero()
	}

	@Test
	@DisplayName("PATCH: 완료 뒤에는 보낸 필드만 바꾸고, 없는 필드와 명시적 null은 바꾸지 않는다")
	fun partialUpdateAfterCompletion() {
		val s = registerEmailUser("ob8@example.com", "부분수정")
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "FORTIES", "gender" to "FEMALE"), s.bearer).andExpect(status().isOk)
		patchJson("/users/me/onboarding", mapOf("ageGroup" to "FIFTIES_PLUS"), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.ageGroup").value("FIFTIES_PLUS"))
			.andExpect(jsonPath("$.data.gender").value("FEMALE"))
		patchJson("/users/me/onboarding", mapOf("ageGroup" to null, "gender" to null), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.ageGroup").value("FIFTIES_PLUS"))
			.andExpect(jsonPath("$.data.gender").value("FEMALE"))
		patchJson("/users/me/onboarding", emptyMap(), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
	}

	@Test
	@DisplayName("이메일 가입자가 보낸 nickname은 무시한다(완료 전후 모두, 형식이 틀려도 400이 아님)")
	fun emailUserNicknameIgnored() {
		val s = registerEmailUser("ob9@example.com", "원래닉네임")
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "바꾸려는닉네임"), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.nickname").value("원래닉네임"))
		patchJson("/users/me/onboarding", mapOf("nickname" to "!!잘못된!!"), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.nickname").value("원래닉네임"))
	}

	// ---------- 카카오 가입자 ----------

	private fun kakaoUser(id: String): Session = kakaoSession("DEV-KAKAO|$id|k$id@example.com|카카오$id")

	@Test
	@DisplayName("카카오 가입자의 첫 온보딩은 nickname이 필수다(누락, 형식 오류는 COMMON400이고 저장하지 않는다)")
	fun kakaoNicknameRequired() {
		val s = kakaoUser("101")
		patchJson("/users/me/onboarding", skip, s.bearer).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("nickname"))
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "가"), s.bearer).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("nickname"))
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "공 백"), s.bearer).andExpect(status().isBadRequest)
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "열한글자닉네임입니다요"), s.bearer).andExpect(status().isBadRequest)
		assertThat(userService.isOnboardingCompleted(s.userId)).isFalse()
		assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String::class.java, s.userId)).isNull()
	}

	@Test
	@DisplayName("카카오 가입자 첫 온보딩: 닉네임을 저장하고 완료 처리, 30일 변경 제한에는 포함하지 않아 바로 PATCH /users/me로 바꿀 수 있다")
	fun kakaoFirstOnboardingSetsNickname() {
		val s = kakaoUser("102")
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "뽈뽈탐험가", "interests" to listOf("MUSEUM")), s.bearer)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.nickname").value("뽈뽈탐험가"))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
		assertThat(jdbc.queryForObject("SELECT nickname_changed_at FROM users WHERE id = ?", java.sql.Timestamp::class.java, s.userId)).isNull()
		patchJson("/users/me", mapOf("nickname" to "곧바꾼닉네임"), s.bearer).andExpect(status().isOk)
	}

	@Test
	@DisplayName("카카오 가입자의 닉네임 규칙은 가입과 같다: USER4001(금칙어), USER4092(중복, 대소문자 무시)")
	fun kakaoNicknameRules() {
		registerEmailUser("holder@example.com", "Holder1")
		val s = kakaoUser("103")
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "관리자님"), s.bearer).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("USER4001"))
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "holder1"), s.bearer).andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER4092"))
		assertThat(userService.isOnboardingCompleted(s.userId)).isFalse()
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "Holder2"), s.bearer).andExpect(status().isOk)
	}

	@Test
	@DisplayName("카카오 가입자도 온보딩을 마친 뒤에는 nickname을 보내도 무시한다")
	fun kakaoNicknameIgnoredAfterCompletion() {
		val s = kakaoUser("104")
		patchJson("/users/me/onboarding", skip + mapOf("nickname" to "처음닉네임"), s.bearer).andExpect(status().isOk)
		patchJson("/users/me/onboarding", mapOf("gender" to "MALE", "nickname" to "다른닉네임"), s.bearer).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.nickname").value("처음닉네임"))
	}

	// ---------- 인증 ----------

	@Test
	@DisplayName("AUTH401: 두 API 모두 토큰이 없거나 탈퇴한 사용자면 거절")
	fun requiresAuth() {
		getAuth("/users/me/onboarding").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		patchJson("/users/me/onboarding", skip).andExpect(status().isUnauthorized)
		val s = registerEmailUser("ob10@example.com", "곧탈퇴")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		getAuth("/users/me/onboarding", s.bearer).andExpect(status().isUnauthorized)
		patchJson("/users/me/onboarding", skip, s.bearer).andExpect(status().isUnauthorized)
	}

	@Test
	@DisplayName("isOnboardingCompleted: 없는 사용자는 false")
	fun unknownUser() {
		assertThat(userService.isOnboardingCompleted(424242L)).isFalse()
	}
}
