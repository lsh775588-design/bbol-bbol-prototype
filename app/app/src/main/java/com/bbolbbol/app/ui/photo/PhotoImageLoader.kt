package com.bbolbbol.app.ui.photo

import android.content.Context
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Call
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 사진 경로는 인증이 필요하므로 앱의 OkHttp 클라이언트(AuthInterceptor가 Bearer를 붙인다)로 불러온다.
 * 디스크 캐시는 끄고(서버도 no-store), 메모리 캐시는 로그아웃(loggedIn=false)될 때 비운다.
 */
fun installPhotoImageLoader(context: Context, client: OkHttpClient, loggedIn: StateFlow<Boolean>) {
    val app = context.applicationContext
    val loader = ImageLoader.Builder(app)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client as Call.Factory })) }
        .diskCache(null)
        .build()
    SingletonImageLoader.setSafe { loader }
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        loggedIn.collect { if (!it) loader.memoryCache?.clear() }
    }
}
