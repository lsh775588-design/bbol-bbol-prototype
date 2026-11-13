package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.ReissueApi
import com.bbolbbol.app.data.api.TokenRefreshAuthenticator
import com.bbolbbol.app.data.api.apiCall
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.InMemoryTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** 토큰 재발급(Authenticator) 테스트: MockWebServer + 실제 Retrofit/OkHttp. */
class ReissueTest {
    private val server = MockWebServer()
    private val store = InMemoryTokenStore().apply { accessToken = "old-acc"; refreshToken = "old-ref" }
    private val session = AuthSession(store)

    private val reissueCount = AtomicInteger()
    private val meCount = AtomicInteger()
    private val reissueBodies = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** 재발급 응답 정책. */
    @Volatile private var reissueMode = "ok"

    /** /users/me에서 새 토큰도 거절할지. */
    @Volatile private var meAlways401 = false

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/auth/reissue" -> {
                        reissueCount.incrementAndGet()
                        reissueBodies += request.body.readUtf8()
                        when (reissueMode) {
                            "ok" -> json(200, Json.ok("""{"accessToken":"new-acc","refreshToken":"new-ref"}""")).setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                            "4012" -> json(401, Json.error("AUTH4012"))
                            "503" -> json(503, Json.error("COMMON500"))
                            else -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        }
                    }
                    path == "/users/me" -> {
                        meCount.incrementAndGet()
                        val auth = request.getHeader("Authorization")
                        if (!meAlways401 && auth == "Bearer new-acc") {
                            json(200, Json.ok("""{"userId":1,"email":"a@b.com","nickname":"닉","loginType":"EMAIL","onboardingCompleted":true}"""))
                        } else {
                            json(401, Json.error("AUTH401"))
                        }
                    }
                    path == "/places/9" -> json(401, Json.error("COMMON401")) // AUTH401이 아닌 401
                    path == "/auth/logout" -> {
                        json(200, Json.ok("null"))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    private fun json(status: Int, body: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val bare = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
    private val authenticator = TokenRefreshAuthenticator(session, bare.create(ReissueApi::class.java))
    private val api: BbolApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(session, clearOnUnauthorized = false))
                .authenticator(authenticator)
                .build(),
        )
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BbolApi::class.java)

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun singleRequestReissuesOnceAndRetries() = runBlocking {
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Success)
        assertEquals(1, reissueCount.get())
        assertEquals(2, meCount.get()) // 실패 1 + 재시도 1
        assertEquals("new-acc", session.accessToken)
        assertEquals("new-ref", session.refreshToken)
        assertEquals("""{"refreshToken":"old-ref"}""", reissueBodies.single())
    }

    @Test
    fun parallel401sTriggerExactlyOneReissue() {
        val n = 6
        val pool = Executors.newFixedThreadPool(n)
        val ready = CountDownLatch(n)
        val go = CountDownLatch(1)
        val results = (1..n).map {
            pool.submit<ApiResult<*>> {
                ready.countDown()
                go.await()
                runBlocking { apiCall { api.me() } }
            }
        }
        ready.await()
        go.countDown()
        val done = results.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
        pool.shutdownNow()
        assertTrue("모든 요청이 재시도로 성공해야 한다: $done", done.all { it is ApiResult.Success })
        assertEquals("재발급 요청은 정확히 한 번", 1, reissueCount.get())
        assertEquals("new-acc", session.accessToken)
        assertTrue(session.loggedIn.value)
    }

    @Test
    fun reissueRejectedClearsTokensAndDoesNotRetry() = runBlocking {
        reissueMode = "4012"
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Failure && r.code == "AUTH401")
        assertEquals(1, reissueCount.get())
        assertEquals(1, meCount.get()) // 재시도하지 않는다
        assertNull(session.accessToken)
        assertNull(store.refreshToken)
        assertFalse(session.loggedIn.value)
    }

    @Test
    fun reissueNetworkFailureKeepsTokens() = runBlocking {
        reissueMode = "network"
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Failure)
        assertEquals(1, reissueCount.get())
        assertEquals(1, meCount.get()) // 재시도 없음
        assertTrue(session.loggedIn.value)
        assertEquals("old-ref", store.refreshToken)
        assertEquals("old-acc", session.accessToken)
    }

    @Test
    fun reissue503KeepsTokensAndDoesNotLoop() = runBlocking {
        reissueMode = "503"
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Failure)
        assertEquals(1, reissueCount.get())
        assertEquals(1, meCount.get())
        assertTrue(session.loggedIn.value)
        assertEquals("old-ref", store.refreshToken)
    }

    @Test
    fun noLoopWhenFreshTokenIsAlsoRejected() = runBlocking {
        meAlways401 = true
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Failure && r.code == "AUTH401")
        assertEquals("재발급은 한 번만", 1, reissueCount.get())
        assertEquals("원래 요청 + 재시도 1번까지만", 2, meCount.get())
        assertFalse(session.loggedIn.value) // 새 토큰도 거절되면 세션을 정리한다
    }

    @Test
    fun a401ThatIsNotAuth401DoesNotReissue() = runBlocking {
        val r = apiCall { api.place(9) }
        assertTrue(r is ApiResult.Failure)
        assertEquals(0, reissueCount.get())
        assertTrue(session.loggedIn.value)
    }

    @Test
    fun withoutRefreshTokenClearsSessionWithoutCallingServer() = runBlocking {
        store.refreshToken = null
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Failure)
        assertEquals(0, reissueCount.get())
        assertFalse(session.loggedIn.value)
    }

    /** 락을 기다린 요청: 실패한 요청의 토큰이 이미 낡았으면(저장된 토큰이 더 새롭다) 재발급 없이 새 토큰으로 재시도만 한다. */
    @Test
    fun staleFailedRequestRetriesWithCurrentTokenWithoutReissue() {
        store.accessToken = "fresh-acc"
        val request = Request.Builder().url(server.url("/users/me")).header("Authorization", "Bearer stale-acc").build()
        val response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
            .body(Json.error("AUTH401").toResponseBody("application/json".toMediaType()))
            .build()
        val retry = authenticator.authenticate(null, response)
        assertEquals("Bearer fresh-acc", retry?.header("Authorization"))
        assertEquals(0, reissueCount.get())
    }

    @Test
    fun logoutRequestCarriesBearerTokenEvenThoughItIsUnderAuth() {
        val extra = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session, clearOnUnauthorized = false)).build())
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.bbolbbol.app.data.api.AuthExtraApi::class.java)
        runBlocking { extra.logout() }
        assertEquals("Bearer old-acc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun otherAuthPathsNeverCarryBearerToken() {
        val extra = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session, clearOnUnauthorized = false)).build())
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.bbolbbol.app.data.api.AuthExtraApi::class.java)
        runBlocking {
            extra.sendCode(com.bbolbbol.app.data.api.EmailSendRequest("a@b.com", "SIGNUP"))
            extra.confirmCode(com.bbolbbol.app.data.api.EmailConfirmRequest("a@b.com", "SIGNUP", "123456"))
            extra.passwordReset(com.bbolbbol.app.data.api.PasswordResetRequest("t", "pw"))
            extra.kakao(com.bbolbbol.app.data.api.KakaoLoginRequest("k"))
            api.login(com.bbolbbol.app.data.api.LoginRequest("a@b.com", "pw"))
            api.signup(com.bbolbbol.app.data.api.SignupRequest("t", "pw", "nn"))
        }
        repeat(6) {
            val r = server.takeRequest()
            assertNull(r.path, r.getHeader("Authorization"))
        }
    }

    @Test
    fun legacyInterceptorStillClearsOnAuth401WhenAskedTo() = runBlocking {
        val legacy = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build())
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BbolApi::class.java)
        apiCall { legacy.me() }
        assertFalse(session.loggedIn.value)
        assertEquals(0, reissueCount.get())
    }
}
