package com.bbolbbol.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbolbbol.place.domain.GeoDistance.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    @DisplayName("같은 좌표의 거리는 0")
    void zero() {
        assertThat(GeoDistance.haversineMeters(37.5759, 126.9768, 37.5759, 126.9768)).isZero();
    }

    @Test
    @DisplayName("위도 0.01도는 약 1112m")
    void oneHundredthDegreeLatitude() {
        assertThat(GeoDistance.haversineMeters(37.5, 127.0, 37.51, 127.0)).isCloseTo(1111.95, within(1.0));
    }

    @Test
    @DisplayName("서울-부산은 약 325km")
    void seoulBusan() {
        double d = GeoDistance.haversineMeters(37.5665, 126.9780, 35.1796, 129.0756);
        assertThat(d / 1000).isCloseTo(325.0, within(3.0));
    }

    @Test
    @DisplayName("거리는 대칭이다")
    void symmetric() {
        double ab = GeoDistance.haversineMeters(37.58, 126.97, 37.57, 126.99);
        double ba = GeoDistance.haversineMeters(37.57, 126.99, 37.58, 126.97);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    @DisplayName("대척점도 NaN 없이 계산된다(반지구 둘레)")
    void antipodal() {
        double d = GeoDistance.haversineMeters(0, 0, 0, 180);
        assertThat(d).isCloseTo(Math.PI * GeoDistance.EARTH_RADIUS_M, within(1.0));
    }

    @Test
    @DisplayName("바운딩 박스는 반경 안의 동서남북 점을 모두 포함한다")
    void boundingBoxContainsCircle() {
        double lat = 37.5759;
        double lng = 126.9768;
        BoundingBox b = GeoDistance.boundingBox(lat, lng, 1000);
        double latDeg = 1000 / 111_194.9;
        assertThat(b.maxLat()).isGreaterThanOrEqualTo(lat + latDeg - 1e-6);
        assertThat(b.minLat()).isLessThanOrEqualTo(lat - latDeg + 1e-6);
        double lngDeg = 1000 / (111_194.9 * Math.cos(Math.toRadians(lat)));
        assertThat(b.maxLng()).isGreaterThanOrEqualTo(lng + lngDeg - 1e-6);
        assertThat(b.minLng()).isLessThanOrEqualTo(lng - lngDeg + 1e-6);
    }

    @Test
    @DisplayName("극 근처에서도 바운딩 박스 경도 폭이 180을 넘지 않는다")
    void boundingBoxNearPole() {
        BoundingBox b = GeoDistance.boundingBox(90, 0, 5000);
        assertThat(b.minLng()).isGreaterThanOrEqualTo(-180.0);
        assertThat(b.maxLng()).isLessThanOrEqualTo(180.0);
    }
}
