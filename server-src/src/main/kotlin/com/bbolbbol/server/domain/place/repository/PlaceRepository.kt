package com.bbolbbol.server.domain.place.repository

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface PlaceRepository : JpaRepository<Place, Long> {

	/** 바운딩 박스 안의 활성 장소. category를 fetch join해 N+1을 피한다. allCategories가 true면 카테고리 필터 없음. */
	@Query(
		"""
		select p from Place p join fetch p.category c
		where p.active = true
		  and p.lat between :minLat and :maxLat
		  and p.lng between :minLng and :maxLng
		  and (:allCategories = true or c.code in :categoryCodes)
		""",
	)
	fun findInBox(
		@Param("minLat") minLat: Double,
		@Param("maxLat") maxLat: Double,
		@Param("minLng") minLng: Double,
		@Param("maxLng") maxLng: Double,
		@Param("allCategories") allCategories: Boolean,
		@Param("categoryCodes") categoryCodes: Collection<String>,
	): List<Place>

	@Query("select p from Place p join fetch p.category where p.id = :id and p.active = true")
	fun findActiveById(@Param("id") id: Long): Optional<Place>

	/** 지정한 hot_type의 활성 장소(추천 후보). category를 fetch join한다. */
	@Query("select p from Place p join fetch p.category where p.hotType = :hotType and p.active = true")
	fun findActiveByHotType(@Param("hotType") hotType: HotType): List<Place>
}
