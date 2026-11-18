package com.bbolbbol.server.domain.place.repository

import com.bbolbbol.server.domain.place.entity.PlaceCategory
import org.springframework.data.jpa.repository.JpaRepository

interface PlaceCategoryRepository : JpaRepository<PlaceCategory, Long> {

	fun findAllByOrderByIdAsc(): List<PlaceCategory>
}
