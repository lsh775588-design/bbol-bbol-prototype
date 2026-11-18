package com.bbolbbol.server.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/** 통합 테스트용: 한국 시간 기준으로 시각을 바꿀 수 있는 Clock. 테스트 안에서 [MutableClock.setKst]로 옮긴다. */
@TestConfiguration
class MutableClockConfig {

	class MutableClock(initial: Instant = Instant.parse("2026-11-10T03:00:00Z")) : Clock() {
		private val now = AtomicReference(initial)

		override fun getZone(): ZoneId = KST
		override fun withZone(zone: ZoneId): Clock = this
		override fun instant(): Instant = now.get()

		/** 한국 시간 "2026-11-09T23:59:30" 형태로 지정 */
		fun setInstant(instant: Instant) = now.set(instant)
		fun setKst(local: LocalDateTime) = now.set(local.atZone(KST).toInstant())
		fun setKst(text: String) = setKst(LocalDateTime.parse(text))
	}

	@Bean
	@Primary
	fun mutableClock(): MutableClock = MutableClock()

	companion object {
		val KST: ZoneId = ZoneId.of("Asia/Seoul")
	}
}
