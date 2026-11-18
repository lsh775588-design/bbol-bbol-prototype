package com.bbolbbol.server.domain.auth.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** local, test 프로파일용 인메모리 구현(Redis 없이 동작). TTL과 유예는 Clock 기준으로 판단한다. */
@Component
@Profile("local | test")
class InMemoryRefreshTokenStore(
	private val clock: Clock,
) : RefreshTokenStore {

	private class State(var hash: String, var expiresAt: Instant) {
		/** 밀려난 토큰 해시 -> 유예 만료 시각 */
		val recent = LinkedHashMap<String, Instant>()
	}

	private val store = HashMap<Long, State>()

	@Synchronized
	override fun save(userId: Long, tokenHash: String, ttl: Duration) {
		store[userId] = State(tokenHash, clock.instant().plus(ttl))
	}

	@Synchronized
	override fun find(userId: Long): String? = live(userId)?.hash

	@Synchronized
	override fun delete(userId: Long) {
		store.remove(userId)
	}

	@Synchronized
	override fun rotate(userId: Long, presentedHash: String, newHash: String, ttl: Duration, grace: Duration): Boolean {
		val state = live(userId) ?: return false
		val now = clock.instant()
		state.recent.values.removeIf { !now.isBefore(it) }
		val allowed = state.hash == presentedHash || state.recent[presentedHash]?.let { now.isBefore(it) } == true
		if (!allowed) return false
		state.recent[state.hash] = now.plus(grace)
		while (state.recent.size > MAX_RECENT) state.recent.remove(state.recent.keys.first())
		state.hash = newHash
		state.expiresAt = now.plus(ttl)
		return true
	}

	/** 테스트 정리용. */
	@Synchronized
	fun clearAll() = store.clear()

	private fun live(userId: Long): State? {
		val state = store[userId] ?: return null
		if (!clock.instant().isBefore(state.expiresAt)) {
			store.remove(userId)
			return null
		}
		return state
	}

	companion object {
		/** 유예 중인 옛 토큰을 기억하는 최대 개수(동시 재발급 폭주로 커지지 않게). */
		const val MAX_RECENT = 50
	}
}
