package com.bbolbbol.app.data.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import java.io.IOException

/** 이메일 인증, 재설정, 카카오, 온보딩, 로그아웃, 탈퇴. (BbolApi는 건드리지 않으려고 따로 둔다.) */
interface AuthExtraApi {
    @POST("auth/email-verifications")
    suspend fun sendCode(@Body body: EmailSendRequest): Response<ApiEnvelope<EmailSendResponse>>

    @POST("auth/email-verifications/confirm")
    suspend fun confirmCode(@Body body: EmailConfirmRequest): Response<ApiEnvelope<EmailConfirmResponse>>

    /** 성공 시 data가 null이다. */
    @POST("auth/password-reset")
    suspend fun passwordReset(@Body body: PasswordResetRequest): Response<ResponseBody>

    @POST("auth/kakao")
    suspend fun kakao(@Body body: KakaoLoginRequest): Response<ApiEnvelope<KakaoLoginResponse>>

    /** 인증이 필요한 유일한 auth 경로. AuthInterceptor가 예외로 토큰을 붙인다. */
    @POST("auth/logout")
    suspend fun logout(): Response<ResponseBody>

    @DELETE("users/me")
    suspend fun withdraw(): Response<ResponseBody>

    @GET("users/me/onboarding")
    suspend fun onboarding(): Response<ApiEnvelope<OnboardingInfo>>

    @PATCH("users/me/onboarding")
    suspend fun saveOnboarding(@Body body: OnboardingRequest): Response<ApiEnvelope<OnboardingResult>>
}

/** Authenticator 안(OkHttp 스레드)에서 쓰는 동기 호출. 이 인터페이스를 만든 클라이언트에는 Authenticator를 달지 않는다. */
interface ReissueApi {
    @POST("auth/reissue")
    fun reissue(@Body body: ReissueRequest): Call<ApiEnvelope<ReissueResponse>>
}

/** data가 없는 성공 응답(로그아웃, 탈퇴, 비밀번호 재설정)용. 본문을 파싱하지 않으므로 빈 본문이어도 성공으로 본다. */
suspend fun apiCallNoData(block: suspend () -> Response<ResponseBody>): ApiResult<Unit> {
    val response = try {
        block()
    } catch (e: IOException) {
        return ApiResult.NetworkError
    }
    if (response.isSuccessful) return ApiResult.Success(Unit)
    val raw = try {
        response.errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    return parseFailure(response.code(), raw)
}
