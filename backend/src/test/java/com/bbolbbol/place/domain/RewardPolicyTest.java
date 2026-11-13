package com.bbolbbol.place.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RewardPolicyTest {

    private final PlaceCategory cafe = new PlaceCategory("CAFE", "카페", 100);

    private Place place(Integer reward) {
        return new Place(cafe, "p", "addr", null, null, 37.5, 127.0, 100, 200, reward, "k1",
                "https://place.map.kakao.com/1", null, true);
    }

    @Test
    @DisplayName("장소 점수가 null이면 카테고리 기본 점수를 쓴다")
    void fallsBackToCategory() {
        assertThat(RewardPolicy.effective(null, 100)).isEqualTo(100);
        assertThat(place(null).effectiveRewardPoint()).isEqualTo(100);
    }

    @Test
    @DisplayName("장소 점수가 있으면 카테고리 값을 덮는다(특별 카페 150)")
    void placeOverridesCategory() {
        assertThat(RewardPolicy.effective(150, 100)).isEqualTo(150);
        assertThat(place(150).effectiveRewardPoint()).isEqualTo(150);
    }

    @Test
    @DisplayName("장소 점수 0도 값으로 인정한다(null과 구분)")
    void zeroIsAValue() {
        assertThat(RewardPolicy.effective(0, 100)).isZero();
    }
}
