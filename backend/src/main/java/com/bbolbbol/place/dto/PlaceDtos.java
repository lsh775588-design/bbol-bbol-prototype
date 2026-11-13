package com.bbolbbol.place.dto;

import com.bbolbbol.place.domain.HotType;
import com.bbolbbol.place.domain.Place;
import com.bbolbbol.place.domain.PlaceCategory;
import com.bbolbbol.place.service.PlaceUserState;
import java.time.LocalDateTime;
import java.util.List;

/** 응답 DTO 모음(명세 필드명 그대로). */
public final class PlaceDtos {

    private PlaceDtos() {
    }

    public record CategoryItem(String code, String name, int rewardPoint) {
        public static CategoryItem from(PlaceCategory c) {
            return new CategoryItem(c.getCode(), c.getName(), c.getRewardPoint());
        }
    }

    public record CategoryListResponse(List<CategoryItem> categories) {
    }

    public record PlaceSummary(Long placeId, String name, String category, double lat, double lng, long distance,
                               int radius, int notifyRadius, int rewardPoint, int expectedPoint,
                               boolean isHot, HotType hotType, boolean visitedBefore, boolean isCompleted) {
        public static PlaceSummary of(Place p, double distanceM, PlaceUserState s, HotType hotType) {
            return new PlaceSummary(p.getId(), p.getName(), p.getCategory().getCode(), p.getLat(), p.getLng(),
                    Math.round(distanceM), p.getRadiusM(), p.getNotifyRadiusM(), p.effectiveRewardPoint(),
                    s.expectedPoint(p.effectiveRewardPoint()), hotType != null, hotType,
                    s.visitedBefore(), s.completedToday());
        }
    }

    public record PlaceListResponse(List<PlaceSummary> places) {
    }

    public record PlaceDetail(Long placeId, String name, String category, String categoryName, String address,
                              String roadAddress, String phone, double lat, double lng, int radius, int notifyRadius,
                              String kakaoPlaceUrl, int rewardPoint, int expectedPoint, boolean photoBonusAvailable,
                              boolean isHot, HotType hotType, long completedCount, boolean visitedBefore,
                              boolean isCompleted, LocalDateTime myLastCompletedAt) {
        public static PlaceDetail of(Place p, PlaceUserState s, HotType hotType) {
            int reward = p.effectiveRewardPoint();
            return new PlaceDetail(p.getId(), p.getName(), p.getCategory().getCode(), p.getCategory().getName(),
                    p.getAddress(), p.getRoadAddress(), p.getPhone(), p.getLat(), p.getLng(), p.getRadiusM(),
                    p.getNotifyRadiusM(), p.getKakaoPlaceUrl(), reward, s.expectedPoint(reward),
                    s.photoBonusAvailable(), hotType != null, hotType, s.completedCount(), s.visitedBefore(),
                    s.completedToday(), s.myLastCompletedAt());
        }
    }
}
