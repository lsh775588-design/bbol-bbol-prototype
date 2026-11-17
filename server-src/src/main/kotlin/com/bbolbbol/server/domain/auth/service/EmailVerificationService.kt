package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.dto.EmailConfirmResponse
import com.bbolbbol.server.domain.auth.dto.EmailSendResponse
import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.domain.auth.repository.EmailVerificationStore
import com.bbolbbol.server.domain.auth.repository.LoginFailureStore
import com.bbolbbol.server.domain.auth.repository.EmailVerificationStore.SendPermit
import com.bbolbbol.server.domain.auth.repository.VerificationTokenEntry
import com.bbolbbol.server.domain.user.entity.LoginType
import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.infra.mail.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.HexFormat

/**
 * 이메일 인증(코드 발송, 코드 확인, verificationToken 발급과 소비).
 *
 * 결정과 함정은 코드 옆 주석에 적었다. 핵심만 요약하면:
 * - 코드는 SecureRandom 6자리, 비교는 상수 시간. 5번 틀리면 코드를 지운다(5번째 틀림은 EMAIL4002).
 * - 토큰은 이메일에서 유도하지 않는 무작위 32바이트(base64url)이고 저장소에는 해시만 둔다. 이메일과 purpose가 묶인다.
 * - PASSWORD_RESET은 가입 여부와 상관없이 같은 모양으로 응답한다(쿨다운, 횟수 제한, 코드 확인 단계 포함).
 */
