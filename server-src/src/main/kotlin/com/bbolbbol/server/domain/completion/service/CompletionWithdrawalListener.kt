package com.bbolbbol.server.domain.completion.service

import com.bbolbbol.server.domain.user.event.UserWithdrawnEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 탈퇴 이벤트를 받아 completion 도메인의 개인정보를 정리한다. 이벤트는 탈퇴 DB 트랜잭션이 커밋된 뒤 발행되므로
 * 일반 리스너(동기)로 충분하다. 예외는 여기서 잡아 로그로만 남긴다(탈퇴 응답에 영향 없음). 못 한 정리는
 * [CompletionDataCleanupService.cleanupWithdrawnUsers]가 나중에 이어서 한다.
 * 순서는 가장 먼저(다른 도메인 리스너가 예외를 던져 뒤의 리스너가 실행되지 못하는 일을 피한다).
 */
@Component
class CompletionWithdrawalListener(private val cleanup: CompletionDataCleanupService) {

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@EventListener
	fun on(event: UserWithdrawnEvent) {
		try {
			cleanup.cleanupUser(event.userId)
		} catch (e: Exception) {
			log.error("탈퇴 후 completion 정리 실패(점검 작업이 다시 시도): userId={}, {}", event.userId, e.javaClass.simpleName)
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(CompletionWithdrawalListener::class.java)
	}
}
