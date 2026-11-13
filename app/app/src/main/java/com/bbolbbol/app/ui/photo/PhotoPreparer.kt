package com.bbolbbol.app.ui.photo

import kotlin.math.max

/** 비트맵 종류와 무관한 이미지 연산. Android 구현은 [AndroidImageOps], 테스트는 가짜를 쓴다. */
interface ImageOps<B : Any> {
    /** 디코드하지 않고 읽은 원본 크기(회전 전). 읽을 수 없는 형식이면 null. */
    fun readSize(): Pair<Int, Int>?

    /** EXIF 방향(0, 90, 180, 270). */
    fun orientationDegrees(): Int

    /** [sampleSize] 분의 1로 줄여 디코드. 실패하면 null. */
    fun decode(sampleSize: Int): B?

    /** [degrees]만큼 돌리고 [width]x[height]로 맞춘 새 비트맵(투명은 흰색 배경). 입력은 필요하면 호출자가 recycle한다. */
    fun transform(src: B, degrees: Int, width: Int, height: Int): B

    fun encodeJpeg(bitmap: B, quality: Int): ByteArray

    fun recycle(bitmap: B)
}

sealed interface PrepareResult {
    class Ready(val jpeg: ByteArray, val width: Int, val height: Int, val quality: Int) : PrepareResult

    /** 열 수 없는 파일, 또는 이 기기가 디코드하지 못하는 형식(예: 오래된 기기의 HEIC). */
    data object Unreadable : PrepareResult

    /** 가장 낮은 품질과 크기까지 줄여도 [PhotoPreparer.maxBytes]를 넘는다. */
    data object TooLarge : PrepareResult
}

/**
 * 업로드 전에 사진을 줄이고 JPEG로 다시 인코딩한다(HEIC/WebP 등은 서버가 못 읽으므로 항상 JPEG로 보낸다).
 * 서버 한도는 10MB이고, 여유를 두려고 기본 [maxBytes]는 8MB, 긴 변은 [maxEdge]px 이하로 맞춘다.
 */
class PhotoPreparer(
    val maxEdge: Int = 2048,
    val maxBytes: Int = 8 * 1024 * 1024,
    private val qualities: List<Int> = listOf(88, 78, 68, 55),
    private val maxShrinkRounds: Int = 3,
) {
    fun <B : Any> prepare(ops: ImageOps<B>): PrepareResult {
        val (rawW, rawH) = ops.readSize() ?: return PrepareResult.Unreadable
        if (rawW <= 0 || rawH <= 0) return PrepareResult.Unreadable
        val degrees = ((ops.orientationDegrees() % 360) + 360) % 360
        val swap = degrees == 90 || degrees == 270
        val orientedW = if (swap) rawH else rawW
        val orientedH = if (swap) rawW else rawH

        var edge = maxEdge
        repeat(maxShrinkRounds + 1) {
            val (w, h) = fit(orientedW, orientedH, edge)
            // 목표의 2배 안쪽까지만 샘플링으로 줄이고 나머지는 transform이 정확히 맞춘다.
            val decoded = ops.decode(sampleSizeFor(max(rawW, rawH), edge)) ?: return PrepareResult.Unreadable
            val scaled = ops.transform(decoded, degrees, w, h)
            if (scaled !== decoded) ops.recycle(decoded)
            try {
                for (q in qualities) {
                    val bytes = ops.encodeJpeg(scaled, q)
                    if (bytes.size <= maxBytes) return PrepareResult.Ready(bytes, w, h, q)
                }
            } finally {
                ops.recycle(scaled)
            }
            edge = edge * 3 / 4
        }
        return PrepareResult.TooLarge
    }

    companion object {
        /** 긴 변이 [maxEdge] 이하가 되도록 비율을 유지해 줄인 크기. 키우지 않는다. */
        fun fit(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
            val longest = max(width, height)
            if (longest <= maxEdge) return width to height
            val scale = maxEdge.toDouble() / longest
            return max(1, Math.round(width * scale).toInt()) to max(1, Math.round(height * scale).toInt())
        }

        /** 디코드 메모리를 아끼는 2의 거듭제곱 샘플 크기. 결과 긴 변이 [maxEdge] 이상으로 남게 한다. */
        fun sampleSizeFor(longestEdge: Int, maxEdge: Int): Int {
            var sample = 1
            while (longestEdge / (sample * 2) >= maxEdge) sample *= 2
            return sample
        }
    }
}
