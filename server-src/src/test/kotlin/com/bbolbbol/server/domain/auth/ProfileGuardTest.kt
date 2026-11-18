package com.bbolbbol.server.domain.auth

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbolbbol.server.domain.auth.kakao.FakeKakaoClient
import com.bbolbbol.server.domain.auth.kakao.KakaoClient
import com.bbolbbol.server.domain.auth.kakao.RestKakaoClient
import com.bbolbbol.server.domain.auth.repository.InMemoryEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.InMemoryLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.InMemoryRefreshTokenStore
import com.bbolbbol.server.domain.auth.repository.RedisEmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.RedisLoginFailureStore
import com.bbolbbol.server.domain.auth.repository.RedisRefreshTokenStore
import com.bbolbbol.server.domain.auth.service.LocalVerificationTokenVerifier
import com.bbolbbol.server.domain.auth.service.VerificationTokenVerifier
import com.bbolbbol.server.global.infra.mail.EmailSender
import com.bbolbbol.server.global.infra.mail.LoggingEmailSender
import com.bbolbbol.server.global.infra.mail.SmtpEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Profiles
import org.springframework.core.env.StandardEnvironment
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** 개발용 빈(LoggingEmailSender, FakeKakaoClient)이 local, test 밖에는 없고, 운영용 구현은 그 밖에서만 쓰이는지 고정한다. */
class ProfileGuardTest {

	private fun accepts(clazz: Class<*>, vararg active: String): Boolean {
		val env = StandardEnvironment()
		if (active.isNotEmpty()) env.setActiveProfiles(*active)
		val expressions = clazz.getAnnotation(Profile::class.java)?.value ?: return true
		return env.acceptsProfiles(Profiles.of(*expressions))
	}

	private val devOnlyLocal = listOf(LoggingEmailSender::class.java, LocalVerificationTokenVerifier::class.java)
	private val localAndTest = listOf(
		FakeKakaoClient::class.java, InMemoryEmailVerificationStore::class.java,
		InMemoryRefreshTokenStore::class.java, InMemoryLoginFailureStore::class.java,
	)
	private val production = listOf(
		RestKakaoClient::class.java, RedisEmailVerificationStore::class.java,
		RedisRefreshTokenStore::class.java, RedisLoginFailureStore::class.java,
	)

	@Test
	@DisplayName("LoggingEmailSender, 고정 토큰 검증기는 local에서만 만들어진다(test, prod, 프로파일 없음에서는 없다)")
	fun localOnly() {
		for (c in devOnlyLocal) {
			assertThat(accepts(c, "local")).`as`("${c.simpleName} local").isTrue()
			for (other in listOf("test", "prod", "mysql")) assertThat(accepts(c, other)).`as`("${c.simpleName} $other").isFalse()
			assertThat(accepts(c)).`as`("${c.simpleName} no profile").isFalse()
		}
		assertThat(LoggingEmailSender::class.java.getAnnotation(Primary::class.java)).isNotNull()
	}

	@Test
	@DisplayName("가짜 카카오와 인메모리 저장소는 local, test에서만, Redis와 실제 카카오 구현은 그 밖에서만 만들어진다")
	fun localTestVersusProduction() {
		for (c in localAndTest) {
			for (p in listOf("local", "test")) assertThat(accepts(c, p)).`as`("${c.simpleName} $p").isTrue()
			for (p in listOf("prod", "mysql")) assertThat(accepts(c, p)).`as`("${c.simpleName} $p").isFalse()
			assertThat(accepts(c)).`as`("${c.simpleName} no profile").isFalse()
		}
		for (c in production) {
			for (p in listOf("local", "test")) assertThat(accepts(c, p)).`as`("${c.simpleName} $p").isFalse()
			for (p in listOf("prod", "mysql")) assertThat(accepts(c, p)).`as`("${c.simpleName} $p").isTrue()
			assertThat(accepts(c)).`as`("${c.simpleName} no profile").isTrue()
		}
	}

