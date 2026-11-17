package com.bbolbbol.app

import android.app.Application
import android.content.Context
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.AuthExtraApi
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.StatsApi
import com.bbolbbol.app.data.repository.PhotoRepository
import com.bbolbbol.app.data.repository.RankingRepository
import com.bbolbbol.app.data.repository.StatsRepository
import com.bbolbbol.app.ui.photo.AndroidPhotoPrepareService
import com.bbolbbol.app.ui.photo.PhotoPrepareService
import com.bbolbbol.app.ui.photo.installPhotoImageLoader
import com.bbolbbol.app.data.api.ReissueApi
import com.bbolbbol.app.data.api.TokenRefreshAuthenticator
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.ui.auth.createDevTools
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.EncryptedTokenStore
import com.bbolbbol.app.data.local.PrefsQuestStorage
import com.bbolbbol.app.data.local.QuestBook
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.kakao.vectormap.KakaoMapSdk
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** 수동 DI. 화면 ViewModel은 여기서 저장소를 가져다 쓴다. */
class AppContainer(context: Context) {
    val session = AuthSession(EncryptedTokenStore.createResilient(context))

    // 재발급 호출 전용 클라이언트: 인터셉터/Authenticator가 없어서 재발급이 재발급을 부르지 않는다.
    private val reissueApi: ReissueApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ReissueApi::class.java)

    // 사진 이미지 로더(installPhotoImageLoader)가 같은 클라이언트를 써서 인증 헤더와 재발급을 함께 받는다.
    val client = OkHttpClient.Builder()
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
    private val statsApi: StatsApi = retrofit.create(StatsApi::class.java)

    val authRepository = AuthRepository(api, session)
    val authExtraRepository = AuthExtraRepository(retrofit.create(AuthExtraApi::class.java), session)
    /** 개발 전용 도구. debug 소스셋에서만 실제 구현이 컴파일된다(release는 NoDevTools). */
    val devTools = createDevTools()
    val kakaoTokenProvider = devTools.kakaoProvider
    val userRepository = UserRepository(api)
    val placeRepository = PlaceRepository(api)
    val completionRepository = CompletionRepository(api)
    val pointRepository = PointRepository(api)
    val photoRepository = PhotoRepository(statsApi)
    val statsRepository = StatsRepository(statsApi)
    val rankingRepository = RankingRepository(statsApi)
    val questBook = QuestBook(PrefsQuestStorage(context))

    @Volatile private var cachedUserId: Long? = null

    /** 받은 퀘스트를 사용자별로 나누는 키. 로그아웃하면 비워진다. */
    suspend fun currentUserId(): Long? = cachedUserId
        ?: (userRepository.me() as? com.bbolbbol.app.data.api.ApiResult.Success)?.data?.userId?.also { cachedUserId = it }

    init {
        session.onCleared = {
            cachedUserId = null
            questBook.clearAll()
        }
    }

    val photoPrepareService: PhotoPrepareService = AndroidPhotoPrepareService(context.applicationContext.contentResolver)
}

class BbolApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installPhotoImageLoader(this, container.client, container.session.loggedIn)
        // 카카오맵 SDK. 키가 비어 있으면 초기화하지 않고, 지도 화면이 안내 문구를 보여 준다.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
