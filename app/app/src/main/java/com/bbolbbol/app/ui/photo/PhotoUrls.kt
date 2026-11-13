package com.bbolbbol.app.ui.photo

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object PhotoUrls {
    /**
     * 서버의 photoUrl("/files/completions/<uuid>.jpg")을 요청 주소로 바꾼다.
     * 상대 경로는 API 기본 주소 뒤에 붙인다. 절대 주소는 API 서버와 호스트가 같을 때만 허용한다:
     * 이미지 요청에는 Authorization 헤더가 붙으므로, 다른 호스트(CDN 등)로 토큰이 새지 않게 막는다.
     * 쓸 수 없으면 null.
     */
    fun resolve(baseUrl: String, photoUrl: String?): String? {
        val p = photoUrl?.trim().orEmpty()
        if (p.isEmpty()) return null
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        if (p.startsWith("/") && !p.startsWith("//")) return base.toString().trimEnd('/') + p
        val abs = p.toHttpUrlOrNull() ?: return null
        val same = abs.host == base.host && abs.port == base.port && abs.scheme == base.scheme
        return if (same) abs.toString() else null
    }
}
