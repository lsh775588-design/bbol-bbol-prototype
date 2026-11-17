package com.bbolbbol.server.domain.user.repository

import com.bbolbbol.server.domain.user.entity.User
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository

/** UserQueryService 전용 읽기 조회(새 파일: 기존 UserRepository는 건드리지 않는다). */
internal interface WithdrawnUserLookup : Repository<User, Long> {

	@Query("select u.id from User u where u.status = com.bbolbbol.server.domain.user.entity.UserStatus.WITHDRAWN")
	fun findWithdrawnIds(): List<Long>
}
