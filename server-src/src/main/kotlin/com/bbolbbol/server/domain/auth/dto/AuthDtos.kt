package com.bbolbbol.server.domain.auth.dto

import com.bbolbbol.server.domain.user.dto.NicknamePolicy
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** 비밀번호 규칙: 8~20자, 영문 + 숫자 + 특수문자 각 1개 이상(공백 없는 ASCII 문자만). */
object PasswordPolicy {
	const val REGEX = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{8,20}$"
	const val MESSAGE = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
}

/** 필드를 nullable로 둬서 누락돼도 @NotBlank가 data.errors에 필드명을 담아 응답하게 함 */
data class SignupRequest(
	@field:NotBlank(message = "verificationToken은 필수입니다.")
	val verificationToken: String?,

	@field:NotBlank(message = "비밀번호는 필수입니다.")
	@field:Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
	val password: String?,

	@field:NotBlank(message = "닉네임은 필수입니다.")
	@field:Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.FORMAT_MESSAGE)
	val nickname: String?,
)

data class LoginRequest(
	@field:NotBlank(message = "이메일은 필수입니다.")
	val email: String?,

	@field:NotBlank(message = "비밀번호는 필수입니다.")
	val password: String?,
)

/** signup/login 응답(data). Kotlin의 is 접두사 프로퍼티가 JSON에서 이름이 바뀌지 않도록 키를 명시함 */
data class TokenResponse(
	val userId: Long,
	val accessToken: String,
	val refreshToken: String,
	@get:JsonProperty("isNewUser")
	@param:JsonProperty("isNewUser")
	val isNewUser: Boolean,
)

private const val EMAIL_REQUIRED = "이메일은 필수입니다."
private const val EMAIL_INVALID = "이메일 형식이 올바르지 않습니다."
private const val PURPOSE_PATTERN = "SIGNUP|PASSWORD_RESET"
private const val PURPOSE_INVALID = "purpose는 SIGNUP 또는 PASSWORD_RESET이어야 합니다."

data class EmailSendRequest(
	@field:NotBlank(message = EMAIL_REQUIRED)
	@field:Email(message = EMAIL_INVALID)
	val email: String?,

	@field:NotBlank(message = "purpose는 필수입니다.")
	@field:Pattern(regexp = PURPOSE_PATTERN, message = PURPOSE_INVALID)
	val purpose: String?,
)

data class EmailSendResponse(val expiresIn: Long, val resendAvailableIn: Long)

data class EmailConfirmRequest(
	@field:NotBlank(message = EMAIL_REQUIRED)
	@field:Email(message = EMAIL_INVALID)
	val email: String?,

	@field:NotBlank(message = "purpose는 필수입니다.")
	@field:Pattern(regexp = PURPOSE_PATTERN, message = PURPOSE_INVALID)
	val purpose: String?,

	@field:NotBlank(message = "코드는 필수입니다.")
	@field:Pattern(regexp = "^[0-9]{6}$", message = "코드는 6자리 숫자여야 합니다.")
	val code: String?,
)

data class EmailConfirmResponse(val verificationToken: String, val expiresIn: Long)

data class PasswordResetRequest(
	@field:NotBlank(message = "verificationToken은 필수입니다.")
	val verificationToken: String?,

	@field:NotBlank(message = "비밀번호는 필수입니다.")
	@field:Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
	val newPassword: String?,
)

data class ReissueRequest(
	@field:NotBlank(message = "refreshToken은 필수입니다.")
	val refreshToken: String?,
)

/** reissue 응답(data). 명세상 userId, isNewUser는 없다. */
data class ReissueResponse(val accessToken: String, val refreshToken: String)

data class KakaoLoginRequest(
	@field:NotBlank(message = "kakaoAccessToken은 필수입니다.")
	val kakaoAccessToken: String?,
)

/** 카카오 로그인 응답(data). TokenResponse에 suggestedNickname이 더해진 형태이며 null이어도 키는 내려간다. */
data class KakaoLoginResponse(
	val userId: Long,
	val accessToken: String,
	val refreshToken: String,
	@get:JsonProperty("isNewUser")
	@param:JsonProperty("isNewUser")
	val isNewUser: Boolean,
	val suggestedNickname: String?,
)
