package com.bbolbbol.server.domain.auth.controller

import com.bbolbbol.server.domain.auth.dto.EmailConfirmRequest
import com.bbolbbol.server.domain.auth.dto.EmailConfirmResponse
import com.bbolbbol.server.domain.auth.dto.EmailSendRequest
import com.bbolbbol.server.domain.auth.dto.EmailSendResponse
import com.bbolbbol.server.domain.auth.dto.KakaoLoginRequest
import com.bbolbbol.server.domain.auth.dto.KakaoLoginResponse
import com.bbolbbol.server.domain.auth.dto.LoginRequest
import com.bbolbbol.server.domain.auth.dto.PasswordResetRequest
import com.bbolbbol.server.domain.auth.dto.ReissueRequest
import com.bbolbbol.server.domain.auth.dto.ReissueResponse
import com.bbolbbol.server.domain.auth.dto.SignupRequest
import com.bbolbbol.server.domain.auth.dto.TokenResponse
import com.bbolbbol.server.domain.auth.service.AuthService
import com.bbolbbol.server.domain.auth.service.EmailVerificationService
import com.bbolbbol.server.domain.auth.service.KakaoAuthService
import com.bbolbbol.server.domain.auth.service.VerificationPurpose
import com.bbolbbol.server.domain.user.dto.AvailabilityResult
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.common.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
	private val authService: AuthService,
	private val userService: UserService,
	private val emailVerificationService: EmailVerificationService,
	private val kakaoAuthService: KakaoAuthService,
) {

	@PostMapping("/signup")
	fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<TokenResponse>> =
		ApiResponse.createdResponse(authService.signup(request), "회원가입에 성공했습니다.")

	/** 로그인 시도 제한은 remoteAddr만 쓴다. X-Forwarded-For 같은 헤더는 클라이언트가 위조할 수 있어서 읽지 않는다. */
	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): ApiResponse<TokenResponse> =
		ApiResponse(true, "COMMON200", "로그인에 성공했습니다.", authService.login(request, http.remoteAddr))

	@PostMapping("/kakao")
	fun kakao(@Valid @RequestBody request: KakaoLoginRequest): ApiResponse<KakaoLoginResponse> =
		ApiResponse(true, "COMMON200", "로그인에 성공했습니다.", kakaoAuthService.login(request.kakaoAccessToken!!))

	@PostMapping("/email-verifications")
	fun sendEmailCode(@Valid @RequestBody request: EmailSendRequest): ApiResponse<EmailSendResponse> =
		ApiResponse(
			true, "COMMON200", "인증 코드를 보냈습니다.",
			emailVerificationService.send(request.email!!, VerificationPurpose.valueOf(request.purpose!!)),
		)

	@PostMapping("/email-verifications/confirm")
	fun confirmEmailCode(@Valid @RequestBody request: EmailConfirmRequest, http: HttpServletRequest): ApiResponse<EmailConfirmResponse> =
		ApiResponse(
			true, "COMMON200", "이메일 인증이 완료되었습니다.",
			emailVerificationService.confirm(request.email!!, VerificationPurpose.valueOf(request.purpose!!), request.code!!, http.remoteAddr),
		)

	@PostMapping("/password-reset")
	fun resetPassword(@Valid @RequestBody request: PasswordResetRequest): ApiResponse<Unit> {
		authService.resetPassword(request)
		return ApiResponse(true, "COMMON200", "비밀번호가 변경되었습니다.", null)
	}

	@PostMapping("/reissue")
	fun reissue(@Valid @RequestBody request: ReissueRequest): ApiResponse<ReissueResponse> =
		ApiResponse(true, "COMMON200", "토큰이 재발급되었습니다.", authService.reissue(request.refreshToken!!))

	/** 인증이 필요한 유일한 auth 경로다(SecurityConfig에서 예외 처리). */
	@PostMapping("/logout")
	fun logout(@AuthenticationPrincipal userId: Long): ApiResponse<Unit> {
		authService.logout(userId)
		return ApiResponse(true, "COMMON200", "로그아웃되었습니다.", null)
	}

	@GetMapping("/email-availability")
	fun emailAvailability(
		@RequestParam
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		email: String,
	): ApiResponse<AvailabilityResult> = ApiResponse.ok(userService.checkEmailAvailability(email))

	@GetMapping("/nickname-availability")
	fun nicknameAvailability(
		@RequestParam
		@NotBlank(message = "닉네임은 필수입니다.")
		nickname: String,
	): ApiResponse<AvailabilityResult> = ApiResponse.ok(userService.checkNicknameAvailability(nickname))
}
