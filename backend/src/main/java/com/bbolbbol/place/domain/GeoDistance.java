package com.bbolbbol.place.domain;

/** 지구 표면 거리(haversine)와 바운딩 박스 계산. 순수 함수. */
public final class GeoDistance {

    public static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_M / 180.0;

    private GeoDistance() {
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }

    /** 두 좌표 사이 거리(m). */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = p2 - p1;
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 중심에서 radiusM 안의 모든 점을 포함하는 사각형. 정확한 거리 필터는 haversine으로 한다. */
    public static BoundingBox boundingBox(double lat, double lng, double radiusM) {
        double dLat = radiusM / METERS_PER_DEGREE_LAT;
        double cos = Math.cos(Math.toRadians(lat));
        double dLng = cos < 1e-9 ? 180.0 : Math.min(180.0, radiusM / (METERS_PER_DEGREE_LAT * cos));
        return new BoundingBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng);
    }
}
