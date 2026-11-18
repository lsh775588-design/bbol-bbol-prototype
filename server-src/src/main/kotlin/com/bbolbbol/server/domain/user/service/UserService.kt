package com.bbolbbol.server.domain.user.service

import com.bbolbbol.server.domain.user.dto.AvailabilityResult
import com.bbolbbol.server.domain.user.dto.NicknamePolicy
import com.bbolbbol.server.domain.user.entity.LoginType
import com.bbolbbol.server.domain.user.entity.User
import com.bbolbbol.server.domain.user.entity.UserStatus
import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.repository.UserRepository
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** user 도메인의 공개 진입점. 다른 도메인은 UserRepository가 아니라 이 서비스를 쓴다. */
@Service
class UserService internal constructor(
	private val userRepository: UserRepository,
	private val forbiddenWordFilter: ForbiddenWordFilter,
	private val clock: Clock,
) {

	private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)

	/**
	 * 이메일 가입 사용자를 만든다. 금칙어(USER4001), 이메일 중복(USER409), 닉네임 중복(USER4092).
	 * 동시 가입 경합은 DB 유니크 제약 위반으로 잡아 같은 코드로 변환한다(트랜잭션 없이 saveAndFlush).
	 */
	fun createEmailUser(email: String, passwordHash: String, nickname: String): User {
		val normalized = normalizeEmail(email)
		if (forbiddenWordFilter.containsForbiddenWord(nickname)) {
			throw BusinessException(UserErrorCode.NICKNAME_FORBIDDEN_WORD)
		}
		if (userRepository.existsByEmail(normalized)) {
			throw BusinessException(UserErrorCode.EMAIL_DUPLICATED)
		}
		if (userRepository.existsByNicknameIgnoreCase(nickname)) {
			throw BusinessException(UserErrorCode.NICKNAME_DUPLICATED)
		}
		return try {
			userRepository.saveAndFlush(User.createEmailUser(normalized, passwordHash, nickname))
		} catch (e: DataIntegrityViolationException) {
			throw BusinessException(
				if (userRepository.existsByEmail(normalized)) UserErrorCode.EMAIL_DUPLICATED
				else UserErrorCode.NICKNAME_DUPLICATED,
			)
		}
	}

	/**
	 * 카카오 가입자 생성. 닉네임은 null(온보딩에서 정함). 같은 kakaoId의 동시 첫 로그인이 겹치면
	 * DB 유니크 제약(uk_users_kakao_id) 위반으로 한쪽이 실패하므로, 그때는 먼저 만들어진 사용자를 돌려준다.
	 * email이 이미 다른 활성 사용자에게 있으면(경합 포함) 이메일 없이 만든다.
	 */
	fun createKakaoUser(kakaoId: String, email: String?): User {
		val normalized = email?.let { normalizeEmail(it) }
		return try {
			userRepository.saveAndFlush(User.createKakaoUser(kakaoId, normalized))
		} catch (e: DataIntegrityViolationException) {
			userRepository.findByKakaoId(kakaoId)
				?: if (normalized != null) userRepository.saveAndFlush(User.createKakaoUser(kakaoId, null)) else throw e
		}
	}

	@Transactional(readOnly = true)
	fun findActiveByKakaoId(kakaoId: String): User? =
		userRepository.findByKakaoId(kakaoId)?.takeIf { it.isActive() }

	/**
	 * 비밀번호 재설정. 활성 이메일 가입자만 바꾼다(카카오 가입자는 비밀번호가 없다). 바꿨으면 userId, 아니면 null.
	 */
	@Transactional
	fun resetPassword(email: String, newPasswordHash: String): Long? {
		val user = userRepository.findByEmail(normalizeEmail(email))
			?.takeIf { it.isActive() && it.loginType == LoginType.EMAIL } ?: return null
		user.changePasswordHash(newPasswordHash)
		return userRepository.save(user).id
	}

	/** 온보딩 완료 여부. 없는 사용자는 false. 온보딩 전 사용자를 막는 정책(A4)에서 다른 도메인이 이 메서드를 쓴다. */
	@Transactional(readOnly = true)
	fun isOnboardingCompleted(userId: Long): Boolean =
		userRepository.findById(userId).map { it.onboardingCompleted }.orElse(false)

	@Transactional(readOnly = true)
	fun findActiveByEmail(email: String): User? =
		userRepository.findByEmail(normalizeEmail(email))?.takeIf { it.isActive() }

	/** 활성 사용자만. 탈퇴/없음이면 USER404. */
	@Transactional(readOnly = true)
	fun getActiveUser(userId: Long): User =
		userRepository.findById(userId).orElse(null)?.takeIf { it.isActive() }
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

	/**
	 * 닉네임 변경. 순서: 금칙어(USER4001) -> 30일(USER4002, data.nextChangeableAt) -> 중복(USER4092).
	 * 현재 닉네임과 같은 값은 "이미 사용 중"으로 보아 USER4092.
	 */
	fun changeNickname(userId: Long, nickname: String): User {
		val user = getActiveUser(userId)
		if (forbiddenWordFilter.containsForbiddenWord(nickname)) {
			throw BusinessException(UserErrorCode.NICKNAME_FORBIDDEN_WORD)
		}
		val now = now()
		if (!user.canChangeNickname(now)) {
			throw BusinessException(
				UserErrorCode.NICKNAME_CHANGE_LIMIT,
				mapOf("nextChangeableAt" to user.nextNicknameChangeableAt()),
			)
		}
		if (userRepository.existsByNicknameIgnoreCase(nickname)) {
			throw BusinessException(UserErrorCode.NICKNAME_DUPLICATED)
		}
		user.changeNickname(nickname, now)
		return try {
			userRepository.saveAndFlush(user)
		} catch (e: DataIntegrityViolationException) {
			throw BusinessException(UserErrorCode.NICKNAME_DUPLICATED)
		}
	}

	@Transactional(readOnly = true)
	fun checkEmailAvailability(email: String): AvailabilityResult {
		val user = userRepository.findByEmail(normalizeEmail(email))?.takeIf { it.isActive() }
			?: return AvailabilityResult.ok()
		return AvailabilityResult.unavailable(
			if (user.loginType == LoginType.KAKAO) "REGISTERED_WITH_KAKAO" else "DUPLICATED_EMAIL",
		)
	}

	@Transactional(readOnly = true)
	fun checkNicknameAvailability(nickname: String): AvailabilityResult = when {
		!NicknamePolicy.isValidFormat(nickname) -> AvailabilityResult.unavailable("INVALID_FORMAT")
		forbiddenWordFilter.containsForbiddenWord(nickname) -> AvailabilityResult.unavailable("FORBIDDEN_WORD")
		userRepository.existsByNicknameIgnoreCase(nickname) -> AvailabilityResult.unavailable("DUPLICATED")
		else -> AvailabilityResult.ok()
	}

	// ---- 다른 도메인(ranking, completion, point 등)용 ----

	@Transactional(readOnly = true)
	fun isActive(userId: Long): Boolean = userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)

	/** 표시용 닉네임. 탈퇴/없음이면 "탈퇴한 사용자", 활성이지만 닉네임 미설정이면 null. */
	@Transactional(readOnly = true)
	fun getDisplayNickname(userId: Long): String? {
		val user = userRepository.findById(userId).orElse(null) ?: return WITHDRAWN_NICKNAME
		return if (user.isActive()) user.nickname else WITHDRAWN_NICKNAME
	}

	/** 여러 사용자의 표시 닉네임(랭킹 등). 없는 id는 "탈퇴한 사용자". 활성인데 닉네임이 없으면 값이 null. */
	@Transactional(readOnly = true)
	fun getDisplayNicknames(userIds: Collection<Long>): Map<Long, String?> {
		val result = HashMap<Long, String?>()
		userIds.forEach { result[it] = WITHDRAWN_NICKNAME }
		userRepository.findAllByIdIn(userIds).forEach {
			result[it.id!!] = if (it.isActive()) it.nickname else WITHDRAWN_NICKNAME
		}
		return result
	}

	companion object {
		/** 탈퇴한 사용자(또는 존재하지 않는 사용자)의 표시 닉네임. */
		const val WITHDRAWN_NICKNAME = "탈퇴한 사용자"

		fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
	}
}
