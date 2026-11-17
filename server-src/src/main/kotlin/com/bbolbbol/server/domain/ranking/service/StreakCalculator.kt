package com.bbolbbol.server.domain.ranking.service

import java.time.LocalDate

/**
 * 연속 참여 일수(streak). 입력은 사용자가 참여한(완료 기록이 있는) 한국 시간 날짜들이다. 재방문도 참여다.
 * - 현재 연속: 오늘 참여했으면 오늘부터, 오늘은 아직이고 어제 참여했으면 어제부터 거슬러 올라가며 이어진 날 수.
 *   그 외(어제도 오늘도 없음)는 0. 하루라도 빠지면 거기서 끊긴다.
 * - 최장 연속: 날짜 집합에서 가장 긴 이어진 구간.
 * - today 이후(미래) 날짜는 무시한다.
 */
object StreakCalculator {

	data class Streak(val current: Int, val longest: Int)

	fun compute(days: Collection<LocalDate>, today: LocalDate): Streak {
		val set = days.filter { !it.isAfter(today) }.toSortedSet()
		if (set.isEmpty()) return Streak(0, 0)

		var longest = 0
		var run = 0
		var prev: LocalDate? = null
		for (d in set) {
			run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
			if (run > longest) longest = run
			prev = d
		}

		val anchor = when {
			today in set -> today
			today.minusDays(1) in set -> today.minusDays(1)
			else -> return Streak(0, longest)
		}
		var current = 0
		var d = anchor
		while (d in set) {
			current++
			d = d.minusDays(1)
		}
		return Streak(current, longest)
	}
}
