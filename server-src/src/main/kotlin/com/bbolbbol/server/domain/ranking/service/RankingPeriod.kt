package com.bbolbbol.server.domain.ranking.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 랭킹 기간. DAY는 오늘, WEEK는 이번 주(월~일), MONTH는 이번 달. 끝 날짜는 포함(inclusive)이다. */
enum class RankingPeriod {
	DAY, WEEK, MONTH;

	data class Range(val start: LocalDate, val end: LocalDate) {
		val endExclusive: LocalDate get() = end.plusDays(1)
	}

	fun rangeOf(today: LocalDate): Range = when (this) {
		DAY -> Range(today, today)
		WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { Range(it, it.plusDays(6)) }
		MONTH -> Range(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
	}
}
