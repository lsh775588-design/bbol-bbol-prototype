package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.completion.service.CompletionDataCleanupService
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.infra.storage.FileStorage
import com.bbolbbol.server.support.AuthFlowTest
import com.bbolbbol.server.support.DbFixture
import com.bbolbbol.server.support.ImageTestSupport
import com.bbolbbol.server.support.MutableClockConfig
import com.bbolbbol.server.support.RecordingEmailConfig
import com.bbolbbol.server.support.WithdrawalListenersConfig
import com.bbolbbol.server.support.arr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 탈퇴 후 completion 도메인 정리(결정 D4)를 실제 엔드포인트로 확인한다: 도착 인증 -> 사진 업로드 -> DELETE /users/me.
 * 사진 파일과 photo_key, 제출 좌표는 지워지고 완료 기록과 포인트 내역은 통계용으로 남는다.
 */
@Import(MutableClockConfig::class, RecordingEmailConfig::class, WithdrawalListenersConfig::class)
class WithdrawalCleanupTest : AuthFlowTest() {

	@Autowired lateinit var userService: UserService
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var cleanup: CompletionDataCleanupService

	@MockitoSpyBean lateinit var storage: FileStorage

	private lateinit var fx: DbFixture
	private var placeId = 0L
	private val storageDir: Path = Path.of("build/test-uploads").toAbsolutePath()

	@BeforeEach
	fun setUp() {
		fx = DbFixture(jdbc, userService, places, categories)
		placeId = fx.place("CAFE", "탈퇴카페")
	}

	@AfterEach
	fun tearDown() {
		Mockito.reset(storage)
		jdbc.update("DELETE FROM point_histories")
		jdbc.update("DELETE FROM completions")
		fx.cleanUp()
	}

	// ---- 도우미

	private fun onboard(s: Session) {
		jdbc.update("UPDATE users SET onboarding_completed = TRUE WHERE id = ?", s.userId)
	}

	private fun arrive(s: Session, place: Long = placeId): Long {
		val r = mvc.perform(
			post("/places/$place/completions").header("Authorization", s.bearer).contentType(MediaType.APPLICATION_JSON)
				.content("""{"lat":37.58,"lng":126.98,"accuracy":10.0,"isMockLocation":false}"""),
		).andExpect(status().isCreated)
		return tree(r).at("/data/completionId").asLong()
	}

	private fun uploadPhoto(s: Session, completionId: Long) {
		mvc.perform(
			multipart("/completions/$completionId/photo")
				.file(MockMultipartFile("photo", "a.jpg", "image/jpeg", ImageTestSupport.jpeg()))
				.header("Authorization", s.bearer),
		).andExpect(status().isCreated)
	}

	private fun photoKey(completionId: Long): String? =
		jdbc.queryForList("SELECT photo_key FROM completions WHERE id = ?", completionId).single()["photo_key"] as String?

	private fun file(key: String): Path = storageDir.resolve(key)

	/** 사용자 한 명이 첫 방문 인증과 사진 업로드까지 마친 상태. 완료 기록 id와 저장 key를 돌려준다. */
	private fun userWithPhoto(email: String, nickname: String): Triple<Session, Long, String> {
		val s = registerEmailUser(email, nickname)
		onboard(s)
		val cid = arrive(s)
		uploadPhoto(s, cid)
		return Triple(s, cid, photoKey(cid)!!)
	}

	private fun coordsNullCount(userId: Long) = count(
		"SELECT COUNT(*) FROM completions WHERE user_id = $userId AND lat IS NULL AND lng IS NULL AND accuracy_m IS NULL",
	)

	// ---- 테스트

