package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.dto.LoginRequest
import com.bbolbbol.server.domain.auth.dto.PasswordResetRequest
import com.bbolbbol.server.domain.auth.dto.ReissueResponse
import com.bbolbbol.server.domain.auth.dto.SignupRequest
import com.bbolbbol.server.domain.auth.dto.TokenResponse
import com.bbolbbol.server.domain.auth.exception.AuthErrorCode
import com.bbolbbol.server.domain.auth.exception.EmailErrorCode
import com.bbolbbol.server.domain.user.entity.User
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
	private val userService: UserService,
	private val passwordEncoder: PasswordEncoder,
	private val verificationTokenVerifier: VerificationTokenVerifier,
	private val emailVerificationService: EmailVerificationService,
	private val jwtProvider: JwtProvider,
	private val refreshTokenService: RefreshTokenService,
	private val loginRateLimiter: LoginRateLimiter,
) {

	/** 존재하지 않는 이메일에도 BCrypt 비교를 한 번 수행해 응답 시간 차이로 이메일 존재를 알 수 없게 한다. */
	private val dummyHash: String = passwordEncoder.encode("dummy-password-for-timing")!!

	fun signup(request: SignupRequest): TokenResponse {
		val verificationToken = request.verificationToken!!
		val email = verificationTokenVerifier.verifySignupToken(verificationToken)
		val user = userService.createEmailUser(email, passwordEncoder.encode(request.password!!)!!, request.nickname!!)
		verificationTokenVerifier.consume(verificationToken)
		return tokens(user, isNewUser = true)
	}

	/**
	 * clientIp는 서블릿 remoteAddr다(로그인 시도 제한용, X-Forwarded-For는 믿지 않는다).
	 * 실패(AUTH4011)만 센다. 입력 검증 실패(COMMON400)는 세지 않는다.
	 */
	fun login(request: LoginRequest, clientIp: String): TokenResponse {
		loginRateLimiter.reserveAttempt(clientIp)
		val user = userService.findActiveByEmail(request.email!!)
		val hash = user?.passwordHash ?: dummyHash
		val matches = passwordEncoder.matches(request.password!!, hash)
		if (user == null || user.passwordHash == null || !matches) {
			throw BusinessException(AuthErrorCode.INVALID_CREDENTIALS)
		}
		loginRateLimiter.recordSuccess(clientIp)
		return tokens(user, isNewUser = !user.onboardingCompleted)
	}

	/**
	 * refresh token 재발급(rotation). 서명, 만료, type 검증 -> 활성 사용자 확인 -> 저장소에서 원자적으로 교체.
	 * 현재 토큰이거나 20초 유예 안의 옛 토큰이면 성공한다. 그 밖(없음, 만료, 위조, 유예 지난 재사용, 다른 기기 로그인으로 교체됨)은 전부 AUTH4012.
	 */
	fun reissue(refreshToken: String): ReissueResponse {
		val userId = jwtProvider.getUserIdFromRefreshToken(refreshToken) ?: throw invalidRefresh()
		if (!userService.isActive(userId)) throw invalidRefresh()
		val newRefresh = refreshTokenService.rotate(userId, refreshToken) ?: throw invalidRefresh()
		return ReissueResponse(jwtProvider.createAccessToken(userId), newRefresh)
	}

	fun logout(userId: Long) = refreshTokenService.delete(userId)

	/**
	 * 비밀번호 재설정. PASSWORD_RESET 토큰을 한 번만 쓰게 지우고, 비밀번호를 바꾸고, refresh token을 지운다(다른 기기 로그아웃).
	 * 이미 발급된 access token은 만료까지 유효하다. 토큰의 이메일이 가입자가 아니면 EMAIL4003.
	 */
	fun resetPassword(request: PasswordResetRequest) {
		val email = emailVerificationService.consumeToken(request.verificationToken, VerificationPurpose.PASSWORD_RESET)
		val userId = userService.resetPassword(email, passwordEncoder.encode(request.newPassword!!)!!)
			?: throw BusinessException(EmailErrorCode.INVALID_VERIFICATION_TOKEN)
		refreshTokenService.delete(userId)
	}

	private fun invalidRefresh() = BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)

	private fun tokens(user: User, isNewUser: Boolean): TokenResponse {
		val userId = user.id!!
		return TokenResponse(
			userId = userId,
			accessToken = jwtProvider.createAccessToken(userId),
			refreshToken = refreshTokenService.issue(userId),
			isNewUser = isNewUser,
		)
	}
}
