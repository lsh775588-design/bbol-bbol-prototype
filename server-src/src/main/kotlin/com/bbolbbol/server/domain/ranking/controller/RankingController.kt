package com.bbolbbol.server.domain.ranking.controller

import com.bbolbbol.server.domain.ranking.dto.PlaceRankingResponse
import com.bbolbbol.server.domain.ranking.dto.StreakRankingResponse
import com.bbolbbol.server.domain.ranking.dto.UserRankingResponse
import com.bbolbbol.server.domain.ranking.service.RankingPeriod
import com.bbolbbol.server.domain.ranking.service.RankingService
import com.bbolbbol.server.global.common.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 인증 필요. period가 없는 값이면 타입 변환 실패로 COMMON400, 유저 랭킹은 period 필수다. */
@RestController
@RequestMapping("/rankings")
class RankingController(private val rankingService: RankingService) {

	@GetMapping("/users")
	fun users(
		@AuthenticationPrincipal userId: Long,
		@RequestParam period: RankingPeriod,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<UserRankingResponse> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", rankingService.userRanking(userId, period, size))

	@GetMapping("/places")
	fun places(
		@RequestParam(required = false) period: RankingPeriod?,
		@RequestParam(required = false) category: String?,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<PlaceRankingResponse> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", rankingService.placeRanking(period, category, size))

	@GetMapping("/streaks")
	fun streaks(
		@AuthenticationPrincipal userId: Long,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<StreakRankingResponse> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", rankingService.streakRanking(userId, size))
}
