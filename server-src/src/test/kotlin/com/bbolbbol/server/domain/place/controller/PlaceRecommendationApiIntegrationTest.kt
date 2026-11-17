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
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AllUsersActiveConfig::class)
@Transactional
class PlaceRecommendationApiIntegrationTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository

	private val lat = 37.5759
	private val lng = 126.9768
	private val mapper = JsonMapper.builder().build()
	private val url = "/places/recommendations?lat=$lat&lng=$lng"

	private fun save(
		code: String, name: String, dLat: Double, hot: HotType?,
		active: Boolean = true, reward: Int? = null,
	): Place = places.saveAndFlush(
		Place(
			category = categories.findAll().first { it.code == code }, name = name, address = "서울 종로구 테스트 1",
			roadAddress = null, phone = null, lat = lat + dLat, lng = lng, radiusM = 120, notifyRadiusM = 250,
			rewardPoint = reward, kakaoPlaceId = "r-$name", kakaoPlaceUrl = "https://place.map.kakao.com/r-$name",
			hotType = hot, active = active,
		),
	)

	@BeforeEach
	fun fixtures() {
		save("CAFE", "중간추천", 0.0045, HotType.CURATED)
		save("PARK", "가까운추천", 0.0009, HotType.CURATED, reward = 300)
		save("CAFE", "멀리추천", 0.0135, HotType.CURATED)
		save("CAFE", "일반", 0.0001, null)
		save("CAFE", "폐업추천", 0.0002, HotType.CURATED, active = false)
	}

	private fun authed(u: String): ResultActionsDsl =
		mvc.get(u) { header("Authorization", "Bearer ${jwt.createAccessToken(7L)}") }

	private fun ResultActionsDsl.body() =
		mapper.readTree(andReturn().response.getContentAsString(Charsets.UTF_8))

	@Test
	@DisplayName("CURATED 활성 장소만, 거리순으로 돌려준다")
	fun onlyCuratedByDistance() {
		val r = authed("$url&size=10")
		r.andExpect {
			status { isOk() }
			jsonPath("$.success") { value(true) }
			jsonPath("$.code") { value("COMMON200") }
		}
		val ps = r.body().at("/data/places").toList()
		// 테스트 프로파일에 시드가 없다면 3곳, 있어도 우리가 만든 곳의 상대 순서는 거리순이다.
		val mine = ps.map { it.get("name").asString() }.filter { it.endsWith("추천") }
		assertEquals(listOf("가까운추천", "중간추천", "멀리추천"), mine)
		assertTrue(ps.none { it.get("name").asString() in setOf("일반", "폐업추천") })
		ps.forEach { assertEquals("CURATED", it.get("hotType").asString()) }
	}

	@Test
	@DisplayName("JSON 키: 장소 요약 필드 + reason, isHot/isCompleted 키 고정")
	fun exactKeys() {
		val first = authed("$url&size=10").body().at("/data/places").first { it.get("name").asString() == "가까운추천" }
		assertEquals(
			listOf(
				"placeId", "name", "category", "lat", "lng", "distance", "radius", "notifyRadius", "rewardPoint",
				"expectedPoint", "isHot", "hotType", "visitedBefore", "isCompleted", "reason",
			),
			first.propertyNames().toList(),
		)
		assertEquals(300, first.get("rewardPoint").asInt())
		assertEquals(300, first.get("expectedPoint").asInt())
		assertEquals(true, first.get("isHot").asBoolean())
		assertEquals(false, first.get("isCompleted").asBoolean())
		assertEquals(120, first.get("radius").asInt())
		assertEquals(250, first.get("notifyRadius").asInt())
		assertEquals(100L, first.get("distance").asLong())
		assertTrue(first.get("reason").asString().isNotBlank())
	}

	@Test
	@DisplayName("size 기본 5 이하, size=1이면 1개")
	fun sizeLimits() {
		assertTrue(authed(url).body().at("/data/places").size() <= 5)
		assertEquals(1, authed("$url&size=1").body().at("/data/places").size())
	}

	@Test
	@DisplayName("추천 후보가 없으면 빈 배열")
	fun empty() {
		places.deleteAll()
		places.flush()
		authed(url).andExpect {
			status { isOk() }
			jsonPath("$.data.places") { isArray() }
			jsonPath("$.data.places") { isEmpty() }
		}
	}

	@Test
	@DisplayName("lat 누락, lng 범위 오류, size 0/11/문자는 COMMON400")
	fun validation() {
		authed("/places/recommendations?lng=$lng").andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors[0].field") { value("lat") }
		}
		authed("/places/recommendations?lat=$lat&lng=181").andExpect {
			status { isBadRequest() }
			jsonPath("$.data.errors[0].field") { value("lng") }
		}
		listOf(0, 11).forEach {
			authed("$url&size=$it").andExpect {
				status { isBadRequest() }
				jsonPath("$.code") { value("COMMON400") }
				jsonPath("$.data.errors[0].field") { value("size") }
			}
		}
		authed("$url&size=abc").andExpect { status { isBadRequest() } }
	}

	@Test
	@DisplayName("토큰이 없거나 잘못되면 AUTH401")
	fun unauthorized() {
		mvc.get(url).andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
		mvc.get(url) { header("Authorization", "Bearer bad") }.andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
	}

	@Test
	@DisplayName("/places/{placeId}와 경로가 겹치지 않는다")
	fun routeDoesNotClash() {
		authed(url).andExpect { status { isOk() } }
		authed("/places/999999").andExpect {
			status { isNotFound() }
			jsonPath("$.code") { value("PLACE404") }
		}
	}
}
