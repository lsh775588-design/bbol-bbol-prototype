package com.bbolbbol.server.domain.user.dto

import com.bbolbbol.server.domain.user.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 이메일/닉네임 사용 가능 여부 응답(data). reason은 사용 가능하면 null. */
data class AvailabilityResult(val available: Boolean, val reason: String?) {
	companion object {
		fun ok() = AvailabilityResult(true, null)
		fun unavailable(reason: String) = AvailabilityResult(false, reason)
	}
}

data class MeResponse(
	val userId: Long,
	val email: String?,
	val nickname: String?,
	val loginType: String,
	val onboardingCompleted: Boolean,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun from(user: User) = MeResponse(
			userId = user.id!!,
			email = user.email,
			nickname = user.nickname,
			loginType = user.loginType.name,
			onboardingCompleted = user.onboardingCompleted,
			createdAt = user.createdAt?.truncatedTo(ChronoUnit.SECONDS),
		)
	}
}

/** 필드를 nullable로 둬서 누락돼도 @NotBlank가 data.errors에 필드명을 담아 응답하게 함 */
data class NicknameChangeRequest(
	@field:NotBlank(message = "닉네임은 필수입니다.")
	@field:Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.FORMAT_MESSAGE)
	val nickname: String?,
)

data class NicknameChangeResponse(val nickname: String?, val nextChangeableAt: LocalDateTime?)
