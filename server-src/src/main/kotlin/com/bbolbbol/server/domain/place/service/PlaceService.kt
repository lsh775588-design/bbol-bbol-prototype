package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.dto.CategoryItem
import com.bbolbbol.server.domain.place.dto.CategoryListResponse
import com.bbolbbol.server.domain.place.dto.PlaceDetail
import com.bbolbbol.server.domain.place.dto.PlaceListResponse
import com.bbolbbol.server.domain.place.dto.PlaceSummary
import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.exception.PlaceErrorCode
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PlaceService(
	private val placeRepository: PlaceRepository,
	private val categoryRepository: PlaceCategoryRepository,
	private val userStateProvider: PlaceUserStateProvider,
) {

	private data class Hit(val place: Place, val distance: Double)

	fun listCategories(): CategoryListResponse =
		CategoryListResponse(categoryRepository.findAllByOrderByIdAsc().map(CategoryItem::from))

	/** 검증된 카테고리 코드 집합(요청 검증용). */
	fun categoryCodes(): Set<String> = categoryRepository.findAll().map { it.code }.toSet()

	/** 반경 안의 장소를 거리순(같으면 id순)으로 최대 size개. DB 조회 1회 + 상태 일괄 조회 1회. */
	fun search(userId: Long, c: PlaceSearchCondition): PlaceListResponse {
		val box = GeoDistance.boundingBox(c.lat, c.lng, c.radius.toDouble())
		val all = c.categories.isEmpty()
		// 빈 컬렉션 IN 절을 피하려고 전체 조회일 때는 더미 값을 넘긴다.
		val codes = if (all) setOf("") else c.categories
		val candidates = placeRepository.findInBox(box.minLat, box.maxLat, box.minLng, box.maxLng, all, codes)

		val hits = candidates
			.map { Hit(it, GeoDistance.haversineMeters(c.lat, c.lng, it.lat, it.lng)) }
			.filter { it.distance <= c.radius }
			.sortedWith(compareBy<Hit> { it.distance }.thenBy { it.place.id })

		val states = userStateProvider.statesFor(userId, hits.map { it.place.id!! })
		val places = hits
			.filter { !(c.excludeCompletedToday && states.getValue(it.place.id!!).completedToday) }
			.take(c.size)
			.map {
				val state = states.getValue(it.place.id!!)
				PlaceSummary.of(it.place, it.distance, state, hotTypeOf(it.place, state))
			}
		return PlaceListResponse(places)
	}

	fun detail(userId: Long, placeId: Long): PlaceDetail {
		val place = placeRepository.findActiveById(placeId)
			.orElseThrow { BusinessException(PlaceErrorCode.PLACE_NOT_FOUND) }
		val state = userStateProvider.statesFor(userId, listOf(placeId)).getValue(placeId)
		return PlaceDetail.of(place, state, hotTypeOf(place, state))
	}

	companion object {
		/** 둘 다 해당하면 POPULAR가 우선한다(명세). */
		internal fun hotTypeOf(place: Place, state: PlaceUserState): HotType? =
			if (state.popular) HotType.POPULAR else place.hotType
	}
}
