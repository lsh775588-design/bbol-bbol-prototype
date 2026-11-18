package com.bbolbbol.server.global.infra.mail

/**
 * 메일 발송 (이메일 인증 코드, 비밀번호 재설정 코드)
 *
 * 서비스 코드는 이 인터페이스에만 의존해야 함
 *
 * 발송에 실패하면 org.springframework.mail.MailException이 발생하니,
 * 이메일 서비스에서 잡아서 EMAIL502로 바꿔 응답하면 됨
 */
interface EmailSender {
	fun send(to: String, subject: String, text: String)
}
