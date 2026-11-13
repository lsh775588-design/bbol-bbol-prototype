package com.bbolbbol.app

import android.app.Application
import android.content.Context
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.AuthExtraApi
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.ReissueApi
import com.bbolbbol.app.data.api.TokenRefreshAuthenticator
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.ui.auth.KakaoTokenProviders
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.EncryptedTokenStore
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

/** 수동 DI. 화면 ViewModel은 여기서 저장소를 가져다 쓴다. */
class AppContainer(context: Context) {
    val session = AuthSession(EncryptedTokenStore(context))

    // 재발급 호출 전용 클라이언트: 인터셉터/Authenticator가 없어서 재발급이 재발급을 부르지 않는다.
    private val reissueApi: ReissueApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ReissueApi::class.java)

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(session, clearOnUnauthorized = false))
        .authenticator(TokenRefreshAuthenticator(session, reissueApi))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api: BbolApi = retrofit.create(BbolApi::class.java)

    val authRepository = AuthRepository(api, session)
    val authExtraRepository = AuthExtraRepository(retrofit.create(AuthExtraApi::class.java), session)
    val kakaoTokenProvider = KakaoTokenProviders.create(BuildConfig.DEBUG)
    val userRepository = UserRepository(api)
    val placeRepository = PlaceRepository(api)
    val completionRepository = CompletionRepository(api)
    val pointRepository = PointRepository(api)
}

class BbolApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // osmdroid: OSM 타일 서버 정책상 User-Agent 필수. 캐시는 앱 전용 캐시 폴더(저장소 권한 불필요).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}
