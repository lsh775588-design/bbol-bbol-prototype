package com.bbolbbol.place.controller;

import com.bbolbbol.common.response.ApiResponse;
import com.bbolbbol.place.dto.PlaceDtos.CategoryListResponse;
import com.bbolbbol.place.dto.PlaceDtos.PlaceDetail;
import com.bbolbbol.place.dto.PlaceDtos.PlaceListResponse;
import com.bbolbbol.place.service.PlaceSearchCondition;
import com.bbolbbol.place.service.PlaceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 모든 엔드포인트는 인증이 필요하다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/place-categories")
    public ApiResponse<CategoryListResponse> categories() {
        return ApiResponse.success(placeService.listCategories());
    }

    @GetMapping("/places")
    public ApiResponse<PlaceListResponse> places(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean excludeCompletedToday,
            @RequestParam(required = false) Integer size) {
        PlaceSearchCondition cond = PlaceSearchCondition.validated(lat, lng, radius, category,
                excludeCompletedToday, size, placeService.categoryCodes());
        return ApiResponse.success(placeService.search(userId, cond));
    }

    @GetMapping("/places/{placeId}")
    public ApiResponse<PlaceDetail> detail(@AuthenticationPrincipal Long userId, @PathVariable Long placeId) {
        return ApiResponse.success(placeService.detail(userId, placeId));
    }
}
