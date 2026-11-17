package com.bbolbbol.server.support

import com.bbolbbol.server.domain.user.event.UserWithdrawnEvent
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import java.util.concurrent.CopyOnWriteArrayList

/** 탈퇴 이벤트 테스트용: 이벤트를 기록하는 리스너와, 항상 예외를 던지는 리스너(응답에 영향이 없어야 한다). */
@TestConfiguration
class WithdrawalListenersConfig {

	class Recorder {
		val userIds: MutableList<Long> = CopyOnWriteArrayList()

		@Order(1)
		@EventListener
		fun on(event: UserWithdrawnEvent) {
			userIds.add(event.userId)
		}
	}

	class Thrower {
		@Order(2)
		@EventListener
		fun on(event: UserWithdrawnEvent) {
			throw IllegalStateException("listener failure for test")
		}
	}

	@Bean
	fun withdrawalRecorder(): Recorder = Recorder()

	@Bean
	fun withdrawalThrower(): Thrower = Thrower()
}
