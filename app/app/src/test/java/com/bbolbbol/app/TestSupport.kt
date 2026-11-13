package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.InMemoryTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** MockWebServer + 실제 Retrofit/인터셉터 조합. */
class Fixture {
    val server = MockWebServer().apply { start() }
    val store = InMemoryTokenStore()
    val session = AuthSession(store)
    val api: BbolApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BbolApi::class.java)

    private val routes = java.util.concurrent.ConcurrentHashMap<String, MockResponse>()
    private val queue = okhttp3.mockwebserver.QueueDispatcher()

    init {
        // 경로(쿼리 제외)가 등록돼 있으면 그 응답을, 아니면 enqueue 순서대로 돌려준다.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return routes[path] ?: queue.dispatch(request)
            }
        }
    }

    /** 경로 고정 응답. 요청 순서와 상관없이 같은 경로에 항상 이 응답을 준다. */
    fun route(path: String, status: Int, body: String) {
        routes[path] = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    }

    fun enqueue(status: Int, body: String) {
        queue.enqueueResponse(
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body),
        )
    }

    fun shutdown() = server.shutdown()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object Json {
    const val TOKENS =
        """{"success":true,"code":"COMMON200","message":"ok","data":{"userId":7,"accessToken":"acc-1","refreshToken":"ref-1","isNewUser":false}}"""

    fun ok(data: String) = """{"success":true,"code":"COMMON200","message":"ok","data":$data}"""

    fun error(code: String, data: String = "null") =
        """{"success":false,"code":"$code","message":"x","data":$data}"""
}
