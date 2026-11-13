package com.bbolbbol.place.repository;

import com.bbolbbol.place.domain.Place;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 바운딩 박스 안의 활성 장소. category를 fetch join해 N+1을 피한다. allCategories가 true면 카테고리 필터 없음. */
    @Query("""
            select p from Place p join fetch p.category c
            where p.active = true
              and p.lat between :minLat and :maxLat
              and p.lng between :minLng and :maxLng
              and (:allCategories = true or c.code in :categoryCodes)
            """)
    List<Place> findInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                          @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                          @Param("allCategories") boolean allCategories,
                          @Param("categoryCodes") Collection<String> categoryCodes);

    @Query("select p from Place p join fetch p.category where p.id = :id and p.active = true")
    Optional<Place> findActiveById(@Param("id") Long id);
}
