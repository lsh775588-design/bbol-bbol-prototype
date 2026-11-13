package com.bbolbbol.place.service;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.common.error.GlobalExceptionHandler.FieldError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GET /places 쿼리 파라미터를 검증해 만든 검색 조건. 검증 실패는 COMMON400 + data.errors. */
public record PlaceSearchCondition(double lat, double lng, int radius, Set<String> categories,
                                   boolean excludeCompletedToday, int size) {

    public static final int DEFAULT_RADIUS = 1000;
    public static final int MAX_RADIUS = 5000;
    public static final int DEFAULT_SIZE = 100;
    public static final int MAX_SIZE = 200;

    /** @param knownCategoryCodes 존재하는 카테고리 코드(없는 category 검증용) */
    public static PlaceSearchCondition validated(Double lat, Double lng, Integer radius, String category,
                                                 Boolean excludeCompletedToday, Integer size,
                                                 Set<String> knownCategoryCodes) {
        List<FieldError> errors = new ArrayList<>();
        if (lat == null) {
            errors.add(new FieldError("lat", "필수 파라미터가 누락되었습니다."));
        } else if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            errors.add(new FieldError("lat", "위도는 -90 이상 90 이하여야 합니다."));
        }
        if (lng == null) {
            errors.add(new FieldError("lng", "필수 파라미터가 누락되었습니다."));
        } else if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            errors.add(new FieldError("lng", "경도는 -180 이상 180 이하여야 합니다."));
        }
        int r = radius == null ? DEFAULT_RADIUS : radius;
        if (r < 1 || r > MAX_RADIUS) {
            errors.add(new FieldError("radius", "반경은 1 이상 " + MAX_RADIUS + " 이하여야 합니다."));
        }
        int s = size == null ? DEFAULT_SIZE : size;
        if (s < 1 || s > MAX_SIZE) {
            errors.add(new FieldError("size", "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다."));
        }
        Set<String> codes = new LinkedHashSet<>();
        if (category != null) {
            for (String raw : category.split(",", -1)) {
                String code = raw.trim();
                if (code.isEmpty() || !knownCategoryCodes.contains(code)) {
                    errors.add(new FieldError("category", "존재하지 않는 카테고리 코드입니다: " + code));
                    break;
                }
                codes.add(code);
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON400, Map.of("errors", errors));
        }
        return new PlaceSearchCondition(lat, lng, r, codes, Boolean.TRUE.equals(excludeCompletedToday), s);
    }
}
