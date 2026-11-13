package com.bbolbbol.app.data.api

import com.bbolbbol.app.data.local.AuthSession
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 보호된 요청이 401 AUTH401을 받으면 refresh token으로 POST /auth/reissue를 부르고 원래 요청을 한 번 다시 보낸다.
 *
 * - single-flight: 재발급은 락 안에서만 일어난다. 락을 기다린 요청은 "실패한 요청이 쓴 access token"과
 *   "지금 저장된 access token"을 비교해서, 이미 바뀌었으면(다른 요청이 재발급을 끝냄) 재발급 없이 새 토큰으로 재시도만 한다.
 * - 루프 없음: 이미 재시도한 응답(priorResponse가 있음)이 또 401이면 포기한다. 재발급 호출은 이 Authenticator가 없는 별도 클라이언트로 보낸다.
 * - 서버가 refresh를 거절(AUTH4012 등 4xx)하면 토큰을 모두 지운다 -> AuthSession.loggedIn=false -> 로그인 화면.
 *   네트워크 오류, 타임아웃, 5xx는 토큰을 유지하고 요청만 실패시킨다.
 * - 재발급 중 서버의 20초 유예(옛 refresh 재사용 허용)가 있어도 앱은 옛 refresh를 두 번 보내지 않도록 만든다.
 */
class TokenRefreshAuthenticator(
    private val session: AuthSession,
    private val reissueApi: ReissueApi,
) : Authenticator {
    private val lock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath.removePrefix("/")
        if (path.startsWith("auth/") && path != "auth/logout") return null // 로그인/재발급 자체의 401은 건드리지 않는다.
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (sentToken.isNullOrEmpty()) return null // 토큰 없이 보낸 요청이면 재발급 대상이 아니다.
        if (!isAccessTokenInvalid(response)) return null
        if (response.priorResponse != null) {
            // 새 토큰으로 다시 보냈는데도 401. 세션이 망가졌으니 지우고 끝낸다.
            session.clear()
            return null
        }
        lock.withLock {
            val current = session.accessToken
            if (current.isNullOrEmpty()) return null // 이미 로그아웃/만료로 지워졌다.
            if (current != sentToken) return retryWith(response.request, current) // 다른 요청이 이미 재발급했다.
            val refresh = session.refreshToken
            if (refresh.isNullOrEmpty()) {
                session.clear()
                return null
            }
            when (val outcome = reissue(refresh)) {
                is Outcome.Renewed -> {
                    session.saveTokens(outcome.tokens.accessToken, outcome.tokens.refreshToken)
                    return retryWith(response.request, outcome.tokens.accessToken)
                }
                // 서버가 refresh를 거절했다(AUTH4012 등). 다시 로그인해야 한다.
                Outcome.Rejected -> {
                    session.clear()
                    return null
                }
                // 네트워크 오류, 타임아웃, 5xx: 토큰은 그대로 두고 이 요청만 실패시킨다(오프라인에서 로그아웃되지 않게).
                Outcome.Transient -> return null
            }
        }
    }

    private sealed interface Outcome {
        data class Renewed(val tokens: ReissueResponse) : Outcome
        data object Rejected : Outcome
        data object Transient : Outcome
    }

    private fun reissue(refreshToken: String): Outcome = try {
        val r = reissueApi.reissue(ReissueRequest(refreshToken)).execute()
        val tokens = r.body()?.takeIf { r.isSuccessful }?.data
            ?.takeIf { it.accessToken.isNotEmpty() && it.refreshToken.isNotEmpty() }
        when {
            tokens != null -> Outcome.Renewed(tokens)
            // 4xx(요청 시간 초과 408, 제한 429 제외)는 서버의 거절. 그 밖(5xx, 빈 본문)은 일시 오류로 본다.
            r.code() in 400..499 && r.code() != 408 && r.code() != 429 -> Outcome.Rejected
            else -> Outcome.Transient
        }
    } catch (e: IOException) {
        Outcome.Transient
    } catch (e: RuntimeException) {
        Outcome.Transient // 본문 파싱 실패 등
    }

    private fun retryWith(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    /** 401 중에서도 access token 문제(AUTH401)일 때만 재발급한다. */
    private fun isAccessTokenInvalid(response: Response): Boolean {
        val body = try {
            response.peekBody(MAX_PEEK).string()
        } catch (e: IOException) {
            return false
        }
        return parseFailure(401, body).code == "AUTH401"
    }

    private companion object {
        const val MAX_PEEK = 64L * 1024
    }
}