	@Nested
	@SpringBootTest
	@ActiveProfiles("test")
	inner class TestProfile {
		@Autowired lateinit var ctx: ApplicationContext
		@Autowired lateinit var verifier: VerificationTokenVerifier

		@Test
		@DisplayName("test 컨텍스트: LoggingEmailSender와 고정 토큰 검증기와 실제 카카오 클라이언트는 없고 가짜 카카오가 쓰인다")
		fun beans() {
			assertThat(ctx.getBeansOfType(LoggingEmailSender::class.java)).isEmpty()
			assertThat(ctx.getBeansOfType(LocalVerificationTokenVerifier::class.java)).isEmpty()
			assertThat(ctx.getBeansOfType(RestKakaoClient::class.java)).isEmpty()
			assertThat(ctx.getBeansOfType(RedisEmailVerificationStore::class.java)).isEmpty()
			assertThat(ctx.getBean(KakaoClient::class.java)).isInstanceOf(FakeKakaoClient::class.java)
			// 메일은 실제 SMTP 구현이 남아 있다(test에서는 보내지 않도록 테스트가 대체한다)
			assertThat(ctx.getBean(EmailSender::class.java)).isInstanceOf(SmtpEmailSender::class.java)
		}
	}

	/** local 프로파일: 서버 로그에서 코드를 읽어 가입까지 전체 흐름이 도는지(curl 데모와 같은 경로). */
	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("local")
	inner class LocalProfile {
		@Autowired lateinit var ctx: ApplicationContext
		@Autowired lateinit var mvc: MockMvc
		@Autowired lateinit var json: JsonMapper

		private val appender = ListAppender<ILoggingEvent>()
		private val logger = LoggerFactory.getLogger(LoggingEmailSender::class.java) as Logger

		@BeforeEach
		fun attach() {
			appender.start()
			logger.addAppender(appender)
		}

		@AfterEach
		fun detach() {
			logger.detachAppender(appender)
			appender.stop()
		}

		private fun post(path: String, body: String) =
			mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

		@Test
		@DisplayName("local 컨텍스트: LoggingEmailSender가 쓰이고 코드가 INFO 로그에 남는다. 로그의 코드로 확인 -> 가입이 끝까지 된다")
		fun logFlow() {
			assertThat(ctx.getBean(EmailSender::class.java)).isInstanceOf(LoggingEmailSender::class.java)
			assertThat(ctx.getBean(KakaoClient::class.java)).isInstanceOf(FakeKakaoClient::class.java)
			post("/auth/email-verifications", """{"email":"Local.Flow@example.com","purpose":"SIGNUP"}""")
				.andExpect(status().isOk)
			val line = appender.list.single { it.level.toString() == "INFO" }.formattedMessage
			assertThat(line).contains("local.flow@example.com")
			val code = Regex("[0-9]{6}").find(line)!!.value
			val token = json.readTree(
				post("/auth/email-verifications/confirm", """{"email":"local.flow@example.com","purpose":"SIGNUP","code":"$code"}""")
					.andExpect(status().isOk).andReturn().response.contentAsString,
			).get("data").get("verificationToken").asString()
			post("/auth/signup", """{"verificationToken":"$token","password":"bbol1234!","nickname":"로컬흐름"}""")
				.andExpect(status().isCreated)
				.andExpect(jsonPath("$.data.isNewUser").value(true))
			// 같은 토큰은 한 번만(고정 개발 토큰이 아니라 실제 토큰 경로로 처리됐다)
			post("/auth/signup", """{"verificationToken":"$token","password":"bbol1234!","nickname":"로컬두번"}""")
				.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
		}

		@Test
		@DisplayName("local에서는 고정 개발 토큰(고정토큰:이메일)도 추가로 통한다")
		fun devTokenStillWorksInLocal() {
			assertThat(ctx.getBean(VerificationTokenVerifier::class.java)).isInstanceOf(LocalVerificationTokenVerifier::class.java)
			post(
				"/auth/signup",
				"""{"verificationToken":"DEV-ONLY-FAKE-verification-token:devtoken@example.com","password":"bbol1234!","nickname":"개발토큰"}""",
			).andExpect(status().isCreated)
		}
	}
}
