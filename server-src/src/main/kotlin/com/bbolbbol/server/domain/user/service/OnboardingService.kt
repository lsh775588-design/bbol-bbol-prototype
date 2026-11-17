package com.bbolbbol.server.domain.user.service

import com.bbolbbol.server.domain.place.service.PlaceQueryService
import com.bbolbbol.server.domain.user.dto.NicknamePolicy
import com.bbolbbol.server.domain.user.dto.OnboardingInfo
import com.bbolbbol.server.domain.user.dto.OnboardingRequest
import com.bbolbbol.server.domain.user.dto.OnboardingResult
import com.bbolbbol.server.domain.user.entity.AgeGroup
import com.bbolbbol.server.domain.user.entity.Gender
import com.bbolbbol.server.domain.user.entity.LoginType
import com.bbolbbol.server.domain.user.entity.User
import com.bbolbbol.server.domain.user.entity.UserInterest
import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.repository.UserInterestRepository
import com.bbolbbol.server.domain.user.repository.UserRepository
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 온보딩 조회와 저장. 규칙(노션 명세):
 * - 첫 온보딩(onboardingCompleted=false)은 ageGroup과 gender가 필수이고 카카오 가입자는 nickname도 필수. 다 저장되면 완료로 표시한다.
 * - nickname은 카카오 가입자의 첫 온보딩에서만 받는다. 그 밖에는 값이 와도 무시한다(변경은 PATCH /users/me). 30일 제한에 포함하지 않는다.
 * - interests가 오면 목록을 통째로 교체한다(빈 배열이면 모두 삭제). 최대 3개, place 도메인이 아는 카테고리 코드만.
 */
@Service
class OnboardingService internal constructor(
	private val userRepository: UserRepository,
	private val interestRepository: UserInterestRepository,
	private val forbiddenWordFilter: ForbiddenWordFilter,
	private val placeQueryService: PlaceQueryService,
) {

	@Transactional(readOnly = true)
	fun get(userId: Long): OnboardingInfo {
		val user = activeUser(userId)
		return OnboardingInfo.of(user, interests(userId))
	}

	@Transactional
	fun save(userId: Long, request: OnboardingRequest): OnboardingResult {
		val user = activeUser(userId)
		val firstTime = !user.onboardingCompleted
		val takesNickname = firstTime && user.loginType == LoginType.KAKAO

		val errors = ArrayList<FieldError>()
		if (firstTime && request.ageGroup == null && user.ageGroup == null) errors += FieldError("ageGroup", "연령대는 필수입니다.")
		if (firstTime && request.gender == null && user.gender == null) errors += FieldError("gender", "성별은 필수입니다.")
		val nickname = request.nickname?.takeIf { takesNickname }
		if (takesNickname) {
			when {
				nickname.isNullOrBlank() -> errors += FieldError("nickname", "닉네임은 필수입니다.")
				!NicknamePolicy.isValidFormat(nickname) -> errors += FieldError("nickname", NicknamePolicy.FORMAT_MESSAGE)
			}
		}
		val interests = request.interests?.distinct()
		if (interests != null) {
			if (interests.size > UserInterest.MAX_INTERESTS) {
				errors += FieldError("interests", "관심 카테고리는 최대 ${UserInterest.MAX_INTERESTS}개까지 고를 수 있습니다.")
			} else if (interests.any { !placeQueryService.isKnownCategory(it) }) {
				errors += FieldError("interests", "존재하지 않는 카테고리 코드가 있습니다.")
			}
		}
		if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))

		if (nickname != null) {
			if (forbiddenWordFilter.containsForbiddenWord(nickname)) throw BusinessException(UserErrorCode.NICKNAME_FORBIDDEN_WORD)
			if (userRepository.existsByNicknameIgnoreCase(nickname)) throw BusinessException(UserErrorCode.NICKNAME_DUPLICATED)
			user.setInitialNickname(nickname)
		}
		user.applyOnboarding(request.ageGroup?.let { AgeGroup.valueOf(it) }, request.gender?.let { Gender.valueOf(it) })
		if (firstTime) user.completeOnboarding()
		try {
			userRepository.saveAndFlush(user)
		} catch (e: DataIntegrityViolationException) {
			throw BusinessException(UserErrorCode.NICKNAME_DUPLICATED)
		}
		if (interests != null) {
			interestRepository.deleteAllByUserId(userId)
			interestRepository.saveAll(interests.mapIndexed { i, code -> UserInterest(userId, code, i) })
		}
		return OnboardingResult.of(user, interests ?: interests(userId))
	}

	private fun activeUser(userId: Long): User =
		userRepository.findById(userId).orElse(null)?.takeIf { it.isActive() }
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

	private fun interests(userId: Long): List<String> =
		interestRepository.findByUserIdOrderBySortOrderAsc(userId).map { it.categoryCode }
}
