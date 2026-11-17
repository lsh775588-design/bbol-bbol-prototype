package com.bbolbbol.server.domain.completion.service

import com.bbolbbol.server.domain.completion.repository.CompletionRepository
import com.bbolbbol.server.domain.place.service.PlaceUserState
import com.bbolbbol.server.domain.place.service.PlaceUserStateProvider
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * place 도메인의 PlaceUserStateProvider 구현(completion 도메인의 인증 기록 기반).
 * 장소 목록을 쿼리 2번(내 인증 요약, 장소별 전체 인증 횟수)으로 계산해 N+1을 피한다.
 * popular(최근 7일 인기)는 집계 담당(recommendations/ranking)이 정하는 값이라 여기서는 항상 false다.
 */
@Primary
@Component
@Transactional(readOnly = true)
class CompletionPlaceUserStateProvider(
	private val completions: CompletionRepository,
	private val clock: Clock,
) : PlaceUserStateProvider {

	override fun statesFor(userId: Long, placeIds: Collection<Long>): Map<Long, PlaceUserState> {
		if (placeIds.isEmpty()) return emptyMap()
		val ids = placeIds.toSet()
		val today = LocalDate.now(clock)
		val mine = completions.summarizeMyVisits(userId, ids).associateBy { it.placeId }
		val totals = completions.countByPlaces(ids).associate { it.placeId to it.total }
		return ids.associateWith { id ->
			val visit = mine[id]
			PlaceUserState(
				visitedBefore = visit != null,
				completedToday = visit != null && visit.lastCompletionDate == today,
				completedCount = totals[id] ?: 0L,
				myLastCompletedAt = visit?.lastCompletedAt,
				popular = false,
			)
		}
	}
}
