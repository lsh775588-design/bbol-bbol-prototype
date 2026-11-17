package com.bbolbbol.server.domain.auth.repository

import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import java.time.Duration

/** 인증 토큰에 묶인 정보. */
data class VerificationTokenEntry(val purpose: VerificationPurpose, val email: String)

/**
 * 이메일 인증 저장소 포트(Redis 운영, 인메모리 local/test). 담당:
 * 1) 발송 제한(재발송 1분 쿨다운, 하루 횟수) 2) 6자리 코드와 틀린 횟수 3) 인증 후 발급하는 verificationToken.
 * 이메일은 호출하는 쪽에서 trim + lowercase 한 값을 넘긴다. 토큰은 원문이 아니라 SHA-256 해시를 키로 쓴다.
 */
interface EmailVerificationStore {

	enum class SendPermit { OK, COOLDOWN, DAILY_LIMIT }

	/**
	 * 발송 허가를 원자적으로 얻는다. 쿨다운과 하루 횟수는 purpose와 상관없이 이메일 하나에 건다
	 * (purpose별로 따로 세면 SIGNUP 5번 + PASSWORD_RESET 5번으로 같은 주소에 10통을 보낼 수 있다).
	 * OK이면 쿨다운이 시작되고 오늘 횟수가 1 늘어난다.
	 */
	fun tryAcquireSend(email: String, cooldown: Duration, dailyLimit: Int): SendPermit

	/** 메일 발송에 실패했을 때 방금 얻은 허가를 돌려준다(쿨다운 해제, 횟수 -1). */
	fun releaseSend(email: String)

	/** 코드를 저장한다. 같은 키가 있으면 덮어쓰고 틀린 횟수도 0으로 되돌린다. */
	fun saveCode(purpose: VerificationPurpose, email: String, code: String, ttl: Duration)

	fun findCode(purpose: VerificationPurpose, email: String): String?

	/**
	 * 코드를 비교하기 "전에" 시도 1회를 선점하고 늘어난 시도 횟수를 돌려준다(원자적). 코드가 없거나 만료됐으면 0.
	 * 맞든 틀리든 시도로 센다. 호출하는 쪽은 돌려받은 값이 최대 횟수 이하일 때만 비교해야 하고,
	 * 그래야 동시 요청이 몰려도 비교 횟수가 최대 횟수를 넘지 않는다. 횟수 키는 항상 코드와 같은 TTL을 가진다.
	 */
	fun reserveAttempt(purpose: VerificationPurpose, email: String): Long

	/** 코드를 지운다. 이 호출이 실제로 지웠으면 true(두 요청이 같은 코드로 동시에 성공하는 것을 막는 선점 용도). */
	fun deleteCode(purpose: VerificationPurpose, email: String): Boolean

	fun saveToken(tokenHash: String, entry: VerificationTokenEntry, ttl: Duration)

	fun findToken(tokenHash: String): VerificationTokenEntry?

	/** 토큰을 읽으면서 지운다(원자적, 한 번만 성공). */
	fun consumeToken(tokenHash: String): VerificationTokenEntry?
}
