package com.bbolbbol.server.domain.user.dto

import com.bbolbbol.server.domain.user.entity.User
import jakarta.validation.constraints.Pattern

/**
 * PATCH /users/me/onboarding 요청. 모든 필드가 선택이다(PATCH라서 없는 필드는 바꾸지 않는다).
 * enum 대신 String + @Pattern을 쓰는 이유: 잘못된 값이 Jackson 역직렬화 오류(필드명 없는 COMMON400)가 아니라
 * data.errors에 필드명과 이유가 담긴 COMMON400이 되게 하려는 것이다. 값이 명시적 null이면 "없음"과 같게 취급한다.
 * nickname은 형식 검증을 여기서 하지 않는다(카카오 가입자의 첫 온보딩 외에는 값을 무시해야 하므로 서비스에서 검증).
 */
data class OnboardingRequest(
	val nickname: String?,

	@field:Pattern(regexp = "TEENS|TWENTIES|THIRTIES|FORTIES|FIFTIES_PLUS|UNDISCLOSED", message = "연령대 값이 올바르지 않습니다.")
	val ageGroup: String?,

	@field:Pattern(regexp = "MALE|FEMALE|UNDISCLOSED", message = "성별 값이 올바르지 않습니다.")
	val gender: String?,

	val interests: List<String>?,
)

/** GET /users/me/onboarding 응답(data). ageGroup, gender는 온보딩 전이면 null, interests는 없으면 빈 배열. */
data class OnboardingInfo(
	val ageGroup: String?,
	val gender: String?,
	val interests: List<String>,
	val onboardingCompleted: Boolean,
) {
	companion object {
		fun of(user: User, interests: List<String>) = OnboardingInfo(
			ageGroup = user.ageGroup?.name,
			gender = user.gender?.name,
			interests = interests,
			onboardingCompleted = user.onboardingCompleted,
		)
	}
}

/** PATCH /users/me/onboarding 응답(data). 조회 응답에 nickname이 더해진 형태. */
data class OnboardingResult(
	val nickname: String?,
	val ageGroup: String?,
	val gender: String?,
	val interests: List<String>,
	val onboardingCompleted: Boolean,
) {
	companion object {
		fun of(user: User, interests: List<String>) = OnboardingResult(
			nickname = user.nickname,
			ageGroup = user.ageGroup?.name,
			gender = user.gender?.name,
			interests = interests,
			onboardingCompleted = user.onboardingCompleted,
		)
	}
}
