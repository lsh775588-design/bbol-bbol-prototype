package com.bbolbbol.server.domain.auth.repository

import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** local, test 프로파일용 인메모리 구현(Redis 없이 동작). 만료는 Clock 기준이고 모든 메서드가 한 락 안에서 실행된다. */
@Component
@Profile("local | test")
class InMemoryEmailVerificationStore(
	private val clock: Clock,
) : EmailVerificationStore {

	private data class CodeEntry(val code: String, val expiresAt: Instant, var attempts: Long = 0)
	private data class TokenEntry(val entry: VerificationTokenEntry, val expiresAt: Instant)
	private class Daily(val date: LocalDate, var count: Int)

	private val codes = HashMap<String, CodeEntry>()
	private val tokens = HashMap<String, TokenEntry>()
	private val cooldowns = HashMap<String, Instant>()
	private val daily = HashMap<String, Daily>()

	private fun now(): Instant = clock.instant()
	private fun key(purpose: VerificationPurpose, email: String) = "$purpose:$email"

	@Synchronized
	override fun tryAcquireSend(email: String, cooldown: Duration, dailyLimit: Int): EmailVerificationStore.SendPermit {
		val now = now()
		val until = cooldowns[email]
		if (until != null && now.isBefore(until)) return EmailVerificationStore.SendPermit.COOLDOWN
		val today = LocalDate.now(clock)
		val d = daily[email]?.takeIf { it.date == today } ?: Daily(today, 0).also { daily[email] = it }
		if (d.count >= dailyLimit) return EmailVerificationStore.SendPermit.DAILY_LIMIT
		d.count++
		cooldowns[email] = now.plus(cooldown)
		return EmailVerificationStore.SendPermit.OK
	}

	@Synchronized
	override fun releaseSend(email: String) {
		cooldowns.remove(email)
		daily[email]?.let { if (it.count > 0) it.count-- }
	}

	@Synchronized
	override fun saveCode(purpose: VerificationPurpose, email: String, code: String, ttl: Duration) {
		codes[key(purpose, email)] = CodeEntry(code, now().plus(ttl))
	}

	@Synchronized
	override fun findCode(purpose: VerificationPurpose, email: String): String? = liveCode(purpose, email)?.code

	@Synchronized
	override fun reserveAttempt(purpose: VerificationPurpose, email: String): Long {
		val entry = liveCode(purpose, email) ?: return 0
		entry.attempts++
		return entry.attempts
	}

	@Synchronized
	override fun deleteCode(purpose: VerificationPurpose, email: String): Boolean {
		val live = liveCode(purpose, email) != null
		codes.remove(key(purpose, email))
		return live
	}

	@Synchronized
	override fun saveToken(tokenHash: String, entry: VerificationTokenEntry, ttl: Duration) {
		tokens[tokenHash] = TokenEntry(entry, now().plus(ttl))
	}

	@Synchronized
	override fun findToken(tokenHash: String): VerificationTokenEntry? = liveToken(tokenHash)?.entry

	@Synchronized
	override fun consumeToken(tokenHash: String): VerificationTokenEntry? {
		val live = liveToken(tokenHash)
		tokens.remove(tokenHash)
		return live?.entry
	}

	/** 테스트 정리용. */
	@Synchronized
	fun clearAll() {
		codes.clear()
		tokens.clear()
		cooldowns.clear()
		daily.clear()
	}

	private fun liveCode(purpose: VerificationPurpose, email: String): CodeEntry? {
		val k = key(purpose, email)
		val e = codes[k] ?: return null
		if (!now().isBefore(e.expiresAt)) {
			codes.remove(k)
			return null
		}
		return e
	}

	private fun liveToken(tokenHash: String): TokenEntry? {
		val e = tokens[tokenHash] ?: return null
		if (!now().isBefore(e.expiresAt)) {
			tokens.remove(tokenHash)
			return null
		}
		return e
	}
}
