package com.bbolbbol.server.domain.auth.repository

import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * 운영 프로파일용. 실제 Redis에 붙여 보지 못했다(미검증).
 * 키: email-verify:{purpose}:{email}(코드, TTL 10분), email-verify-attempts:{purpose}:{email}(틀린 횟수),
 * email-verify-token:{sha256(token)}(토큰, TTL 30분), email-send-cooldown:{email}, email-send-count:{email}:{yyyyMMdd}.
 */
@Component
@Profile("!local & !test")
class RedisEmailVerificationStore(
	private val redis: StringRedisTemplate,
	private val clock: Clock,
) : EmailVerificationStore {

	private val ops get() = redis.opsForValue()

	override fun tryAcquireSend(email: String, cooldown: Duration, dailyLimit: Int): EmailVerificationStore.SendPermit {
		val cooldownKey = COOLDOWN_PREFIX + email
		if (ops.setIfAbsent(cooldownKey, "1", cooldown) != true) return EmailVerificationStore.SendPermit.COOLDOWN
		val countKey = countKey(email)
		val count = ops.increment(countKey) ?: 1L
		if (count == 1L) redis.expire(countKey, COUNT_TTL)
		if (count > dailyLimit) {
			ops.decrement(countKey)
			redis.delete(cooldownKey)
			return EmailVerificationStore.SendPermit.DAILY_LIMIT
		}
		return EmailVerificationStore.SendPermit.OK
	}

	override fun releaseSend(email: String) {
		redis.delete(COOLDOWN_PREFIX + email)
		ops.decrement(countKey(email))
	}

	override fun saveCode(purpose: VerificationPurpose, email: String, code: String, ttl: Duration) {
		// 횟수 키를 먼저 만든다(코드가 보이면 횟수 키도 이미 있다)
		ops.set(attemptsKey(purpose, email), "0", ttl)
		ops.set(codeKey(purpose, email), code, ttl)
	}

	override fun findCode(purpose: VerificationPurpose, email: String): String? = ops.get(codeKey(purpose, email))

	/** Lua 한 번으로 "코드가 있으면 INCR, 횟수 키 TTL = 코드의 남은 TTL"을 처리한다(코드가 지워진 뒤 TTL 없는 횟수 키가 남지 않는다). */
	override fun reserveAttempt(purpose: VerificationPurpose, email: String): Long =
		redis.execute(RESERVE_SCRIPT, listOf(codeKey(purpose, email), attemptsKey(purpose, email))) ?: 0L

	override fun deleteCode(purpose: VerificationPurpose, email: String): Boolean {
		val removed = redis.delete(codeKey(purpose, email))
		redis.delete(attemptsKey(purpose, email))
		return removed
	}

	override fun saveToken(tokenHash: String, entry: VerificationTokenEntry, ttl: Duration) {
		ops.set(TOKEN_PREFIX + tokenHash, "${entry.purpose}|${entry.email}", ttl)
	}

	override fun findToken(tokenHash: String): VerificationTokenEntry? = parse(ops.get(TOKEN_PREFIX + tokenHash))

	/** GETDEL(Redis 6.2 이상)로 읽기와 삭제를 한 번에 한다. */
	override fun consumeToken(tokenHash: String): VerificationTokenEntry? = parse(ops.getAndDelete(TOKEN_PREFIX + tokenHash))

	private fun parse(value: String?): VerificationTokenEntry? {
		if (value == null) return null
		val sep = value.indexOf('|')
		if (sep <= 0) return null
		val purpose = runCatching { VerificationPurpose.valueOf(value.substring(0, sep)) }.getOrNull() ?: return null
		return VerificationTokenEntry(purpose, value.substring(sep + 1))
	}

	private fun codeKey(purpose: VerificationPurpose, email: String) = "email-verify:$purpose:$email"
	private fun attemptsKey(purpose: VerificationPurpose, email: String) = "email-verify-attempts:$purpose:$email"
	private fun countKey(email: String) = "$COUNT_PREFIX$email:${LocalDate.now(clock).toString().replace("-", "")}"

	companion object {
		const val COOLDOWN_PREFIX = "email-send-cooldown:"
		const val COUNT_PREFIX = "email-send-count:"
		const val TOKEN_PREFIX = "email-verify-token:"
		private val COUNT_TTL: Duration = Duration.ofHours(48)

		/** KEYS[1]=코드, KEYS[2]=횟수. 코드가 없으면 0. */
		private val RESERVE_SCRIPT = DefaultRedisScript(
			"""
			local ttl = redis.call('PTTL', KEYS[1])
			if ttl < 0 then return 0 end
			local n = redis.call('INCR', KEYS[2])
			if n == 1 or redis.call('PTTL', KEYS[2]) < 0 then redis.call('PEXPIRE', KEYS[2], ttl) end
			return n
			""".trimIndent(),
			Long::class.java,
		)
	}
}
