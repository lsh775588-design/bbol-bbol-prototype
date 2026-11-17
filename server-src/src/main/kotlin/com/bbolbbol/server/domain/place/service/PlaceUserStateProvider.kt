package com.bbolbbol.server.domain.place.service

/**
 * 사용자별 방문/인증 상태와 인증 집계 기반 값(POPULAR, completedCount)의 공급원.
 * 여러 장소를 한 번에 조회해 N+1을 피한다. 구현은 completion 도메인의 CompletionPlaceUserStateProvider다.
 */
interface PlaceUserStateProvider {

	fun statesFor(userId: Long, placeIds: Collection<Long>): Map<Long, PlaceUserState>
}
