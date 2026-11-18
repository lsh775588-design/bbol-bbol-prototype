package com.bbolbbol.server.domain.place.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "place_categories")
class PlaceCategory(
	@Column(nullable = false, length = 30, unique = true)
	val code: String,

	@Column(nullable = false, length = 50)
	val name: String,

	@Column(name = "reward_point", nullable = false)
	val rewardPoint: Int,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null
}
