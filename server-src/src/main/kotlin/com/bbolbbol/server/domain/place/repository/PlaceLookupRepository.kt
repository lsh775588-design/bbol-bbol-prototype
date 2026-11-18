package com.bbolbbol.server.domain.place.repository

import com.bbolbbol.server.domain.place.entity.Place
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.Optional

/** 다른 도메인(completion, point)이 PlaceQueryService를 통해서만 쓰는 읽기 전용 조회. 카테고리를 fetch join한다. */
interface PlaceLookupRepository : Repository<Place, Long> {

	@Query("select p from Place p join fetch p.category where p.id = :id and p.active = true")
	fun findActiveWithCategory(@Param("id") id: Long): Optional<Place>

	/** 비활성(폐업) 장소도 포함한다. 과거 기록의 장소 이름을 보여줘야 하기 때문이다. */
	@Query("select p from Place p join fetch p.category where p.id in :ids")
	fun findAllWithCategory(@Param("ids") ids: Collection<Long>): List<Place>

	@Query("select p.id from Place p where p.category.code = :code")
	fun findIdsByCategoryCode(@Param("code") code: String): List<Long>

	@Query("select count(c) > 0 from PlaceCategory c where c.code = :code")
	fun existsCategoryCode(@Param("code") code: String): Boolean
}
