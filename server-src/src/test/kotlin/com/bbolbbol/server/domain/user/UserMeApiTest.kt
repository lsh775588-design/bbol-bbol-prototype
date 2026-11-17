package com.bbolbbol.server.domain.user

import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserMeApiTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var json: JsonMapper

	var userId: Long = 0
	lateinit var auth: String

	@BeforeEach
	fun setUp() {
		jdbc.update("DELETE FROM user_interests")
		jdbc.update("DELETE FROM users")
		val u = userService.createEmailUser(
			"me@example.com",
			"\$2a\$10\$fakehashfakehashfakehashfakehashfakehashfakehashfakeh",
			"내닉네임",
		)
		userId = u.id!!
		auth = "Bearer " + jwt.createAccessToken(userId)
	}

	private fun patchNickname(body: String, authorization: String?): ResultActions {
		val req = patch("/users/me").contentType(MediaType.APPLICATION_JSON).content(body)
		if (authorization != null) req.header("Authorization", authorization)
		return mvc.perform(req)
	}

	@Suppress("UNCHECKED_CAST")
	private fun dataKeys(result: ResultActions): Set<String> {
		val map = json.readValue(result.andReturn().response.contentAsString, Map::class.java) as Map<String, Any?>
		return (map["data"] as Map<String, Any?>).keys
	}

	@Test
	@DisplayName("GET /users/me: 프로필 필드가 명세대로")
	fun me() {
		mvc.perform(get("/users/me").header("Authorization", auth))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.data.userId").value(userId))
			.andExpect(jsonPath("$.data.email").value("me@example.com"))
			.andExpect(jsonPath("$.data.nickname").value("내닉네임"))
			.andExpect(jsonPath("$.data.loginType").value("EMAIL"))
			.andExpect(jsonPath("$.data.onboardingCompleted").value(false))
			.andExpect(jsonPath("$.data.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
			.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
	}

	@Test
	@DisplayName("응답 JSON 키가 계약과 정확히 같다(/users/me, PATCH /users/me)")
	fun exactJsonKeys() {
		val meKeys = dataKeys(mvc.perform(get("/users/me").header("Authorization", auth)))
		assertThat(meKeys).containsExactlyInAnyOrder(
			"userId", "email", "nickname", "loginType", "onboardingCompleted", "createdAt",
		)
		val patchKeys = dataKeys(patchNickname("{\"nickname\":\"키검사\"}", auth))
		assertThat(patchKeys).containsExactlyInAnyOrder("nickname", "nextChangeableAt")
	}

	@Test
	@DisplayName("토큰 없음/잘못된 토큰 -> 401 AUTH401 (GET, PATCH)")
	fun authRequired() {
		mvc.perform(get("/users/me")).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH401"))
		mvc.perform(get("/users/me").header("Authorization", "Bearer bad.token.value"))
			.andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		patchNickname("{\"nickname\":\"바꿀닉\"}", null).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("refresh token으로는 API를 호출할 수 없다(type=REFRESH) -> 401 AUTH401")
	fun refreshTokenRejectedAsAccess() {
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + jwt.createRefreshToken(userId)))
			.andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("탈퇴한 사용자의 유효한 토큰 -> 401 AUTH401")
	fun withdrawnUserTokenRejected() {
		jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId)
		mvc.perform(get("/users/me").header("Authorization", auth))
			.andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		patchNickname("{\"nickname\":\"바꿀닉\"}", auth).andExpect(status().isUnauthorized)
	}

	@Test
	@DisplayName("존재하지 않는 사용자의 유효한 토큰 -> 401 AUTH401")
	fun nonexistentUserTokenRejected() {
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + jwt.createAccessToken(987654L)))
			.andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("PATCH 성공: 닉네임 변경, nextChangeableAt = 지금 + 30일")
	fun changeNickname() {
		val before = LocalDateTime.now()
		patchNickname("{\"nickname\":\"종로탐험가\"}", auth)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("닉네임이 변경되었습니다."))
			.andExpect(jsonPath("$.data.nickname").value("종로탐험가"))
			.andExpect(jsonPath("$.data.nextChangeableAt").isString)
		assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String::class.java, userId))
			.isEqualTo("종로탐험가")
		val changedAt = jdbc.queryForObject(
			"SELECT nickname_changed_at FROM users WHERE id = ?", LocalDateTime::class.java, userId,
		)
		assertThat(changedAt).isAfterOrEqualTo(before.minusSeconds(2))
		mvc.perform(get("/users/me").header("Authorization", auth))
			.andExpect(jsonPath("$.data.nickname").value("종로탐험가"))
	}

	@Test
	@DisplayName("USER4002: 변경 직후 다시 변경하면 400 + data.nextChangeableAt")
	fun changeTwiceWithin30Days() {
		patchNickname("{\"nickname\":\"첫번째\"}", auth).andExpect(status().isOk)
		patchNickname("{\"nickname\":\"두번째\"}", auth)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("USER4002"))
			.andExpect(jsonPath("$.data.nextChangeableAt").isString)
		assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String::class.java, userId))
			.isEqualTo("첫번째")
	}

	@Test
	@DisplayName("30일이 지나면 다시 변경 가능, 가입 때 닉네임(null)은 제한 없음")
	fun changeAfter30Days() {
		patchNickname("{\"nickname\":\"첫번째\"}", auth).andExpect(status().isOk)
		jdbc.update(
			"UPDATE users SET nickname_changed_at = ? WHERE id = ?",
			LocalDateTime.now().minusDays(31), userId,
		)
		patchNickname("{\"nickname\":\"두번째\"}", auth).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.nickname").value("두번째"))
	}

	@Test
	@DisplayName("USER4001: 금칙어, USER4092: 중복(대소문자 무시)/현재 닉네임과 동일")
	fun forbiddenAndDuplicate() {
		userService.createEmailUser("other@example.com", "\$2a\$10\$x", "Taken1")
		patchNickname("{\"nickname\":\"관리자123\"}", auth).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("USER4001"))
		patchNickname("{\"nickname\":\"taken1\"}", auth).andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER4092"))
		patchNickname("{\"nickname\":\"내닉네임\"}", auth).andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER4092"))
		assertThat(
			jdbc.queryForObject(
				"SELECT nickname_changed_at FROM users WHERE id = ?", LocalDateTime::class.java, userId,
			),
		).isNull()
	}

	@Test
	@DisplayName("PATCH COMMON400: 형식 오류, 필드 누락, 깨진 본문")
	fun validation() {
		patchNickname("{\"nickname\":\"가\"}", auth).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("nickname"))
		patchNickname("{\"nickname\":\"뽈 뽈\"}", auth).andExpect(status().isBadRequest)
		patchNickname("{\"nickname\":\"열한글자닉네임입니다요\"}", auth).andExpect(status().isBadRequest)
		patchNickname("{}", auth).andExpect(status().isBadRequest)
		patchNickname("{\"nickname\":\"\"}", auth).andExpect(status().isBadRequest)
		patchNickname("not json", auth).andExpect(status().isBadRequest)
	}

	@Test
	@DisplayName("UserService 공개 메서드: 표시 닉네임, 탈퇴/없음은 탈퇴한 사용자")
	fun displayNicknames() {
		assertThat(userService.getDisplayNickname(userId)).isEqualTo("내닉네임")
		assertThat(userService.isActive(userId)).isTrue()
		jdbc.update("UPDATE users SET status = 'WITHDRAWN', nickname = NULL WHERE id = ?", userId)
		assertThat(userService.isActive(userId)).isFalse()
		assertThat(userService.getDisplayNickname(userId)).isEqualTo(UserService.WITHDRAWN_NICKNAME)
		assertThat(userService.getDisplayNickname(424242L)).isEqualTo(UserService.WITHDRAWN_NICKNAME)
		assertThat(userService.getDisplayNicknames(listOf(userId, 424242L)))
			.containsEntry(userId, UserService.WITHDRAWN_NICKNAME)
			.containsEntry(424242L, UserService.WITHDRAWN_NICKNAME)
	}
}
