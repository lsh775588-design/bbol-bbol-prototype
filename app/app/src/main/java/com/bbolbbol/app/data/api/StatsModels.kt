package com.bbolbbol.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotoUploadResponse(
    val completionId: Long = 0,
    val photoUrl: String = "",
    val bonusPoint: Int = 0,
    val totalPoint: Long = 0,
)

// ---- GET /users/me/stats ----

@Serializable
data class StatsPrevious(val completionCount: Int = 0, val totalPoint: Long = 0)

/** category가 ""이면 서버가 장소 카테고리를 찾지 못한 묶음이다. */
@Serializable
data class StatsCategory(val category: String = "", val count: Int = 0, val point: Long = 0)

/** label: WEEK, MONTH는 yyyy-MM-dd, YEAR는 yyyy-MM. */
@Serializable
data class StatsBar(val label: String = "", val count: Int = 0, val point: Long = 0)

@Serializable
data class StatsResponse(
    val period: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val completionCount: Int = 0,
    val totalPoint: Long = 0,
    val placeCount: Int = 0,
    val previous: StatsPrevious = StatsPrevious(),
    val categories: List<StatsCategory> = emptyList(),
    val timeline: List<StatsBar> = emptyList(),
)

// ---- GET /rankings/* ----

@Serializable
data class UserRankingItem(
    val rank: Int = 0,
    val nickname: String = "",
    val point: Long = 0,
    val completionCount: Long = 0,
    @SerialName("isMe") val isMe: Boolean = false,
)

@Serializable
data class UserRankingMe(
    val rank: Int = 0,
    val nickname: String = "",
    val point: Long = 0,
    val completionCount: Long = 0,
)

@Serializable
data class UserRankingResponse(
    val period: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rankings: List<UserRankingItem> = emptyList(),
    val me: UserRankingMe? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlaceRankingItem(
    val rank: Int = 0,
    val placeId: Long = 0,
    val placeName: String = "",
    val category: String = "",
    val address: String = "",
    val completionCount: Long = 0,
    val rewardPoint: Int = 0,
)

@Serializable
data class PlaceRankingResponse(
    val period: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rankings: List<PlaceRankingItem> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class StreakRankingItem(
    val rank: Int = 0,
    val nickname: String = "",
    val streakDays: Int = 0,
    @SerialName("isMe") val isMe: Boolean = false,
)

@Serializable
data class StreakRankingMe(
    val rank: Int = 0,
    val nickname: String = "",
    val streakDays: Int = 0,
    val longestStreakDays: Int = 0,
)

@Serializable
data class StreakRankingResponse(
    val rankings: List<StreakRankingItem> = emptyList(),
    val me: StreakRankingMe? = null,
)
