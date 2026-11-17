package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.entity.PlaceCategory
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceRecommendationServiceTest {

	private fun place(id: Long, dLat: Double, hot: HotType? = HotType.CURATED, reward: Int? = null) = Place(
		category = PlaceCategory("CAFE", "카페", 100), name = "p$id", address = "a", roadAddress = null, phone = null,
		lat = 37.5 + dLat, lng = 127.0, radiusM = 100, notifyRadiusM = 200, rewardPoint = reward,
		kakaoPlaceId = "k$id", kakaoPlaceUrl = "https://place.map.kakao.com/$id", hotType = hot, active = true,
	).also { ReflectionTestUtils.setField(it, "id", id) }

	private fun state(visited: Boolean = false, today: Boolean = false, popular: Boolean = false) =
		PlaceUserState(visited, today, 0, null, popular)

	private fun service(
		places: List<Place>,
		states: Map<Long, PlaceUserState> = emptyMap(),
		extra: List<Place> = emptyList(),
	) = PlaceRecommendationService(
		listOf(
			object : RecommendationSource {
				override fun candidates() = places
			},
			object : RecommendationSource {
				override fun candidates() = extra
			},
		),
		object : PlaceUserStateProvider {
			override fun statesFor(userId: Long, placeIds: Collection<Long>) =
				placeIds.associateWith { states[it] ?: state() }
		},
	)

	private val cond = RecommendationCondition(37.5, 127.0, 5)

	@Test
	@DisplayName("조건 기본값 size 5, 경계 1과 10 허용")
	fun conditionDefaults() {
		assertEquals(5, RecommendationCondition.validated(37.5, 127.0, null).size)
		assertEquals(1, RecommendationCondition.validated(37.5, 127.0, 1).size)
		assertEquals(10, RecommendationCondition.validated(37.5, 127.0, 10).size)
	}

	@Test
	@DisplayName("조건 오류: lat/lng 누락, 범위, size 범위는 COMMON400")
	fun conditionErrors() {
		listOf(
			Triple(null, 127.0, 5), Triple(37.5, null, 5), Triple(37.5, 127.0, 0),
			Triple(37.5, 127.0, 11), Triple(91.0, 127.0, null),
		).forEach { (a, b, c) ->
			val e = assertThrows<BusinessException> { RecommendationCondition.validated(a, b, c) }
			assertEquals(CommonErrorCode.INVALID_INPUT, e.errorCode)
		}
	}

	@Test
	@DisplayName("거리순, 같으면 id순")
	fun byDistanceThenId() {
		val r = service(listOf(place(3, 0.0045), place(1, 0.0009), place(2, 0.0009))).recommend(1, cond)
		assertEquals(listOf(1L, 2L, 3L), r.places.map { it.placeId })
		assertEquals(listOf(100L, 100L, 500L), r.places.map { it.distance })
	}

	@Test
	@DisplayName("안 가본 곳이 가까운 가본 곳보다 먼저, 오늘 인증한 곳은 맨 뒤")
	fun unvisitedFirstCompletedLast() {
		val ps = listOf(place(1, 0.0009), place(2, 0.0045), place(3, 0.0001))
		val states = mapOf(1L to state(visited = true), 3L to state(visited = true, today = true))
		val r = service(ps, states).recommend(1, cond)
		assertEquals(listOf(2L, 1L, 3L), r.places.map { it.placeId })
		assertTrue(r.places.last().isCompleted)
		assertTrue(r.places.last().reason.contains("오늘"))
	}

	@Test
	@DisplayName("size만큼 자르고, 빈 후보는 빈 목록")
	fun sizeAndEmpty() {
		val ps = (1L..4L).map { place(it, it * 0.001) }
		assertEquals(2, service(ps).recommend(1, RecommendationCondition(37.5, 127.0, 2)).places.size)
		assertTrue(service(emptyList()).recommend(1, cond).places.isEmpty())
	}

	@Test
	@DisplayName("여러 공급원의 중복 후보는 한 번만 나온다")
	fun dedupe() {
		val p = place(1, 0.001)
		assertEquals(1, service(listOf(p), extra = listOf(p)).recommend(1, cond).places.size)
	}

	@Test
	@DisplayName("점수와 hotType 매핑: 장소 점수 우선, POPULAR 우선, 재방문 50%")
	fun mapping() {
		val r = service(
			listOf(place(1, 0.001, reward = 300), place(2, 0.002)),
			mapOf(1L to state(visited = true), 2L to state(popular = true)),
		).recommend(1, cond)
		val popular = r.places[0] // 안 가본 곳이 먼저
		val visited = r.places[1]
		assertEquals(300, visited.rewardPoint)
		assertEquals(150, visited.expectedPoint)
		assertEquals(HotType.CURATED, visited.hotType)
		assertEquals(HotType.POPULAR, popular.hotType)
		assertTrue(popular.isHot)
	}
}
