package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.completion.entity.Completion
import com.bbolbbol.server.domain.completion.repository.CompletionRepository
import com.bbolbbol.server.domain.completion.service.CompletionService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.point.entity.PointType
import com.bbolbbol.server.domain.point.service.PointService
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 도착 인증, 포인트, 활동 일지 통합 테스트(H2). 동시성 테스트가 있어서 테스트 트랜잭션을 쓰지 않고
 * 매 테스트가 만든 데이터를 직접 지운다. 시각은 MutableClock(한국 시간)으로 옮긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class)
class CompletionApiIntegrationTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var completions: CompletionRepository
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var pointService: PointService
	@Autowired lateinit var completionService: CompletionService
	@Autowired lateinit var clock: MutableClock

	private val mapper = JsonMapper.builder().build()
	private val baseLat = 37.5796
	private val baseLng = 126.9770
	private val metersPerDegLat = 6_371_000.0 * Math.PI / 180.0

	private val createdUsers = mutableListOf<Long>()
	private val createdPlaces = mutableListOf<Long>()
	private var userId = 0L
	private var heritageId = 0L // HERITAGE 300점, 반경 300
	private var cafeId = 0L // CAFE 100점, 반경 100
	private var specialId = 0L // CAFE 장소 값 150점, 반경 100
	private var inactiveId = 0L

	@BeforeEach
	fun setUp() {
		clock.setKst("2026-11-10T12:00:00")
		userId = newUser()
		heritageId = newPlace("HERITAGE", "테스트궁", null, 300, 0.0, "tk-heritage", true)
		cafeId = newPlace("CAFE", "테스트카페", null, 100, 0.01, "tk-cafe", true)
		specialId = newPlace("CAFE", "특별카페", 150, 100, 0.02, "tk-special", true)
		inactiveId = newPlace("CAFE", "폐업카페", null, 100, 0.03, "tk-off", false)
	}

	@AfterEach
	fun cleanUp() {
		for (id in createdUsers) {
			jdbc.update("DELETE FROM point_histories WHERE user_id = ?", id)
			jdbc.update("DELETE FROM completions WHERE user_id = ?", id)
		}
		for (id in createdPlaces) jdbc.update("DELETE FROM places WHERE id = ?", id)
		for (id in createdUsers) jdbc.update("DELETE FROM users WHERE id = ?", id)
		createdUsers.clear()
		createdPlaces.clear()
	}

	// ---- 도우미

	private var seq = AtomicInteger((System.nanoTime() % 100000).toInt())

	private fun newUser(): Long {
		val n = seq.incrementAndGet()
		val user = userService.createEmailUser("cmp$n@example.com", "x", "cp$n")
		return user.id!!.also {
			createdUsers += it
			jdbc.update("UPDATE users SET onboarding_completed = TRUE WHERE id = ?", it) // A4: 온보딩 완료 사용자만 도착 인증
		}
	}

	private fun newPlace(code: String, name: String, reward: Int?, radius: Int, dLat: Double, kakao: String, active: Boolean): Long {
		val c = categories.findAll().first { it.code == code }
		val place = places.saveAndFlush(
			Place(
				category = c, name = name, address = "서울 종로구 $name 1", roadAddress = null, phone = null,
				lat = baseLat + dLat, lng = baseLng, radiusM = radius, notifyRadiusM = radius + 100, rewardPoint = reward,
				kakaoPlaceId = "$kakao-$userId-${seq.incrementAndGet()}", kakaoPlaceUrl = "https://example.com/$kakao",
				hotType = null, active = active,
			),
		)
		return place.id!!.also { createdPlaces += it }
	}

	private fun auth(id: Long = userId) = "Bearer ${jwt.createAccessToken(id)}"

	private fun body(lat: Double, lng: Double, accuracy: Double = 10.0, mock: Boolean = false) =
		"""{"lat":$lat,"lng":$lng,"accuracy":$accuracy,"isMockLocation":$mock}"""

	/** 장소 위치에서 위도 방향으로 meters만큼 떨어진 좌표의 위도 */
	private fun latOffset(placeDLat: Double, meters: Double) = baseLat + placeDLat + meters / metersPerDegLat

	private fun complete(placeId: Long, json: String, token: String? = auth()): ResultActions =
		mvc.perform(
			post("/places/$placeId/completions").contentType(MediaType.APPLICATION_JSON).content(json).also {
				if (token != null) it.header("Authorization", token)
			},
		)

	private fun completeAtHeritage(token: String? = auth()) = complete(heritageId, body(baseLat, baseLng), token)
	private fun completeAtCafe() = complete(cafeId, body(baseLat + 0.01, baseLng))
	private fun completeAtSpecial() = complete(specialId, body(baseLat + 0.02, baseLng))

	private fun json(r: ResultActions) = mapper.readTree(r.andReturn().response.getContentAsString(Charsets.UTF_8))

	private fun keys(r: ResultActions, pointer: String): List<String> = json(r).at(pointer).propertyNames().toList()

	private fun getAuthed(url: String, id: Long = userId) = mvc.perform(get(url).header("Authorization", auth(id)))

	private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

	// ---- POST /places/{placeId}/completions: 성공

	@Test
	@DisplayName("첫 방문 성공: HTTP 201, 점수 100%, 사진 보너스 안내, 완료 기록과 포인트 내역이 함께 생긴다")
	fun firstVisitSuccess() {
		val r = completeAtHeritage()
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON201"))
			.andExpect(jsonPath("$.message").value("퀘스트를 완료했습니다."))
			.andExpect(jsonPath("$.data.placeId").value(heritageId))
			.andExpect(jsonPath("$.data.placeName").value("테스트궁"))
			.andExpect(jsonPath("$.data.isFirstVisit").value(true))
			.andExpect(jsonPath("$.data.earnedPoint").value(300))
			.andExpect(jsonPath("$.data.totalPoint").value(300))
			.andExpect(jsonPath("$.data.completedAt").value("2026-11-10T12:00:00"))
			.andExpect(jsonPath("$.data.photoBonusPoint").value(50))
			.andExpect(jsonPath("$.data.photoDeadline").value("2026-11-10T13:00:00"))
		assertThat(keys(r, "/data")).containsExactlyInAnyOrder(
			"completionId", "placeId", "placeName", "isFirstVisit", "earnedPoint", "totalPoint",
			"completedAt", "photoBonusPoint", "photoDeadline",
		)
		val completionId = json(r).at("/data/completionId").asLong()
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isEqualTo(1)
		val row = jdbc.queryForMap("SELECT * FROM completions WHERE id = ?", completionId)
		assertThat(row["first_visit"]).isEqualTo(true)
		assertThat((row["earned_point"] as Number).toInt()).isEqualTo(300)
		assertThat(row["completion_date"].toString()).isEqualTo("2026-11-10")
		val h = jdbc.queryForMap("SELECT * FROM point_histories WHERE user_id = ?", userId)
		assertThat(h["type"]).isEqualTo("QUEST_COMPLETE")
		assertThat((h["point"] as Number).toInt()).isEqualTo(300)
		assertThat((h["completion_id"] as Number).toLong()).isEqualTo(completionId)
	}

	@Test
	@DisplayName("장소 값(150)이 카테고리 값(100)을 덮는다")
	fun placeRewardOverridesCategory() {
		completeAtSpecial().andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.earnedPoint").value(150))
	}

	@Test
	@DisplayName("재방문(다음 날): 점수 50%, isFirstVisit false, 사진 보너스 0과 null, 누적 합산")
	fun revisitNextDay() {
		completeAtHeritage().andExpect(status().isCreated)
		clock.setKst("2026-11-11T09:00:00")
		completeAtHeritage().andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.isFirstVisit").value(false))
			.andExpect(jsonPath("$.data.earnedPoint").value(150))
			.andExpect(jsonPath("$.data.totalPoint").value(450))
			.andExpect(jsonPath("$.data.photoBonusPoint").value(0))
			.andExpect(jsonPath("$.data.photoDeadline").value(null))
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isEqualTo(2)
	}

	@Test
	@DisplayName("날짜 경계: 한국 23:59에 인증하고 00:01에 다시 하면 새 날이라 성공(재방문 50%), 같은 날 또 하면 409")
	fun midnightBoundary() {
		clock.setKst("2026-11-09T23:59:30")
		completeAtHeritage().andExpect(status().isCreated).andExpect(jsonPath("$.data.earnedPoint").value(300))
		clock.setKst("2026-11-09T23:59:59")
		completeAtHeritage().andExpect(status().isConflict).andExpect(jsonPath("$.code").value("COMPLETION409"))
		clock.setKst("2026-11-10T00:01:00")
		completeAtHeritage().andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.earnedPoint").value(150))
			.andExpect(jsonPath("$.data.completedAt").value("2026-11-10T00:01:00"))
		clock.setKst("2026-11-10T23:59:00")
		completeAtHeritage().andExpect(status().isConflict)
		val dates = jdbc.queryForList(
			"SELECT completion_date FROM completions WHERE user_id = ? ORDER BY id", userId,
		).map { it["completion_date"].toString() }
		assertThat(dates).containsExactly("2026-11-09", "2026-11-10")
	}

	@Test
	@DisplayName("온보딩을 마쳤다면 닉네임이 없어도 막지 않는다")
	fun nullNicknameAllowed() {
		jdbc.update("UPDATE users SET nickname = NULL WHERE id = ?", userId)
		completeAtHeritage().andExpect(status().isCreated)
	}

	@Test
	@DisplayName("A4: 온보딩을 마치지 않은 사용자는 COMPLETION4031(403)이고 기록이 남지 않는다")
	fun onboardingGate() {
		jdbc.update("UPDATE users SET onboarding_completed = FALSE WHERE id = ?", userId)
		completeAtHeritage().andExpect(status().isForbidden)
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("COMPLETION4031"))
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isZero()
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ?", userId)).isZero()
		// 온보딩을 마치면 같은 요청이 성공한다
		jdbc.update("UPDATE users SET onboarding_completed = TRUE WHERE id = ?", userId)
		completeAtHeritage().andExpect(status().isCreated)
	}

	@Test
	@DisplayName("정확도 정확히 100m는 허용, 100.1m는 COMPLETION4002")
	fun accuracyBoundary() {
		complete(heritageId, body(baseLat, baseLng, accuracy = 100.0)).andExpect(status().isCreated)
		clock.setKst("2026-11-11T12:00:00")
		complete(heritageId, body(baseLat, baseLng, accuracy = 100.1))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4002"))
	}

	@Test
	@DisplayName("반경 경계: 카페(100m) 99m는 성공, 101m는 COMPLETION4001. 궁(300m)은 250m도 성공")
	fun radiusBoundary() {
		complete(cafeId, body(latOffset(0.01, 101.0), baseLng))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4001"))
		complete(cafeId, body(latOffset(0.01, 99.0), baseLng)).andExpect(status().isCreated)
		complete(heritageId, body(latOffset(0.0, 250.0), baseLng)).andExpect(status().isCreated)
	}

	// ---- 에러와 검사 순서

	@Test
	@DisplayName("없는 장소와 비활성 장소는 PLACE404, 모의 위치여도 장소가 먼저다")
	fun placeNotFound() {
		complete(987654321L, body(baseLat, baseLng, mock = true))
			.andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("PLACE404"))
		complete(inactiveId, body(baseLat + 0.03, baseLng))
			.andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("PLACE404"))
	}

	@Test
	@DisplayName("검사 순서: 모의 위치(4003) -> 정확도(4002) -> 반경(4001), 실패하면 기록이 남지 않는다")
	fun validationOrder() {
		val far = baseLat + 1.0
		complete(heritageId, body(far, baseLng, accuracy = 500.0, mock = true))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4003"))
		complete(heritageId, body(far, baseLng, accuracy = 500.0))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4002"))
		complete(heritageId, body(far, baseLng))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4001"))
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isZero()
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ?", userId)).isZero()
	}

	@Test
	@DisplayName("오늘 이미 인증했으면 모의 위치, 정확도, 반경보다 COMPLETION409가 먼저다")
	fun duplicateComesBeforeLocationChecks() {
		completeAtHeritage().andExpect(status().isCreated)
		completeAtHeritage().andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("COMPLETION409"))
			.andExpect(jsonPath("$.success").value(false))
		complete(heritageId, body(baseLat + 1.0, baseLng, accuracy = 500.0, mock = true))
			.andExpect(status().isConflict).andExpect(jsonPath("$.code").value("COMPLETION409"))
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ?", userId)).isEqualTo(1)
	}

	@Test
	@DisplayName("다른 사용자와 다른 장소는 같은 날에도 각각 인증된다")
	fun independentUsersAndPlaces() {
		completeAtHeritage().andExpect(status().isCreated)
		completeAtCafe().andExpect(status().isCreated)
		completeAtHeritage(auth(newUser())).andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.isFirstVisit").value(true))
	}

	@Test
	@DisplayName("입력 검증: 필드 누락, 좌표 범위, 음수 정확도, 깨진 JSON은 COMMON400")
	fun inputValidation() {
		fun expect400(json: String, field: String? = null) {
			val r = complete(heritageId, json).andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("COMMON400"))
			if (field != null) assertThat(json(r).at("/data/errors").toString()).contains(field)
		}
		expect400("""{"lng":126.9,"accuracy":1.0,"isMockLocation":false}""", "lat")
		expect400("""{"lat":37.5,"accuracy":1.0,"isMockLocation":false}""", "lng")
		expect400("""{"lat":37.5,"lng":126.9,"isMockLocation":false}""", "accuracy")
		expect400("""{"lat":37.5,"lng":126.9,"accuracy":1.0}""", "isMockLocation")
		expect400(body(91.0, 126.9), "lat")
		expect400(body(37.5, -181.0), "lng")
		expect400(body(37.5, 126.9, accuracy = -1.0), "accuracy")
		expect400("""{"lat":"abc","lng":126.9,"accuracy":1.0,"isMockLocation":false}""")
		expect400("{not json")
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isZero()
	}

	@Test
	@DisplayName("인증: 토큰 없음, 잘못된 토큰, 탈퇴 사용자는 AUTH401")
	fun authErrors() {
		completeAtHeritage(token = null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		completeAtHeritage(token = "Bearer bad.token.value").andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH401"))
		jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId)
		completeAtHeritage().andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		for (url in listOf("/users/me/points", "/users/me/point-histories", "/users/me/completions")) {
			mvc.perform(get(url).header("Authorization", auth())).andExpect(status().isUnauthorized)
				.andExpect(jsonPath("$.code").value("AUTH401"))
			mvc.perform(get(url)).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		}
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isZero()
	}

	// ---- 동시성과 DB 제약

	@Test
	@DisplayName("같은 요청 8개를 동시에 보내면 201은 정확히 1개, 나머지는 409(500 없음), 기록은 1건")
	fun concurrentDuplicates() {
		val threads = 8
		val pool = Executors.newFixedThreadPool(threads)
		val ready = CountDownLatch(threads)
		val go = CountDownLatch(1)
		val token = auth()
		val statuses = java.util.Collections.synchronizedList(mutableListOf<Int>())
		val codes = java.util.Collections.synchronizedList(mutableListOf<String>())
		repeat(threads) {
			pool.submit {
				ready.countDown()
				go.await()
				val res = completeAtHeritage(token).andReturn().response
				statuses += res.status
				codes += mapper.readTree(res.getContentAsString(Charsets.UTF_8)).at("/code").asString()
			}
		}
		ready.await()
		go.countDown()
		pool.shutdown()
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue()
		assertThat(statuses.count { it == 201 }).isEqualTo(1)
		assertThat(statuses.count { it == 409 }).isEqualTo(threads - 1)
		assertThat(statuses).doesNotContain(500)
		assertThat(codes.filter { it != "COMMON201" }).allMatch { it == "COMPLETION409" }
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", userId)).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ?", userId)).isEqualTo(1)
		assertThat(pointService.totalPoint(userId)).isEqualTo(300L)
	}

	@Test
	@DisplayName("DB 유니크 제약(user, place, date)이 직접 중복 저장을 막는다")
	fun uniqueConstraintInDb() {
		val now = LocalDateTime.of(2026, 11, 10, 12, 0)
		fun row() = Completion(userId, heritageId, now.toLocalDate(), now, true, 300, baseLat, baseLng, 5.0)
		completions.saveAndFlush(row())
		val e = org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> { completions.saveAndFlush(row()) }
		assertThat(e.message).containsIgnoringCase("uk_completions_user_place_date")
		// 날짜가 다르면 허용
		completions.saveAndFlush(Completion(userId, heritageId, LocalDate.of(2026, 11, 11), now.plusDays(1), false, 150, baseLat, baseLng, 5.0))
	}

	@Test
	@DisplayName("유니크 위반은 제약 이름으로 식별되고(H2), 다른 무결성 위반(FK)은 409로 바뀌지 않는다")
	fun onlyDuplicateConstraintIsConflict() {
		val now = LocalDateTime.of(2026, 11, 10, 12, 0)
		fun row(user: Long = userId) =
			Completion(user, heritageId, now.toLocalDate(), now, true, 300, baseLat, baseLng, 5.0)
		completions.saveAndFlush(row())
		val dup = org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> { completions.saveAndFlush(row()) }
		assertThat(CompletionService.isDuplicateCompletion(dup)).isTrue()
		val fk = org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> { completions.saveAndFlush(row(user = 987654321L)) }
		assertThat(CompletionService.isDuplicateCompletion(fk)).isFalse()
	}

	@Test
	@DisplayName("서비스: 없는 사용자는 온보딩 게이트(COMPLETION4031)에서 막히고 기록이 남지 않는다(FK 위반 매핑은 DuplicateDetectionTest)")
	fun ghostUserBlockedByOnboardingGate() {
		val ghostUser = 987654321L
		val e = org.junit.jupiter.api.assertThrows<BusinessException> {
			completionService.complete(ghostUser, heritageId, baseLat, baseLng, 5.0, false)
		}
		assertThat(e.errorCode.code).isEqualTo("COMPLETION4031")
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ?", ghostUser)).isZero()
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ?", ghostUser)).isZero()
	}

	@Test
	@DisplayName("point_histories는 completion당 type별 1건 제약과 음수 불가를 가진다")
	fun pointHistoryConstraints() {
		val now = LocalDateTime.of(2026, 11, 10, 12, 0)
		val c = completions.saveAndFlush(Completion(userId, heritageId, now.toLocalDate(), now, true, 300, baseLat, baseLng, 5.0))
		pointService.record(userId, c.id!!, heritageId, PointType.QUEST_COMPLETE, 300, now)
		org.junit.jupiter.api.assertThrows<DataIntegrityViolationException> {
			pointService.record(userId, c.id!!, heritageId, PointType.QUEST_COMPLETE, 300, now)
		}
		org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
			pointService.record(userId, c.id!!, heritageId, PointType.PHOTO_BONUS, -1, now)
		}
	}

	// ---- /places가 실제 provider로 인증 상태를 반영

	@Test
	@DisplayName("/places와 /places/{id}가 인증 후 visitedBefore, isCompleted, completedCount, myLastCompletedAt, expectedPoint를 반영한다")
	fun placesReflectCompletion() {
		val list = "/places?lat=$baseLat&lng=$baseLng&radius=5000"
		fun place(r: ResultActions, id: Long) = json(r).at("/data/places").first { it.at("/placeId").asLong() == id }

		val before = place(getAuthed(list), heritageId)
		assertThat(before.at("/visitedBefore").asBoolean()).isFalse()
		assertThat(before.at("/isCompleted").asBoolean()).isFalse()
		assertThat(before.at("/expectedPoint").asInt()).isEqualTo(300)

		completeAtHeritage().andExpect(status().isCreated)
		val same = place(getAuthed(list), heritageId)
		assertThat(same.at("/visitedBefore").asBoolean()).isTrue()
		assertThat(same.at("/isCompleted").asBoolean()).isTrue()
		assertThat(same.at("/expectedPoint").asInt()).isEqualTo(150)
		val other = place(getAuthed(list), cafeId)
		assertThat(other.at("/visitedBefore").asBoolean()).isFalse()
		assertThat(other.at("/isCompleted").asBoolean()).isFalse()

		// 다른 사용자에게는 반영되지 않지만 장소 전체 인증 횟수는 늘어난다
		val stranger = newUser()
		assertThat(place(getAuthed(list, stranger), heritageId).at("/visitedBefore").asBoolean()).isFalse()
		getAuthed("/places/$heritageId", stranger).andExpect(status().isOk)
			.andExpect(jsonPath("$.data.completedCount").value(1))
			.andExpect(jsonPath("$.data.visitedBefore").value(false))
			.andExpect(jsonPath("$.data.photoBonusAvailable").value(true))

		// 다음 날: 방문한 적은 있지만 오늘은 아직(isCompleted false), 사진 보너스 불가
		clock.setKst("2026-11-11T08:00:00")
		getAuthed("/places/$heritageId").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.visitedBefore").value(true))
			.andExpect(jsonPath("$.data.isCompleted").value(false))
			.andExpect(jsonPath("$.data.photoBonusAvailable").value(false))
			.andExpect(jsonPath("$.data.myLastCompletedAt").value("2026-11-10T12:00:00"))
			.andExpect(jsonPath("$.data.completedCount").value(1))
		// excludeCompletedToday: 어제 인증한 장소는 오늘 목록에 남는다
		val excl = json(getAuthed("$list&excludeCompletedToday=true")).at("/data/places").toList().map { it.at("/placeId").asLong() }
		assertThat(excl).contains(heritageId)
		completeAtHeritage().andExpect(status().isCreated)
		val excl2 = json(getAuthed("$list&excludeCompletedToday=true")).at("/data/places").toList().map { it.at("/placeId").asLong() }
		assertThat(excl2).doesNotContain(heritageId).contains(cafeId)
	}

	// ---- GET /users/me/points

	@Test
	@DisplayName("포인트 요약: 총합, 오늘(한국 0시 기준) 획득, 완료 수, 정확한 JSON 키, 빈 상태")
	fun pointsSummary() {
		val empty = getAuthed("/users/me/points").andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
			.andExpect(jsonPath("$.data.totalPoint").value(0))
			.andExpect(jsonPath("$.data.todayPoint").value(0))
			.andExpect(jsonPath("$.data.completionCount").value(0))
		assertThat(keys(empty, "/data")).containsExactlyInAnyOrder("totalPoint", "todayPoint", "completionCount")

		clock.setKst("2026-11-09T23:30:00")
		completeAtHeritage().andExpect(status().isCreated) // 300, 11/09
		clock.setKst("2026-11-10T00:10:00")
		completeAtCafe().andExpect(status().isCreated) // 100, 11/10
		completeAtSpecial().andExpect(status().isCreated) // 150, 11/10
		getAuthed("/users/me/points").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.totalPoint").value(550))
			.andExpect(jsonPath("$.data.todayPoint").value(250)) // 23:30 적립분은 어제
			.andExpect(jsonPath("$.data.completionCount").value(3))
		clock.setKst("2026-11-10T23:59:59")
		getAuthed("/users/me/points").andExpect(jsonPath("$.data.todayPoint").value(250))
		clock.setKst("2026-11-11T00:00:00")
		getAuthed("/users/me/points").andExpect(jsonPath("$.data.todayPoint").value(0))
			.andExpect(jsonPath("$.data.totalPoint").value(550))
		// 다른 사용자 격리
		getAuthed("/users/me/points", newUser()).andExpect(jsonPath("$.data.totalPoint").value(0))
	}

	// ---- GET /users/me/point-histories

	private fun seedFiveCompletions(): List<Long> {
		val ids = mutableListOf<Long>()
		val plan = listOf(
			"2026-11-05T10:00:00" to heritageId, "2026-11-06T10:00:00" to cafeId, "2026-11-07T10:00:00" to specialId,
			"2026-11-08T10:00:00" to heritageId, "2026-11-09T10:00:00" to cafeId,
		)
		for ((at, placeId) in plan) {
			clock.setKst(at)
			val lat = when (placeId) {
				heritageId -> baseLat
				cafeId -> baseLat + 0.01
				else -> baseLat + 0.02
			}
			val r = complete(placeId, body(lat, baseLng)).andExpect(status().isCreated)
			ids += json(r).at("/data/completionId").asLong()
		}
		clock.setKst("2026-11-10T12:00:00")
		return ids
	}

	@Test
	@DisplayName("적립 내역: 최신순, 커서 페이지네이션(첫 페이지, 다음 페이지, 끝), 정확한 JSON 키와 값")
	fun pointHistoriesPaging() {
		val ids = seedFiveCompletions() // 점수: 300, 100, 150, 150(재방문 50%), 50
		val p1 = getAuthed("/users/me/point-histories?size=2").andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.data.histories.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.histories[0].type").value("QUEST_COMPLETE"))
			.andExpect(jsonPath("$.data.histories[0].point").value(50))
			.andExpect(jsonPath("$.data.histories[0].completionId").value(ids[4]))
			.andExpect(jsonPath("$.data.histories[0].placeId").value(cafeId))
			.andExpect(jsonPath("$.data.histories[0].placeName").value("테스트카페"))
			.andExpect(jsonPath("$.data.histories[0].category").value("CAFE"))
			.andExpect(jsonPath("$.data.histories[0].createdAt").value("2026-11-09T10:00:00"))
			.andExpect(jsonPath("$.data.histories[1].point").value(150))
			.andExpect(jsonPath("$.data.histories[1].placeName").value("테스트궁"))
			.andExpect(jsonPath("$.data.histories[1].category").value("HERITAGE"))
		assertThat(keys(p1, "/data")).containsExactlyInAnyOrder("histories", "nextCursor", "hasNext")
		assertThat(keys(p1, "/data/histories/0")).containsExactlyInAnyOrder(
			"historyId", "type", "point", "completionId", "placeId", "placeName", "category", "createdAt",
		)
		val c1 = json(p1).at("/data/nextCursor").asLong()
		assertThat(c1).isEqualTo(json(p1).at("/data/histories/1/historyId").asLong())

		val p2 = getAuthed("/users/me/point-histories?size=2&cursor=$c1").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.histories.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.histories[0].point").value(150)) // 11/07 특별카페
		val c2 = json(p2).at("/data/nextCursor").asLong()
		assertThat(c2).isLessThan(c1)

		getAuthed("/users/me/point-histories?size=2&cursor=$c2").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.histories.length()").value(1))
			.andExpect(jsonPath("$.data.histories[0].point").value(300))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(null))

		// 딱 맞는 크기: 5개를 size=5로 받으면 hasNext false
		getAuthed("/users/me/point-histories?size=5").andExpect(jsonPath("$.data.histories.length()").value(5))
			.andExpect(jsonPath("$.data.hasNext").value(false)).andExpect(jsonPath("$.data.nextCursor").value(null))
		// 기본 size 20
		getAuthed("/users/me/point-histories").andExpect(jsonPath("$.data.histories.length()").value(5))
	}

	@Test
	@DisplayName("적립 내역: 새 내역이 생겨도 커서 페이지에 중복이나 누락이 없다")
	fun pointHistoriesStableCursor() {
		seedFiveCompletions()
		val p1 = getAuthed("/users/me/point-histories?size=3")
		val cursor = json(p1).at("/data/nextCursor").asLong()
		val firstIds = json(p1).at("/data/histories").toList().map { it.at("/historyId").asLong() }
		clock.setKst("2026-11-10T12:00:00")
		completeAtHeritage().andExpect(status().isCreated) // 스크롤 중 새 내역
		val p2 = getAuthed("/users/me/point-histories?size=3&cursor=$cursor")
		val secondIds = json(p2).at("/data/histories").toList().map { it.at("/historyId").asLong() }
		assertThat(secondIds).hasSize(2).doesNotContainAnyElementsOf(firstIds)
	}

	@Test
	@DisplayName("적립 내역: type 필터와 빈 목록")
	fun pointHistoriesTypeFilterAndEmpty() {
		getAuthed("/users/me/point-histories").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.histories.length()").value(0))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(null))
		val ids = seedFiveCompletions()
		// 사진 보너스는 아직 만드는 API가 없으므로 서비스로 한 건 넣어 필터만 확인한다
		pointService.record(userId, ids[0], heritageId, PointType.PHOTO_BONUS, 50, LocalDateTime.of(2026, 11, 5, 10, 5))
		getAuthed("/users/me/point-histories?type=PHOTO_BONUS").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.histories.length()").value(1))
			.andExpect(jsonPath("$.data.histories[0].type").value("PHOTO_BONUS"))
			.andExpect(jsonPath("$.data.histories[0].point").value(50))
		getAuthed("/users/me/point-histories?type=QUEST_COMPLETE&size=50")
			.andExpect(jsonPath("$.data.histories.length()").value(5))
		getAuthed("/users/me/points").andExpect(jsonPath("$.data.totalPoint").value(800))
	}

	@Test
	@DisplayName("적립 내역: size 범위, 없는 type, 잘못된 cursor는 COMMON400")
	fun pointHistoriesValidation() {
		for (q in listOf("size=0", "size=51", "size=-1", "size=abc", "type=BAD", "cursor=0", "cursor=abc")) {
			getAuthed("/users/me/point-histories?$q").andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("COMMON400"))
		}
		getAuthed("/users/me/point-histories?size=50").andExpect(status().isOk)
		getAuthed("/users/me/point-histories?size=1").andExpect(status().isOk)
	}

	@Test
	@DisplayName("적립 내역은 내 것만 보인다")
	fun pointHistoriesIsolation() {
		completeAtHeritage().andExpect(status().isCreated)
		getAuthed("/users/me/point-histories", newUser()).andExpect(jsonPath("$.data.histories.length()").value(0))
	}

	// ---- GET /users/me/completions

	@Test
	@DisplayName("활동 일지: 최신순 커서 페이지네이션과 정확한 JSON 키, photoUrl은 null")
	fun logPaging() {
		val ids = seedFiveCompletions()
		val p1 = getAuthed("/users/me/completions?size=2").andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.data.completions.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.completions[0].completionId").value(ids[4]))
			.andExpect(jsonPath("$.data.completions[0].placeId").value(cafeId))
			.andExpect(jsonPath("$.data.completions[0].placeName").value("테스트카페"))
			.andExpect(jsonPath("$.data.completions[0].category").value("CAFE"))
			.andExpect(jsonPath("$.data.completions[0].address").value("서울 종로구 테스트카페 1"))
			.andExpect(jsonPath("$.data.completions[0].earnedPoint").value(50))
			.andExpect(jsonPath("$.data.completions[0].photoUrl").value(null))
			.andExpect(jsonPath("$.data.completions[0].completedAt").value("2026-11-09T10:00:00"))
			.andExpect(jsonPath("$.data.nextCursor").value(ids[3]))
		assertThat(keys(p1, "/data")).containsExactlyInAnyOrder("completions", "nextCursor", "hasNext")
		assertThat(keys(p1, "/data/completions/0")).containsExactlyInAnyOrder(
			"completionId", "placeId", "placeName", "category", "address", "earnedPoint", "photoUrl", "completedAt",
		)
		getAuthed("/users/me/completions?size=2&cursor=${ids[3]}")
			.andExpect(jsonPath("$.data.completions.length()").value(2))
			.andExpect(jsonPath("$.data.completions[0].completionId").value(ids[2]))
			.andExpect(jsonPath("$.data.nextCursor").value(ids[1]))
			.andExpect(jsonPath("$.data.hasNext").value(true))
		getAuthed("/users/me/completions?size=2&cursor=${ids[1]}")
			.andExpect(jsonPath("$.data.completions.length()").value(1))
			.andExpect(jsonPath("$.data.completions[0].completionId").value(ids[0]))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(null))
	}

	@Test
	@DisplayName("활동 일지: from, to(포함)와 category 필터, 하루 조회")
	fun logFilters() {
		val ids = seedFiveCompletions() // 11/05 궁, 11/06 카페, 11/07 특별, 11/08 궁, 11/09 카페
		fun idsOf(q: String) = json(getAuthed("/users/me/completions?$q").andExpect(status().isOk))
			.at("/data/completions").toList().map { it.at("/completionId").asLong() }
		assertThat(idsOf("from=2026-11-07&to=2026-11-08")).containsExactly(ids[3], ids[2])
		assertThat(idsOf("from=2026-11-09&to=2026-11-09")).containsExactly(ids[4])
		assertThat(idsOf("from=2026-11-08")).containsExactly(ids[4], ids[3])
		assertThat(idsOf("to=2026-11-05")).containsExactly(ids[0])
		assertThat(idsOf("from=2026-11-10&to=2026-11-10")).isEmpty()
		assertThat(idsOf("category=HERITAGE")).containsExactly(ids[3], ids[0])
		assertThat(idsOf("category=CAFE&from=2026-11-07")).containsExactly(ids[4], ids[2])
		assertThat(idsOf("category=MOVIE")).isEmpty()
	}

	@Test
	@DisplayName("활동 일지: 날짜는 한국 시간 기준으로 자른다(23:59와 00:01)")
	fun logKstDates() {
		clock.setKst("2026-11-09T23:59:30")
		val a = json(completeAtHeritage()).at("/data/completionId").asLong()
		clock.setKst("2026-11-10T00:01:00")
		val b = json(completeAtCafe()).at("/data/completionId").asLong()
		fun idsOf(q: String) = json(getAuthed("/users/me/completions?$q"))
			.at("/data/completions").toList().map { it.at("/completionId").asLong() }
		assertThat(idsOf("from=2026-11-09&to=2026-11-09")).containsExactly(a)
		assertThat(idsOf("from=2026-11-10&to=2026-11-10")).containsExactly(b)
	}

	@Test
	@DisplayName("커서가 목록 끝을 넘어가면(가장 오래된 id 이하, 아주 큰 값 포함) 빈 목록, hasNext=false, nextCursor=null")
	fun cursorPastEnd() {
		val ids = seedFiveCompletions()
		val oldestHistory = jdbc.queryForObject("SELECT MIN(id) FROM point_histories WHERE user_id = ?", Long::class.java, userId)!!
		for (cursor in listOf(oldestHistory, 1L, Long.MAX_VALUE)) {
			val r = getAuthed("/users/me/point-histories?cursor=$cursor&size=5").andExpect(status().isOk)
			if (cursor == Long.MAX_VALUE) {
				r.andExpect(jsonPath("$.data.histories.length()").value(5)) // MAX는 "끝 이후"가 아니라 맨 앞이다
			} else {
				r.andExpect(jsonPath("$.data.histories.length()").value(0))
					.andExpect(jsonPath("$.data.hasNext").value(false))
					.andExpect(jsonPath("$.data.nextCursor").value(null))
			}
		}
		for (cursor in listOf(ids.min(), 1L)) {
			getAuthed("/users/me/completions?cursor=$cursor&size=5").andExpect(status().isOk)
				.andExpect(jsonPath("$.data.completions.length()").value(0))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").value(null))
		}
		// 필터와 함께여도 같다
		getAuthed("/users/me/point-histories?cursor=1&type=QUEST_COMPLETE").andExpect(jsonPath("$.data.histories.length()").value(0))
		getAuthed("/users/me/completions?cursor=1&category=CAFE").andExpect(jsonPath("$.data.completions.length()").value(0))
	}

	@Test
	@DisplayName("size 경계: 1과 50은 성공(1이면 한 건과 hasNext), 0과 51은 두 API 모두 COMMON400")
	fun sizeBoundaries() {
		seedFiveCompletions()
		for (path in listOf("point-histories" to "histories", "completions" to "completions")) {
			val (url, key) = path
			getAuthed("/users/me/$url?size=1").andExpect(status().isOk)
				.andExpect(jsonPath("$.data.$key.length()").value(1))
				.andExpect(jsonPath("$.data.hasNext").value(true))
			getAuthed("/users/me/$url?size=50").andExpect(status().isOk)
				.andExpect(jsonPath("$.data.$key.length()").value(5))
				.andExpect(jsonPath("$.data.hasNext").value(false))
			for (bad in listOf(0, 51, -1)) {
				getAuthed("/users/me/$url?size=$bad").andExpect(status().isBadRequest)
					.andExpect(jsonPath("$.code").value("COMMON400"))
			}
		}
	}

	@Test
	@DisplayName("활동 일지: 날짜 형식, from > to, size, cursor, 없는 category는 COMMON400")
	fun logValidation() {
		for (q in listOf(
			"from=2026-13-01", "from=abc", "to=20261109", "from=2026-11-10&to=2026-11-09",
			"size=0", "size=51", "cursor=0", "category=NOPE",
		)) {
			getAuthed("/users/me/completions?$q").andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("COMMON400"))
		}
		getAuthed("/users/me/completions?from=2026-11-10&to=2026-11-10").andExpect(status().isOk)
	}

	@Test
	@DisplayName("활동 일지: 빈 목록, 다른 사용자 격리, 재방문 기록의 earnedPoint")
	fun logEmptyAndIsolation() {
		getAuthed("/users/me/completions").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.completions.length()").value(0))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(null))
		completeAtHeritage().andExpect(status().isCreated)
		clock.setKst("2026-11-11T12:00:00")
		completeAtHeritage().andExpect(status().isCreated)
		getAuthed("/users/me/completions").andExpect(jsonPath("$.data.completions.length()").value(2))
			.andExpect(jsonPath("$.data.completions[0].earnedPoint").value(150))
			.andExpect(jsonPath("$.data.completions[1].earnedPoint").value(300))
		getAuthed("/users/me/completions", newUser()).andExpect(jsonPath("$.data.completions.length()").value(0))
	}
}
