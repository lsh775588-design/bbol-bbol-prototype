package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.MutableClockConfig
import com.bbolbbol.server.support.MutableClockConfig.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 도메인 통합 검증: 진짜 completion provider와 진짜 recommendations 서비스가 함께 동작한다(H2, 고정 가능한 Clock).
 * 추천 순서(명세): 안 가본 곳(거리순) -> 가봤지만 오늘은 안 한 곳 -> 오늘 인증한 곳.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class)
class RecommendationCompletionIntegrationTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var clock: MutableClock

	private val mapper = JsonMapper.builder().build()
	private val baseLat = 37.5759
	private val baseLng = 126.9768
	private val createdPlaces = mutableListOf<Long>()
	private var userId = 0L
	private val dLat = mutableMapOf<Long, Double>()

	private var todayId = 0L // 가장 가깝고 오늘 인증한 곳
	private var earlierId = 0L // 어제 인증한 곳
	private var nearUnvisitedId = 0L
	private var farUnvisitedId = 0L

	@BeforeEach
	fun setUp() {
		clock.setKst("2026-11-09T12:00:00")
		userId = userService.createEmailUser("rc${System.nanoTime() % 1000000}@example.com", "x", "rc${System.nanoTime() % 100000}").id!!
		jdbc.update("UPDATE users SET onboarding_completed = TRUE WHERE id = ?", userId) // A4: 온보딩 완료 사용자만 도착 인증
		todayId = place("오늘간곳", 0.0009)
		nearUnvisitedId = place("가까운안간곳", 0.0045)
		earlierId = place("전에간곳", 0.0090)
		farUnvisitedId = place("먼안간곳", 0.0135)
	}

	@AfterEach
	fun cleanUp() {
		jdbc.update("DELETE FROM point_histories WHERE user_id = ?", userId)
		jdbc.update("DELETE FROM completions WHERE user_id = ?", userId)
		createdPlaces.forEach { jdbc.update("DELETE FROM places WHERE id = ?", it) }
		jdbc.update("DELETE FROM users WHERE id = ?", userId)
		createdPlaces.clear()
	}

	private fun place(name: String, d: Double): Long {
		val p = places.saveAndFlush(
			Place(
				category = categories.findAll().first { it.code == "CAFE" }, name = name, address = "서울 종로구 $name 1",
				roadAddress = null, phone = null, lat = baseLat + d, lng = baseLng, radiusM = 120, notifyRadiusM = 250,
				rewardPoint = null, kakaoPlaceId = "rc-$name-${System.nanoTime()}", kakaoPlaceUrl = "https://example.com/$name",
				hotType = HotType.CURATED, active = true,
			),
		)
		return p.id!!.also { createdPlaces += it; dLat[it] = d }
	}

	private val auth get() = "Bearer ${jwt.createAccessToken(userId)}"

	private fun completeAt(placeId: Long): ResultActions = mvc.perform(
		post("/places/$placeId/completions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
			.content("""{"lat":${baseLat + dLat.getValue(placeId)},"lng":$baseLng,"accuracy":8.0,"isMockLocation":false}"""),
	)

	private fun json(r: ResultActions): JsonNode = mapper.readTree(r.andReturn().response.getContentAsString(Charsets.UTF_8))

	private fun recommended(): List<JsonNode> =
		json(mvc.perform(get("/places/recommendations?lat=$baseLat&lng=$baseLng&size=10").header("Authorization", auth)).andExpect(status().isOk))
			.at("/data/places").toList().filter { it.at("/placeId").asLong() in createdPlaces }

	@Test
	@DisplayName("추천: 안 가본 곳(거리순) -> 전에 간 곳 -> 오늘 인증한 곳 순서이고 /places 상태도 같이 바뀐다")
	fun recommendationsFollowCompletion() {
		// 인증 전: 거리순(오늘간곳이 가장 가깝다)
		assertThat(recommended().map { it.at("/placeId").asLong() })
			.containsExactly(todayId, nearUnvisitedId, earlierId, farUnvisitedId)

		completeAt(earlierId).andExpect(status().isCreated) // 11/09
		clock.setKst("2026-11-10T12:00:00")
		completeAt(todayId).andExpect(status().isCreated) // 11/10

		val recs = recommended()
		assertThat(recs.map { it.at("/placeId").asLong() })
			.containsExactly(nearUnvisitedId, farUnvisitedId, earlierId, todayId)
		fun flags(id: Long) = recs.first { it.at("/placeId").asLong() == id }.let {
			Triple(it.at("/visitedBefore").asBoolean(), it.at("/isCompleted").asBoolean(), it.at("/expectedPoint").asInt())
		}
		assertThat(flags(nearUnvisitedId)).isEqualTo(Triple(false, false, 100))
		assertThat(flags(farUnvisitedId)).isEqualTo(Triple(false, false, 100))
		assertThat(flags(earlierId)).isEqualTo(Triple(true, false, 50))
		assertThat(flags(todayId)).isEqualTo(Triple(true, true, 50))

		// /places도 같은 provider를 쓴다
		val list = json(mvc.perform(get("/places?lat=$baseLat&lng=$baseLng&radius=3000").header("Authorization", auth)).andExpect(status().isOk))
			.at("/data/places").toList().first { it.at("/placeId").asLong() == todayId }
		assertThat(list.at("/visitedBefore").asBoolean()).isTrue()
		assertThat(list.at("/isCompleted").asBoolean()).isTrue()

		// 다음 날이 되면 어제 인증한 곳은 "오늘 한 곳"이 아니므로 가본 곳 그룹으로 올라온다(거리순)
		clock.setKst("2026-11-11T09:00:00")
		assertThat(recommended().map { it.at("/placeId").asLong() })
			.containsExactly(nearUnvisitedId, farUnvisitedId, todayId, earlierId)
	}
}
