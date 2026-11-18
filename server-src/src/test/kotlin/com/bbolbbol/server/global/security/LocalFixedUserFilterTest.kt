package com.bbolbbol.server.global.security

import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import com.bbolbbol.server.support.AllUsersActiveConfig
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DEV ONLY 필터는 local 프로파일 + 프로퍼티가 있을 때만 존재해야 함 */
class LocalFixedUserFilterTest {

	@Nested
	@SpringBootTest(properties = ["bbolbbol.local.fixed-user-id=77"])
	@AutoConfigureMockMvc
	@ActiveProfiles("local")
	@Import(AllUsersActiveConfig::class)
	inner class LocalWithFixedUserId {

		@Autowired
		lateinit var mvc: MockMvc

		@Autowired
		lateinit var jwt: JwtProvider

		@Autowired
		lateinit var ctx: ApplicationContext

		@Test
		fun `토큰 없는 요청은 고정 userId로 인증된다`() {
			assertEquals(1, ctx.getBeansOfType(LocalFixedUserFilter::class.java).size)
			mvc.get("/test-support/whoami").andExpect {
				status { isOk() }
				jsonPath("$.data") { value(77) }
			}
		}

		@Test
		fun `유효한 토큰이 있으면 토큰의 userId가 우선한다`() {
			mvc.get("/test-support/whoami") { header("Authorization", "Bearer ${jwt.createAccessToken(5L)}") }
				.andExpect { jsonPath("$.data") { value(5) } }
		}

		@Test
		fun `잘못된 토큰은 무시되고 고정 userId로 인증된다`() {
			mvc.get("/test-support/whoami") { header("Authorization", "Bearer garbage") }
				.andExpect { jsonPath("$.data") { value(77) } }
		}

		@Test
		fun `application-local 기본값은 userId 1이고 없는 경로는 COMMON404`() {
			mvc.get("/no-such-path").andExpect {
				status { isNotFound() }
				jsonPath("$.code") { value("COMMON404") }
			}
		}
	}

	@Nested
	@SpringBootTest(properties = ["bbolbbol.local.fixed-user-id="])
	@AutoConfigureMockMvc
	@ActiveProfiles("local")
	inner class LocalWithEmptyFixedUserId {

		@Autowired
		lateinit var mvc: MockMvc

		@Autowired
		lateinit var ctx: ApplicationContext

		@Test
		fun `프로퍼티가 비어 있으면 필터가 없고 토큰 없는 요청은 AUTH401`() {
			assertTrue(ctx.getBeansOfType(LocalFixedUserFilter::class.java).isEmpty())
			mvc.get("/test-support/whoami").andExpect {
				status { isUnauthorized() }
				jsonPath("$.code") { value("AUTH401") }
			}
		}
	}

	@Nested
	@SpringBootTest(properties = ["bbolbbol.local.fixed-user-id=77"])
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	inner class TestProfileIgnoresProperty {

		@Autowired
		lateinit var mvc: MockMvc

		@Autowired
		lateinit var ctx: ApplicationContext

		@Test
		fun `local이 아닌 프로파일에서는 프로퍼티가 있어도 필터가 없다`() {
			assertTrue(ctx.getBeansOfType(LocalFixedUserFilter::class.java).isEmpty())
			mvc.get("/test-support/whoami").andExpect {
				status { isUnauthorized() }
				jsonPath("$.code") { value("AUTH401") }
			}
		}
	}
}
