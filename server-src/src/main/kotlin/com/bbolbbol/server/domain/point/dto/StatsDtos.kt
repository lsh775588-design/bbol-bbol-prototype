package com.bbolbbol.server.domain.point.dto

import java.time.LocalDate

/** 명세 "기간별 활동 통계" 응답. 모든 날짜는 한국 시간 기준이다. */
data class StatsResponse(
	val period: String,
	val startDate: LocalDate,
	val endDate: LocalDate,
	val completionCount: Int,
	val totalPoint: Long,
	val placeCount: Int,
	val previous: StatsPrevious,
	val categories: List<StatsCategory>,
	val timeline: List<StatsBar>,
)

data class StatsPrevious(val completionCount: Int, val totalPoint: Long)

data class StatsCategory(val category: String, val count: Int, val point: Long)

/** label: WEEK, MONTH는 yyyy-MM-dd, YEAR는 yyyy-MM. */
data class StatsBar(val label: String, val count: Int, val point: Long)
