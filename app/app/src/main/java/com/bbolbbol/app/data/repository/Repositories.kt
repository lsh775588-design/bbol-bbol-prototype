package com.bbolbbol.app.data.repository

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AvailabilityResult
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.CategoryItem
import com.bbolbbol.app.data.api.CompletionLogPage
import com.bbolbbol.app.data.api.CompletionRequest
import com.bbolbbol.app.data.api.CompletionResponse
import com.bbolbbol.app.data.api.PointHistoryPage
import com.bbolbbol.app.data.api.PointSummary
import com.bbolbbol.app.data.api.RecommendedPlace
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

    suspend fun recommendations(lat: Double, lng: Double, size: Int = 10): ApiResult<List<RecommendedPlace>> =
        apiCall { api.recommendations(lat, lng, size) }.map { it.places }
}

class CompletionRepository(private val api: BbolApi) {
    suspend fun complete(placeId: Long, request: CompletionRequest): ApiResult<CompletionResponse> =
        apiCall { api.complete(placeId, request) }

    suspend fun log(cursor: Long?, size: Int = PAGE_SIZE): ApiResult<CompletionLogPage> =
        apiCall { api.completions(cursor = cursor, size = size) }
}

class PointRepository(private val api: BbolApi) {
    suspend fun summary(): ApiResult<PointSummary> = apiCall { api.points() }

    suspend fun histories(cursor: Long?, size: Int = PAGE_SIZE): ApiResult<PointHistoryPage> =
        apiCall { api.pointHistories(cursor = cursor, size = size) }
}

const val PAGE_SIZE = 20
