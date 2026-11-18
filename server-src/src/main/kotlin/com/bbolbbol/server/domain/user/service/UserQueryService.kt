package com.bbolbbol.server.domain.user.service

import com.bbolbbol.server.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 다른 도메인(completion, ranking)이 사용자 정보를 읽는 읽기 전용 진입점.
 * 다른 도메인은 UserRepository 대신 이 서비스를 쓴다.
 */
@Service
@Transactional(readOnly = true)
class UserQueryService internal constructor(
	private val userRepository: UserRepository,
	private val userService: UserService,
	private val withdrawnLookup: com.bbolbbol.server.domain.user.repository.WithdrawnUserLookup,
) {

	/** 표시용 닉네임(랭킹 등). 탈퇴/없는 사용자는 "탈퇴한 사용자", 활성인데 닉네임이 없으면 "이름 없음". */
	fun displayNicknames(userIds: Collection<Long>): Map<Long, String> {
		if (userIds.isEmpty()) return emptyMap()
		return userService.getDisplayNicknames(userIds.toSet()).mapValues { it.value ?: UNNAMED }
	}

	/** 활성 사용자이면서 온보딩을 마쳤으면 true. 탈퇴/없는 사용자는 false. */
	fun isOnboardingCompleted(userId: Long): Boolean =
		userRepository.findById(userId).map { it.isActive() && it.onboardingCompleted }.orElse(false)

	/** 탈퇴한 사용자만 true(없는 사용자, 활성 사용자는 false). 파괴적인 정리 작업의 안전장치로 쓴다. */
	fun isWithdrawn(userId: Long): Boolean =
		userRepository.findById(userId).map { !it.isActive() }.orElse(false)

	/** 탈퇴한 모든 사용자 id(재시도용 점검에서만 쓴다). */
	fun withdrawnUserIds(): List<Long> = withdrawnLookup.findWithdrawnIds()

	/** ids 중 랭킹에 노출해도 되는 사용자(활성, 온보딩 완료, 닉네임 있음). 큰 목록은 나눠서 조회한다. */
	fun filterRankable(userIds: Collection<Long>): Set<Long> =
		userIds.toSet().chunked(CHUNK).flatMap { chunk ->
			userRepository.findAllByIdIn(chunk)
				.filter { it.isActive() && it.onboardingCompleted && !it.nickname.isNullOrBlank() }
				.map { it.id!! }
		}.toSet()

	companion object {
		const val UNNAMED = "이름 없음"
		private const val CHUNK = 500
	}
}