	@Test
	@DisplayName("탈퇴: 파일 삭제, photo_key와 좌표 NULL, 기록과 포인트 행은 유지, 장소 랭킹은 계속 세고 유저 랭킹은 제외, 다른 사용자는 그대로 (다른 리스너가 예외를 던져도 200)")
	fun endToEnd() {
		val (a, aCid, aKey) = userWithPhoto("leaver@example.com", "떠나는이")
		val (b, bCid, bKey) = userWithPhoto("stayer@example.com", "남는이")
		assertThat(Files.exists(file(aKey))).isTrue()
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ${a.userId} AND lat IS NOT NULL")).isEqualTo(1)

		// WithdrawalListenersConfig의 Thrower가 항상 예외를 던지지만 응답은 200이고, 우리 리스너는 그보다 먼저 실행된다
		deleteAuth("/users/me", a.bearer).andExpect(status().isOk).andExpect(jsonPath("$.code").value("COMMON200"))

		// 탈퇴자: 파일 삭제, 사진 정보와 좌표 삭제
		assertThat(Files.exists(file(aKey))).isFalse()
		val row = jdbc.queryForMap("SELECT photo_key, photo_uploaded_at, lat, lng, accuracy_m FROM completions WHERE id = ?", aCid)
		assertThat(row.values).allMatch { it == null }
		// 기록과 포인트는 통계용으로 남는다
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ${a.userId}")).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ${a.userId}")).isEqualTo(2)
		assertThat(count("SELECT COALESCE(SUM(point), 0) FROM point_histories WHERE user_id = ${a.userId}")).isEqualTo(150)

		// 다른 사용자는 영향이 없다
		assertThat(Files.exists(file(bKey))).isTrue()
		assertThat(photoKey(bCid)).isEqualTo(bKey)
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ${b.userId} AND lat IS NOT NULL AND lng IS NOT NULL AND accuracy_m IS NOT NULL"))
			.isEqualTo(1)
		val log = tree(mvc.perform(get("/users/me/completions").header("Authorization", b.bearer)).andExpect(status().isOk))
		assertThat(log.at("/data/completions").size()).isEqualTo(1)
		assertThat(log.at("/data/completions/0/photoUrl").asText()).isEqualTo("/files/$bKey")
		assertThat(log.at("/data/completions/0/earnedPoint").asInt()).isEqualTo(150)

		// 장소 랭킹은 탈퇴자의 기록도 센다(D4), 유저 랭킹은 탈퇴자를 뺀다
		val placeRank = tree(mvc.perform(get("/rankings/places?period=WEEK").header("Authorization", b.bearer)).andExpect(status().isOk))
		val mine = placeRank.at("/data/rankings").arr().first { it.at("/placeId").asLong() == placeId }
		assertThat(mine.at("/completionCount").asInt()).isEqualTo(2)
		val userRank = tree(mvc.perform(get("/rankings/users?period=WEEK").header("Authorization", b.bearer)).andExpect(status().isOk))
		assertThat(userRank.at("/data/rankings").arr().map { it.at("/nickname").asText() }).containsExactly("남는이")
		// 통계는 내 것만(탈퇴자 기록과 섞이지 않는다)
		val stats = tree(mvc.perform(get("/users/me/stats?period=WEEK").header("Authorization", b.bearer)).andExpect(status().isOk))
		assertThat(stats.at("/data/completionCount").asInt()).isEqualTo(1)
		assertThat(stats.at("/data/totalPoint").asInt()).isEqualTo(150)
	}

	@Test
	@DisplayName("파일 삭제가 실패하면 탈퇴 응답은 200이고, 그 key는 DB에 남아 재시도할 수 있으며 좌표는 이미 지워진다. 점검(cleanupWithdrawnUsers)이 이어서 끝낸다")
	fun interruptedCleanupIsRepaired() {
		val (a, aCid, aKey) = userWithPhoto("flaky@example.com", "삭제실패")
		Mockito.doThrow(java.io.UncheckedIOException(IOException("disk error"))).`when`(storage).delete(Mockito.anyString())

		deleteAuth("/users/me", a.bearer).andExpect(status().isOk)

		// 실패한 파일은 그대로, photo_key도 남아 있어서 다음 정리가 어떤 파일인지 알 수 있다
		assertThat(Files.exists(file(aKey))).isTrue()
		assertThat(photoKey(aCid)).isEqualTo(aKey)
		assertThat(coordsNullCount(a.userId)).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ${a.userId}")).isEqualTo(2)

		// 점검: 아직 실패하는 동안에는 미완료로 보고하고 상태는 그대로
		val failing = cleanup.cleanupWithdrawnUsers()
		assertThat(failing.checked).isEqualTo(1)
		assertThat(failing.cleaned).isZero()
		assertThat(failing.incomplete).isEqualTo(1)
		assertThat(photoKey(aCid)).isEqualTo(aKey)

		// 저장소가 복구되면 점검이 이어서 끝낸다
		Mockito.reset(storage)
		val repaired = cleanup.cleanupWithdrawnUsers()
		assertThat(repaired).isEqualTo(CompletionDataCleanupService.Summary(checked = 1, cleaned = 1, incomplete = 0))
		assertThat(Files.exists(file(aKey))).isFalse()
		assertThat(photoKey(aCid)).isNull()
		// 정리가 끝난 사용자는 다음 점검 대상이 아니다
		assertThat(cleanup.cleanupWithdrawnUsers().checked).isZero()
	}

	@Test
	@DisplayName("멱등: 정리를 다시 실행해도 결과가 같고 두 번째는 할 일이 없다")
	fun idempotent() {
		val (a, aCid, aKey) = userWithPhoto("twice@example.com", "두번정리")
		deleteAuth("/users/me", a.bearer).andExpect(status().isOk)
		val snapshot = jdbc.queryForMap("SELECT * FROM completions WHERE id = ?", aCid)

		val again = cleanup.cleanupUser(a.userId)
		assertThat(again).isEqualTo(CompletionDataCleanupService.Result(skipped = false, photosDeleted = 0, photosFailed = 0, coordinatesCleared = 0))
		assertThat(again.complete).isTrue()
		assertThat(cleanup.cleanupUser(a.userId).photosDeleted).isZero()
		assertThat(jdbc.queryForMap("SELECT * FROM completions WHERE id = ?", aCid)).isEqualTo(snapshot)
		assertThat(Files.exists(file(aKey))).isFalse()
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ${a.userId}")).isEqualTo(2)
	}

	@Test
	@DisplayName("파일이 이미 없어도 정상 처리되고 photo_key는 비워진다")
	fun fileAlreadyMissing() {
		val (a, aCid, aKey) = userWithPhoto("gone@example.com", "파일없음")
		Files.delete(file(aKey))
		deleteAuth("/users/me", a.bearer).andExpect(status().isOk)
		assertThat(photoKey(aCid)).isNull()
		assertThat(coordsNullCount(a.userId)).isEqualTo(1)
	}

	@Test
	@DisplayName("사진이 없는 사용자(좌표만)도 정리된다. 기록이 아예 없는 사용자의 탈퇴도 문제없다")
	fun noPhotoAndNoCompletions() {
		val s = registerEmailUser("nophoto@example.com", "사진없음")
		onboard(s)
		arrive(s)
		val empty = registerEmailUser("empty@example.com", "기록없음")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		deleteAuth("/users/me", empty.bearer).andExpect(status().isOk)
		assertThat(coordsNullCount(s.userId)).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM point_histories WHERE user_id = ${s.userId}")).isEqualTo(1)
	}

	@Test
	@DisplayName("안전장치: 탈퇴하지 않은 사용자의 정리 요청은 건너뛰고 아무것도 지우지 않는다")
	fun activeUserIsNeverCleaned() {
		val (a, aCid, aKey) = userWithPhoto("active@example.com", "활동중")
		val r = cleanup.cleanupUser(a.userId)
		assertThat(r.skipped).isTrue()
		assertThat(r.complete).isFalse()
		assertThat(Files.exists(file(aKey))).isTrue()
		assertThat(photoKey(aCid)).isEqualTo(aKey)
		assertThat(count("SELECT COUNT(*) FROM completions WHERE user_id = ${a.userId} AND lat IS NOT NULL")).isEqualTo(1)
		// 점검도 활동 중인 사용자는 대상이 아니다
		assertThat(cleanup.cleanupWithdrawnUsers().checked).isZero()
		assertThat(Files.exists(file(aKey))).isTrue()
	}

	@Test
	@DisplayName("좌표가 NULL인 기록이 있어도 활동 일지, 통계, 랭킹, 도착 인증이 깨지지 않는다")
	fun nullCoordinatesDoNotBreakReaders() {
		val (a, _, _) = userWithPhoto("nulls@example.com", "널좌표")
		val (b, _, _) = userWithPhoto("others@example.com", "다른이")
		deleteAuth("/users/me", a.bearer).andExpect(status().isOk)
		for (url in listOf("/users/me/completions", "/users/me/stats?period=MONTH", "/users/me/points", "/rankings/users?period=MONTH",
			"/rankings/places?period=MONTH", "/rankings/streaks", "/users/me/point-histories")) {
			mvc.perform(get(url).header("Authorization", b.bearer)).andExpect(status().isOk)
		}
		// 탈퇴자가 다녀간 장소 말고 다른 장소에 도착 인증도 정상
		arrive(b, fx.place("CAFE", "새장소"))
	}
}
