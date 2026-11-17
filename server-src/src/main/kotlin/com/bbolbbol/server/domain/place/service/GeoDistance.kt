package com.bbolbbol.server.domain.place.service

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 지구 표면 거리(haversine)와 바운딩 박스 계산. 순수 함수. */
object GeoDistance {

	const val EARTH_RADIUS_M = 6_371_000.0
	private const val METERS_PER_DEGREE_LAT = PI * EARTH_RADIUS_M / 180.0

	data class BoundingBox(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

	/** 두 좌표 사이 거리(m). */
	fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
		val p1 = Math.toRadians(lat1)
		val p2 = Math.toRadians(lat2)
		val dLat = p2 - p1
		val dLng = Math.toRadians(lng2 - lng1)
		val a = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLng / 2) * sin(dLng / 2)
		return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
	}

	/** 중심에서 radiusM 안의 모든 점을 포함하는 사각형. 정확한 거리 필터는 haversine으로 한다. */
	fun boundingBox(lat: Double, lng: Double, radiusM: Double): BoundingBox {
		val dLat = radiusM / METERS_PER_DEGREE_LAT
		val cosLat = cos(Math.toRadians(lat))
		val dLng = if (cosLat < 1e-9) 180.0 else min(180.0, radiusM / (METERS_PER_DEGREE_LAT * cosLat))
		return BoundingBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng)
	}
}
