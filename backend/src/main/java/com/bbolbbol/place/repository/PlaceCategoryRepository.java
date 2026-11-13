package com.bbolbbol.place.repository;

import com.bbolbbol.place.domain.PlaceCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {

    List<PlaceCategory> findAllByOrderByIdAsc();
}
