package com.bbolbbol.app.util

/** 화면 표시용 포맷. 서버 시각은 Asia/Seoul LocalDateTime 문자열("2026-11-14T10:30:00")이다. */
object Formatters {
    fun dateTime(iso: String?): String = iso?.take(16)?.replace('T', ' ') ?: "-"

    fun distance(meters: Long): String =
        if (meters >= 1000) String.format(java.util.Locale.KOREA, "%.1fkm", meters / 1000.0) else "${meters}m"

    fun pointType(type: String): String = when (type) {
        "QUEST_COMPLETE" -> "도착 인증"
        "PHOTO_BONUS" -> "사진 보너스"
        else -> type
    }
}
