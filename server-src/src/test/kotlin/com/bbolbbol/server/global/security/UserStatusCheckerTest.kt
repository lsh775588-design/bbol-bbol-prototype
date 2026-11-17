package com.bbolbbol.server.global.security

import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** user 도메인이 UserStatusChecker 빈을 등록했을 때의 동작 (일반 빈으로 등록해도 쓰이는지 포함) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserStatusCheckerTest.WithdrawnUserConfig::class)
class UserStatusCheckerTest {

	@TestConfiguration
	class WithdrawnUserConfig {
		@Bean
		@Primary
		fun withdrawnUserChecker(): UserStatusChecker = UserStatusChecker { it != WITHDRAWN_USER }
	}

	@Autowired
	lateinit var mvc: MockMvc

	@Autowired
	lateinit var jwt: JwtProvider

	@Test
	fun `탈퇴한 사용자의 유효한 토큰은 AUTH401`() {
		mvc.get("/test-support/whoami") { header("Authorization", "Bearer ${jwt.createAccessToken(WITHDRAWN_USER)}") }
			.andExpect {
				status { isUnauthorized() }
				jsonPath("$.code") { value("AUTH401") }
			}
	}

	@Test
	fun `활성 사용자의 토큰은 통과`() {
		mvc.get("/test-support/whoami") { header("Authorization", "Bearer ${jwt.createAccessToken(1L)}") }
			.andExpect {
				status { isOk() }
				jsonPath("$.data") { value(1) }
			}
	}

	companion object {
		const val WITHDRAWN_USER = 999L
	}
}
