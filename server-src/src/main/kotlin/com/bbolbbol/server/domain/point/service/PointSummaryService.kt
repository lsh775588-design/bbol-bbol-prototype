package com.bbolbbol.server.domain.point.service

import com.bbolbbol.server.domain.completion.service.CompletionService
import com.bbolbbol.server.domain.point.dto.PointSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내 포인트 요약. 완료 수는 completion 도메인에서 받는다.
 * PointService는 completion을 모르고 CompletionService가 PointService를 쓰므로, 순환을 피하려고 요약만 따로 둔다.
 */
@Service
@Transactional(readOnly = true)
class PointSummaryService(
	private val pointService: PointService,
	private val completionService: CompletionService,
	private val clock: Clock,
) {

	fun summary(userId: Long): PointSummary {
		val today: LocalDate = LocalDate.now(clock)
		val from: LocalDateTime = today.atStartOfDay()
		return PointSummary(
			totalPoint = pointService.totalPoint(userId),
			todayPoint = pointService.pointBetween(userId, from, from.plusDays(1)),
			completionCount = completionService.countByUser(userId),
		)
	}
}
