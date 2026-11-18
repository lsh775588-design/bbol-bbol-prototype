package com.bbolbbol.server.domain.point.controller

import com.bbolbbol.server.domain.point.dto.PointHistoryPage
import com.bbolbbol.server.domain.point.dto.PointSummary
import com.bbolbbol.server.domain.point.entity.PointType
import com.bbolbbol.server.domain.point.service.PointService
import com.bbolbbol.server.domain.point.service.PointSummaryService
import com.bbolbbol.server.domain.point.service.StatsPeriod
import com.bbolbbol.server.domain.point.service.StatsService
import com.bbolbbol.server.domain.point.dto.StatsResponse
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
import com.bbolbbol.server.global.common.ApiResponse
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me")
class PointController(
	private val pointService: PointService,
	private val pointSummaryService: PointSummaryService,
	private val statsService: StatsService,
) {

	/** period 누락/없는 값, date 형식 오류는 타입 변환 실패로, 미래 날짜는 서비스에서 COMMON400이 된다. */
	@GetMapping("/stats")
	fun stats(
		@AuthenticationPrincipal userId: Long,
		@RequestParam period: StatsPeriod,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
	): ApiResponse<StatsResponse> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", statsService.stats(userId, period, date))


	@GetMapping("/points")
	fun points(@AuthenticationPrincipal userId: Long): ApiResponse<PointSummary> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", pointSummaryService.summary(userId))

	/** type이 QUEST_COMPLETE, PHOTO_BONUS가 아니면 타입 변환 실패로 COMMON400이 된다. */
	@GetMapping("/point-histories")
	fun histories(
		@AuthenticationPrincipal userId: Long,
		@RequestParam(required = false) cursor: Long?,
		@RequestParam(required = false) size: Int?,
		@RequestParam(required = false) type: PointType?,
	): ApiResponse<PointHistoryPage> {
		val s = size ?: DEFAULT_SIZE
		val errors = mutableListOf<FieldError>()
		if (s < 1 || s > MAX_SIZE) errors += FieldError("size", "size는 1 이상 $MAX_SIZE 이하여야 합니다.")
		if (cursor != null && cursor < 1) errors += FieldError("cursor", "cursor는 1 이상이어야 합니다.")
		if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))
		return ApiResponse(true, "COMMON200", "요청에 성공했습니다.", pointService.page(userId, cursor, s, type))
	}

	private companion object {
		const val DEFAULT_SIZE = 20
		const val MAX_SIZE = 50
	}
}
