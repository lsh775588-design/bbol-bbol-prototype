package com.bbolbbol.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 공통 응답 `{success, code, message, data}`. */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val code: String = "",
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class FieldError(val field: String = "", val reason: String = "")

/** COMMON400의 data. */
@Serializable
data class ValidationErrors(val errors: List<FieldError> = emptyList(), val remainingAttempts: Int? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** 백엔드 SignupRequest에는 email이 없다(이메일은 verificationToken에 묶여 있다). */
@Serializable
data class SignupRequest(val verificationToken: String, val password: String, val nickname: String)

@Serializable
data class TokenResponse(
    val userId: Long = 0,
    val accessToken: String = "",
    val refreshToken: String = "",
    val isNewUser: Boolean = false,
) {
    // 토큰이 로그에 찍히지 않게 한다.
    override fun toString() = "TokenResponse(userId=$userId)"
}

@Serializable
data class AvailabilityResult(val available: Boolean = false, val reason: String? = null)

@Serializable
data class MeResponse(
    val userId: Long = 0,
    val email: String = "",
    val nickname: String = "",
    val loginType: String = "",
    val onboardingCompleted: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class NicknameChangeRequest(val nickname: String)

@Serializable
data class NicknameChangeResponse(val nickname: String = "", val nextChangeableAt: String? = null)

@Serializable
data class CategoryItem(val code: String, val name: String, val rewardPoint: Int = 0)

@Serializable
data class CategoryListResponse(val categories: List<CategoryItem> = emptyList())

@Serializable
data class PlaceSummary(
    val placeId: Long,
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val distance: Long = 0,
    val radius: Int = 0,
    val notifyRadius: Int = 0,
    val rewardPoint: Int = 0,
    val expectedPoint: Int = 0,
    // 백엔드가 isHot / isCompleted 이름 그대로 내보낸다.
    @SerialName("isHot") val isHot: Boolean = false,
    val hotType: String? = null,
    val visitedBefore: Boolean = false,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
)

@Serializable
data class PlaceListResponse(val places: List<PlaceSummary> = emptyList())

@Serializable
data class PlaceDetail(
    val placeId: Long,
    val name: String,
    val category: String,
    val categoryName: String? = null,
    val address: String? = null,
    val roadAddress: String? = null,
    val phone: String? = null,
    val lat: Double,
    val lng: Double,
    val radius: Int = 0,
    val notifyRadius: Int = 0,
    val kakaoPlaceUrl: String? = null,
    val rewardPoint: Int = 0,
    val expectedPoint: Int = 0,
    val photoBonusAvailable: Boolean = false,
    @SerialName("isHot") val isHot: Boolean = false,
    val hotType: String? = null,
    val completedCount: Long = 0,
    val visitedBefore: Boolean = false,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
    val myLastCompletedAt: String? = null,
)

// ---- 2단계: 도착 인증, 포인트, 활동 일지, 추천 ----

/** 서버 CompletionRequest와 같은 키. isMockLocation은 키 그대로 보낸다. */
@Serializable
data class CompletionRequest(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val isMockLocation: Boolean,
)

@Serializable
data class CompletionResponse(
    val completionId: Long = 0,
    val placeId: Long = 0,
    val placeName: String = "",
    @SerialName("isFirstVisit") val isFirstVisit: Boolean = false,
    val earnedPoint: Int = 0,
    val totalPoint: Long = 0,
    val completedAt: String? = null,
    val photoBonusPoint: Int = 0,
    val photoDeadline: String? = null,
)

@Serializable
data class PointSummary(val totalPoint: Long = 0, val todayPoint: Long = 0, val completionCount: Long = 0)

@Serializable
data class PointHistoryItem(
    val historyId: Long,
    val type: String = "",
    val point: Int = 0,
    val completionId: Long = 0,
    val placeId: Long = 0,
    val placeName: String = "",
    val category: String = "",
    val createdAt: String? = null,
)

@Serializable
data class PointHistoryPage(
    val histories: List<PointHistoryItem> = emptyList(),
    val hasNext: Boolean = false,
    val nextCursor: Long? = null,
)

@Serializable
data class CompletionLogItem(
    val completionId: Long,
    val placeId: Long = 0,
    val placeName: String = "",
    val category: String = "",
    val address: String = "",
    val earnedPoint: Int = 0,
    val photoUrl: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class CompletionLogPage(
    val completions: List<CompletionLogItem> = emptyList(),
    val hasNext: Boolean = false,
    val nextCursor: Long? = null,
)

@Serializable
data class RecommendedPlace(
    val placeId: Long,
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val distance: Long = 0,
    val radius: Int = 0,
    val notifyRadius: Int = 0,
    val rewardPoint: Int = 0,
    val expectedPoint: Int = 0,
    @SerialName("isHot") val isHot: Boolean = false,
    val hotType: String? = null,
    val visitedBefore: Boolean = false,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
    val reason: String = "",
)

@Serializable
data class RecommendationListResponse(val places: List<RecommendedPlace> = emptyList())
