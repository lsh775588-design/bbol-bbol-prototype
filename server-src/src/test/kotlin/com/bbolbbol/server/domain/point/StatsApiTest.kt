package com.bbolbbol.server.domain.point

import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.DbFixture
import com.bbolbbol.server.support.arr
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/** GET /users/me/stats. 시계는 2026-11-10(화) 12:00 KST. 그 주는 11-09(월) ~ 11-15(일). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class)
class StatsApiTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var clock: MutableClock

	private lateinit var fx: DbFixture
	private val mapper = JsonMapper.builder().build()
	private var me = 0L
	private var cafe = 0L
	private var heritage = 0L
	private var park = 0L

	@BeforeEach
	fun setUp() {
		fx = DbFixture(jdbc, userService, places, categories)
		clock.setKst("2026-11-10T12:00:00")
		me = fx.user("통계러")
		cafe = fx.place("CAFE")
		heritage = fx.place("HERITAGE")
		park = fx.place("PARK")
	}

	@AfterEach
	fun cleanUp() = fx.cleanUp()

	private fun at(text: String) = LocalDateTime.parse(text)

	private fun stats(query: String, id: Long = me): JsonNode {
		val res = mvc.perform(get("/users/me/stats?$query").header("Authorization", "Bearer ${jwt.createAccessToken(id)}"))
			.andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)
		return mapper.readTree(res).at("/data")
	}

	private fun statsRaw(query: String, token: String? = "Bearer ${jwt.createAccessToken(me)}") =
		mvc.perform(get("/users/me/stats$query").also { if (token != null) it.header("Authorization", token) })

	@Test
	@DisplayName("활동이 없는 사용자: 200, 0과 빈 categories, 0으로 채운 7칸 timeline(월~일), 정확한 키")
	fun emptyWeek() {
		val d = stats("period=WEEK")
		assertThat(d.propertyNames().toList()).containsExactlyInAnyOrder(
			"period", "startDate", "endDate", "completionCount", "totalPoint", "placeCount", "previous", "categories", "timeline",
		)
		assertThat(d.at("/period").asText()).isEqualTo("WEEK")
		assertThat(d.at("/startDate").asText()).isEqualTo("2026-11-09")
		assertThat(d.at("/endDate").asText()).isEqualTo("2026-11-15")
		assertThat(d.at("/completionCount").asInt()).isZero()
		assertThat(d.at("/totalPoint").asLong()).isZero()
		assertThat(d.at("/placeCount").asInt()).isZero()
		assertThat(d.at("/previous").propertyNames().toList()).containsExactlyInAnyOrder("completionCount", "totalPoint")
		assertThat(d.at("/categories").size()).isZero()
		assertThat(d.at("/timeline").size()).isEqualTo(7)
		assertThat(d.at("/timeline").arr().map { it.at("/label").asText() }).containsExactly(
			"2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-15",
		)
		assertThat(d.at("/timeline").arr().all { it.at("/count").asInt() == 0 && it.at("/point").asLong() == 0L }).isTrue()
		assertThat(d.at("/timeline/0").propertyNames().toList()).containsExactlyInAnyOrder("label", "count", "point")
	}

	@Test
	@DisplayName("혼합 데이터: 23:59와 00:01은 다른 날, 주 경계(일요일 밤은 지난주), 사진 보너스는 적립한 날에 점수만 더한다")
	fun mixedWeek() {
		// 지난주 일요일 23:59:59 -> 이번 주에 들어오면 안 된다
		fx.completion(me, cafe, at("2026-11-08T23:59:59"), 100)
		// 이번 주 월요일 00:00:00 (주의 첫 순간), 월요일 23:59:30, 화요일 00:01
		fx.completion(me, heritage, at("2026-11-09T00:00:00"), 300)
		fx.completion(me, cafe, at("2026-11-09T23:59:30"), 100, photoBonusAt = at("2026-11-10T00:10:00"))
		fx.completion(me, park, at("2026-11-10T00:01:00"), 150)
		// 같은 장소를 다른 날 또 방문(재방문 50%)
		fx.completion(me, cafe, at("2026-11-12T10:00:00"), 50, firstVisit = false)

		val d = stats("period=WEEK")
		assertThat(d.at("/completionCount").asInt()).isEqualTo(4)
		assertThat(d.at("/totalPoint").asLong()).isEqualTo(300 + 100 + 50 + 150 + 50)
		assertThat(d.at("/placeCount").asInt()).isEqualTo(3)
		assertThat(d.at("/previous/completionCount").asInt()).isEqualTo(1)
		assertThat(d.at("/previous/totalPoint").asLong()).isEqualTo(100)

		val t = d.at("/timeline")
		fun bar(i: Int) = Triple(t.at("/$i/label").asText(), t.at("/$i/count").asInt(), t.at("/$i/point").asLong())
		assertThat(bar(0)).isEqualTo(Triple("2026-11-09", 2, 400L)) // 월: 00:00 + 23:59:30
		assertThat(bar(1)).isEqualTo(Triple("2026-11-10", 1, 200L)) // 화: 00:01(150) + 사진 보너스(50, 카운트 아님)
		assertThat(bar(2)).isEqualTo(Triple("2026-11-11", 0, 0L))
		assertThat(bar(3)).isEqualTo(Triple("2026-11-12", 1, 50L))

		// 카테고리: 횟수 많은 순, 같으면 점수 많은 순. CAFE 2회(100 + 50 + 보너스 50 = 200), HERITAGE 1회 300, PARK 1회 150
		val c = d.at("/categories")
		assertThat(c.arr().map { it.at("/category").asText() }).containsExactly("CAFE", "HERITAGE", "PARK")
		assertThat(c.arr().map { it.at("/count").asInt() }).containsExactly(2, 1, 1)
		assertThat(c.arr().map { it.at("/point").asLong() }).containsExactly(200L, 300L, 150L)
		assertThat(c.at("/0").propertyNames().toList()).containsExactlyInAnyOrder("category", "count", "point")
		// 카테고리 count 합 = completionCount, point 합 = totalPoint
		assertThat(c.arr().sumOf { it.at("/count").asInt() }).isEqualTo(4)
		assertThat(c.arr().sumOf { it.at("/point").asLong() }).isEqualTo(d.at("/totalPoint").asLong())
	}

	@Test
	@DisplayName("date를 주면 그 날이 속한 주: 일요일(11-08)은 지난주 11-02~11-08")
	fun dateSelectsWeek() {
		fx.completion(me, cafe, at("2026-11-08T23:59:59"), 100)
		val d = stats("period=WEEK&date=2026-11-08")
		assertThat(d.at("/startDate").asText()).isEqualTo("2026-11-02")
		assertThat(d.at("/endDate").asText()).isEqualTo("2026-11-08")
		assertThat(d.at("/completionCount").asInt()).isEqualTo(1)
		assertThat(d.at("/timeline/6/label").asText()).isEqualTo("2026-11-08")
		assertThat(d.at("/timeline/6/count").asInt()).isEqualTo(1)
		// 프론트의 [< 이전]은 startDate - 1일
		assertThat(stats("period=WEEK&date=2026-11-01").at("/startDate").asText()).isEqualTo("2026-10-26")
	}

	@Test
	@DisplayName("MONTH: 11월은 30칸, 윤년 2월은 29칸, 이전 기간 비교")
	fun month() {
		fx.completion(me, cafe, at("2026-10-31T23:59:59"), 100)
		fx.completion(me, heritage, at("2026-11-01T00:00:00"), 300)
		val d = stats("period=MONTH")
		assertThat(d.at("/startDate").asText()).isEqualTo("2026-11-01")
		assertThat(d.at("/endDate").asText()).isEqualTo("2026-11-30")
		assertThat(d.at("/timeline").size()).isEqualTo(30)
		assertThat(d.at("/timeline/0/count").asInt()).isEqualTo(1)
		assertThat(d.at("/completionCount").asInt()).isEqualTo(1)
		assertThat(d.at("/previous/completionCount").asInt()).isEqualTo(1)
		assertThat(d.at("/previous/totalPoint").asLong()).isEqualTo(100)

		val feb = stats("period=MONTH&date=2024-02-10")
		assertThat(feb.at("/timeline").size()).isEqualTo(29)
		assertThat(feb.at("/endDate").asText()).isEqualTo("2024-02-29")
	}

	@Test
	@DisplayName("YEAR: 12칸이고 label은 yyyy-MM, 달별 합계")
	fun year() {
		fx.completion(me, cafe, at("2026-01-01T00:00:00"), 100)
		fx.completion(me, heritage, at("2026-11-02T10:00:00"), 300)
		fx.completion(me, park, at("2025-12-31T23:59:59"), 150)
		val d = stats("period=YEAR")
		assertThat(d.at("/startDate").asText()).isEqualTo("2026-01-01")
		assertThat(d.at("/endDate").asText()).isEqualTo("2026-12-31")
		assertThat(d.at("/timeline").arr().map { it.at("/label").asText() }).containsExactly(
			"2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12",
		)
		assertThat(d.at("/timeline/0/count").asInt()).isEqualTo(1)
		assertThat(d.at("/timeline/10/point").asLong()).isEqualTo(300)
		assertThat(d.at("/completionCount").asInt()).isEqualTo(2)
		assertThat(d.at("/previous/completionCount").asInt()).isEqualTo(1)
	}

	@Test
	@DisplayName("다른 사용자의 기록은 섞이지 않는다")
	fun onlyMine() {
		val other = fx.user("남")
		fx.completion(other, cafe, at("2026-11-10T09:00:00"), 100)
		assertThat(stats("period=WEEK").at("/completionCount").asInt()).isZero()
		assertThat(stats("period=WEEK", other).at("/completionCount").asInt()).isEqualTo(1)
	}

	@Test
	@DisplayName("COMMON400: period 누락, 없는 값(DAY, 소문자), date 형식 오류, 미래 날짜. 오늘은 허용")
	fun validation() {
		statsRaw("").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		statsRaw("?period=DAY").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		statsRaw("?period=week").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		statsRaw("?period=WEEK&date=2026/11/10").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		statsRaw("?period=WEEK&date=2026-13-40").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		statsRaw("?period=WEEK&date=2026-11-11").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("date"))
		statsRaw("?period=WEEK&date=2026-11-10").andExpect(status().isOk)
	}

	@Test
	@DisplayName("AUTH401: 토큰 없음")
	fun unauthorized() {
		statsRaw("?period=WEEK", token = null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}
}
