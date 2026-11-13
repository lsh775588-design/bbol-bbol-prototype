package com.bbolbbol.place.service;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.place.domain.GeoDistance;
import com.bbolbbol.place.domain.GeoDistance.BoundingBox;
import com.bbolbbol.place.domain.HotType;
import com.bbolbbol.place.domain.Place;
import com.bbolbbol.place.domain.PlaceCategory;
import com.bbolbbol.place.dto.PlaceDtos.CategoryItem;
import com.bbolbbol.place.dto.PlaceDtos.CategoryListResponse;
import com.bbolbbol.place.dto.PlaceDtos.PlaceDetail;
import com.bbolbbol.place.dto.PlaceDtos.PlaceListResponse;
import com.bbolbbol.place.dto.PlaceDtos.PlaceSummary;
import com.bbolbbol.place.repository.PlaceCategoryRepository;
import com.bbolbbol.place.repository.PlaceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final PlaceUserStateProvider userStateProvider;

    public PlaceService(PlaceRepository placeRepository, PlaceCategoryRepository categoryRepository,
                        PlaceUserStateProvider userStateProvider) {
        this.placeRepository = placeRepository;
        this.categoryRepository = categoryRepository;
        this.userStateProvider = userStateProvider;
    }

    public CategoryListResponse listCategories() {
        return new CategoryListResponse(categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryItem::from).toList());
    }

    /** 검증된 카테고리 코드 집합(요청 검증용). */
    public Set<String> categoryCodes() {
        return categoryRepository.findAll().stream().map(PlaceCategory::getCode).collect(Collectors.toSet());
    }

    /** 반경 안의 장소를 거리순(같으면 id순)으로 최대 size개. DB 조회 1회 + 상태 일괄 조회 1회. */
    public PlaceListResponse search(Long userId, PlaceSearchCondition c) {
        BoundingBox box = GeoDistance.boundingBox(c.lat(), c.lng(), c.radius());
        boolean all = c.categories().isEmpty();
        // 빈 컬렉션 IN 절을 피하려고 전체 조회일 때는 더미 값을 넘긴다.
        Set<String> codes = all ? Set.of("") : c.categories();
        List<Place> candidates = placeRepository.findInBox(box.minLat(), box.maxLat(), box.minLng(), box.maxLng(),
                all, codes);

        record Hit(Place place, double distance) {
        }
        List<Hit> hits = candidates.stream()
                .map(p -> new Hit(p, GeoDistance.haversineMeters(c.lat(), c.lng(), p.getLat(), p.getLng())))
                .filter(h -> h.distance() <= c.radius())
                .sorted(Comparator.comparingDouble(Hit::distance).thenComparing(h -> h.place().getId()))
                .toList();

        Map<Long, PlaceUserState> states = userStateProvider.statesFor(userId,
                hits.stream().map(h -> h.place().getId()).toList());
        List<PlaceSummary> places = hits.stream()
                .filter(h -> !(c.excludeCompletedToday() && states.get(h.place().getId()).completedToday()))
                .limit(c.size())
                .map(h -> PlaceSummary.of(h.place(), h.distance(), states.get(h.place().getId()),
                        hotTypeOf(h.place(), states.get(h.place().getId()))))
                .toList();
        return new PlaceListResponse(places);
    }

    public PlaceDetail detail(Long userId, Long placeId) {
        Place place = placeRepository.findActiveById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE404));
        PlaceUserState state = userStateProvider.statesFor(userId, List.of(placeId)).get(placeId);
        return PlaceDetail.of(place, state, hotTypeOf(place, state));
    }

    /** 둘 다 해당하면 POPULAR가 우선한다(명세). */
    static HotType hotTypeOf(Place place, PlaceUserState state) {
        if (state.popular()) {
            return HotType.POPULAR;
        }
        return place.getHotType();
    }
}
