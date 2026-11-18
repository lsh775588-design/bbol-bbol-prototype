package com.bbolbbol.server.domain.ranking

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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * 랭킹 3종. 시계는 2026-11-10(화) 12:00 KST. 이번 주는 11-09(월) ~ 11-15(일), 이번 달은 11-01 ~ 11-30.
 * 랭킹은 DB 전체를 읽으므로 각 테스트가 만든 데이터를 반드시 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class)
class RankingApiTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var clock: MutableClock

	private lateinit var fx: DbFixture
	private val mapper = JsonMapper.builder().build()

	@BeforeEach
	fun setUp() {
		fx = DbFixture(jdbc, userService, places, categories)
		clock.setKst("2026-11-10T12:00:00")
	}

	@AfterEach
	fun cleanUp() = fx.cleanUp()

	private fun at(text: String) = LocalDateTime.parse(text)

	private fun token(id: Long) = "Bearer ${jwt.createAccessToken(id)}"

	private fun call(url: String, id: Long?): ResultActions =
		mvc.perform(get(url).also { if (id != null) it.header("Authorization", token(id)) })

	private fun ok(url: String, id: Long): JsonNode =
		mapper.readTree(call(url, id).andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)).at("/data")

	/** 이번 주(화요일)에 한 장소를 한 번 방문하고 point점을 받은 사용자 */
	private fun earner(nickname: String, point: Int, count: Int = 1, onboarded: Boolean = true, withdrawn: Boolean = false): Long {
		val u = fx.user(nickname, onboarded, withdrawn)
		repeat(count) {
			// 방문마다 다른 장소(같은 날 같은 장소 유니크 회피). 한 번에 point를 나눠 담지 않고 첫 방문에 몰아준다.
			fx.completion(u, fx.place("CAFE"), at("2026-11-10T09:00:00").plusMinutes(it.toLong()), if (it == 0) point else 0)
		}
		return u
	}

	// ---- /rankings/users

	@Test
	@DisplayName("유저 랭킹: 점수 순, 같은 점수는 같은 순위 다음은 건너뜀(1,2,2,4), 정확한 키, 다른 사용자 식별 정보 없음")
	fun userRankingOrderAndTies() {
		earner("뽈뽈왕", 2400, 3)
		earner("산책러", 1900, 2)
		earner("도서관요정", 1900, 1)
		val meId = earner("나", 1100, 2)

		val d = ok("/rankings/users?period=WEEK", meId)
		assertThat(d.propertyNames().toList()).containsExactlyInAnyOrder("period", "startDate", "endDate", "rankings", "me", "updatedAt")
		assertThat(d.at("/period").asText()).isEqualTo("WEEK")
		assertThat(d.at("/startDate").asText()).isEqualTo("2026-11-09")
		assertThat(d.at("/endDate").asText()).isEqualTo("2026-11-15")
		assertThat(d.at("/updatedAt").asText()).isEqualTo("2026-11-10T12:00:00")

		val r = d.at("/rankings")
		assertThat(r.arr().map { it.at("/rank").asInt() }).containsExactly(1, 2, 2, 4)
		assertThat(r.arr().map { it.at("/nickname").asText() }).containsExactly("뽈뽈왕", "산책러", "도서관요정", "나")
		assertThat(r.arr().map { it.at("/point").asLong() }).containsExactly(2400L, 1900L, 1900L, 1100L)
		// 같은 점수는 완료 수 많은 순으로 표시한다(산책러 2회 > 도서관요정 1회)
		assertThat(r.arr().map { it.at("/completionCount").asInt() }).containsExactly(3, 2, 1, 2)
		assertThat(r.arr().map { it.at("/isMe").asBoolean() }).containsExactly(false, false, false, true)
		assertThat(r.at("/0").propertyNames().toList()).containsExactlyInAnyOrder("rank", "nickname", "point", "completionCount", "isMe")

		assertThat(d.at("/me").propertyNames().toList()).containsExactlyInAnyOrder("rank", "nickname", "point", "completionCount")
		assertThat(d.at("/me/rank").asInt()).isEqualTo(4)
		assertThat(d.at("/me/nickname").asText()).isEqualTo("나")
		assertThat(d.at("/me/point").asLong()).isEqualTo(1100)
		assertThat(d.at("/me/completionCount").asInt()).isEqualTo(2)

		val raw = call("/rankings/users?period=WEEK", meId).andReturn().response.getContentAsString(Charsets.UTF_8)
		assertThat(raw).doesNotContain("userId", "email", "@example.com")
	}

	@Test
	@DisplayName("size로 상위만 자르고, 내가 밖에 있어도 me에는 순위가 들어 있다")
	fun userRankingSizeAndMeOutsideTop() {
		earner("일등", 500)
		earner("이등", 400)
		earner("삼등", 300)
		val meId = earner("꼴등", 100)

		val d = ok("/rankings/users?period=WEEK&size=2", meId)
		assertThat(d.at("/rankings").arr().map { it.at("/nickname").asText() }).containsExactly("일등", "이등")
		assertThat(d.at("/rankings").arr().none { it.at("/isMe").asBoolean() }).isTrue()
		assertThat(d.at("/me/rank").asInt()).isEqualTo(4)
		assertThat(d.at("/me/nickname").asText()).isEqualTo("꼴등")
	}

	@Test
	@DisplayName("탈퇴한 사용자와 온보딩을 마치지 않은 사용자는 랭킹에서 빠지고 순위가 그만큼 당겨진다")
	fun userRankingExcludesWithdrawnAndNotOnboarded() {
		earner("탈퇴자", 9000, withdrawn = true)
		earner("미온보딩", 8000, onboarded = false)
		earner("정상일등", 700)
		val meId = earner("정상이등", 600)

		val d = ok("/rankings/users?period=WEEK", meId)
		assertThat(d.at("/rankings").arr().map { it.at("/nickname").asText() }).containsExactly("정상일등", "정상이등")
		assertThat(d.at("/rankings").arr().map { it.at("/rank").asInt() }).containsExactly(1, 2)
		assertThat(d.at("/me/rank").asInt()).isEqualTo(2)
	}

	@Test
	@DisplayName("me는 기간 내 활동이 없으면 null, 미온보딩 사용자가 조회해도 null")
	fun userRankingMeNull() {
		earner("누군가", 100)
		val idle = fx.user("놀고있음")
		assertThat(ok("/rankings/users?period=WEEK", idle).at("/me").isNull).isTrue()
		assertThat(ok("/rankings/users?period=WEEK", idle).at("/rankings").size()).isEqualTo(1)

		val notOnboarded = earner("미온보딩", 5000, onboarded = false)
		assertThat(ok("/rankings/users?period=WEEK", notOnboarded).at("/me").isNull).isTrue()
	}

	@Test
	@DisplayName("기간: DAY는 오늘만, WEEK는 월요일부터(일요일 밤은 제외), MONTH는 1일부터. 적립 시각 기준(보너스는 적립한 날)")
	fun userRankingPeriods() {
		val u = fx.user("기간러")
		val p = { fx.place("CAFE") }
		fx.completion(u, p(), at("2026-11-10T00:00:00"), 100) // 오늘 0시 정각: DAY 포함
		fx.completion(u, p(), at("2026-11-09T23:59:59"), 200) // 어제(월): WEEK, MONTH
		fx.completion(u, p(), at("2026-11-08T23:59:59"), 400) // 지난주 일요일: MONTH만
		fx.completion(u, p(), at("2026-10-31T23:59:59"), 800) // 지난달: 어디에도 없음
		// 어제(월) 인증의 보너스를 오늘 적립 -> DAY에 50이 더해진다
		fx.completion(u, p(), at("2026-11-09T10:00:00"), 0, photoBonusAt = at("2026-11-10T11:00:00"))

		assertThat(ok("/rankings/users?period=DAY", u).at("/me/point").asLong()).isEqualTo(100 + 50)
		assertThat(ok("/rankings/users?period=DAY", u).at("/startDate").asText()).isEqualTo("2026-11-10")
		assertThat(ok("/rankings/users?period=DAY", u).at("/endDate").asText()).isEqualTo("2026-11-10")
		assertThat(ok("/rankings/users?period=WEEK", u).at("/me/point").asLong()).isEqualTo(100 + 200 + 50)
		assertThat(ok("/rankings/users?period=MONTH", u).at("/me/point").asLong()).isEqualTo(100 + 200 + 400 + 50)
		assertThat(ok("/rankings/users?period=MONTH", u).at("/startDate").asText()).isEqualTo("2026-11-01")
		assertThat(ok("/rankings/users?period=MONTH", u).at("/endDate").asText()).isEqualTo("2026-11-30")
		// 완료 수는 QUEST_COMPLETE 줄만 센다(보너스 제외): 오늘 1건 + 어제(월) 2건 = 3
		assertThat(ok("/rankings/users?period=WEEK", u).at("/me/completionCount").asInt()).isEqualTo(3)
	}

	@Test
	@DisplayName("월요일 아침 0시에 주가 바뀐다")
	fun userRankingMondayRollover() {
		val u = fx.user("월요일")
		fx.completion(u, fx.place("CAFE"), at("2026-11-08T22:00:00"), 100) // 일요일 밤
		clock.setKst("2026-11-08T23:59:59")
		assertThat(ok("/rankings/users?period=WEEK", u).at("/me/point").asLong()).isEqualTo(100)
		clock.setKst("2026-11-09T00:00:00")
		assertThat(ok("/rankings/users?period=WEEK", u).at("/me").isNull).isTrue()
		assertThat(ok("/rankings/users?period=WEEK", u).at("/startDate").asText()).isEqualTo("2026-11-09")
	}

	@Test
	@DisplayName("유저 랭킹 COMMON400: period 누락, 없는 값, size 0과 101. 기본 50, 최대 100은 허용. AUTH401")
	fun userRankingValidation() {
		val u = fx.user("검증")
		call("/rankings/users", u).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		call("/rankings/users?period=YEAR", u).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		call("/rankings/users?period=WEEK&size=0", u).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		call("/rankings/users?period=WEEK&size=101", u).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("size"))
		call("/rankings/users?period=WEEK&size=abc", u).andExpect(status().isBadRequest)
		call("/rankings/users?period=WEEK&size=100", u).andExpect(status().isOk)
		call("/rankings/users?period=WEEK&size=1", u).andExpect(status().isOk)
		call("/rankings/users?period=WEEK", null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	// ---- /rankings/places

	@Test
	@DisplayName("인기 장소: 인증 횟수 순, 같으면 서로 다른 사용자 수 순, 키와 rewardPoint, 탈퇴 사용자의 기록도 센다")
	fun placeRanking() {
		val a = fx.user("에이")
		val b = fx.user("비")
		val gone = fx.user("탈퇴", withdrawn = true)
		val hot = fx.place("CAFE", "핫플", reward = 120)
		val two = fx.place("CAFE", "둘이서")
		val one = fx.place("HERITAGE", "혼자서")
		// hot: 3회(서로 다른 3명, 탈퇴자 포함)
		fx.completion(a, hot, at("2026-11-10T09:00:00"))
		fx.completion(b, hot, at("2026-11-10T10:00:00"))
		fx.completion(gone, hot, at("2026-11-10T11:00:00"))
		// two: 2회, 한 명이 이틀 -> 횟수 2, 사용자 1
		fx.completion(a, two, at("2026-11-09T09:00:00"))
		fx.completion(a, two, at("2026-11-10T09:00:00"))
		// one: 2회, 서로 다른 2명 -> two와 횟수는 같지만 사용자 수가 많아 앞선다
		fx.completion(a, one, at("2026-11-09T09:00:00"))
		fx.completion(b, one, at("2026-11-10T09:00:00"))

		val d = ok("/rankings/places", a) // period 기본 WEEK
		assertThat(d.propertyNames().toList()).containsExactlyInAnyOrder("period", "startDate", "endDate", "rankings", "updatedAt")
		assertThat(d.at("/period").asText()).isEqualTo("WEEK")
		val r = d.at("/rankings")
		assertThat(r.arr().map { it.at("/placeId").asLong() }).containsExactly(hot, one, two)
		assertThat(r.arr().map { it.at("/rank").asInt() }).containsExactly(1, 2, 3)
		assertThat(r.arr().map { it.at("/completionCount").asInt() }).containsExactly(3, 2, 2)
		assertThat(r.at("/0").propertyNames().toList()).containsExactlyInAnyOrder(
			"rank", "placeId", "placeName", "category", "address", "completionCount", "rewardPoint",
		)
		assertThat(r.at("/0/category").asText()).isEqualTo("CAFE")
		assertThat(r.at("/0/rewardPoint").asInt()).isEqualTo(120)
		assertThat(r.at("/1/category").asText()).isEqualTo("HERITAGE")
		assertThat(r.at("/1/rewardPoint").asInt()).isEqualTo(300)
		assertThat(r.at("/0/placeName").asText()).startsWith("핫플")
	}

	@Test
	@DisplayName("인기 장소: 횟수와 사용자 수가 모두 같으면 같은 순위(표시 순서는 placeId)")
	fun placeRankingTies() {
		val a = fx.user("에이")
		val p1 = fx.place("CAFE")
		val p2 = fx.place("CAFE")
		val p3 = fx.place("CAFE")
		fx.completion(a, p1, at("2026-11-10T09:00:00"))
		fx.completion(a, p2, at("2026-11-10T09:00:00"))
		fx.completion(fx.user("비"), p2, at("2026-11-10T09:00:00"))
		fx.completion(fx.user("씨"), p3, at("2026-11-10T09:00:00"))
		val r = ok("/rankings/places", a).at("/rankings")
		assertThat(r.arr().map { it.at("/placeId").asLong() }).containsExactly(p2, p1, p3)
		assertThat(r.arr().map { it.at("/rank").asInt() }).containsExactly(1, 2, 2)
	}

	@Test
	@DisplayName("인기 장소: period와 category 필터, size, 폐업(비활성) 장소 제외")
	fun placeRankingFilters() {
		val a = fx.user("에이")
		val cafe = fx.place("CAFE")
		val heritage = fx.place("HERITAGE")
		val closed = fx.place("CAFE", active = false)
		fx.completion(a, cafe, at("2026-11-10T09:00:00"))
		fx.completion(a, heritage, at("2026-11-09T09:00:00")) // 어제
		fx.completion(a, closed, at("2026-11-10T09:00:00"))
		fx.completion(a, fx.place("PARK"), at("2026-11-02T09:00:00")) // 지난주

		fun ids(q: String) = ok("/rankings/places?$q", a).at("/rankings").arr().map { it.at("/placeId").asLong() }
		assertThat(ids("period=DAY")).containsExactly(cafe)
		assertThat(ids("period=WEEK")).containsExactlyInAnyOrder(cafe, heritage)
		assertThat(ids("period=MONTH")).hasSize(3)
		assertThat(ids("period=WEEK&category=HERITAGE")).containsExactly(heritage)
		assertThat(ids("period=WEEK&category=CAFE")).containsExactly(cafe)
		assertThat(ids("period=WEEK&size=1")).hasSize(1)
		assertThat(ids("period=DAY&category=PARK")).isEmpty()
		assertThat(ok("/rankings/places?period=DAY", a).at("/startDate").asText()).isEqualTo("2026-11-10")
	}

	@Test
	@DisplayName("인기 장소 COMMON400: 없는 period, 없는 category, size 0과 51. 기본 10, 최대 50은 허용")
	fun placeRankingValidation() {
		val a = fx.user("에이")
		call("/rankings/places?period=YEAR", a).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		call("/rankings/places?category=NOPE", a).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("category"))
		call("/rankings/places?size=0", a).andExpect(status().isBadRequest)
		call("/rankings/places?size=51", a).andExpect(status().isBadRequest)
		call("/rankings/places?size=50", a).andExpect(status().isOk)
		call("/rankings/places", null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("인기 장소 기본 size는 10")
	fun placeRankingDefaultSize() {
		val a = fx.user("에이")
		repeat(12) { fx.completion(a, fx.place("CAFE"), at("2026-11-10T09:00:00")) }
		assertThat(ok("/rankings/places", a).at("/rankings").size()).isEqualTo(10)
	}

	// ---- /rankings/streaks

	private fun streaker(nickname: String, vararg dayOffsets: Int, onboarded: Boolean = true, withdrawn: Boolean = false): Long {
		val u = fx.user(nickname, onboarded, withdrawn)
		dayOffsets.forEach { fx.completion(u, fx.place("CAFE"), at("2026-11-10T09:00:00").plusDays(it.toLong())) }
		return u
	}

	@Test
	@DisplayName("연속 랭킹: 현재 연속 일수 순, 같으면 같은 순위, me에 최장 기록, 키 정확히")
	fun streakRanking() {
		streaker("산책러", -4, -3, -2, -1, 0) // 5일
		streaker("뽈뽈왕", -2, -1, 0) // 3일
		streaker("도서관요정", -3, -2, -1) // 어제까지 3일: 오늘 아직이어도 유지
		val meId = streaker("나", -9, -8, -7, -6, -5, -1, 0) // 현재 2일, 최장 5일

		val d = ok("/rankings/streaks", meId)
		assertThat(d.propertyNames().toList()).containsExactlyInAnyOrder("rankings", "me")
		val r = d.at("/rankings")
		assertThat(r.arr().map { it.at("/nickname").asText() }).containsExactly("산책러", "뽈뽈왕", "도서관요정", "나") // 같은 3일이면 최장 기록, 그다음 id(가입 순서)
		assertThat(r.arr().map { it.at("/streakDays").asInt() }).containsExactly(5, 3, 3, 2)
		assertThat(r.arr().map { it.at("/rank").asInt() }).containsExactly(1, 2, 2, 4)
		assertThat(r.arr().map { it.at("/isMe").asBoolean() }).containsExactly(false, false, false, true)
		assertThat(r.at("/0").propertyNames().toList()).containsExactlyInAnyOrder("rank", "nickname", "streakDays", "isMe")
		assertThat(d.at("/me").propertyNames().toList()).containsExactlyInAnyOrder("rank", "nickname", "streakDays", "longestStreakDays")
		assertThat(d.at("/me/rank").asInt()).isEqualTo(4)
		assertThat(d.at("/me/streakDays").asInt()).isEqualTo(2)
		assertThat(d.at("/me/longestStreakDays").asInt()).isEqualTo(5)
	}

	@Test
	@DisplayName("연속 랭킹: 끊긴 사용자(그제가 마지막)는 목록에 없고 me는 null")
	fun streakBrokenUserNotListed() {
		streaker("현역", -1, 0)
		val broken = streaker("끊김", -4, -3, -2)
		val d = ok("/rankings/streaks", broken)
		assertThat(d.at("/rankings").arr().map { it.at("/nickname").asText() }).containsExactly("현역")
		assertThat(d.at("/me").isNull).isTrue()
	}

	@Test
	@DisplayName("연속 랭킹: 재방문(같은 장소 다른 날)도 참여로 센다")
	fun streakCountsRevisits() {
		val u = fx.user("재방문러")
		val place = fx.place("CAFE")
		fx.completion(u, place, at("2026-11-08T09:00:00"))
		fx.completion(u, place, at("2026-11-09T09:00:00"), 50, firstVisit = false)
		fx.completion(u, place, at("2026-11-10T09:00:00"), 50, firstVisit = false)
		assertThat(ok("/rankings/streaks", u).at("/me/streakDays").asInt()).isEqualTo(3)
	}

	@Test
	@DisplayName("연속 랭킹: 한국 시간 자정 경계. 23:59 인증과 다음날 00:01 인증은 이틀, 같은 날 두 번은 하루")
	fun streakKstBoundary() {
		val u = fx.user("자정")
		fx.completion(u, fx.place("CAFE"), at("2026-11-09T23:59:00"))
		fx.completion(u, fx.place("CAFE"), at("2026-11-09T00:01:00")) // 같은 날 두 번째
		fx.completion(u, fx.place("CAFE"), at("2026-11-10T00:01:00"))
		assertThat(ok("/rankings/streaks", u).at("/me/streakDays").asInt()).isEqualTo(2)

		// 시계가 11-11 00:00이 되면 어제(11-10)까지 이어져 있어 아직 유지, 11-12 00:00이면 끊긴다
		clock.setKst("2026-11-11T00:00:00")
		assertThat(ok("/rankings/streaks", u).at("/me/streakDays").asInt()).isEqualTo(2)
		clock.setKst("2026-11-11T23:59:59")
		assertThat(ok("/rankings/streaks", u).at("/me/streakDays").asInt()).isEqualTo(2)
		clock.setKst("2026-11-12T00:00:00")
		assertThat(ok("/rankings/streaks", u).at("/me").isNull).isTrue()
	}

	@Test
	@DisplayName("연속 랭킹: 탈퇴 사용자와 미온보딩 사용자 제외, size 적용, size 범위 오류 COMMON400, 기본 10")
	fun streakExclusionAndSize() {
		streaker("탈퇴자", -5, -4, -3, -2, -1, 0, withdrawn = true)
		streaker("미온보딩", -5, -4, -3, -2, -1, 0, onboarded = false)
		val a = streaker("에이", -2, -1, 0)
		streaker("비", -1, 0)
		streaker("씨", 0)

		val d = ok("/rankings/streaks?size=2", a)
		assertThat(d.at("/rankings").arr().map { it.at("/nickname").asText() }).containsExactly("에이", "비")
		assertThat(d.at("/me/rank").asInt()).isEqualTo(1)

		call("/rankings/streaks?size=0", a).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		call("/rankings/streaks?size=51", a).andExpect(status().isBadRequest)
		call("/rankings/streaks?size=50", a).andExpect(status().isOk)
		call("/rankings/streaks", null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("연속 랭킹 기본 size는 10")
	fun streakDefaultSize() {
		repeat(12) { streaker("스트릭$it", 0) }
		assertThat(ok("/rankings/streaks", fx.user("조회자")).at("/rankings").size()).isEqualTo(10)
	}
}
