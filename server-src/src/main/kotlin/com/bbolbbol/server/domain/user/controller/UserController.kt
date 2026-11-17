package com.bbolbbol.server.domain.user.controller

import com.bbolbbol.server.domain.auth.service.WithdrawalService
import com.bbolbbol.server.domain.user.dto.MeResponse
import com.bbolbbol.server.domain.user.dto.NicknameChangeRequest
import com.bbolbbol.server.domain.user.dto.NicknameChangeResponse
import com.bbolbbol.server.domain.user.dto.OnboardingInfo
import com.bbolbbol.server.domain.user.dto.OnboardingRequest
import com.bbolbbol.server.domain.user.dto.OnboardingResult
import com.bbolbbol.server.domain.user.service.OnboardingService
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.common.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
	private val userService: UserService,
	private val onboardingService: OnboardingService,
	private val withdrawalService: WithdrawalService,
) {

	@GetMapping("/me")
	fun me(@AuthenticationPrincipal userId: Long): ApiResponse<MeResponse> =
		ApiResponse.ok(MeResponse.from(userService.getActiveUser(userId)))

	@PatchMapping("/me")
	fun changeNickname(
		@AuthenticationPrincipal userId: Long,
		@Valid @RequestBody request: NicknameChangeRequest,
	): ApiResponse<NicknameChangeResponse> {
		val user = userService.changeNickname(userId, request.nickname!!)
		return ApiResponse(
			true, "COMMON200", "닉네임이 변경되었습니다.",
			NicknameChangeResponse(user.nickname, user.nextNicknameChangeableAt()),
		)
	}

	/** 탈퇴: 개인정보 즉시 삭제, 모든 기기 로그아웃, 카카오 연결 끊기. 인증 기록과 포인트는 통계용으로 남는다. */
	@DeleteMapping("/me")
	fun withdraw(@AuthenticationPrincipal userId: Long): ApiResponse<Unit> {
		withdrawalService.withdraw(userId)
		return ApiResponse(true, "COMMON200", "회원 탈퇴가 완료되었습니다.", null)
	}

	@GetMapping("/me/onboarding")
	fun getOnboarding(@AuthenticationPrincipal userId: Long): ApiResponse<OnboardingInfo> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", onboardingService.get(userId))

	@PatchMapping("/me/onboarding")
	fun saveOnboarding(
		@AuthenticationPrincipal userId: Long,
		@Valid @RequestBody request: OnboardingRequest,
	): ApiResponse<OnboardingResult> =
		ApiResponse(true, "COMMON200", "온보딩 정보가 저장되었습니다.", onboardingService.save(userId, request))
}
