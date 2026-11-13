package com.bbolbbol.place;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.security.JwtProvider;
import com.bbolbbol.place.domain.HotType;
import com.bbolbbol.place.domain.Place;
import com.bbolbbol.place.domain.PlaceCategory;
import com.bbolbbol.place.repository.PlaceCategoryRepository;
import com.bbolbbol.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlaceApiIntegrationTest {

    private static final double LAT = 37.5759;
    private static final double LNG = 126.9768;

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired PlaceRepository places;
    @Autowired PlaceCategoryRepository categories;

    Long nearId;
    Long midId;
    Long farId;
    Long inactiveId;

    @BeforeEach
    void fixtures() {
        // 위도 0.001도 = 약 111m. 거리순서와 id순서를 일부러 다르게 넣는다.
        midId = save("CAFE", "중간", 0.0045, null, 100, 200, null, "k-mid", true).getId();
        nearId = save("PARK", "가까움", 0.0009, null, 300, 400, HotType.CURATED, "k-near", true).getId();
        farId = save("CAFE", "멀다", 0.0135, 150, 100, 200, null, "k-far", true).getId();
        inactiveId = save("CAFE", "폐업", 0.0001, null, 100, 200, null, "k-off", false).getId();
        save("MUSEUM", "부산", -2.4, null, 100, 200, null, "k-busan", true);
    }

    private Place save(String code, String name, double dLat, Integer reward, int radius, int notify,
                       HotType hot, String kakaoId, boolean active) {
        PlaceCategory c = categories.findAll().stream().filter(x -> x.getCode().equals(code)).findFirst().orElseThrow();
        return places.saveAndFlush(new Place(c, name, "서울 종로구 테스트 1", "서울 종로구 테스트로 1", null,
                LAT + dLat, LNG, radius, notify, reward, kakaoId, "https://place.map.kakao.com/" + kakaoId,
                hot, active));
    }

    private ResultActions authed(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + jwt.createAccessToken(7L)));
    }

    // ---- /place-categories

    @Test
    @DisplayName("카테고리 목록: 8개, 명세 순서와 점수")
    void categoriesOk() throws Exception {
        authed("/place-categories").andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.data.categories", hasSize(8)))
                .andExpect(jsonPath("$.data.categories[0].code").value("CAFE"))
                .andExpect(jsonPath("$.data.categories[0].name").value("카페"))
                .andExpect(jsonPath("$.data.categories[0].rewardPoint").value(100))
                .andExpect(jsonPath("$.data.categories[7].code").value("HERITAGE"))
                .andExpect(jsonPath("$.data.categories[7].rewardPoint").value(300));
    }

    @Test
    @DisplayName("카테고리 목록: 토큰 없으면 AUTH401")
    void categoriesUnauthenticated() throws Exception {
        mvc.perform(get("/place-categories")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }

    // ---- /places

    @Test
    @DisplayName("주변 검색: 거리순 정렬, 반경 밖과 비활성 제외, 필드 값")
    void searchOrdered() throws Exception {
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places", hasSize(3)))
                .andExpect(jsonPath("$.data.places[0].placeId").value(nearId))
                .andExpect(jsonPath("$.data.places[0].name").value("가까움"))
                .andExpect(jsonPath("$.data.places[0].category").value("PARK"))
                .andExpect(jsonPath("$.data.places[0].distance").value(100))
                .andExpect(jsonPath("$.data.places[0].radius").value(300))
                .andExpect(jsonPath("$.data.places[0].notifyRadius").value(400))
                .andExpect(jsonPath("$.data.places[0].rewardPoint").value(100))
                .andExpect(jsonPath("$.data.places[0].expectedPoint").value(100))
                .andExpect(jsonPath("$.data.places[0].isHot").value(true))
                .andExpect(jsonPath("$.data.places[0].hotType").value("CURATED"))
                .andExpect(jsonPath("$.data.places[0].visitedBefore").value(false))
                .andExpect(jsonPath("$.data.places[0].isCompleted").value(false))
                .andExpect(jsonPath("$.data.places[1].placeId").value(midId))
                .andExpect(jsonPath("$.data.places[1].isHot").value(false))
                .andExpect(jsonPath("$.data.places[1].hotType").value((Object) null))
                .andExpect(jsonPath("$.data.places[2].placeId").value(farId))
                .andExpect(jsonPath("$.data.places[2].rewardPoint").value(150))
                .andExpect(jsonPath("$.data.places[2].expectedPoint").value(150));
    }

    @Test
    @DisplayName("주변 검색: 기본 반경 1000m")
    void defaultRadius() throws Exception {
        authed("/places?lat=" + LAT + "&lng=" + LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places", hasSize(2)));
    }

    @Test
    @DisplayName("주변 검색: size로 개수 제한(가까운 순으로 자른다)")
    void sizeLimit() throws Exception {
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000&size=2")
                .andExpect(jsonPath("$.data.places", hasSize(2)))
                .andExpect(jsonPath("$.data.places[0].placeId").value(nearId))
                .andExpect(jsonPath("$.data.places[1].placeId").value(midId));
    }

    @Test
    @DisplayName("주변 검색: 카테고리 필터(단일, 쉼표 여러 개)")
    void categoryFilter() throws Exception {
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000&category=CAFE")
                .andExpect(jsonPath("$.data.places", hasSize(2)));
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000&category=PARK,CAFE")
                .andExpect(jsonPath("$.data.places", hasSize(3)));
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000&category=MOVIE")
                .andExpect(jsonPath("$.data.places", hasSize(0)));
    }

    @Test
    @DisplayName("주변 검색: 결과가 없으면 빈 배열")
    void empty() throws Exception {
        authed("/places?lat=35.0&lng=129.0&radius=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places", hasSize(0)));
    }

    @Test
    @DisplayName("주변 검색: excludeCompletedToday=true는 오늘 인증한 장소가 없으면 결과가 같다(placeholder)")
    void excludeCompletedTodayAccepted() throws Exception {
        authed("/places?lat=" + LAT + "&lng=" + LNG + "&radius=2000&excludeCompletedToday=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places", hasSize(3)));
    }

    @Test
    @DisplayName("주변 검색: lat, lng 누락은 COMMON400 + 필드별 errors")
    void missingLatLng() throws Exception {
        authed("/places")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors", hasSize(2)))
                .andExpect(jsonPath("$.data.errors[0].field").value("lat"))
                .andExpect(jsonPath("$.data.errors[1].field").value("lng"));
    }

    @Test
    @DisplayName("주변 검색: 범위 오류(lat, lng, radius, size, 없는 category)는 COMMON400")
    void rangeErrors() throws Exception {
        String base = "/places?lat=37.5&lng=127.0";
        authed("/places?lat=91&lng=127.0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors[0].field").value("lat"));
        authed("/places?lat=37.5&lng=-181").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].field").value("lng"));
        authed(base + "&radius=5001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].field").value("radius"));
        authed(base + "&radius=0").andExpect(status().isBadRequest());
        authed(base + "&size=201").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].field").value("size"));
        authed(base + "&size=0").andExpect(status().isBadRequest());
        authed(base + "&category=NOPE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[0].field").value("category"));
        authed(base + "&category=CAFE,").andExpect(status().isBadRequest());
        authed(base + "&radius=5000&size=200").andExpect(status().isOk());
    }

    @Test
    @DisplayName("주변 검색: 숫자 형식 오류와 NaN은 COMMON400")
    void typeErrors() throws Exception {
        authed("/places?lat=abc&lng=127").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
        authed("/places?lat=NaN&lng=127").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
        authed("/places?lat=37&lng=127&excludeCompletedToday=maybe").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주변 검색: 토큰 없으면 AUTH401")
    void searchUnauthenticated() throws Exception {
        mvc.perform(get("/places?lat=37.5&lng=127.0")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }

    // ---- /places/{id}

    @Test
    @DisplayName("상세: 명세 필드와 장소 점수가 카테고리 점수를 덮는다")
    void detailOk() throws Exception {
        authed("/places/" + farId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeId").value(farId))
                .andExpect(jsonPath("$.data.name").value("멀다"))
                .andExpect(jsonPath("$.data.category").value("CAFE"))
                .andExpect(jsonPath("$.data.categoryName").value("카페"))
                .andExpect(jsonPath("$.data.address").value("서울 종로구 테스트 1"))
                .andExpect(jsonPath("$.data.roadAddress").value("서울 종로구 테스트로 1"))
                .andExpect(jsonPath("$.data.phone").value((Object) null))
                .andExpect(jsonPath("$.data.radius").value(100))
                .andExpect(jsonPath("$.data.notifyRadius").value(200))
                .andExpect(jsonPath("$.data.kakaoPlaceUrl").value("https://place.map.kakao.com/k-far"))
                .andExpect(jsonPath("$.data.rewardPoint").value(150))
                .andExpect(jsonPath("$.data.expectedPoint").value(150))
                .andExpect(jsonPath("$.data.photoBonusAvailable").value(true))
                .andExpect(jsonPath("$.data.isHot").value(false))
                .andExpect(jsonPath("$.data.hotType").value((Object) null))
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.visitedBefore").value(false))
                .andExpect(jsonPath("$.data.isCompleted").value(false))
                .andExpect(jsonPath("$.data.myLastCompletedAt").value((Object) null));
    }

    @Test
    @DisplayName("상세: 장소 점수가 없으면 카테고리 점수, CURATED 표시")
    void detailCategoryReward() throws Exception {
        authed("/places/" + nearId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardPoint").value(100))
                .andExpect(jsonPath("$.data.hotType").value("CURATED"))
                .andExpect(jsonPath("$.data.isHot").value(true));
    }

    @Test
    @DisplayName("상세: 없는 장소와 비활성 장소는 404 PLACE404")
    void detailNotFound() throws Exception {
        authed("/places/99999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PLACE404"));
        authed("/places/" + inactiveId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE404"));
    }

    @Test
    @DisplayName("상세: 숫자가 아닌 id는 COMMON400")
    void detailBadId() throws Exception {
        authed("/places/abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("상세: 토큰 없으면 AUTH401")
    void detailUnauthenticated() throws Exception {
        mvc.perform(get("/places/" + nearId)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }
}
