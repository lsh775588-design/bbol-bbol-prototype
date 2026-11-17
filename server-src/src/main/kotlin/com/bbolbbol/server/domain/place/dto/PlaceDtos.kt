package com.bbolbbol.server.domain.place.dto

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.entity.PlaceCategory
import com.bbolbbol.server.domain.place.service.PlaceUserState
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/*
 * 응답 DTO 모음(명세 필드명 그대로).
 * isHot, isCompleted는 @get:JsonProperty로 키를 명시해 고정한다(Jackson 3 + kotlin module은 없어도 isHot으로 나오는 것을 확인했지만, 계약이라 방어한다).
 */

data class CategoryItem(val code: String, val name: String, val rewardPoint: Int) {
	companion object {
		fun from(c: PlaceCategory) = CategoryItem(c.code, c.name, c.rewardPoint)
	}
}

data class CategoryListResponse(val categories: List<CategoryItem>)

data class PlaceSummary(
	val placeId: Long,
	val name: String,
	val category: String,
	val lat: Double,
	val lng: Double,
	val distance: Long,
	val radius: Int,
	val notifyRadius: Int,
	val rewardPoint: Int,
	val expectedPoint: Int,
	@get:JsonProperty("isHot") val isHot: Boolean,
	val hotType: HotType?,
	val visitedBefore: Boolean,
	@get:JsonProperty("isCompleted") val isCompleted: Boolean,
) {
	companion object {
		fun of(p: Place, distanceM: Double, s: PlaceUserState, hotType: HotType?): PlaceSummary {
			val reward = p.effectiveRewardPoint()
			return PlaceSummary(
				placeId = p.id!!,
				name = p.name,
				category = p.category.code,
				lat = p.lat,
				lng = p.lng,
				distance = Math.round(distanceM),
				radius = p.radiusM,
				notifyRadius = p.notifyRadiusM,
				rewardPoint = reward,
				expectedPoint = s.expectedPoint(reward),
				isHot = hotType != null,
				hotType = hotType,
				visitedBefore = s.visitedBefore,
				isCompleted = s.completedToday,
			)
		}
	}
}

/** 추천 카드. 명세 예시에 없는 radius/expectedPoint/hotType 등 "나머지 필드는 주변 장소 조회와 같다"를 따라 PlaceSummary 필드 + reason. */
data class RecommendedPlace(
	val placeId: Long,
	val name: String,
	val category: String,
	val lat: Double,
	val lng: Double,
	val distance: Long,
	val radius: Int,
	val notifyRadius: Int,
	val rewardPoint: Int,
	val expectedPoint: Int,
	@get:JsonProperty("isHot") val isHot: Boolean,
	val hotType: HotType?,
	val visitedBefore: Boolean,
	@get:JsonProperty("isCompleted") val isCompleted: Boolean,
	val reason: String,
) {
	companion object {
		fun of(s: PlaceSummary, reason: String) = RecommendedPlace(
			s.placeId, s.name, s.category, s.lat, s.lng, s.distance, s.radius, s.notifyRadius, s.rewardPoint,
			s.expectedPoint, s.isHot, s.hotType, s.visitedBefore, s.isCompleted, reason,
		)
	}
}

data class RecommendationListResponse(val places: List<RecommendedPlace>)

data class PlaceListResponse(val places: List<PlaceSummary>)

data class PlaceDetail(
	val placeId: Long,
	val name: String,
	val category: String,
	val categoryName: String,
	val address: String,
	val roadAddress: String?,
	val phone: String?,
	val lat: Double,
	val lng: Double,
	val radius: Int,
	val notifyRadius: Int,
	val kakaoPlaceUrl: String,
	val rewardPoint: Int,
	val expectedPoint: Int,
	val photoBonusAvailable: Boolean,
	@get:JsonProperty("isHot") val isHot: Boolean,
	val hotType: HotType?,
	val completedCount: Long,
	val visitedBefore: Boolean,
	@get:JsonProperty("isCompleted") val isCompleted: Boolean,
	val myLastCompletedAt: LocalDateTime?,
) {
	companion object {
		fun of(p: Place, s: PlaceUserState, hotType: HotType?): PlaceDetail {
			val reward = p.effectiveRewardPoint()
			return PlaceDetail(
				placeId = p.id!!,
				name = p.name,
				category = p.category.code,
				categoryName = p.category.name,
				address = p.address,
				roadAddress = p.roadAddress,
				phone = p.phone,
				lat = p.lat,
				lng = p.lng,
				radius = p.radiusM,
				notifyRadius = p.notifyRadiusM,
				kakaoPlaceUrl = p.kakaoPlaceUrl,
				rewardPoint = reward,
				expectedPoint = s.expectedPoint(reward),
				photoBonusAvailable = s.photoBonusAvailable(),
				isHot = hotType != null,
				hotType = hotType,
				completedCount = s.completedCount,
				visitedBefore = s.visitedBefore,
				isCompleted = s.completedToday,
				myLastCompletedAt = s.myLastCompletedAt,
			)
		}
	}
}
