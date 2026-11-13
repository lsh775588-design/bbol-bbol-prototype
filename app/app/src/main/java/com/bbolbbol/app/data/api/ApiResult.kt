package com.bbolbbol.app.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    /** 서버가 돌려준 실패. [code]는 백엔드 에러 코드(없으면 빈 문자열). */
    data class Failure(
        val httpStatus: Int,
        val code: String,
        val serverMessage: String?,
        val fieldErrors: List<FieldError> = emptyList(),
        /** EMAIL4001의 data.remainingAttempts. */
        val remainingAttempts: Int? = null,
    ) : ApiResult<Nothing>

    /** 연결 실패/타임아웃 등. */
    data object NetworkError : ApiResult<Nothing>
}

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Retrofit Response를 ApiResult로 바꾼다. 4xx/5xx 본문의 {code, data.errors}도 파싱한다. */
suspend fun <T : Any> apiCall(block: suspend () -> Response<ApiEnvelope<T>>): ApiResult<T> {
    val response = try {
        block()
    } catch (e: IOException) {
        return ApiResult.NetworkError
    }
    if (response.isSuccessful) {
        val data = response.body()?.data
        return if (data != null) ApiResult.Success(data)
        else ApiResult.Failure(response.code(), "", "응답 본문이 비어 있습니다.")
    }
    val raw = try {
        response.errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    return parseFailure(response.code(), raw)
}

fun parseFailure(httpStatus: Int, raw: String?): ApiResult.Failure {
    if (raw.isNullOrBlank()) return ApiResult.Failure(httpStatus, "", null)
    return try {
        val env = ApiJson.decodeFromString<ApiEnvelope<ValidationErrors>>(raw)
        ApiResult.Failure(httpStatus, env.code, env.message, env.data?.errors.orEmpty(), env.data?.remainingAttempts)
    } catch (e: Exception) {
        // data가 errors 형태가 아닐 수 있다(USER4002의 nextChangeableAt 등). code만 다시 읽는다.
        try {
            val env = ApiJson.decodeFromString<ApiEnvelope<JsonElement>>(raw)
            ApiResult.Failure(httpStatus, env.code, env.message)
        } catch (e2: Exception) {
            ApiResult.Failure(httpStatus, "", null)
        }
    }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this as ApiResult<Nothing>
    ApiResult.NetworkError -> this as ApiResult<Nothing>
}
