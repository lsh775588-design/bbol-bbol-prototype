package com.bbolbbol.server.domain.place.entity

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RewardPolicyTest {

	private val cafe = PlaceCategory("CAFE", "카페", 100)

	private fun place(reward: Int?) = Place(
		category = cafe, name = "p", address = "addr", roadAddress = null, phone = null,
		lat = 37.5, lng = 127.0, radiusM = 100, notifyRadiusM = 200, rewardPoint = reward,
		kakaoPlaceId = "k1", kakaoPlaceUrl = "https://place.map.kakao.com/1", hotType = null, active = true,
	)

	@Test
	@DisplayName("장소 점수가 null이면 카테고리 기본 점수를 쓴다")
	fun fallsBackToCategory() {
		assertEquals(100, RewardPolicy.effective(null, 100))
		assertEquals(100, place(null).effectiveRewardPoint())
	}

	@Test
	@DisplayName("장소 점수가 있으면 카테고리 값을 덮는다(특별 카페 150)")
	fun placeOverridesCategory() {
		assertEquals(150, RewardPolicy.effective(150, 100))
		assertEquals(150, place(150).effectiveRewardPoint())
	}

	@Test
	@DisplayName("장소 점수 0도 값으로 인정한다(null과 구분)")
	fun zeroIsAValue() {
		assertEquals(0, RewardPolicy.effective(0, 100))
	}
}
