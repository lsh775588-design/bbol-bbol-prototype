package com.bbolbbol.server.domain.place.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoDistanceTest {

	private fun assertClose(expected: Double, actual: Double, tolerance: Double) =
		assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual (tolerance $tolerance)")

	@Test
	@DisplayName("같은 좌표의 거리는 0")
	fun zero() {
		assertEquals(0.0, GeoDistance.haversineMeters(37.5759, 126.9768, 37.5759, 126.9768))
	}

	@Test
	@DisplayName("위도 0.01도는 약 1112m")
	fun oneHundredthDegreeLatitude() {
		assertClose(1111.95, GeoDistance.haversineMeters(37.5, 127.0, 37.51, 127.0), 1.0)
	}

	@Test
	@DisplayName("서울-부산은 약 325km")
	fun seoulBusan() {
		val d = GeoDistance.haversineMeters(37.5665, 126.9780, 35.1796, 129.0756)
		assertClose(325.0, d / 1000, 3.0)
	}

	@Test
	@DisplayName("거리는 대칭이다")
	fun symmetric() {
		val ab = GeoDistance.haversineMeters(37.58, 126.97, 37.57, 126.99)
		val ba = GeoDistance.haversineMeters(37.57, 126.99, 37.58, 126.97)
		assertEquals(ab, ba)
	}

	@Test
	@DisplayName("대척점도 NaN 없이 계산된다(반지구 둘레)")
	fun antipodal() {
		val d = GeoDistance.haversineMeters(0.0, 0.0, 0.0, 180.0)
		assertClose(PI * GeoDistance.EARTH_RADIUS_M, d, 1.0)
	}

	@Test
	@DisplayName("바운딩 박스는 반경 안의 동서남북 점을 모두 포함한다")
	fun boundingBoxContainsCircle() {
		val lat = 37.5759
		val lng = 126.9768
		val b = GeoDistance.boundingBox(lat, lng, 1000.0)
		val latDeg = 1000 / 111_194.9
		assertTrue(b.maxLat >= lat + latDeg - 1e-6)
		assertTrue(b.minLat <= lat - latDeg + 1e-6)
		val lngDeg = 1000 / (111_194.9 * cos(Math.toRadians(lat)))
		assertTrue(b.maxLng >= lng + lngDeg - 1e-6)
		assertTrue(b.minLng <= lng - lngDeg + 1e-6)
	}

	@Test
	@DisplayName("극 근처에서도 바운딩 박스 경도 폭이 180을 넘지 않는다")
	fun boundingBoxNearPole() {
		val b = GeoDistance.boundingBox(90.0, 0.0, 5000.0)
		assertTrue(b.minLng >= -180.0)
		assertTrue(b.maxLng <= 180.0)
	}
}
