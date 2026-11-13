package com.bbolbbol.place.service;

import java.util.Collection;
import java.util.Map;

/**
 * 사용자별 방문/인증 상태와 인증 집계 기반 값(POPULAR, completedCount)의 공급원.
 * 여러 장소를 한 번에 조회해 N+1을 피한다. completion 도메인이 생기면 그 서비스를 호출하는 구현으로 교체한다.
 */
public interface PlaceUserStateProvider {

    Map<Long, PlaceUserState> statesFor(Long userId, Collection<Long> placeIds);
}
