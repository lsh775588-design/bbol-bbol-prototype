package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.entity.PlaceCategory
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceServiceUnitTest {

	private val known = setOf("CAFE", "PARK")

	private fun place(hot: HotType?) = Place(
		category = PlaceCategory("CAFE", "카페", 100), name = "p", address = "a", roadAddress = null, phone = null,
		lat = 37.5, lng = 127.0, radiusM = 100, notifyRadiusM = 200, rewardPoint = null,
		kakaoPlaceId = "k", kakaoPlaceUrl = "https://place.map.kakao.com/1", hotType = hot, active = true,
	)

	@Test
	@DisplayName("검색 조건 기본값: radius 1000, size 100, 카테고리 없음")
	fun defaults() {
		val c = PlaceSearchCondition.validated(37.5, 127.0, null, null, null, null, known)
		assertEquals(1000, c.radius)
		assertEquals(100, c.size)
		assertTrue(c.categories.isEmpty())
		assertFalse(c.excludeCompletedToday)
	}

	@Test
	@DisplayName("검색 조건: 쉼표로 여러 카테고리, 경계값 허용")
	fun parsesCategories() {
		val c = PlaceSearchCondition.validated(-90.0, 180.0, 5000, "CAFE,PARK", true, 200, known)
		assertEquals(listOf("CAFE", "PARK"), c.categories.toList())
		assertEquals(5000, c.radius)
		assertEquals(200, c.size)
	}

	@Test
	@DisplayName("검색 조건: 여러 오류는 한 번에 모아 COMMON400")
	fun collectsErrors() {
		val e = assertThrows<BusinessException> {
			PlaceSearchCondition.validated(null, 200.0, 5001, "X", null, 0, known)
		}
		assertEquals(CommonErrorCode.INVALID_INPUT, e.errorCode)
	}

	@Test
	@DisplayName("재방문이면 expectedPoint는 50%, 사진 보너스는 첫 방문에만")
	fun expectedPoint() {
		val first = PlaceUserState(false, false, 0, null, false)
		val again = PlaceUserState(true, false, 3, null, false)
		assertEquals(300, first.expectedPoint(300))
		assertEquals(150, again.expectedPoint(300))
		assertTrue(first.photoBonusAvailable())
		assertFalse(again.photoBonusAvailable())
	}

	@Test
	@DisplayName("POPULAR가 CURATED보다 우선한다")
	fun popularWins() {
		val popular = PlaceUserState(false, false, 5, null, true)
		val normal = PlaceUserState(false, false, 0, null, false)
		assertEquals(HotType.POPULAR, PlaceService.hotTypeOf(place(HotType.CURATED), popular))
		assertEquals(HotType.CURATED, PlaceService.hotTypeOf(place(HotType.CURATED), normal))
		assertNull(PlaceService.hotTypeOf(place(null), normal))
	}
}
