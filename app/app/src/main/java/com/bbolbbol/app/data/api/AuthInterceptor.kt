package com.bbolbbol.app.data.api

import com.bbolbbol.app.data.local.AuthSession
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 로그인/가입/중복 확인 외의 요청에 `Authorization: Bearer <access>`를 붙이고,
 * 응답이 AUTH401이면 토큰을 지운다(재발급 흐름 없음).
 */
class AuthInterceptor(private val session: AuthSession) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isPublic = original.url.encodedPath.removePrefix("/").startsWith("auth/")
        val token = session.accessToken
        val request = if (!isPublic && !token.isNullOrEmpty()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (!isPublic && response.code == 401 && !token.isNullOrEmpty()) {
            val body = response.peekBody(MAX_PEEK).string()
            if (parseFailure(401, body).code == "AUTH401") session.clear()
        }
        return response
    }

    private companion object {
        const val MAX_PEEK = 64L * 1024
    }
}
