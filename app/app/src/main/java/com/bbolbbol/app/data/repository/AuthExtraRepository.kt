package com.bbolbbol.app.data.repository

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthExtraApi
import com.bbolbbol.app.data.api.EmailConfirmRequest
import com.bbolbbol.app.data.api.EmailConfirmResponse
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.api.EmailSendRequest
import com.bbolbbol.app.data.api.EmailSendResponse
import com.bbolbbol.app.data.api.KakaoLoginRequest
import com.bbolbbol.app.data.api.KakaoLoginResponse
import com.bbolbbol.app.data.api.OnboardingInfo
import com.bbolbbol.app.data.api.OnboardingRequest
import com.bbolbbol.app.data.api.OnboardingResult
import com.bbolbbol.app.data.api.PasswordResetRequest
import com.bbolbbol.app.data.api.apiCall
import com.bbolbbol.app.data.api.apiCallNoData
import com.bbolbbol.app.data.local.AuthSession

/** 이메일 인증, 비밀번호 재설정, 카카오 로그인, 로그아웃, 탈퇴, 온보딩. */
class AuthExtraRepository(private val api: AuthExtraApi, private val session: AuthSession) {

    suspend fun sendCode(email: String, purpose: EmailPurpose): ApiResult<EmailSendResponse> =
        apiCall { api.sendCode(EmailSendRequest(email.trim(), purpose.wire)) }

    suspend fun confirmCode(email: String, purpose: EmailPurpose, code: String): ApiResult<EmailConfirmResponse> =
        apiCall { api.confirmCode(EmailConfirmRequest(email.trim(), purpose.wire, code.trim())) }

    suspend fun resetPassword(verificationToken: String, newPassword: String): ApiResult<Unit> =
        apiCallNoData { api.passwordReset(PasswordResetRequest(verificationToken, newPassword)) }

    suspend fun kakaoLogin(kakaoAccessToken: String): ApiResult<KakaoLoginResponse> =
        apiCall { api.kakao(KakaoLoginRequest(kakaoAccessToken.trim())) }.also {
            if (it is ApiResult.Success) {
                session.markOnboarding(it.data.isNewUser, it.data.suggestedNickname)
                session.saveTokens(it.data.accessToken, it.data.refreshToken)
            }
        }

    /** 서버 호출이 실패해도(네트워크 오류, 이미 만료) 로컬 토큰은 반드시 지운다. */
    suspend fun logout(): ApiResult<Unit> {
        val result = try {
            apiCallNoData { api.logout() }
        } finally {
            session.clear()
        }
        return result
    }

    /** 성공했을 때만 토큰을 지운다(실패하면 계정이 남아 있으니 로그인 상태를 유지하고 다시 시도하게 한다). */
    suspend fun withdraw(): ApiResult<Unit> =
        apiCallNoData { api.withdraw() }.also { if (it is ApiResult.Success) session.clear() }

    suspend fun onboarding(): ApiResult<OnboardingInfo> = apiCall { api.onboarding() }

    suspend fun saveOnboarding(request: OnboardingRequest): ApiResult<OnboardingResult> =
        apiCall { api.saveOnboarding(request) }.also { if (it is ApiResult.Success && it.data.onboardingCompleted) session.markOnboarding(false) }
}
