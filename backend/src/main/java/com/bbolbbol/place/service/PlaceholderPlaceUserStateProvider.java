package com.bbolbbol.place.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * PLACEHOLDER (stage 1). completion 도메인이 아직 없어서 모든 장소를 "방문한 적 없음, 오늘 인증 안 함,
 * 인증 0회, POPULAR 아님"으로 돌려준다.
 * TODO(stage 2): completion 서비스 메서드(내 완료 장소 ID 집합, 장소별 인증 횟수, 최근 7일 인기 집계)를 호출하는
 * 구현으로 교체하고 이 클래스를 삭제한다. place 도메인은 completion repository를 직접 쓰지 않는다.
 */
@Component
public class PlaceholderPlaceUserStateProvider implements PlaceUserStateProvider {

    private static final PlaceUserState NEVER_VISITED = new PlaceUserState(false, false, 0, null, false);

    @Override
    public Map<Long, PlaceUserState> statesFor(Long userId, Collection<Long> placeIds) {
        Map<Long, PlaceUserState> result = new HashMap<>();
        placeIds.forEach(id -> result.put(id, NEVER_VISITED));
        return result;
    }
}
