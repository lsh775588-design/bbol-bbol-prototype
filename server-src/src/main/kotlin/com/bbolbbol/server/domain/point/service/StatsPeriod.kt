package com.bbolbbol.server.domain.point.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 통계 기간. 주는 월요일 시작(ISO 8601). 끝 날짜는 포함(inclusive)이다. */
enum class StatsPeriod {
	WEEK, MONTH, YEAR;

	data class Range(val start: LocalDate, val end: LocalDate) {
		/** [start 00:00, end+1 00:00) 조회용 */
		val endExclusive: LocalDate get() = end.plusDays(1)
	}

	fun rangeOf(date: LocalDate): Range = when (this) {
		WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { Range(it, it.plusDays(6)) }
		MONTH -> Range(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
		YEAR -> Range(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))
	}
}
