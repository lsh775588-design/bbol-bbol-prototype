package com.bbolbbol.server.domain.user.repository

import com.bbolbbol.server.domain.user.entity.UserInterest
import com.bbolbbol.server.domain.user.entity.UserInterestId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

internal interface UserInterestRepository : JpaRepository<UserInterest, UserInterestId> {

	fun findByUserIdOrderBySortOrderAsc(userId: Long): List<UserInterest>

	/**
	 * 벌크 삭제. 파생 삭제(deleteByUserId)를 쓰면 Hibernate가 flush 때 INSERT를 DELETE보다 먼저 실행해서
	 * 같은 코드를 다시 보낼 때 PK 위반이 난다. 즉시 실행하고 영속성 컨텍스트도 비운다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM UserInterest i WHERE i.userId = :userId")
	fun deleteAllByUserId(userId: Long): Int
}
