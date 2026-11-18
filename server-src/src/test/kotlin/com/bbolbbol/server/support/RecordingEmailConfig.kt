package com.bbolbbol.server.support

import com.bbolbbol.server.global.infra.mail.EmailSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mail.MailSendException
import java.util.concurrent.CopyOnWriteArrayList

/** 통합 테스트용: 메일을 보내지 않고 기록한다. [RecordingEmailSender.failing]을 켜면 발송 실패(MailException)를 흉내 낸다. */
@TestConfiguration
class RecordingEmailConfig {

	class RecordingEmailSender : EmailSender {
		data class Mail(val to: String, val subject: String, val text: String)

		val mails: MutableList<Mail> = CopyOnWriteArrayList()

		@Volatile
		var failing: Boolean = false

		override fun send(to: String, subject: String, text: String) {
			if (failing) throw MailSendException("simulated failure")
			mails.add(Mail(to, subject, text))
		}

		fun mailsTo(email: String): List<Mail> = mails.filter { it.to == email }

		/** 그 주소로 마지막에 보낸 메일 본문에서 6자리 코드를 꺼낸다. */
		fun lastCode(email: String): String =
			CODE.find(mailsTo(email).last().text)!!.value

		fun clear() = mails.clear()

		companion object {
			private val CODE = Regex("[0-9]{6}")
		}
	}

	@Bean
	@Primary
	fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()
}
