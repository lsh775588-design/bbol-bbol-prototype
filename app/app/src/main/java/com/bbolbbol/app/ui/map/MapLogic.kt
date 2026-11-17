package com.bbolbbol.app.ui.map

import kotlin.math.max
import kotlin.math.min

/** 지도에 그릴 장소 핀 한 개. SDK 객체에 의존하지 않는 순수 모델이다. */
data class MarkerData(
    val id: Long,
    val title: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val hot: Boolean,
    val state: PinState,
)

/** 카카오맵 줌 레벨은 정수(0~21)다. 소수 줌 값은 반올림해서 쓴다. */
const val KAKAO_MIN_LEVEL = 11
const val KAKAO_MAX_LEVEL = 21

fun kakaoLevel(zoom: Double): Int = Math.round(zoom).toInt().coerceIn(KAKAO_MIN_LEVEL, KAKAO_MAX_LEVEL)

/** 둘러보기 모드에서 장소들이 한눈에 들어오게 맞춘 뒤 너무 가깝거나 멀지 않게 14~15로 제한한다. */
fun clampBrowseLevel(level: Int): Int = level.coerceIn(14, 15)

/** 선택된 핀이 맨 위에 오도록 마지막에 그리는 순서. */
fun orderForDrawing(places: List<MarkerData>, selectedId: Long?): List<MarkerData> =
    places.sortedBy { it.id == selectedId }

/** 라벨 순위. 값이 클수록 우선한다(선택된 핀 > 받은 퀘스트 > 나머지). */
fun labelRank(p: MarkerData, selectedId: Long?): Long = when {
    p.id == selectedId -> 3L
    p.state == PinState.ACCEPTED -> 2L
    p.state == PinState.NORMAL -> 1L
    else -> 0L
}

/** 핀 비트맵 캐시 키. 같은 모양은 한 번만 그린다. */
fun pinKey(p: MarkerData, selectedId: Long?): String = "${p.category}|${p.state}|${p.id == selectedId}|${p.hot}"

data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val latSpan get() = north - south
    val lngSpan get() = east - west
    val centerLat get() = (north + south) / 2
    val centerLng get() = (east + west) / 2
}

fun boundsOf(places: List<MarkerData>): Bounds? {
    if (places.isEmpty()) return null
    return Bounds(
        south = places.minOf { it.lat },
        west = places.minOf { it.lng },
        north = places.maxOf { it.lat },
        east = places.maxOf { it.lng },
    )
}

/** 위쪽 제목/칩 영역에 핀이 가려지지 않도록 북쪽을 더 넓힌 경계. */
fun withNorthMargin(b: Bounds, ratio: Double = 0.1): Bounds = b.copy(north = b.north + b.latSpan * ratio)

/** 내 위치 후광 반지름(dp). 정확도(m)에 비례하되 18~60dp로 제한한다. 정확도를 모르면 40m로 본다. */
fun haloRadiusDp(accuracyMeters: Float?): Float {
    val acc = max(accuracyMeters ?: 40f, 25f)
    return min(max(acc * 0.6f, 18f), 60f)
}

/** 지도 키가 설정됐는지. 빈 값이면 지도 대신 안내를 보여 준다. */
fun isMapKeyConfigured(key: String): Boolean = key.isNotBlank()
