package com.bbolbbol.server.domain.user.service

import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.repository.UserInterestRepository
import com.bbolbbol.server.domain.user.repository.UserRepository
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 탈퇴의 DB 부분(user 도메인 몫). 토큰 삭제, 카카오 연결 끊기, 이벤트 발행은 auth 도메인의 WithdrawalService가 이어서 한다.
 * 인증 기록과 포인트는 지우지 않는다(통계용). 랭킹 제외는 UserService.isActive 같은 상태 조회로 각 도메인이 한다.
 */
@Service
class UserWithdrawalService internal constructor(
	private val userRepository: UserRepository,
	private val interestRepository: UserInterestRepository,
	private val clock: Clock,
) {

	/** 탈퇴 처리하고 카카오 연결을 끊는 데 필요한 이전 kakaoId를 돌려준다(없으면 null). 이미 탈퇴/없음이면 USER404. */
	@Transactional
	fun withdraw(userId: Long): WithdrawalResult {
		val user = userRepository.findById(userId).orElse(null)?.takeIf { it.isActive() }
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
		val kakaoId = user.kakaoId
		user.withdraw(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
		userRepository.saveAndFlush(user)
		interestRepository.deleteAllByUserId(userId)
		return WithdrawalResult(kakaoId)
	}

	data class WithdrawalResult(val kakaoId: String?)
}
