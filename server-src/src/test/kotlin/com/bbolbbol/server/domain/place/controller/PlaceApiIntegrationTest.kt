package com.bbolbbol.server.domain.place.controller

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AllUsersActiveConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AllUsersActiveConfig::class) // 토큰의 userId가 users 테이블에 없어도 장소 API 자체를 검증한다
@Transactional
class PlaceApiIntegrationTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository

	private val lat = 37.5759
	private val lng = 126.9768
	private val mapper = JsonMapper.builder().build()

	private var nearId = 0L
	private var midId = 0L
	private var farId = 0L
	private var inactiveId = 0L

	@BeforeEach
	fun fixtures() {
		// 위도 0.001도 = 약 111m. 거리순서와 id순서를 일부러 다르게 넣는다.
		midId = save("CAFE", "중간", 0.0045, null, 100, 200, null, "k-mid", true).id!!
		nearId = save("PARK", "가까움", 0.0009, null, 300, 400, HotType.CURATED, "k-near", true).id!!
		farId = save("CAFE", "멀다", 0.0135, 150, 100, 200, null, "k-far", true).id!!
		inactiveId = save("CAFE", "폐업", 0.0001, null, 100, 200, null, "k-off", false).id!!
		save("MUSEUM", "부산", -2.4, null, 100, 200, null, "k-busan", true)
	}

	private fun save(
		code: String, name: String, dLat: Double, reward: Int?, radius: Int, notify: Int,
		hot: HotType?, kakaoId: String, active: Boolean,
	): Place {
		val c = categories.findAll().first { it.code == code }
		return places.saveAndFlush(
			Place(
				category = c, name = name, address = "서울 종로구 테스트 1", roadAddress = "서울 종로구 테스트로 1",
				phone = null, lat = lat + dLat, lng = lng, radiusM = radius, notifyRadiusM = notify,
				rewardPoint = reward, kakaoPlaceId = kakaoId, kakaoPlaceUrl = "https://place.map.kakao.com/$kakaoId",
				hotType = hot, active = active,
			),
		)
	}

	private fun authed(url: String): ResultActionsDsl =
		mvc.get(url) { header("Authorization", "Bearer ${jwt.createAccessToken(7L)}") }

	private fun ResultActionsDsl.expect400(field: String? = null) = andExpect {
		status { isBadRequest() }
		jsonPath("$.code") { value("COMMON400") }
		if (field != null) jsonPath("$.data.errors[0].field") { value(field) }
	}

	private fun ResultActionsDsl.jsonKeys(pointer: String): List<String> {
		val body = andReturn().response.getContentAsString(Charsets.UTF_8)
		return mapper.readTree(body).at(pointer).propertyNames().toList()
	}

	// ---- /place-categories

	@Test
	@DisplayName("카테고리 목록: 8개, 명세 순서와 점수")
	fun categoriesOk() {
		authed("/place-categories").andExpect {
			status { isOk() }
			jsonPath("$.success") { value(true) }
			jsonPath("$.code") { value("COMMON200") }
			jsonPath("$.data.categories.length()") { value(8) }
			jsonPath("$.data.categories[0].code") { value("CAFE") }
			jsonPath("$.data.categories[0].name") { value("카페") }
			jsonPath("$.data.categories[0].rewardPoint") { value(100) }
			jsonPath("$.data.categories[7].code") { value("HERITAGE") }
			jsonPath("$.data.categories[7].rewardPoint") { value(300) }
		}
	}

	@Test
	@DisplayName("카테고리 목록: 토큰 없으면 AUTH401")
	fun categoriesUnauthenticated() {
		mvc.get("/place-categories").andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
	}

	// ---- /places

	@Test
	@DisplayName("주변 검색: 거리순 정렬, 반경 밖과 비활성 제외, 필드 값")
	fun searchOrdered() {
		authed("/places?lat=$lat&lng=$lng&radius=2000").andExpect {
			status { isOk() }
			jsonPath("$.data.places.length()") { value(3) }
			jsonPath("$.data.places[0].placeId") { value(nearId) }
			jsonPath("$.data.places[0].name") { value("가까움") }
			jsonPath("$.data.places[0].category") { value("PARK") }
			jsonPath("$.data.places[0].distance") { value(100) }
			jsonPath("$.data.places[0].radius") { value(300) }
			jsonPath("$.data.places[0].notifyRadius") { value(400) }
			jsonPath("$.data.places[0].rewardPoint") { value(100) }
			jsonPath("$.data.places[0].expectedPoint") { value(100) }
			jsonPath("$.data.places[0].isHot") { value(true) }
			jsonPath("$.data.places[0].hotType") { value("CURATED") }
			jsonPath("$.data.places[0].visitedBefore") { value(false) }
			jsonPath("$.data.places[0].isCompleted") { value(false) }
			jsonPath("$.data.places[1].placeId") { value(midId) }
			jsonPath("$.data.places[1].isHot") { value(false) }
			jsonPath("$.data.places[1].hotType") { value(null) }
			jsonPath("$.data.places[2].placeId") { value(farId) }
			jsonPath("$.data.places[2].rewardPoint") { value(150) }
			jsonPath("$.data.places[2].expectedPoint") { value(150) }
		}
	}

	@Test
	@DisplayName("JSON 키: 목록 항목은 명세 필드명과 정확히 같다(isHot, isCompleted가 hot, completed로 바뀌지 않는다)")
	fun summaryExactJsonKeys() {
		val keys = authed("/places?lat=$lat&lng=$lng&radius=2000").jsonKeys("/data/places/0")
		assertEquals(
			listOf(
				"placeId", "name", "category", "lat", "lng", "distance", "radius", "notifyRadius",
				"rewardPoint", "expectedPoint", "isHot", "hotType", "visitedBefore", "isCompleted",
			),
			keys,
		)
	}

	@Test
	@DisplayName("JSON 키: 상세는 명세 필드명과 정확히 같다")
	fun detailExactJsonKeys() {
		val keys = authed("/places/$farId").jsonKeys("/data")
		assertEquals(
			listOf(
				"placeId", "name", "category", "categoryName", "address", "roadAddress", "phone", "lat", "lng",
				"radius", "notifyRadius", "kakaoPlaceUrl", "rewardPoint", "expectedPoint", "photoBonusAvailable",
				"isHot", "hotType", "completedCount", "visitedBefore", "isCompleted", "myLastCompletedAt",
			),
			keys,
		)
	}

	@Test
	@DisplayName("JSON 키: 응답 래퍼와 카테고리 항목")
	fun envelopeAndCategoryKeys() {
		assertEquals(listOf("success", "code", "message", "data"), authed("/place-categories").jsonKeys(""))
		assertEquals(listOf("code", "name", "rewardPoint"), authed("/place-categories").jsonKeys("/data/categories/0"))
	}

	@Test
	@DisplayName("주변 검색: 기본 반경 1000m")
	fun defaultRadius() {
		authed("/places?lat=$lat&lng=$lng").andExpect {
			status { isOk() }
			jsonPath("$.data.places.length()") { value(2) }
		}
	}

	@Test
	@DisplayName("주변 검색: size로 개수 제한(가까운 순으로 자른다)")
	fun sizeLimit() {
		authed("/places?lat=$lat&lng=$lng&radius=2000&size=2").andExpect {
			jsonPath("$.data.places.length()") { value(2) }
			jsonPath("$.data.places[0].placeId") { value(nearId) }
			jsonPath("$.data.places[1].placeId") { value(midId) }
		}
	}

	@Test
	@DisplayName("주변 검색: 카테고리 필터(단일, 쉼표 여러 개)")
	fun categoryFilter() {
		authed("/places?lat=$lat&lng=$lng&radius=2000&category=CAFE")
			.andExpect { jsonPath("$.data.places.length()") { value(2) } }
		authed("/places?lat=$lat&lng=$lng&radius=2000&category=PARK,CAFE")
			.andExpect { jsonPath("$.data.places.length()") { value(3) } }
		authed("/places?lat=$lat&lng=$lng&radius=2000&category=MOVIE")
			.andExpect { jsonPath("$.data.places.length()") { value(0) } }
	}

	@Test
	@DisplayName("주변 검색: 결과가 없으면 빈 배열")
	fun empty() {
		authed("/places?lat=35.0&lng=129.0&radius=100").andExpect {
			status { isOk() }
			jsonPath("$.data.places.length()") { value(0) }
		}
	}

	@Test
	@DisplayName("주변 검색: excludeCompletedToday=true는 오늘 인증한 장소가 없으면 결과가 같다(placeholder)")
	fun excludeCompletedTodayAccepted() {
		authed("/places?lat=$lat&lng=$lng&radius=2000&excludeCompletedToday=true").andExpect {
			status { isOk() }
			jsonPath("$.data.places.length()") { value(3) }
		}
	}

	@Test
	@DisplayName("주변 검색: lat, lng 누락은 COMMON400 + 필드별 errors")
	fun missingLatLng() {
		authed("/places").andExpect {
			status { isBadRequest() }
			jsonPath("$.success") { value(false) }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors.length()") { value(2) }
			jsonPath("$.data.errors[0].field") { value("lat") }
			jsonPath("$.data.errors[0].reason") { exists() }
			jsonPath("$.data.errors[1].field") { value("lng") }
		}
	}

	@Test
	@DisplayName("주변 검색: 범위 오류(lat, lng, radius, size, 없는 category)는 COMMON400")
	fun rangeErrors() {
		val base = "/places?lat=37.5&lng=127.0"
		authed("/places?lat=91&lng=127.0").expect400("lat")
		authed("/places?lat=37.5&lng=-181").expect400("lng")
		authed("$base&radius=5001").expect400("radius")
		authed("$base&radius=0").expect400()
		authed("$base&size=201").expect400("size")
		authed("$base&size=0").expect400()
		authed("$base&category=NOPE").expect400("category")
		authed("$base&category=CAFE,").expect400()
		authed("$base&radius=5000&size=200").andExpect { status { isOk() } }
	}

	@Test
	@DisplayName("주변 검색: 숫자 형식 오류와 NaN은 COMMON400")
	fun typeErrors() {
		authed("/places?lat=abc&lng=127").expect400()
		authed("/places?lat=NaN&lng=127").expect400()
		authed("/places?lat=37&lng=127&excludeCompletedToday=maybe").expect400()
	}

	@Test
	@DisplayName("주변 검색: 토큰 없으면 AUTH401")
	fun searchUnauthenticated() {
		mvc.get("/places?lat=37.5&lng=127.0").andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
	}

	// ---- /places/{id}

	@Test
	@DisplayName("상세: 명세 필드와 장소 점수가 카테고리 점수를 덮는다")
	fun detailOk() {
		authed("/places/$farId").andExpect {
			status { isOk() }
			jsonPath("$.data.placeId") { value(farId) }
			jsonPath("$.data.name") { value("멀다") }
			jsonPath("$.data.category") { value("CAFE") }
			jsonPath("$.data.categoryName") { value("카페") }
			jsonPath("$.data.address") { value("서울 종로구 테스트 1") }
			jsonPath("$.data.roadAddress") { value("서울 종로구 테스트로 1") }
			jsonPath("$.data.phone") { value(null) }
			jsonPath("$.data.radius") { value(100) }
			jsonPath("$.data.notifyRadius") { value(200) }
			jsonPath("$.data.kakaoPlaceUrl") { value("https://place.map.kakao.com/k-far") }
			jsonPath("$.data.rewardPoint") { value(150) }
			jsonPath("$.data.expectedPoint") { value(150) }
			jsonPath("$.data.photoBonusAvailable") { value(true) }
			jsonPath("$.data.isHot") { value(false) }
			jsonPath("$.data.hotType") { value(null) }
			jsonPath("$.data.completedCount") { value(0) }
			jsonPath("$.data.visitedBefore") { value(false) }
			jsonPath("$.data.isCompleted") { value(false) }
			jsonPath("$.data.myLastCompletedAt") { value(null) }
		}
	}

	@Test
	@DisplayName("상세: 장소 점수가 없으면 카테고리 점수, CURATED 표시")
	fun detailCategoryReward() {
		authed("/places/$nearId").andExpect {
			status { isOk() }
			jsonPath("$.data.rewardPoint") { value(100) }
			jsonPath("$.data.hotType") { value("CURATED") }
			jsonPath("$.data.isHot") { value(true) }
		}
	}

	@Test
	@DisplayName("상세: 없는 장소와 비활성 장소는 404 PLACE404")
	fun detailNotFound() {
		authed("/places/99999999").andExpect {
			status { isNotFound() }
			jsonPath("$.success") { value(false) }
			jsonPath("$.code") { value("PLACE404") }
			jsonPath("$.message") { value("존재하지 않는 장소") }
		}
		authed("/places/$inactiveId").andExpect {
			status { isNotFound() }
			jsonPath("$.code") { value("PLACE404") }
		}
	}

	@Test
	@DisplayName("상세: 숫자가 아닌 id는 COMMON400")
	fun detailBadId() {
		authed("/places/abc").expect400()
	}

	@Test
	@DisplayName("상세: 토큰 없으면 AUTH401")
	fun detailUnauthenticated() {
		mvc.get("/places/$nearId").andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
	}
}
