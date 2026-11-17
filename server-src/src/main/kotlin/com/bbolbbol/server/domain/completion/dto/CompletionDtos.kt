package com.bbolbbol.server.domain.completion.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * POST /places/{placeId}/completions 요청. 필드를 nullable로 둬서 누락돼도 @NotNull이 data.errors에 필드명을 담는다.
 * isMockLocation은 Kotlin is 접두사 프로퍼티라 키를 명시한다.
 */
data class CompletionRequest(
	@field:NotNull(message = "lat은 필수입니다.")
	@field:DecimalMin(value = "-90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
	@field:DecimalMax(value = "90.0", message = "위도는 -90 이상 90 이하여야 합니다.")
	val lat: Double?,

	@field:NotNull(message = "lng는 필수입니다.")
	@field:DecimalMin(value = "-180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
	@field:DecimalMax(value = "180.0", message = "경도는 -180 이상 180 이하여야 합니다.")
	val lng: Double?,

	@field:NotNull(message = "accuracy는 필수입니다.")
	@field:DecimalMin(value = "0.0", message = "accuracy는 0 이상이어야 합니다.")
	val accuracy: Double?,

	@get:JsonProperty("isMockLocation")
	@param:JsonProperty("isMockLocation")
	@field:NotNull(message = "isMockLocation은 필수입니다.")
	val isMockLocation: Boolean?,
)

/** 201 응답 data. 명세 필드명 그대로. */
data class CompletionResponse(
	val completionId: Long,
	val placeId: Long,
	val placeName: String,
	@get:JsonProperty("isFirstVisit")
	@param:JsonProperty("isFirstVisit")
	val isFirstVisit: Boolean,
	val earnedPoint: Int,
	val totalPoint: Long,
	val completedAt: LocalDateTime,
	val photoBonusPoint: Int,
	val photoDeadline: LocalDateTime?,
)

/** 활동 일지 한 줄(방문 기준). earnedPoint는 이 방문으로 받은 점수 합계. */
data class CompletionLogItem(
	val completionId: Long,
	val placeId: Long,
	val placeName: String,
	val category: String,
	val address: String,
	val earnedPoint: Int,
	val photoUrl: String?,
	val completedAt: LocalDateTime,
)

data class CompletionLogPage(
	val completions: List<CompletionLogItem>,
	val nextCursor: Long?,
	val hasNext: Boolean,
)

/** 201 응답 data(사진 업로드). bonusPoint는 이번에 받은 보너스(재방문이면 0). */
data class PhotoUploadResponse(
	val completionId: Long,
	val photoUrl: String,
	val bonusPoint: Int,
	val totalPoint: Long,
)
