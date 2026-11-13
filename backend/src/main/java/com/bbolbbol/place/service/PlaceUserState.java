package com.bbolbbol.place.service;

import java.time.LocalDateTime;

/**
 * 한 사용자와 한 장소의 관계 중 completion 도메인에 속한 값. place 도메인은 이 값을 직접 계산하지 않고
 * {@link PlaceUserStateProvider}에서만 받는다.
 */
public record PlaceUserState(boolean visitedBefore, boolean completedToday, long completedCount,
                             LocalDateTime myLastCompletedAt, boolean popular) {

    /** 지금 인증하면 받을 점수: 첫 방문이면 전액, 재방문이면 50%(Gray Area A3). */
    public int expectedPoint(int rewardPoint) {
        return visitedBefore ? rewardPoint / 2 : rewardPoint;
    }

    /** 사진 보너스(+50)는 첫 방문일 때만 받을 수 있다(Gray Area C2). */
    public boolean photoBonusAvailable() {
        return !visitedBefore && !completedToday;
    }
}
