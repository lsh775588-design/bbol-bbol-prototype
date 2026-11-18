package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.repository.PlaceLookupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 도착 인증에 필요한 장소 값. 점수는 RewardPolicy 적용 후(장소 값 > 카테고리 기본 값). */
data class PlaceCompletionTarget(
	val placeId: Long,
	val name: String,
	val lat: Double,
	val lng: Double,
	val radiusM: Int,
	val rewardPoint: Int,
	val categoryCode: String,
)

/** 내역/일지 표시용 장소 정보. */
data class PlaceBrief(
	val placeId: Long,
	val name: String,
	val categoryCode: String,
	val address: String,
)

/** (ranking 추가) 장소 랭킹 표시용. active=false(폐업)면 랭킹에서 뺀다. rewardPoint는 RewardPolicy 적용 후 값. */
data class PlaceRankingBrief(
	val placeId: Long,
	val name: String,
	val categoryCode: String,
	val address: String,
	val rewardPoint: Int,
	val active: Boolean,
)

/**
 * completion, point 도메인이 장소 정보를 읽는 공개 진입점. 다른 도메인은 PlaceRepository 대신 이 서비스를 쓴다.
 */
@Service
@Transactional(readOnly = true)
class PlaceQueryService(private val lookup: PlaceLookupRepository) {

	/** 활성 장소만. 없거나 비활성이면 null. */
	fun findCompletionTarget(placeId: Long): PlaceCompletionTarget? =
		lookup.findActiveWithCategory(placeId).orElse(null)?.let {
			PlaceCompletionTarget(
				placeId = it.id!!,
				name = it.name,
				lat = it.lat,
				lng = it.lng,
				radiusM = it.radiusM,
				rewardPoint = it.effectiveRewardPoint(),
				categoryCode = it.category.code,
			)
		}

	/** 여러 장소의 표시 정보를 한 번에(비활성 포함). 없는 id는 결과에 없다. */
	fun briefs(placeIds: Collection<Long>): Map<Long, PlaceBrief> {
		if (placeIds.isEmpty()) return emptyMap()
		return lookup.findAllWithCategory(placeIds.toSet()).associate {
			it.id!! to PlaceBrief(it.id!!, it.name, it.category.code, it.address)
		}
	}

	/** (ranking 추가) 장소 랭킹 표시 정보. 없는 id는 결과에 없다. */
	fun rankingBriefs(placeIds: Collection<Long>): Map<Long, PlaceRankingBrief> {
		if (placeIds.isEmpty()) return emptyMap()
		return lookup.findAllWithCategory(placeIds.toSet()).associate {
			it.id!! to PlaceRankingBrief(it.id!!, it.name, it.category.code, it.address, it.effectiveRewardPoint(), it.active)
		}
	}

	fun isKnownCategory(code: String): Boolean = lookup.existsCategoryCode(code)

	fun placeIdsByCategory(code: String): List<Long> = lookup.findIdsByCategoryCode(code)
}
