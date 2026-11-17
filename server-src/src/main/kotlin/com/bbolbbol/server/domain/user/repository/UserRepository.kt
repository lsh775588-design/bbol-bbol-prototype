package com.bbolbbol.server.domain.user.repository

import com.bbolbbol.server.domain.user.entity.User
import com.bbolbbol.server.domain.user.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository

/** internal: 다른 도메인은 UserService만 쓴다. */
internal interface UserRepository : JpaRepository<User, Long> {

	fun findByEmail(email: String): User?

	/** 탈퇴하면 kakao_id를 비우므로 값이 있으면 활성 사용자다. */
	fun findByKakaoId(kakaoId: String): User?

	fun existsByEmail(email: String): Boolean

	fun existsByNicknameIgnoreCase(nickname: String): Boolean

	fun existsByIdAndStatus(id: Long, status: UserStatus): Boolean

	fun findAllByIdIn(ids: Collection<Long>): List<User>
}
