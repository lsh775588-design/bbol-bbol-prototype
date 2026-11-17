package com.bbolbbol.server.domain.place.entity

/** 점수 출처 규칙(쟁점 7 가정): 장소별 값이 있으면 카테고리 기본 값을 덮는다. */
object RewardPolicy {

	fun effective(placeReward: Int?, categoryReward: Int): Int = placeReward ?: categoryReward
}
