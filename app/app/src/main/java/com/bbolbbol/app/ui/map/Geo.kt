package com.bbolbbol.app.ui.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 두 좌표 사이의 거리(미터, 하버사인). 화면에 보여 주는 "남은 거리" 용도다(인증 판정은 서버가 한다). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Long {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return Math.round(2 * r * asin(sqrt(a)))
}
