package com.bbolbbol.app.data.api

import com.bbolbbol.app.data.local.AuthSession
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 로그인/가입/중복 확인 외의 요청에 `Authorization: Bearer <access>`를 붙이고,
 * 응답이 AUTH401이면 토큰을 지운다(재발급 흐름 없음).
 */
class AuthInterceptor(
    private val session: AuthSession,
    /** false면 AUTH401에서 토큰을 지우지 않는다(TokenRefreshAuthenticator가 재발급을 맡을 때). */
    private val clearOnUnauthorized: Boolean = true,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath.removePrefix("/")
        // auth/ 아래는 공개 경로지만 logout만 인증이 필요하다.
        val isPublic = path.startsWith("auth/") && path != "auth/logout"
        val token = session.accessToken
        val request = if (!isPublic && !token.isNullOrEmpty()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (clearOnUnauthorized && !isPublic && response.code == 401 && !token.isNullOrEmpty()) {
            val body = response.peekBody(MAX_PEEK).string()
            if (parseFailure(401, body).code == "AUTH401") session.expire()
        }
        return response
    }

    private companion object {
        const val MAX_PEEK = 64L * 1024
    }
}
