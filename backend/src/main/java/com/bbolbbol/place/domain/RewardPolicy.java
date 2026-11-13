package com.bbolbbol.place.domain;

/** 점수 출처 규칙(쟁점 7 가정): 장소별 값이 있으면 카테고리 기본 값을 덮는다. */
public final class RewardPolicy {

    private RewardPolicy() {
    }

    public static int effective(Integer placeReward, int categoryReward) {
        return placeReward != null ? placeReward : categoryReward;
    }
}
