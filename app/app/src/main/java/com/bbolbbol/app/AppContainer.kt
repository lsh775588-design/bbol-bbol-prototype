package com.bbolbbol.app

import android.app.Application
import android.content.Context
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.EncryptedTokenStore
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.PlaceRepository
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

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(session))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api: BbolApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BbolApi::class.java)

    val authRepository = AuthRepository(api, session)
    val userRepository = UserRepository(api)
    val placeRepository = PlaceRepository(api)
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
