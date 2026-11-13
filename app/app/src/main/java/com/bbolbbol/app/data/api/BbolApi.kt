package com.bbolbbol.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BbolApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<TokenResponse>>

    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<ApiEnvelope<TokenResponse>>

    @GET("auth/email-availability")
    suspend fun emailAvailability(@Query("email") email: String): Response<ApiEnvelope<AvailabilityResult>>

    @GET("auth/nickname-availability")
    suspend fun nicknameAvailability(@Query("nickname") nickname: String): Response<ApiEnvelope<AvailabilityResult>>

    @GET("users/me")
    suspend fun me(): Response<ApiEnvelope<MeResponse>>

    @PATCH("users/me")
    suspend fun changeNickname(@Body body: NicknameChangeRequest): Response<ApiEnvelope<NicknameChangeResponse>>

    @GET("place-categories")
    suspend fun categories(): Response<ApiEnvelope<CategoryListResponse>>

    @GET("places")
    suspend fun places(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Int? = null,
        @Query("category") category: String? = null,
        @Query("size") size: Int? = null,
    ): Response<ApiEnvelope<PlaceListResponse>>

    @GET("places/{id}")
    suspend fun place(@Path("id") id: Long): Response<ApiEnvelope<PlaceDetail>>

    @POST("places/{id}/completions")
    suspend fun complete(
        @Path("id") id: Long,
        @Body body: CompletionRequest,
    ): Response<ApiEnvelope<CompletionResponse>>

    @GET("users/me/points")
    suspend fun points(): Response<ApiEnvelope<PointSummary>>

    @GET("users/me/point-histories")
    suspend fun pointHistories(
        @Query("cursor") cursor: Long? = null,
        @Query("size") size: Int? = null,
        @Query("type") type: String? = null,
    ): Response<ApiEnvelope<PointHistoryPage>>

    @GET("users/me/completions")
    suspend fun completions(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("category") category: String? = null,
        @Query("cursor") cursor: Long? = null,
        @Query("size") size: Int? = null,
    ): Response<ApiEnvelope<CompletionLogPage>>

    @GET("places/recommendations")
    suspend fun recommendations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("size") size: Int? = null,
    ): Response<ApiEnvelope<RecommendationListResponse>>
}
