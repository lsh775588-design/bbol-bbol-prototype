package com.bbolbbol.server.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

data class UserInterestId(
	val userId: Long = 0,
	val categoryCode: String = "",
) : Serializable

/** user_interests 테이블(온보딩 관심 카테고리, 최대 3개). 카테고리는 place_categories.code 값으로 저장한다(V103). */
@Entity
@Table(name = "user_interests")
@IdClass(UserInterestId::class)
class UserInterest(
	@Id
	@Column(name = "user_id")
	val userId: Long,

	@Id
	@Column(name = "category_code")
	val categoryCode: String,

	@Column(name = "sort_order", nullable = false)
	val sortOrder: Int,
) {
	companion object {
		const val MAX_INTERESTS = 3
	}
}
