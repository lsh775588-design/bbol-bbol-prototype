package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.dto.KakaoLoginResponse
import com.bbolbbol.server.domain.auth.kakao.KakaoClient
import com.bbolbbol.server.domain.user.entity.LoginType
import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.springframework.stereotype.Service

/**
 * 카카오 로그인(없으면 자동 가입). 규칙(노션 명세와 Gray Area B5):
 * - 카카오가 확인한 kakaoId로 사용자를 찾는다. 없으면 새로 만든다(닉네임 null, 온보딩 전).
 * - 새 가입인데 카카오 이메일이 이미 이메일 가입 계정이면 USER4093. 카카오 계정을 이메일 계정에 자동으로 합치지 않는다
 *   (이메일 주인이 아닌 사람이 계정을 넘겨받는 사고를 막는다).
 * - 이메일 동의가 없으면 이메일 없이 가입시키고 중복 검사도 하지 않는다.
 */
@Service
class KakaoAuthService(
	private val kakaoClient: KakaoClient,
	private val userService: UserService,
	private val jwtProvider: JwtProvider,
	private val refreshTokenService: RefreshTokenService,
) {

	fun login(kakaoAccessToken: String): KakaoLoginResponse {
		val profile = kakaoClient.fetchProfile(kakaoAccessToken)
		val user = userService.findActiveByKakaoId(profile.kakaoId) ?: createUser(profile.kakaoId, profile.email)
		val userId = user.id!!
		return KakaoLoginResponse(
			userId = userId,
			accessToken = jwtProvider.createAccessToken(userId),
			refreshToken = refreshTokenService.issue(userId),
			isNewUser = !user.onboardingCompleted,
			suggestedNickname = if (user.nickname == null) suggestNickname(profile.nickname) else null,
		)
	}

	private fun createUser(kakaoId: String, rawEmail: String?) = userService.createKakaoUser(kakaoId, usableEmail(rawEmail))

	/** 이메일 가입 계정과 겹치면 USER4093. 다른 카카오 계정이 이미 쓰는 이메일이면 이메일 없이 가입시킨다. */
	private fun usableEmail(rawEmail: String?): String? {
		val email = rawEmail?.let { UserService.normalizeEmail(it) } ?: return null
		val existing = userService.findActiveByEmail(email) ?: return email
		if (existing.loginType == LoginType.EMAIL) throw BusinessException(UserErrorCode.KAKAO_EMAIL_REGISTERED_AS_EMAIL)
		return null
	}

	/** 카카오 닉네임을 우리 규칙(2~10자, 한글/영문/숫자)에 맞게 다듬는다. 다듬을 수 없거나 금칙어, 중복이면 null. */
	private fun suggestNickname(kakaoNickname: String?): String? {
		val cleaned = kakaoNickname?.filter { it in '가'..'힣' || it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
			?.take(MAX_NICKNAME_LENGTH)
			?.takeIf { it.length >= MIN_NICKNAME_LENGTH } ?: return null
		return cleaned.takeIf { userService.checkNicknameAvailability(it).available }
	}

	companion object {
		private const val MIN_NICKNAME_LENGTH = 2
		private const val MAX_NICKNAME_LENGTH = 10
	}
}
