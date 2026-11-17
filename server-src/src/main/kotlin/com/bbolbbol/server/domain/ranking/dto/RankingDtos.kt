package com.bbolbbol.server.domain.ranking.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.LocalDateTime

/** 다른 사용자의 userId, 이메일, 온보딩 정보는 내려주지 않는다(명세). */
data class UserRankingItem(
	val rank: Int,
	val nickname: String,
	val point: Long,
	val completionCount: Long,
	@get:JsonProperty("isMe")
	@param:JsonProperty("isMe")
	val isMe: Boolean,
)

data class UserRankingMe(val rank: Int, val nickname: String, val point: Long, val completionCount: Long)

data class UserRankingResponse(
	val period: String,
	val startDate: LocalDate,
	val endDate: LocalDate,
	val rankings: List<UserRankingItem>,
	val me: UserRankingMe?,
	val updatedAt: LocalDateTime,
)

data class PlaceRankingItem(
	val rank: Int,
	val placeId: Long,
	val placeName: String,
	val category: String,
	val address: String,
	val completionCount: Long,
	val rewardPoint: Int,
)

data class PlaceRankingResponse(
	val period: String,
	val startDate: LocalDate,
	val endDate: LocalDate,
	val rankings: List<PlaceRankingItem>,
	val updatedAt: LocalDateTime,
)

data class StreakRankingItem(
	val rank: Int,
	val nickname: String,
	val streakDays: Int,
	@get:JsonProperty("isMe")
	@param:JsonProperty("isMe")
	val isMe: Boolean,
)

data class StreakRankingMe(val rank: Int, val nickname: String, val streakDays: Int, val longestStreakDays: Int)

data class StreakRankingResponse(val rankings: List<StreakRankingItem>, val me: StreakRankingMe?)
