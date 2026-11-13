package com.bbolbbol.app.data.api

import kotlinx.serialization.Serializable

/** 서버 VerificationPurpose의 JSON 값. 비밀번호 재설정은 "RESET"이 아니라 "PASSWORD_RESET"이다. */
enum class EmailPurpose(val wire: String) {
    SIGNUP("SIGNUP"),
    PASSWORD_RESET("PASSWORD_RESET"),
}

// 요청 DTO의 필수 필드에는 기본값을 두지 않는다(ApiJson은 encodeDefaults=false라 기본값과 같은 값은 키가 빠진다).
@Serializable
data class EmailSendRequest(val email: String, val purpose: String)

@Serializable
data class EmailSendResponse(val expiresIn: Long = 0, val resendAvailableIn: Long = 0)

@Serializable
data class EmailConfirmRequest(val email: String, val purpose: String, val code: String)

@Serializable
data class EmailConfirmResponse(val verificationToken: String = "", val expiresIn: Long = 0) {
    override fun toString() = "EmailConfirmResponse(expiresIn=$expiresIn)"
}

@Serializable
data class PasswordResetRequest(val verificationToken: String, val newPassword: String) {
    override fun toString() = "PasswordResetRequest()"
}

@Serializable
data class ReissueRequest(val refreshToken: String) {
    override fun toString() = "ReissueRequest()"
}

/** 재발급 응답에는 userId, isNewUser가 없다. */
@Serializable
data class ReissueResponse(val accessToken: String = "", val refreshToken: String = "") {
    override fun toString() = "ReissueResponse()"
}

@Serializable
data class KakaoLoginRequest(val kakaoAccessToken: String) {
    override fun toString() = "KakaoLoginRequest()"
}

@Serializable
data class KakaoLoginResponse(
    val userId: Long = 0,
    val accessToken: String = "",
    val refreshToken: String = "",
    val isNewUser: Boolean = false,
    val suggestedNickname: String? = null,
) {
    override fun toString() = "KakaoLoginResponse(userId=$userId)"
}

/** GET 응답. ageGroup/gender는 온보딩 전이면 null. */
@Serializable
data class OnboardingInfo(
    val ageGroup: String? = null,
    val gender: String? = null,
    val interests: List<String> = emptyList(),
    val onboardingCompleted: Boolean = false,
)

/** PATCH 응답. 조회 응답에 nickname이 더해진다. */
@Serializable
data class OnboardingResult(
    val nickname: String? = null,
    val ageGroup: String? = null,
    val gender: String? = null,
    val interests: List<String> = emptyList(),
    val onboardingCompleted: Boolean = false,
)

/** PATCH 본문. null 필드는 키가 빠진다(= 바꾸지 않음). interests는 빈 배열도 "모두 삭제"로 보낸다. */
@Serializable
data class OnboardingRequest(
    val nickname: String? = null,
    val ageGroup: String? = null,
    val gender: String? = null,
    val interests: List<String>? = null,
)

object AgeGroups {
    val all = listOf(
        "TEENS" to "10대",
        "TWENTIES" to "20대",
        "THIRTIES" to "30대",
        "FORTIES" to "40대",
        "FIFTIES_PLUS" to "50대 이상",
        "UNDISCLOSED" to "비공개",
    )
}

object Genders {
    val all = listOf("MALE" to "남성", "FEMALE" to "여성", "UNDISCLOSED" to "비공개")
}
