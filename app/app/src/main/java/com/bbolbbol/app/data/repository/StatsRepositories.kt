package com.bbolbbol.app.data.repository

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.PhotoUploadResponse
import com.bbolbbol.app.data.api.PlaceRankingResponse
import com.bbolbbol.app.data.api.StatsApi
import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.api.StreakRankingResponse
import com.bbolbbol.app.data.api.UserRankingResponse
import com.bbolbbol.app.data.api.apiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class PhotoRepository(private val api: StatsApi) {
    /** [jpeg]는 앱이 다시 인코딩한 JPEG 바이트. 서버는 JPEG/PNG만 읽고 EXIF는 서버가 지운다. */
    suspend fun upload(completionId: Long, jpeg: ByteArray): ApiResult<PhotoUploadResponse> {
        val part = MultipartBody.Part.createFormData("photo", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        return apiCall { api.uploadPhoto(completionId, part) }
    }
}

enum class StatsPeriod(val param: String) { WEEK("WEEK"), MONTH("MONTH"), YEAR("YEAR") }

enum class RankingPeriod(val param: String) { DAY("DAY"), WEEK("WEEK"), MONTH("MONTH") }

class StatsRepository(private val api: StatsApi) {
    suspend fun stats(period: StatsPeriod, date: String? = null): ApiResult<StatsResponse> =
        apiCall { api.stats(period.param, date) }
}

class RankingRepository(private val api: StatsApi) {
    suspend fun users(period: RankingPeriod, size: Int = USER_SIZE): ApiResult<UserRankingResponse> =
        apiCall { api.userRankings(period.param, size) }

    suspend fun places(period: RankingPeriod, size: Int = PLACE_SIZE): ApiResult<PlaceRankingResponse> =
        apiCall { api.placeRankings(period.param, null, size) }

    suspend fun streaks(size: Int = STREAK_SIZE): ApiResult<StreakRankingResponse> =
        apiCall { api.streakRankings(size) }

    companion object {
        // 서버 기본값/최댓값: 유저 50/100, 장소 10/50, 연속 10/50. 커서 페이지는 없고 size만 있다.
        const val USER_SIZE = 50
        const val PLACE_SIZE = 30
        const val STREAK_SIZE = 30
    }
}
