package com.bbolbbol.app.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** 3단계(사진, 통계, 랭킹) API. 공유 파일 BbolApi와 충돌하지 않게 따로 둔다. */
interface StatsApi {
    /** multipart 파트 이름은 서버 계약대로 `photo`. */
    @Multipart
    @POST("completions/{id}/photo")
    suspend fun uploadPhoto(
        @Path("id") completionId: Long,
        @Part photo: MultipartBody.Part,
    ): Response<ApiEnvelope<PhotoUploadResponse>>

    @GET("users/me/stats")
    suspend fun stats(
        @Query("period") period: String,
        @Query("date") date: String? = null,
    ): Response<ApiEnvelope<StatsResponse>>

    @GET("rankings/users")
    suspend fun userRankings(
        @Query("period") period: String,
        @Query("size") size: Int? = null,
    ): Response<ApiEnvelope<UserRankingResponse>>

    @GET("rankings/places")
    suspend fun placeRankings(
        @Query("period") period: String? = null,
        @Query("category") category: String? = null,
        @Query("size") size: Int? = null,
    ): Response<ApiEnvelope<PlaceRankingResponse>>

    @GET("rankings/streaks")
    suspend fun streakRankings(@Query("size") size: Int? = null): Response<ApiEnvelope<StreakRankingResponse>>
}
