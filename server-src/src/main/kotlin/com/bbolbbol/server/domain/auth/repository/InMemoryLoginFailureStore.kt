package com.bbolbbol.server.domain.auth.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** local, test 프로파일용 인메모리 구현. 창 만료는 Clock 기준이다. */
@Component
@Profile("local | test")
class InMemoryLoginFailureStore(
	private val clock: Clock,
) : LoginFailureStore {

	private class Window(var count: Long, val endsAt: Instant)

	private val windows = HashMap<String, Window>()

	@Synchronized
	override fun count(key: String): Long = live(key)?.count ?: 0

	@Synchronized
	override fun increment(key: String, window: Duration): Long {
		val w = live(key) ?: Window(0, clock.instant().plus(window)).also { windows[key] = it }
		w.count++
		return w.count
	}

	@Synchronized
	override fun reset(key: String) {
		windows.remove(key)
	}

	/** 테스트 정리용. */
	@Synchronized
	fun clearAll() = windows.clear()

	private fun live(key: String): Window? {
		val w = windows[key] ?: return null
		if (!clock.instant().isBefore(w.endsAt)) {
			windows.remove(key)
			return null
		}
		return w
	}
}
