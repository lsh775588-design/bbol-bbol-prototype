package com.bbolbbol.server.domain.place.controller

import com.bbolbbol.server.domain.place.dto.CategoryListResponse
import com.bbolbbol.server.domain.place.dto.PlaceDetail
import com.bbolbbol.server.domain.place.dto.PlaceListResponse
import com.bbolbbol.server.domain.place.dto.RecommendationListResponse
import com.bbolbbol.server.domain.place.service.PlaceRecommendationService
import com.bbolbbol.server.domain.place.service.PlaceSearchCondition
import com.bbolbbol.server.domain.place.service.RecommendationCondition
import com.bbolbbol.server.domain.place.service.PlaceService
import com.bbolbbol.server.global.common.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 모든 엔드포인트는 인증이 필요하다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
class PlaceController(
	private val placeService: PlaceService,
	private val recommendationService: PlaceRecommendationService,
) {

	@GetMapping("/place-categories")
	fun categories(): ApiResponse<CategoryListResponse> = ApiResponse.ok(placeService.listCategories())

	@GetMapping("/places")
	fun places(
		@AuthenticationPrincipal userId: Long,
		@RequestParam(required = false) lat: Double?,
		@RequestParam(required = false) lng: Double?,
		@RequestParam(required = false) radius: Int?,
		@RequestParam(required = false) category: String?,
		@RequestParam(required = false) excludeCompletedToday: Boolean?,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<PlaceListResponse> {
		val cond = PlaceSearchCondition.validated(
			lat, lng, radius, category, excludeCompletedToday, size, placeService.categoryCodes(),
		)
		return ApiResponse.ok(placeService.search(userId, cond))
	}

	/** /places/{placeId}보다 구체적인 경로라 "recommendations"가 placeId로 해석되지 않는다. */
	@GetMapping("/places/recommendations")
	fun recommendations(
		@AuthenticationPrincipal userId: Long,
		@RequestParam(required = false) lat: Double?,
		@RequestParam(required = false) lng: Double?,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<RecommendationListResponse> =
		ApiResponse.ok(recommendationService.recommend(userId, RecommendationCondition.validated(lat, lng, size)))

	@GetMapping("/places/{placeId}")
	fun detail(@AuthenticationPrincipal userId: Long, @PathVariable placeId: Long): ApiResponse<PlaceDetail> =
		ApiResponse.ok(placeService.detail(userId, placeId))
}