@Service
class EmailVerificationService(
	private val store: EmailVerificationStore,
	private val userService: UserService,
	private val emailSender: EmailSender,
	private val ipAttempts: LoginFailureStore,
	@Value("\${bbolbbol.auth.email-confirm-max-per-ip:30}") private val maxAttemptsPerIp: Int,
) {

	private val log = LoggerFactory.getLogger(javaClass)
	private val random = SecureRandom()

	fun send(rawEmail: String, purpose: VerificationPurpose): EmailSendResponse {
		val email = UserService.normalizeEmail(rawEmail)
		when (purpose) {
			VerificationPurpose.SIGNUP -> sendSignupCode(email)
			VerificationPurpose.PASSWORD_RESET -> sendResetCode(email)
		}
		return EmailSendResponse(expiresIn = CODE_TTL.seconds, resendAvailableIn = COOLDOWN.seconds)
	}

	private fun sendSignupCode(email: String) {
		// 명세: 이미 가입된 이메일(카카오 가입 포함)은 USER409. 발송 허가를 쓰기 전에 거절한다.
		if (userService.findActiveByEmail(email) != null) throw BusinessException(UserErrorCode.EMAIL_DUPLICATED)
		acquirePermit(email)
		val code = newCode()
		store.saveCode(VerificationPurpose.SIGNUP, email, code, CODE_TTL)
		deliver(email, VerificationPurpose.SIGNUP, "[뽈뽈] 회원가입 인증 코드", "회원가입 인증 코드는 $code 입니다. 10분 안에 입력해 주세요.")
	}

	private fun sendResetCode(email: String) {
		// 가입 여부를 응답으로 알 수 없게, 사용자 조회보다 먼저 발송 허가(429)를 소비한다.
		acquirePermit(email)
		val user = userService.findActiveByEmail(email)
		when {
			user != null && user.loginType == LoginType.EMAIL -> {
				val code = newCode()
				store.saveCode(VerificationPurpose.PASSWORD_RESET, email, code, CODE_TTL)
				deliver(email, VerificationPurpose.PASSWORD_RESET, "[뽈뽈] 비밀번호 재설정 인증 코드", "비밀번호 재설정 인증 코드는 $code 입니다. 10분 안에 입력해 주세요.")
			}
			else -> {
				// 가입 안 된 이메일이거나 카카오로만 가입한 이메일. 코드 확인 단계도 똑같이 보이도록 절대 맞을 수 없는 코드를 저장한다.
				store.saveCode(VerificationPurpose.PASSWORD_RESET, email, DECOY_CODE, CODE_TTL)
				if (user != null) sendKakaoNotice(email)
			}
		}
	}

	private fun acquirePermit(email: String) {
		if (store.tryAcquireSend(email, COOLDOWN, DAILY_LIMIT) != SendPermit.OK) {
			throw BusinessException(EmailErrorCode.RESEND_LIMIT)
		}
	}

	private fun deliver(email: String, purpose: VerificationPurpose, subject: String, text: String) {
		try {
			emailSender.send(email, subject, text)
		} catch (e: MailException) {
			// 못 보낸 메일이 쿨다운과 일일 횟수를 먹지 않게 되돌리고, 받을 수 없는 코드도 지운다.
			store.releaseSend(email)
			store.deleteCode(purpose, email)
			log.warn("인증 메일 발송 실패: {}", e.javaClass.simpleName)
			throw BusinessException(EmailErrorCode.SEND_FAILED)
		}
	}

	/** 카카오로만 가입한 이메일의 비밀번호 찾기 안내 메일. 실패해도 응답을 바꾸지 않는다(바꾸면 가입 여부가 드러난다). */
	private fun sendKakaoNotice(email: String) {
		try {
			emailSender.send(email, "[뽈뽈] 비밀번호 찾기 안내", "카카오로 가입한 계정이에요. 카카오로 로그인해 주세요.")
		} catch (e: MailException) {
			log.warn("카카오 가입 안내 메일 발송 실패: {}", e.javaClass.simpleName)
		}
	}

	/**
	 * 코드 확인. 순서가 중요하다:
	 * 1) IP당 시도 상한(clientIp는 remoteAddr만): 한 IP가 여러 메일함을 돌아가며 찍어 보는 것을 막는다. 시도를 먼저 선점(증가)하고 넘으면 거절한다.
	 * 2) 이메일별 시도를 비교하기 전에 원자적으로 선점한다. 선점한 번호가 5 이하일 때만 비교하므로 동시 요청이 몰려도 비교는 최대 5번이다.
	 * 3) 5번째 시도가 틀리면 코드를 지우고 EMAIL4002, 그 뒤(6번째 이후)는 맞는 코드도 비교하지 않고 EMAIL4002.
	 * 맞는 시도도 횟수에 포함된다(5번째 시도까지는 맞으면 성공).
	 */
	fun confirm(rawEmail: String, purpose: VerificationPurpose, code: String, clientIp: String): EmailConfirmResponse {
		if (ipAttempts.increment(IP_KEY_PREFIX + clientIp, IP_WINDOW) > maxAttemptsPerIp) {
			throw BusinessException(EmailErrorCode.RESEND_LIMIT)
		}
		val email = UserService.normalizeEmail(rawEmail)
		val stored = store.findCode(purpose, email) ?: throw BusinessException(EmailErrorCode.CODE_EXPIRED)
		val attempt = store.reserveAttempt(purpose, email)
		if (attempt <= 0 || attempt > MAX_ATTEMPTS) {
			if (attempt > MAX_ATTEMPTS) store.deleteCode(purpose, email)
			throw BusinessException(EmailErrorCode.CODE_EXPIRED)
		}
		if (!constantTimeEquals(stored, code)) {
			if (attempt >= MAX_ATTEMPTS) {
				store.deleteCode(purpose, email)
				throw BusinessException(EmailErrorCode.CODE_EXPIRED)
			}
			throw BusinessException(EmailErrorCode.CODE_MISMATCH, mapOf("remainingAttempts" to MAX_ATTEMPTS - attempt))
		}
		// 같은 코드로 동시에 두 요청이 와도 코드를 실제로 지운 쪽만 토큰을 받는다.
		if (!store.deleteCode(purpose, email)) throw BusinessException(EmailErrorCode.CODE_EXPIRED)
		val token = newToken()
		store.saveToken(hash(token), VerificationTokenEntry(purpose, email), TOKEN_TTL)
		return EmailConfirmResponse(verificationToken = token, expiresIn = TOKEN_TTL.seconds)
	}

	/** 토큰을 지우지 않고 검증해서 묶인 이메일을 돌려준다. 없음, 만료, 다른 용도면 EMAIL4003. */
	fun peekToken(token: String?, purpose: VerificationPurpose): String {
		val entry = token?.takeIf { it.isNotBlank() }?.let { store.findToken(hash(it)) }
		if (entry == null || entry.purpose != purpose) throw BusinessException(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
		return entry.email
	}

	/** 토큰을 검증하고 한 번만 성공하게 지운다. 다른 용도 토큰은 지우지 않고 거절한다. */
	fun consumeToken(token: String?, purpose: VerificationPurpose): String {
		peekToken(token, purpose)
		val entry = store.consumeToken(hash(token!!))
		if (entry == null || entry.purpose != purpose) throw BusinessException(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
		return entry.email
	}

	private fun newCode(): String = String.format("%06d", random.nextInt(1_000_000))

	private fun newToken(): String {
		val bytes = ByteArray(32)
		random.nextBytes(bytes)
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
	}

	private fun constantTimeEquals(a: String, b: String): Boolean =
		MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

	private fun hash(token: String): String =
		HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)))

	companion object {
		val CODE_TTL: Duration = Duration.ofMinutes(10)
		val TOKEN_TTL: Duration = Duration.ofMinutes(30)
		val COOLDOWN: Duration = Duration.ofMinutes(1)
		const val DAILY_LIMIT = 5
		const val MAX_ATTEMPTS = 5L

		/** IP당 코드 확인 시도 상한: 10분 창에 30번(기본). 같은 NAT 뒤 사용자는 한 IP로 보인다. */
		val IP_WINDOW: Duration = Duration.ofMinutes(10)
		private const val IP_KEY_PREFIX = "email-confirm:"

		/** 6자리 숫자 입력과 절대 같을 수 없는 값. 가입되지 않은 이메일의 재설정 코드 자리를 채운다. */
		private const val DECOY_CODE = "decoy"
	}
}
