package com.bbolbbol.server.global.security

import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AuthTestSupport
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import com.bbolbbol.server.support.AllUsersActiveConfig
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 실제 보안 설정 전체를 띄워서 인증 규칙을 확인함 (user 도메인의 UserStatusChecker 구현이 등록된 상태) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AllUsersActiveConfig::class) // 토큰의 userId가 users 테이블에 없어도 공통 동작을 검증함
class SecurityIntegrationTest {

	@Autowired
	lateinit var mvc: MockMvc

	@Autowired
	lateinit var jwt: JwtProvider

	@Autowired
	lateinit var ctx: ApplicationContext

	@Value("\${jwt.secret}")
	lateinit var secret: String

	private fun whoami(header: String?): ResultActionsDsl =
		mvc.get("/test-support/whoami") { header?.let { header("Authorization", it) } }

	private fun ResultActionsDsl.expect401() {
		andExpect {
			status { isUnauthorized() }
			jsonPath("$.success") { value(false) }
			jsonPath("$.code") { value("AUTH401") }
			jsonPath("$.data") { value(null) }
		}
	}

	@Test
	fun `기본 상태에서는 user 도메인의 UserStatusChecker만 있고 LocalFixedUserFilter 빈은 없다`() {
		assertTrue(ctx.getBeansOfType(UserStatusChecker::class.java).containsKey("userStatusCheckerImpl"))
		assertTrue(ctx.getBeansOfType(LocalFixedUserFilter::class.java).isEmpty())
	}

	@Test
	fun `유효한 access token이면 principal은 Long 타입 userId`() {
		whoami("Bearer ${jwt.createAccessToken(42L)}").andExpect {
			status { isOk() }
			jsonPath("$.data") { value(42) }
		}
		mvc.get("/test-support/principal-type") { header("Authorization", "Bearer ${jwt.createAccessToken(42L)}") }
			.andExpect { jsonPath("$.data") { value("java.lang.Long") } }
	}

	@Test
	fun `만료된 토큰은 AUTH401`() {
		whoami("Bearer " + AuthTestSupport.token(secret, expiresInMillis = -60_000)).expect401()
	}

	@Test
	fun `다른 키로 서명한 토큰은 AUTH401`() {
		whoami("Bearer " + AuthTestSupport.token("another-FAKE-key-0123456789-abcdefghijklmnop")).expect401()
	}

	@Test
	fun `alg none 토큰은 AUTH401`() {
		whoami("Bearer " + AuthTestSupport.algNoneToken()).expect401()
	}

	@Test
	fun `type 클레임이 없거나 refresh인 토큰은 AUTH401`() {
		whoami("Bearer " + AuthTestSupport.token(secret, type = null)).expect401()
		whoami("Bearer " + AuthTestSupport.token(secret, type = "REFRESH")).expect401()
		whoami("Bearer ${jwt.createRefreshToken(1L)}").expect401()
		whoami("Bearer " + AuthTestSupport.token(secret)).andExpect { status { isOk() } }
	}

	@Test
	fun `subject가 숫자가 아닌 토큰은 AUTH401`() {
		whoami("Bearer " + AuthTestSupport.token(secret, subject = "not-a-number")).expect401()
	}

	@Test
	fun `Authorization 헤더 형식이 틀리거나 없으면 AUTH401`() {
		val token = jwt.createAccessToken(1L)
		listOf("Token $token", "bearer $token", "Basic $token", token, "Bearer", "Bearer ", "").forEach {
			whoami(it).expect401()
		}
		whoami(null).expect401()
	}

	@Test
	fun `401 응답은 JSON UTF-8이고 실패 사유를 노출하지 않는다`() {
		listOf<String?>(null, "Bearer garbage").forEach {
			whoami(it).andExpect {
				status { isUnauthorized() }
				content { contentType("application/json;charset=UTF-8") }
				jsonPath("$.message") { value("로그인이 필요합니다.") }
				content { string(not(containsString("expired"))) }
				content { string(not(containsString("signature"))) }
				content { string(not(containsString("garbage"))) }
			}
		}
	}

	@Test
	fun `공개 경로는 토큰 없이 401도 403도 아니다`() {
		mvc.get("/health").andExpect { status { isOk() } }
		listOf("/auth/anything", "/swagger-ui/index.html", "/v3/api-docs", "/health/deep").forEach {
			val status = mvc.get(it).andReturn().response.status
			assertNotEquals(401, status, it)
			assertNotEquals(403, status, it)
		}
	}

	@Test
	fun `auth logout은 POST일 때 인증이 필요하고 유효 토큰이면 401이 아니다`() {
		mvc.post("/auth/logout").expect401()
		val status = mvc.post("/auth/logout") { header("Authorization", "Bearer ${jwt.createAccessToken(1L)}") }
			.andReturn().response.status
		assertNotEquals(401, status)
	}

	@Test
	fun `그 밖의 경로는 토큰 없이 모두 401`() {
		listOf("/places", "/users/me", "/", "/actuator/env", "/authx", "/healthz", "/test-support/whoami").forEach {
			mvc.get(it).expect401()
		}
		mvc.post("/places") { contentType = org.springframework.http.MediaType.APPLICATION_JSON; content = "{}" }.expect401()
	}

	@Test
	fun `인증 없는 존재하지 않는 경로도 AUTH401이고 로그인하면 COMMON404`() {
		mvc.get("/no-such-path").expect401()
		mvc.get("/no-such-path") { header("Authorization", "Bearer ${jwt.createAccessToken(1L)}") }
			.andExpect { status { isNotFound() }; jsonPath("$.code") { value("COMMON404") } }
		assertEquals(0, ctx.getBeansOfType(LocalFixedUserFilter::class.java).size)
	}
}
