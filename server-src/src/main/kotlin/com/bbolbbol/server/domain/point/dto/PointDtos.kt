package com.bbolbbol.server.domain.point.dto

import com.bbolbbol.server.domain.point.entity.PointType
import java.time.LocalDateTime

/** GET /users/me/points */
data class PointSummary(val totalPoint: Long, val todayPoint: Long, val completionCount: Long)

/** 명세 필드명 그대로. createdAt은 적립 시각(earned_at)이다. */
data class PointHistoryItem(
	val historyId: Long,
	val type: PointType,
	val point: Int,
	val completionId: Long,
	val placeId: Long,
	val placeName: String,
	val category: String,
	val createdAt: LocalDateTime,
)

data class PointHistoryPage(
	val histories: List<PointHistoryItem>,
	val nextCursor: Long?,
	val hasNext: Boolean,
)
