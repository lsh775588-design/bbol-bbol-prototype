package com.bbolbbol.server.global.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AllUsersActiveConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** 처리되지 않은 예외 로그에 DB 제약 위반 메시지의 개인정보(이메일, 닉네임)가 남지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AllUsersActiveConfig::class)
class ErrorLogPrivacyTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider

	private val appender = ListAppender<ILoggingEvent>()
	private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

	@BeforeEach
	fun attach() {
		appender.start()
		root.addAppender(appender)
	}

	@AfterEach
	fun detach() {
		root.detachAppender(appender)
		appender.stop()
	}

	@Test
	@DisplayName("DataIntegrityViolation 메시지의 이메일과 닉네임은 INFO 이상 로그에 남지 않고, 예외 클래스와 경로만 남는다")
	fun personalDataNotLogged() {
		mvc.get("/test-support/constraint") { header("Authorization", "Bearer ${jwt.createAccessToken(1L)}") }
			.andExpect { status { isInternalServerError() } }
		val visible = appender.list.filter { it.level.isGreaterOrEqual(Level.INFO) }
		val text = visible.joinToString("\n") { it.formattedMessage + " " + (it.throwableProxy?.message ?: "") }
		assertThat(text).doesNotContain("victim.person").doesNotContain("example.com").doesNotContain("비밀닉네임")
		val err = visible.single { it.level == Level.ERROR && it.loggerName == GlobalExceptionHandler::class.java.name }
		assertThat(err.formattedMessage)
			.contains("DataIntegrityViolationException")
			.contains("/test-support/constraint")
		assertThat(err.throwableProxy).isNull() // 스택은 ERROR에 붙이지 않는다
	}

	@Test
	@DisplayName("sanitize: 이메일, Key (col)=(value), 따옴표 값을 지우고 200자로 자른다")
	fun sanitizeMasks() {
		val out = sanitize("Key (email)=(a.b@x.com) exists; user 'kim' mail c@d.org \"zz\"")
		assertThat(out).doesNotContain("a.b@x.com").doesNotContain("kim").doesNotContain("c@d.org").doesNotContain("zz")
		assertThat(out).contains("(email)=(?)")
		assertThat(sanitize("x".repeat(500))).hasSize(200)
		assertThat(sanitize(null)).isEqualTo("(no message)")
	}

	@Test
	@DisplayName("Hibernate의 SQL 예외 로거는 기본 설정에서 꺼져 있다(제약 위반 메시지를 직접 찍지 않음)")
	fun hibernateSqlLoggerOff() {
		val v = YamlPropertySourceLoader().load("application.yml", ClassPathResource("application.yml"))
			.firstNotNullOfOrNull { it.getProperty("logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper") }
		assertThat(v.toString()).isEqualToIgnoringCase("off")
	}
}
