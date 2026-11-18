package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.domain.auth.service.LocalVerificationTokenVerifier
import com.bbolbbol.server.domain.auth.service.RedisVerificationTokenVerifier
import com.bbolbbol.server.domain.auth.service.VerificationTokenVerifier
import com.bbolbbol.server.global.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class VerificationTokenVerifierTest {

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	inner class NonLocalProfile {

		@Autowired lateinit var ctx: ApplicationContext
		@Autowired lateinit var env: Environment
		@Autowired lateinit var verifier: VerificationTokenVerifier
		@Autowired lateinit var mvc: MockMvc

		@Test
		@DisplayName("test(비 local) 컨텍스트에는 Local 구현 빈도 고정 토큰 프로퍼티도 없다")
		fun noLocalBeanAndNoProperty() {
			assertThat(ctx.getBeansOfType(LocalVerificationTokenVerifier::class.java)).isEmpty()
			assertThat(env.getProperty("bbolbbol.local.verification-token")).isNull()
			assertThat(verifier).isInstanceOf(RedisVerificationTokenVerifier::class.java)
		}

		@Test
		@DisplayName("발급하지 않은 토큰은 EMAIL4003으로 거부(개발용 토큰 형식 포함)")
		fun rejectsEverything() {
			for (t in listOf("anything", "DEV-ONLY-FAKE-verification-token:a@example.com", "")) {
				val e = assertThrows<BusinessException> { verifier.verifySignupToken(t) }
				assertThat(e.errorCode).isEqualTo(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
				assertThat(e.errorCode.code).isEqualTo("EMAIL4003")
			}
		}

		@Test
		@DisplayName("signup API도 EMAIL4003(400)으로 거부하고 사용자를 만들지 않는다")
		fun signupRejected() {
			mvc.perform(
				post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
					"{\"verificationToken\":\"DEV-ONLY-FAKE-verification-token:z@example.com\"," +
						"\"password\":\"bbol1234!\",\"nickname\":\"거부될닉\"}",
				),
			)
				.andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("EMAIL4003"))
		}
	}

	@Nested
	@SpringBootTest
	@ActiveProfiles("local")
	inner class LocalProfile {

		@Autowired lateinit var ctx: ApplicationContext
		@Autowired lateinit var verifier: VerificationTokenVerifier
		@Autowired lateinit var env: Environment

		@Test
		@DisplayName("local에서는 Local 구현이 쓰이고 고정 토큰 + 이메일만 통과한다")
		fun localAcceptsFixedToken() {
			assertThat(verifier).isInstanceOf(LocalVerificationTokenVerifier::class.java)
			assertThat(ctx.getBeansOfType(RedisVerificationTokenVerifier::class.java)).hasSize(1)
			val fixed = env.getProperty("bbolbbol.local.verification-token")!!
			assertThat(fixed).startsWith("DEV-ONLY-FAKE")
			assertThat(verifier.verifySignupToken("$fixed:Dev@Example.com")).isEqualTo("Dev@Example.com")
			for (bad in listOf("wrong:dev@example.com", fixed, "$fixed:", "$fixed:notemail")) {
				assertThrows<BusinessException> { verifier.verifySignupToken(bad) }
			}
		}
	}
}
