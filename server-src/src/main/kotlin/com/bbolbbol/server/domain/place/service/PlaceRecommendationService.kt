package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.dto.PlaceSummary
import com.bbolbbol.server.domain.place.dto.RecommendationListResponse
import com.bbolbbol.server.domain.place.dto.RecommendedPlace
import com.bbolbbol.server.domain.place.entity.Place
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 장소 추천. 후보는 [RecommendationSource]들이 공급하고, 거리/보상/사용자 상태 매핑은 [PlaceSummary]와
 * [PlaceUserStateProvider]를 그대로 쓴다.
 * 순서(명세 "안 가본 곳 우선 + 거리순"): 오늘 이미 인증한 곳은 뒤로, 그다음 안 가본 곳 먼저, 거리 가까운 순, id순.
 */
@Service
@Transactional(readOnly = true)
class PlaceRecommendationService(
	private val sources: List<RecommendationSource>,
	private val userStateProvider: PlaceUserStateProvider,
) {

	private data class Candidate(val place: Place, val distance: Double, val state: PlaceUserState)

	fun recommend(userId: Long, c: RecommendationCondition): RecommendationListResponse {
		val candidates = sources.flatMap { it.candidates() }.distinctBy { it.id }
		val states = userStateProvider.statesFor(userId, candidates.map { it.id!! })
		val places = candidates
			.map { Candidate(it, GeoDistance.haversineMeters(c.lat, c.lng, it.lat, it.lng), states.getValue(it.id!!)) }
			.sortedWith(
				compareBy<Candidate> { it.state.completedToday }
					.thenBy { it.state.visitedBefore }
					.thenBy { it.distance }
					.thenBy { it.place.id },
			)
			.take(c.size)
			.map {
				val summary = PlaceSummary.of(it.place, it.distance, it.state, PlaceService.hotTypeOf(it.place, it.state))
				RecommendedPlace.of(summary, reasonOf(it.state))
			}
		return RecommendationListResponse(places)
	}

	private fun reasonOf(state: PlaceUserState): String = when {
		state.completedToday -> "오늘 이미 다녀온 뽈뽈 추천 장소예요"
		state.visitedBefore -> "뽈뽈이 고른 추천 장소예요"
		else -> "뽈뽈이 고른 추천 장소, 아직 가보지 않았어요"
	}
}
