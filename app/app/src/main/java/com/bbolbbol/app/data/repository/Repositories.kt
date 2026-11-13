package com.bbolbbol.app.data.repository

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AvailabilityResult
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.CategoryItem
import com.bbolbbol.app.data.api.LoginRequest
import com.bbolbbol.app.data.api.MeResponse
import com.bbolbbol.app.data.api.NicknameChangeRequest
import com.bbolbbol.app.data.api.NicknameChangeResponse
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.api.PlaceSummary
import com.bbolbbol.app.data.api.SignupRequest
import com.bbolbbol.app.data.api.TokenResponse
import com.bbolbbol.app.data.api.apiCall
import com.bbolbbol.app.data.api.map
import com.bbolbbol.app.data.local.AuthSession

class AuthRepository(private val api: BbolApi, private val session: AuthSession) {

    suspend fun login(email: String, password: String): ApiResult<TokenResponse> =
        apiCall { api.login(LoginRequest(email.trim(), password)) }.also(::storeTokens)

    suspend fun signup(verificationToken: String, password: String, nickname: String): ApiResult<TokenResponse> =
        apiCall { api.signup(SignupRequest(verificationToken.trim(), password, nickname.trim())) }.also(::storeTokens)

    suspend fun emailAvailability(email: String): ApiResult<AvailabilityResult> =
        apiCall { api.emailAvailability(email.trim()) }

    suspend fun nicknameAvailability(nickname: String): ApiResult<AvailabilityResult> =
        apiCall { api.nicknameAvailability(nickname.trim()) }

    /** 백엔드에 로그아웃 API가 아직 없어 로컬 토큰만 지운다. */
    fun logout() = session.clear()

    private fun storeTokens(result: ApiResult<TokenResponse>) {
        if (result is ApiResult.Success) session.saveTokens(result.data.accessToken, result.data.refreshToken)
    }
}

class UserRepository(private val api: BbolApi) {
    suspend fun me(): ApiResult<MeResponse> = apiCall { api.me() }

    suspend fun changeNickname(nickname: String): ApiResult<NicknameChangeResponse> =
        apiCall { api.changeNickname(NicknameChangeRequest(nickname.trim())) }
}

class PlaceRepository(private val api: BbolApi) {
    suspend fun categories(): ApiResult<List<CategoryItem>> =
        apiCall { api.categories() }.map { it.categories }

    suspend fun places(
        lat: Double,
        lng: Double,
        radius: Int,
        category: String?,
        size: Int = 100,
    ): ApiResult<List<PlaceSummary>> =
        apiCall { api.places(lat, lng, radius, category, size) }.map { it.places }

    suspend fun place(id: Long): ApiResult<PlaceDetail> = apiCall { api.place(id) }
}
