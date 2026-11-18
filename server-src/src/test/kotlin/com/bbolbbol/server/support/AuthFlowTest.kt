package com.bbolbbol.server.support

import com.bbolbbol.server.domain.auth.kakao.FakeKakaoClient
import com.bbolbbol.server.domain.auth.repository.InMemoryEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.InMemoryLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.InMemoryRefreshTokenStore
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant

/**
 * 회원 부가 기능(이메일 인증, 재발급, 탈퇴, 카카오, 온보딩, 로그인 제한) 통합 테스트의 공통 틀.
 * 메일은 기록만 하고(RecordingEmailConfig), 시각은 바꿀 수 있는 Clock(MutableClockConfig)을 쓴다.
 * 테스트마다 DB, 인메모리 저장소, 시각을 처음 상태로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class, RecordingEmailConfig::class)
abstract class AuthFlowTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var json: JsonMapper
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var clock: MutableClockConfig.MutableClock
	@Autowired lateinit var mail: RecordingEmailConfig.RecordingEmailSender
	@Autowired lateinit var emailStore: InMemoryEmailVerificationStore
	@Autowired lateinit var refreshStore: InMemoryRefreshTokenStore
	@Autowired lateinit var loginFailures: InMemoryLoginFailureStore
	@Autowired lateinit var kakao: FakeKakaoClient

	data class Session(val userId: Long, val accessToken: String, val refreshToken: String) {
		val bearer: String get() = "Bearer $accessToken"
	}

	@BeforeEach
	fun resetState() {
		clock.setKst("2026-11-10T12:00:00")
		jdbc.update("DELETE FROM user_interests")
		jdbc.update("DELETE FROM users")
		emailStore.clearAll()
		refreshStore.clearAll()
		loginFailures.clearAll()
		mail.clear()
		mail.failing = false
		kakao.unlinkedIds.clear()
	}

	protected fun advance(duration: Duration) {
		clock.setInstant(clock.instant().plus(duration))
	}

	protected fun now(): Instant = clock.instant()

	protected fun postJson(path: String, body: Map<String, Any?>, bearer: String? = null, ip: String? = null): ResultActions {
		val req = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
		bearer?.let { req.header("Authorization", it) }
		ip?.let { addr -> req.with { it.remoteAddr = addr; it } }
		return mvc.perform(req)
	}

	protected fun patchJson(path: String, body: Map<String, Any?>, bearer: String? = null): ResultActions {
		val req = patch(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
		bearer?.let { req.header("Authorization", it) }
		return mvc.perform(req)
	}

	protected fun getAuth(path: String, bearer: String? = null): ResultActions {
		val req = get(path)
		bearer?.let { req.header("Authorization", it) }
		return mvc.perform(req)
	}

	protected fun deleteAuth(path: String, bearer: String? = null): ResultActions {
		val req = delete(path)
		bearer?.let { req.header("Authorization", it) }
		return mvc.perform(req)
	}

	@Suppress("UNCHECKED_CAST")
	protected fun keysOf(node: JsonNode): Set<String> = (json.readValue(node.toString(), Map::class.java) as Map<String, Any?>).keys

	protected fun tree(result: ResultActions): JsonNode = json.readTree(result.andReturn().response.contentAsString)

	protected fun sendCode(email: String, purpose: String = "SIGNUP"): ResultActions =
		postJson("/auth/email-verifications", mapOf("email" to email, "purpose" to purpose))

	protected fun confirmCode(email: String, code: String, purpose: String = "SIGNUP"): ResultActions =
		postJson("/auth/email-verifications/confirm", mapOf("email" to email, "purpose" to purpose, "code" to code))

	/** 코드 발송과 확인을 실제 흐름으로 거쳐 verificationToken을 받는다. */
	protected fun verifiedToken(email: String, purpose: String = "SIGNUP"): String {
		sendCode(email, purpose).andReturn().response.also { check(it.status == 200) { "send failed: ${it.contentAsString}" } }
		val res = confirmCode(email, mail.lastCode(email.trim().lowercase()), purpose).andReturn().response
		check(res.status == 200) { "confirm failed: ${res.contentAsString}" }
		return json.readTree(res.contentAsString).get("data").get("verificationToken").asString()
	}

	/** 이메일 인증 흐름 전체를 거쳐 가입하고 토큰을 돌려준다. */
	protected fun registerEmailUser(email: String, nickname: String, password: String = PW): Session {
		val token = verifiedToken(email)
		val res = postJson(
			"/auth/signup",
			mapOf("verificationToken" to token, "password" to password, "nickname" to nickname),
		).andReturn().response
		check(res.status == 201) { "signup failed: ${res.contentAsString}" }
		return session(json.readTree(res.contentAsString).get("data"))
	}

	protected fun kakaoLogin(token: String): ResultActions = postJson("/auth/kakao", mapOf("kakaoAccessToken" to token))

	protected fun kakaoSession(token: String): Session {
		val res = kakaoLogin(token).andReturn().response
		check(res.status == 200) { "kakao login failed: ${res.contentAsString}" }
		return session(json.readTree(res.contentAsString).get("data"))
	}

	protected fun session(data: JsonNode) = Session(
		userId = data.get("userId")?.asLong() ?: 0,
		accessToken = data.get("accessToken").asString(),
		refreshToken = data.get("refreshToken").asString(),
	)

	protected fun count(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

	companion object {
		const val PW = "bbol1234!"
	}
}
