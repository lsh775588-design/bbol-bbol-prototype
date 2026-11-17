package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.repository.RefreshTokenStore
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AcceptingVerifierConfig
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AcceptingVerifierConfig::class)
class AuthApiTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var json: JsonMapper
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var encoder: PasswordEncoder
	@Autowired lateinit var refreshStore: RefreshTokenStore
	@Autowired lateinit var verifier: AcceptingVerifierConfig.FakeVerifier

	@BeforeEach
	fun clean() {
		jdbc.update("DELETE FROM user_interests")
		jdbc.update("DELETE FROM users")
		verifier.consumed.clear()
	}

	private fun signup(token: String?, password: String?, nickname: String?): ResultActions {
		val body = HashMap<String, Any>()
		token?.let { body["verificationToken"] = it }
		password?.let { body["password"] = it }
		nickname?.let { body["nickname"] = it }
		return mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
	}

	private fun login(email: String?, password: String?): ResultActions {
		val body = HashMap<String, Any>()
		email?.let { body["email"] = it }
		password?.let { body["password"] = it }
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
	}

	@Suppress("UNCHECKED_CAST")
	private fun keys(result: ResultActions, path: String? = null): Set<String> {
		var node: Any? = json.readValue(result.andReturn().response.contentAsString, Map::class.java)
		if (path != null) node = (node as Map<String, Any?>)[path]
		return (node as Map<String, Any?>).keys
	}

	@Test
	@DisplayName("가입 성공: HTTP 201, COMMON201, 토큰 2개, isNewUser=true, 비밀번호는 BCrypt 해시로 저장")
	fun signupSuccess() {
		signup("ok:bbol@example.com", PW, "뽈뽈이")
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON201"))
			.andExpect(jsonPath("$.message").value("회원가입에 성공했습니다."))
			.andExpect(jsonPath("$.data.userId").isNumber)
			.andExpect(jsonPath("$.data.accessToken").isString)
			.andExpect(jsonPath("$.data.refreshToken").isString)
			.andExpect(jsonPath("$.data.isNewUser").value(true))
		val row = jdbc.queryForMap(
			"SELECT email, password_hash, nickname, nickname_changed_at, status, login_type FROM users",
		)
		assertThat(row["email"]).isEqualTo("bbol@example.com")
		assertThat(row["nickname"]).isEqualTo("뽈뽈이")
		assertThat(row["nickname_changed_at"]).isNull()
		assertThat(row["status"]).isEqualTo("ACTIVE")
		assertThat(row["login_type"]).isEqualTo("EMAIL")
		val hash = row["password_hash"] as String
		assertThat(hash).startsWith("\$2").isNotEqualTo(PW)
		assertThat(encoder.matches(PW, hash)).isTrue()
	}

	@Test
	@DisplayName("응답 JSON 키가 계약과 정확히 같다(signup/login: isNewUser 포함, newUser 아님)")
	fun exactJsonKeys() {
		val signupKeys = keys(signup("ok:k@example.com", PW, "키검사"), "data")
		assertThat(signupKeys).containsExactlyInAnyOrder("userId", "accessToken", "refreshToken", "isNewUser")
		val loginKeys = keys(login("k@example.com", PW), "data")
		assertThat(loginKeys).containsExactlyInAnyOrder("userId", "accessToken", "refreshToken", "isNewUser")
		val top = keys(login("k@example.com", PW))
		assertThat(top).containsExactlyInAnyOrder("success", "code", "message", "data")
		val avail = keys(mvc.perform(get("/auth/email-availability").param("email", "free@example.com")), "data")
		assertThat(avail).containsExactlyInAnyOrder("available", "reason")
		val avail2 = keys(mvc.perform(get("/auth/nickname-availability").param("nickname", "새닉네임")), "data")
		assertThat(avail2).containsExactlyInAnyOrder("available", "reason")
	}

	@Test
	@DisplayName("가입 응답의 access token은 userId이고 refresh token은 type=REFRESH JWT이며 저장소에는 해시만 있다")
	fun signupTokens() {
		val res = signup("ok:t@example.com", PW, "토큰맨").andReturn().response.contentAsString
		val data = json.readTree(res).get("data")
		val userId = data.get("userId").asLong()
		val access = data.get("accessToken").asString()
		val refresh = data.get("refreshToken").asString()
		assertThat(jwt.getUserIdFromAccessToken(access)).isEqualTo(userId)
		assertThat(jwt.getUserIdFromRefreshToken(access)).isNull()
		assertThat(jwt.getUserIdFromRefreshToken(refresh)).isEqualTo(userId)
		assertThat(jwt.getUserIdFromAccessToken(refresh)).isNull()
		val stored = refreshStore.find(userId)
		assertThat(stored).isNotNull().isNotEqualTo(refresh).hasSize(64)
	}

	@Test
	@DisplayName("COMMON400: 필드 누락, 비밀번호/닉네임 형식 위반은 data.errors에 field와 reason")
	fun signupValidation() {
		signup(null, PW, "뽈뽈이").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("verificationToken"))
		signup("ok:a@example.com", "short1!", "뽈뽈이").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("password"))
		signup("ok:a@example.com", "onlyletters!!", "뽈뽈이").andExpect(status().isBadRequest)
		signup("ok:a@example.com", "12345678!!", "뽈뽈이").andExpect(status().isBadRequest)
		signup("ok:a@example.com", "abcdefgh12", "뽈뽈이").andExpect(status().isBadRequest)
		signup("ok:a@example.com", "abcdefgh123456789012!", "뽈뽈이").andExpect(status().isBadRequest)
		signup("ok:a@example.com", PW, "가").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("nickname"))
		signup("ok:a@example.com", PW, "열한글자닉네임입니다요").andExpect(status().isBadRequest)
		signup("ok:a@example.com", PW, "뽈 뽈").andExpect(status().isBadRequest)
		signup("ok:a@example.com", PW, "뽈뽈!").andExpect(status().isBadRequest)
		signup("ok:a@example.com", PW, null).andExpect(status().isBadRequest)
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Int::class.java)).isZero()
	}

	@Test
	@DisplayName("EMAIL4003: 토큰이 유효하지 않으면 400")
	fun signupBadToken() {
		signup("garbage", PW, "뽈뽈이").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4003"))
	}

	@Test
	@DisplayName("가입 후 토큰은 폐기되어 재사용하면 EMAIL4003")
	fun signupTokenConsumed() {
		signup("ok:once@example.com", PW, "한번만").andExpect(status().isCreated)
		signup("ok:once@example.com", PW, "두번째").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4003"))
	}

	@Test
	@DisplayName("USER4001: 금칙어 닉네임(대소문자 무시)")
	fun signupForbiddenNickname() {
		signup("ok:f@example.com", PW, "관리자님").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("USER4001"))
		signup("ok:f@example.com", PW, "ADMIN1").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("USER4001"))
	}

	@Test
	@DisplayName("USER409: 이미 가입된 이메일(대소문자 무시)")
	fun signupDuplicateEmail() {
		signup("ok:dup@example.com", PW, "먼저가입").andExpect(status().isCreated)
		signup("ok:DUP@example.com", PW, "나중가입").andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER409"))
	}

	@Test
	@DisplayName("USER4092: 이미 사용 중인 닉네임")
	fun signupDuplicateNickname() {
		signup("ok:n1@example.com", PW, "겹치는닉").andExpect(status().isCreated)
		signup("ok:n2@example.com", PW, "겹치는닉").andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER4092"))
	}

	@Test
	@DisplayName("로그인 성공: 200, COMMON200, 토큰, isNewUser(온보딩 전이라 true)")
	fun loginSuccess() {
		signup("ok:login@example.com", PW, "로그인맨").andExpect(status().isCreated)
		login("login@example.com", PW)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
			.andExpect(jsonPath("$.data.userId").isNumber)
			.andExpect(jsonPath("$.data.accessToken").isString)
			.andExpect(jsonPath("$.data.refreshToken").isString)
			.andExpect(jsonPath("$.data.isNewUser").value(true))
		login("LOGIN@Example.com", PW).andExpect(status().isOk)
	}

	@Test
	@DisplayName("로그인 isNewUser: 온보딩 완료 사용자는 false")
	fun loginIsNewUserFalse() {
		signup("ok:done@example.com", PW, "온보딩끝").andExpect(status().isCreated)
		jdbc.update("UPDATE users SET onboarding_completed = TRUE")
		login("done@example.com", PW).andExpect(jsonPath("$.data.isNewUser").value(false))
	}

	@Test
	@DisplayName("재로그인하면 refresh token이 교체된다(사용자당 1개)")
	fun loginReplacesRefresh() {
		signup("ok:r@example.com", PW, "리프레시").andExpect(status().isCreated)
		val id = jdbc.queryForObject("SELECT id FROM users", Long::class.java)!!
		val h1 = refreshStore.find(id)!!
		login("r@example.com", PW).andExpect(status().isOk)
		assertThat(refreshStore.find(id)).isNotNull().isNotEqualTo(h1)
	}

	@Test
	@DisplayName("AUTH4011: 이메일 없음과 비밀번호 틀림은 같은 응답")
	fun loginFailure() {
		signup("ok:x@example.com", PW, "엑스맨").andExpect(status().isCreated)
		val wrongPw = login("x@example.com", "wrong1234!").andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011")).andReturn().response.contentAsString
		val noUser = login("nobody@example.com", PW).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011")).andReturn().response.contentAsString
		assertThat(noUser).isEqualTo(wrongPw)
	}

	@Test
	@DisplayName("탈퇴한 사용자는 로그인 불가(AUTH4011)")
	fun loginWithdrawn() {
		signup("ok:w@example.com", PW, "탈퇴예정").andExpect(status().isCreated)
		jdbc.update("UPDATE users SET status = 'WITHDRAWN'")
		login("w@example.com", PW).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011"))
	}

	@Test
	@DisplayName("로그인 COMMON400: 필드 누락")
	fun loginMissingFields() {
		login(null, PW).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		login("a@example.com", null).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("password"))
	}

	@Test
	@DisplayName("이메일 중복 확인: 사용 가능 / DUPLICATED_EMAIL / REGISTERED_WITH_KAKAO / 탈퇴 후 재사용")
	fun emailAvailability() {
		mvc.perform(get("/auth/email-availability").param("email", "free@example.com"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.data.available").value(true))
			.andExpect(jsonPath("$.data.reason").value(nullValue()))
		signup("ok:taken@example.com", PW, "점유중").andExpect(status().isCreated)
		mvc.perform(get("/auth/email-availability").param("email", "Taken@example.com"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.available").value(false))
			.andExpect(jsonPath("$.data.reason").value("DUPLICATED_EMAIL"))
		jdbc.update("UPDATE users SET login_type = 'KAKAO'")
		mvc.perform(get("/auth/email-availability").param("email", "taken@example.com"))
			.andExpect(jsonPath("$.data.available").value(false))
			.andExpect(jsonPath("$.data.reason").value("REGISTERED_WITH_KAKAO"))
		jdbc.update("UPDATE users SET status = 'WITHDRAWN', email = NULL")
		mvc.perform(get("/auth/email-availability").param("email", "taken@example.com"))
			.andExpect(jsonPath("$.data.available").value(true))
	}

	@Test
	@DisplayName("이메일 중복 확인 COMMON400: 누락, 빈 값, 형식 오류")
	fun emailAvailabilityValidation() {
		mvc.perform(get("/auth/email-availability")).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
		mvc.perform(get("/auth/email-availability").param("email", " ")).andExpect(status().isBadRequest)
		mvc.perform(get("/auth/email-availability").param("email", "not-an-email"))
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("email"))
	}

	@Test
	@DisplayName("닉네임 중복 확인: 사용 가능, DUPLICATED, FORBIDDEN_WORD, INVALID_FORMAT(모두 200)")
	fun nicknameAvailability() {
		mvc.perform(get("/auth/nickname-availability").param("nickname", "새닉네임"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.available").value(true))
			.andExpect(jsonPath("$.data.reason").value(nullValue()))
		signup("ok:nick@example.com", PW, "뽈뽈이").andExpect(status().isCreated)
		mvc.perform(get("/auth/nickname-availability").param("nickname", "뽈뽈이"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.available").value(false))
			.andExpect(jsonPath("$.data.reason").value("DUPLICATED"))
		mvc.perform(get("/auth/nickname-availability").param("nickname", "운영자1"))
			.andExpect(jsonPath("$.data.reason").value("FORBIDDEN_WORD"))
		mvc.perform(get("/auth/nickname-availability").param("nickname", "가"))
			.andExpect(jsonPath("$.data.reason").value("INVALID_FORMAT"))
		mvc.perform(get("/auth/nickname-availability").param("nickname", "공 백"))
			.andExpect(jsonPath("$.data.reason").value("INVALID_FORMAT"))
		jdbc.update("UPDATE users SET status = 'WITHDRAWN', nickname = NULL")
		mvc.perform(get("/auth/nickname-availability").param("nickname", "뽈뽈이"))
			.andExpect(jsonPath("$.data.available").value(true))
	}

	@Test
	@DisplayName("닉네임 중복 확인 COMMON400: 누락")
	fun nicknameAvailabilityMissing() {
		mvc.perform(get("/auth/nickname-availability")).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
		mvc.perform(get("/auth/nickname-availability").param("nickname", "")).andExpect(status().isBadRequest)
	}

	companion object {
		const val PW = "bbol1234!"
	}
}
