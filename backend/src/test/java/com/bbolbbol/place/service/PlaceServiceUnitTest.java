package com.bbolbbol.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.place.domain.HotType;
import com.bbolbbol.place.domain.Place;
import com.bbolbbol.place.domain.PlaceCategory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceServiceUnitTest {

    private static final Set<String> KNOWN = Set.of("CAFE", "PARK");

    private Place place(HotType hot) {
        return new Place(new PlaceCategory("CAFE", "카페", 100), "p", "a", null, null, 37.5, 127.0, 100, 200, null,
                "k", "https://place.map.kakao.com/1", hot, true);
    }

    @Test
    @DisplayName("검색 조건 기본값: radius 1000, size 100, 카테고리 없음")
    void defaults() {
        PlaceSearchCondition c = PlaceSearchCondition.validated(37.5, 127.0, null, null, null, null, KNOWN);
        assertThat(c.radius()).isEqualTo(1000);
        assertThat(c.size()).isEqualTo(100);
        assertThat(c.categories()).isEmpty();
        assertThat(c.excludeCompletedToday()).isFalse();
    }

    @Test
    @DisplayName("검색 조건: 쉼표로 여러 카테고리, 경계값 허용")
    void parsesCategories() {
        PlaceSearchCondition c = PlaceSearchCondition.validated(-90.0, 180.0, 5000, "CAFE,PARK", true, 200, KNOWN);
        assertThat(c.categories()).containsExactly("CAFE", "PARK");
        assertThat(c.radius()).isEqualTo(5000);
        assertThat(c.size()).isEqualTo(200);
    }

    @Test
    @DisplayName("검색 조건: 여러 오류는 한 번에 모아 COMMON400")
    void collectsErrors() {
        assertThatThrownBy(() -> PlaceSearchCondition.validated(null, 200.0, 5001, "X", null, 0, KNOWN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON400));
    }

    @Test
    @DisplayName("재방문이면 expectedPoint는 50%, 사진 보너스는 첫 방문에만")
    void expectedPoint() {
        PlaceUserState first = new PlaceUserState(false, false, 0, null, false);
        PlaceUserState again = new PlaceUserState(true, false, 3, null, false);
        assertThat(first.expectedPoint(300)).isEqualTo(300);
        assertThat(again.expectedPoint(300)).isEqualTo(150);
        assertThat(first.photoBonusAvailable()).isTrue();
        assertThat(again.photoBonusAvailable()).isFalse();
    }

    @Test
    @DisplayName("POPULAR가 CURATED보다 우선한다")
    void popularWins() {
        PlaceUserState popular = new PlaceUserState(false, false, 5, null, true);
        PlaceUserState normal = new PlaceUserState(false, false, 0, null, false);
        assertThat(PlaceService.hotTypeOf(place(HotType.CURATED), popular)).isEqualTo(HotType.POPULAR);
        assertThat(PlaceService.hotTypeOf(place(HotType.CURATED), normal)).isEqualTo(HotType.CURATED);
        assertThat(PlaceService.hotTypeOf(place(null), normal)).isNull();
    }

    @Test
    @DisplayName("placeholder는 모든 장소를 방문한 적 없음으로 돌려준다")
    void placeholder() {
        var map = new PlaceholderPlaceUserStateProvider().statesFor(1L, List.of(1L, 2L));
        assertThat(map).hasSize(2);
        assertThat(map.get(1L).visitedBefore()).isFalse();
        assertThat(map.get(2L).completedToday()).isFalse();
    }
}
