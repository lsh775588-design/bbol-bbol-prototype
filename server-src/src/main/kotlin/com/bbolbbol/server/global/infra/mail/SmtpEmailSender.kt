package com.bbolbbol.server.global.infra.mail

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Gmail SMTP로 메일 보내기
 * 설정은 application.yml의 spring.mail.*
 */
@Component
class SmtpEmailSender(
	private val mailSender: JavaMailSender,
) : EmailSender {

	override fun send(to: String, subject: String, text: String) {
		val message = SimpleMailMessage().apply {
			setTo(to)
			setSubject(subject)
			setText(text)
		}
		mailSender.send(message)
	}
}
