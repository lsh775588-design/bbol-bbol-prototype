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

    /** "2026-11-14T10:30:00" -> "2026.11.14". 형식이 다르면 "-". */
    fun date(iso: String?): String {
        val d = iso?.take(10)
        return if (d != null && d.length == 10 && d[4] == '-' && d[7] == '-') d.replace('-', '.') else "-"
    }

    /** 서버 카테고리 코드의 한글 이름(기준 데이터 8종). 모르는 코드는 그대로 돌려준다. */
    fun categoryName(code: String?): String = when (code) {
        "CAFE" -> "카페"
        "PARK" -> "공원"
        "SHOPPING" -> "쇼핑센터"
        "MOVIE" -> "영화관"
        "LIBRARY" -> "도서관"
        "STUDY_CAFE" -> "스터디카페"
        "MUSEUM" -> "박물관"
        "HERITAGE" -> "유적지"
        null, "" -> "-"
        else -> code
    }

    /** 아바타에 넣을 닉네임 첫 글자. 비어 있으면 "?". */
    fun initial(nickname: String?): String =
        nickname?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "?"
}
