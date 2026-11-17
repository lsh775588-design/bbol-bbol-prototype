package com.bbolbbol.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLogicTest {
    private fun m(id: Long, lat: Double = 37.57, lng: Double = 126.98, state: PinState = PinState.NORMAL, hot: Boolean = false) =
        MarkerData(id, "p$id", "CAFE", lat, lng, hot, state)

    @Test
    fun kakaoLevel_반올림하고_범위로_제한한다() {
        assertEquals(16, kakaoLevel(16.0))
        assertEquals(15, kakaoLevel(14.6))
        assertEquals(KAKAO_MIN_LEVEL, kakaoLevel(3.0))
        assertEquals(KAKAO_MAX_LEVEL, kakaoLevel(30.0))
        assertEquals(15, kakaoLevel(BROWSE_ZOOM))
    }

    @Test
    fun clampBrowseLevel_14에서_15로_제한() {
        assertEquals(14, clampBrowseLevel(9))
        assertEquals(15, clampBrowseLevel(18))
        assertEquals(15, clampBrowseLevel(15))
    }

    @Test
    fun orderForDrawing_선택된_핀이_마지막() {
        val order = orderForDrawing(listOf(m(1), m(2), m(3)), selectedId = 1).map { it.id }
        assertEquals(1L, order.last())
        assertEquals(setOf(1L, 2L, 3L), order.toSet())
        assertEquals(listOf(1L, 2L, 3L), orderForDrawing(listOf(m(1), m(2), m(3)), selectedId = null).map { it.id })
    }

    @Test
    fun labelRank_선택_받은퀘스트_일반_완료_순() {
        assertTrue(labelRank(m(1), 1) > labelRank(m(2, state = PinState.ACCEPTED), 1))
        assertTrue(labelRank(m(2, state = PinState.ACCEPTED), 1) > labelRank(m(3), 1))
        assertTrue(labelRank(m(3), 1) > labelRank(m(4, state = PinState.COMPLETED), 1))
    }

    @Test
    fun pinKey_선택과_상태가_다르면_다른_키() {
        assertEquals(pinKey(m(1), null), pinKey(m(2), null))
        assertNotEquals(pinKey(m(1), 1), pinKey(m(1), null))
        assertNotEquals(pinKey(m(1), null), pinKey(m(1, state = PinState.ACCEPTED), null))
        assertNotEquals(pinKey(m(1), null), pinKey(m(1, hot = true), null))
    }

    @Test
    fun boundsOf_경계와_북쪽_여백() {
        assertNull(boundsOf(emptyList()))
        val b = boundsOf(listOf(m(1, 37.50, 126.90), m(2, 37.60, 127.00)))!!
        assertEquals(37.50, b.south, 1e-9)
        assertEquals(37.60, b.north, 1e-9)
        assertEquals(126.90, b.west, 1e-9)
        assertEquals(127.00, b.east, 1e-9)
        assertEquals(37.55, b.centerLat, 1e-9)
        val w = withNorthMargin(b)
        assertEquals(37.61, w.north, 1e-9)
        assertEquals(b.south, w.south, 1e-9)
    }

    @Test
    fun haloRadiusDp_정확도에_비례하고_제한된다() {
        assertEquals(24f, haloRadiusDp(null), 1e-4f)
        assertEquals(18f, haloRadiusDp(5f), 1e-4f)
        assertEquals(60f, haloRadiusDp(500f), 1e-4f)
        assertTrue(haloRadiusDp(60f) < haloRadiusDp(80f))
    }

    @Test
    fun isMapKeyConfigured_빈값이면_false() {
        assertFalse(isMapKeyConfigured(""))
        assertFalse(isMapKeyConfigured("   "))
        assertTrue(isMapKeyConfigured("abc"))
    }

    @Test
    fun kakaoLevel_NaN_음수_정확히_반_값() {
        assertEquals(KAKAO_MIN_LEVEL, kakaoLevel(Double.NaN))
        assertEquals(KAKAO_MIN_LEVEL, kakaoLevel(-5.0))
        assertEquals(KAKAO_MIN_LEVEL, kakaoLevel(Double.NEGATIVE_INFINITY))
        assertEquals(KAKAO_MAX_LEVEL, kakaoLevel(Double.POSITIVE_INFINITY))
        assertEquals(16, kakaoLevel(15.5))
        assertEquals(15, kakaoLevel(14.5))
    }

    @Test
    fun boundsOf_한_점이면_폭이_0이고_북쪽_여백도_0() {
        val b = boundsOf(listOf(m(1, 37.57, 126.98)))!!
        assertEquals(b.south, b.north, 0.0)
        assertEquals(b.west, b.east, 0.0)
        assertEquals(0.0, b.latSpan, 0.0)
        assertEquals(37.57, b.centerLat, 1e-9)
        assertEquals(b, withNorthMargin(b))
    }

    @Test
    fun classifyMapError_네트워크_인증_기타() {
        val net = com.kakao.vectormap.MapAuthException.CONNECT_TIMEOUT_EXCEPTION
        assertEquals(MapErrorKind.NETWORK, classifyMapError(com.kakao.vectormap.MapAuthException(net, "t")))
        assertEquals(MapErrorKind.NETWORK, classifyMapError(com.kakao.vectormap.MapTimeoutException()))
        assertEquals(MapErrorKind.AUTH, classifyMapError(com.kakao.vectormap.MapAuthException(com.kakao.vectormap.MapAuthException.APP_KEY_INVALID_ERROR, "k")))
        assertEquals(MapErrorKind.OTHER, classifyMapError(RuntimeException("x")))
    }
}
