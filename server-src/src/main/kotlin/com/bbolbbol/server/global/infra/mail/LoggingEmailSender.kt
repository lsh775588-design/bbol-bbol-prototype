package com.bbolbbol.server.global.infra.mail

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * !!! DEV ONLY !!! local 프로파일에서만 만들어지는 메일 대체. 메일을 보내지 않고 로그(INFO)에 남긴다.
 * 인증 코드가 본문에 들어 있으므로 서버 로그를 보면 SMTP 없이도 가입, 비밀번호 재설정 흐름을 확인할 수 있다.
 * 운영에는 이 빈이 없다. 그래서 운영 로그에 인증 코드가 남지 않는다(프로파일 가드 테스트로 확인).
 * SmtpEmailSender 빈은 local에도 남아 있지만 @Primary인 이 구현이 먼저 쓰인다.
 */
@Component
@Primary
@Profile("local")
class LoggingEmailSender : EmailSender {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun send(to: String, subject: String, text: String) {
		log.info("[LOCAL MAIL] to={} subject={} body={}", to, subject, text)
	}
}
