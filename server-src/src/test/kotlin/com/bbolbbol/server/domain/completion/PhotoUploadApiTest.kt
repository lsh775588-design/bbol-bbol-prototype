package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.DbFixture
import com.bbolbbol.server.support.ImageTestSupport
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * POST /completions/{id}/photo 통합 테스트(H2). 동시성 테스트가 있어 테스트 트랜잭션을 쓰지 않고 [DbFixture]가 정리한다.
 * 저장 파일은 build/test-uploads/completions(application-test.yml)에 생긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class)
class PhotoUploadApiTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var clock: MutableClock

	private lateinit var fx: DbFixture
	private val mapper = JsonMapper.builder().build()
	private val storageDir: Path = Path.of("build/test-uploads/completions").toAbsolutePath()

	private var userId = 0L
	private var placeId = 0L

	@BeforeEach
	fun setUp() {
		fx = DbFixture(jdbc, userService, places, categories)
		clock.setKst("2026-11-10T12:00:00")
		userId = fx.user("사진러")
		placeId = fx.place("CAFE", "사진카페")
	}

	@AfterEach
	fun cleanUp() = fx.cleanUp()

	// ---- 도우미

	private fun auth(id: Long = userId) = "Bearer ${jwt.createAccessToken(id)}"

	private fun upload(
		completionId: Long,
		bytes: ByteArray? = ImageTestSupport.jpeg(),
		type: String = "image/jpeg",
		name: String = "quest.jpg",
		token: String? = auth(),
		part: String = "photo",
	): ResultActions {
		val req = multipart("/completions/$completionId/photo")
		if (bytes != null) req.file(MockMultipartFile(part, name, type, bytes))
		if (token != null) req.header("Authorization", token)
		return mvc.perform(req)
	}

	private fun json(r: ResultActions) = mapper.readTree(r.andReturn().response.getContentAsString(Charsets.UTF_8))

	private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

	private fun photoKey(completionId: Long): String? =
		jdbc.queryForList("SELECT photo_key FROM completions WHERE id = ?", completionId).single()["photo_key"] as String?

	private fun storedFiles(): Set<String> =
		if (Files.isDirectory(storageDir)) Files.list(storageDir).use { s -> s.map { it.fileName.toString() }.toList().toSet() } else emptySet()

	private fun bonusRows(completionId: Long) =
		count("SELECT COUNT(*) FROM point_histories WHERE completion_id = ? AND type = 'PHOTO_BONUS'", completionId)

	/** 지금 시각(12:00) 첫 방문 완료 기록 한 건(점수 100). */
	private fun firstVisitNow(): Long = fx.completion(userId, placeId, LocalDateTime.of(2026, 11, 10, 12, 0))

	// ---- 성공

	@Test
	@DisplayName("첫 방문 사진 업로드: 201, 응답 키 정확히 4개, 보너스 50, 누적 합산, DB 반영")
	fun firstVisitSuccess() {
		val cid = firstVisitNow()
		val r = upload(cid)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON201"))
			.andExpect(jsonPath("$.message").value("사진이 등록되었습니다."))
			.andExpect(jsonPath("$.data.completionId").value(cid))
			.andExpect(jsonPath("$.data.bonusPoint").value(50))
			.andExpect(jsonPath("$.data.totalPoint").value(150))
		assertThat(json(r).at("/data").propertyNames().toList())
			.containsExactlyInAnyOrder("completionId", "photoUrl", "bonusPoint", "totalPoint")
		assertThat(json(r).at("/data/photoUrl").asText()).startsWith("/files/completions/").endsWith(".jpg")

		val key = photoKey(cid)
		assertThat(key).isNotNull()
		assertThat(json(r).at("/data/photoUrl").asText()).isEqualTo("/files/$key")
		assertThat(count("SELECT COUNT(*) FROM completions WHERE id = ? AND photo_uploaded_at = ?", cid, LocalDateTime.of(2026, 11, 10, 12, 0)))
			.isEqualTo(1)
		val bonus = jdbc.queryForMap("SELECT * FROM point_histories WHERE completion_id = ? AND type = 'PHOTO_BONUS'", cid)
		assertThat((bonus["point"] as Number).toInt()).isEqualTo(50)
		assertThat(bonus["earned_at"].toString()).startsWith("2026-11-10 12:00")
		assertThat((bonus["user_id"] as Number).toLong()).isEqualTo(userId)
	}

	@Test
	@DisplayName("저장된 파일에는 EXIF(GPS, 설명 문자열)가 없고, 키는 난수 UUID(v4)이며 id로 추측할 수 없다")
	fun storedFileIsSanitizedAndKeyIsRandom() {
		val cid1 = firstVisitNow()
		val place2 = fx.place("CAFE", "두번째")
		val cid2 = fx.completion(userId, place2, LocalDateTime.of(2026, 11, 10, 12, 0))
		val input = ImageTestSupport.jpegWithExif(orientation = 6)
		assertThat(ImageTestSupport.contains(input, ImageTestSupport.SECRET)).isTrue()

		upload(cid1, input).andExpect(status().isCreated)
		upload(cid2, input).andExpect(status().isCreated)

		val k1 = photoKey(cid1)!!
		val k2 = photoKey(cid2)!!
		val uuid = Regex("^completions/([0-9a-f]{8})-([0-9a-f]{4})-4([0-9a-f]{3})-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg$")
		assertThat(k1).matches(uuid.toPattern())
		assertThat(k2).matches(uuid.toPattern())
		// 연속된 완료 id(같은 사용자)인데 키는 서로 닮지 않았다: 난수(UUID v4)이지 id 기반이 아니다
		assertThat(k1).isNotEqualTo(k2)
		assertThat(k1.substringAfter('/').take(8)).isNotEqualTo(k2.substringAfter('/').take(8))

		val bytes = Files.readAllBytes(storageDir.resolve(k1.substringAfter('/')))
		assertThat(ImageTestSupport.hasApp1(bytes)).isFalse()
		assertThat(ImageTestSupport.contains(bytes, ImageTestSupport.SECRET)).isFalse()
		assertThat(ImageTestSupport.contains(bytes, "Exif")).isFalse()
		// 업로드한 파일이 아니라 다시 인코딩한 JPEG다(회전 6이 반영돼 세로가 됐다)
		val img = javax.imageio.ImageIO.read(bytes.inputStream())
		assertThat(img.width).isEqualTo(20)
		assertThat(img.height).isEqualTo(40)
	}

	@Test
	@DisplayName("클라이언트 파일 이름과 확장자는 저장 경로에 영향을 주지 않는다(../ 포함)")
	fun clientFileNameIgnored() {
		val cid = firstVisitNow()
		upload(cid, name = "../../evil.php").andExpect(status().isCreated)
		assertThat(photoKey(cid)).matches("^completions/[0-9a-f-]{36}\\.jpg$")
	}

	@Test
	@DisplayName("PNG 업로드도 받고 JPEG로 저장한다")
	fun pngAccepted() {
		val cid = firstVisitNow()
		upload(cid, ImageTestSupport.png(), "image/png", "a.png").andExpect(status().isCreated)
		val bytes = Files.readAllBytes(storageDir.resolve(photoKey(cid)!!.substringAfter('/')))
		assertThat(bytes[0]).isEqualTo(0xFF.toByte())
		assertThat(bytes[1]).isEqualTo(0xD8.toByte())
	}

	@Test
	@DisplayName("photoUrl로 내려받기: 로그인하면 저장된 이미지(image/jpeg)를, 로그인하지 않으면 AUTH401, 모르는 key는 404")
	fun download() {
		val cid = firstVisitNow()
		val url = json(upload(cid)).at("/data/photoUrl").asText()
		val expected = Files.readAllBytes(storageDir.resolve(photoKey(cid)!!.substringAfter('/')))

		val res = mvc.perform(get(url).header("Authorization", auth()))
			.andExpect(status().isOk)
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andReturn().response
		assertThat(res.contentAsByteArray).isEqualTo(expected)
		assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff")

		mvc.perform(get(url)).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		mvc.perform(get("/files/completions/00000000-0000-4000-8000-000000000000.jpg").header("Authorization", auth()))
			.andExpect(status().isNotFound)
		// 키 형식이 아닌 이름(예: 원본 확장자 흉내)은 파일이 있어도 404
		mvc.perform(get("/files/completions/secret.txt").header("Authorization", auth())).andExpect(status().isNotFound)
	}

	@Test
	@DisplayName("활동 일지: 업로드 전 earnedPoint 100, photoUrl null / 후 earnedPoint 150(기본 + 보너스), photoUrl 채워짐")
	fun activityLogReflectsPhoto() {
		val cid = firstVisitNow()
		fun item() = json(mvc.perform(get("/users/me/completions").header("Authorization", auth())).andExpect(status().isOk)).at("/data/completions/0")

		assertThat(item().at("/earnedPoint").asInt()).isEqualTo(100)
		assertThat(item().at("/photoUrl").isNull).isTrue()

		upload(cid).andExpect(status().isCreated)
		assertThat(item().at("/earnedPoint").asInt()).isEqualTo(150)
		assertThat(item().at("/photoUrl").asText()).isEqualTo("/files/${photoKey(cid)}")
	}

	@Test
	@DisplayName("도착 인증 응답은 그대로(photoBonusPoint, photoDeadline) 이고 이어서 사진을 올리면 totalPoint가 맞는다")
	fun fullFlowFromArrival() {
		val arrive = mvc.perform(
			post("/places/$placeId/completions").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
				.content("""{"lat":37.58,"lng":126.98,"accuracy":10.0,"isMockLocation":false}"""),
		).andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.photoBonusPoint").value(50))
			.andExpect(jsonPath("$.data.photoDeadline").value("2026-11-10T13:00:00"))
		val cid = json(arrive).at("/data/completionId").asLong()
		upload(cid).andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.bonusPoint").value(50))
			.andExpect(jsonPath("$.data.totalPoint").value(150))
		mvc.perform(get("/users/me/points").header("Authorization", auth()))
			.andExpect(jsonPath("$.data.totalPoint").value(150))
	}

	// ---- 마감과 재방문

	@Test
	@DisplayName("마감 경계: 인증 + 1시간 정각(13:00:00)까지 허용, 1초 지나면 COMPLETION4004이고 파일과 보너스가 없다")
	fun deadlineBoundary() {
		val cid = firstVisitNow()
		val before = storedFiles()
		clock.setKst("2026-11-10T13:00:01")
		upload(cid).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMPLETION4004"))
		assertThat(photoKey(cid)).isNull()
		assertThat(bonusRows(cid)).isZero()
		assertThat(storedFiles()).isEqualTo(before)

		clock.setKst("2026-11-10T13:00:00")
		upload(cid).andExpect(status().isCreated).andExpect(jsonPath("$.data.bonusPoint").value(50))
	}

	@Test
	@DisplayName("재방문 기록: 사진은 올릴 수 있지만 보너스 0, 포인트 내역 없음, 마감 제한 없음, 활동 일지에만 남는다")
	fun revisitUploadHasNoBonusAndNoDeadline() {
		val cid = fx.completion(userId, placeId, LocalDateTime.of(2026, 11, 9, 9, 0), point = 50, firstVisit = false)
		// 하루 넘게 지났어도(마감 제한 없음) 올릴 수 있다
		upload(cid).andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.bonusPoint").value(0))
			.andExpect(jsonPath("$.data.totalPoint").value(50))
		assertThat(bonusRows(cid)).isZero()
		assertThat(photoKey(cid)).isNotNull()
		val item = json(mvc.perform(get("/users/me/completions").header("Authorization", auth()))).at("/data/completions/0")
		assertThat(item.at("/earnedPoint").asInt()).isEqualTo(50)
		assertThat(item.at("/photoUrl").asText()).startsWith("/files/completions/")
		// 두 번째는 재방문이어도 COMPLETION4091
		upload(cid).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("COMPLETION4091"))
	}

	// ---- 에러

	@Test
	@DisplayName("두 번째 업로드는 COMPLETION4091이고 보너스는 한 번만, 두 번째 파일은 저장되지 않는다")
	fun secondUploadConflict() {
		val cid = firstVisitNow()
		upload(cid).andExpect(status().isCreated)
		val files = storedFiles()
		val key = photoKey(cid)
		upload(cid).andExpect(status().isConflict)
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("COMPLETION4091"))
		assertThat(bonusRows(cid)).isEqualTo(1)
		assertThat(photoKey(cid)).isEqualTo(key)
		assertThat(storedFiles()).isEqualTo(files)
		assertThat(count("SELECT COALESCE(SUM(point), 0) FROM point_histories WHERE user_id = ?", userId)).isEqualTo(150)
	}

	@Test
	@DisplayName("없는 완료 기록 COMPLETION404(404)")
	fun notFound() {
		upload(987654321L).andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("COMPLETION404"))
	}

	@Test
	@DisplayName("남의 완료 기록 COMPLETION403(403)이고 아무것도 바뀌지 않는다")
	fun notMine() {
		val cid = firstVisitNow()
		val other = fx.user("다른사람")
		val before = storedFiles()
		upload(cid, token = auth(other)).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("COMPLETION403"))
		assertThat(photoKey(cid)).isNull()
		assertThat(bonusRows(cid)).isZero()
		assertThat(storedFiles()).isEqualTo(before)
	}

	@Test
	@DisplayName("토큰이 없으면 AUTH401")
	fun unauthorized() {
		val cid = firstVisitNow()
		upload(cid, token = null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		upload(cid, token = "Bearer garbage").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("FILE4001: 파트 누락, 다른 이름의 파트, 이미지가 아닌 내용, 지원하지 않는 타입(heic, gif), 빈 파일 - 저장과 보너스 없음")
	fun invalidFiles() {
		val cid = firstVisitNow()
		val before = storedFiles()
		fun expect4001(r: ResultActions) = r.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("FILE4001"))

		expect4001(upload(cid, bytes = null))
		expect4001(upload(cid, part = "file")) // 명세의 파트 이름은 photo다
		expect4001(upload(cid, "plain text".toByteArray(), "image/jpeg", "a.jpg"))
		expect4001(upload(cid, ImageTestSupport.jpeg(), "text/plain", "a.txt"))
		expect4001(upload(cid, ImageTestSupport.jpeg(), "image/heic", "a.heic"))
		expect4001(upload(cid, ImageTestSupport.encode(ImageTestSupport.image(8, 8), "gif"), "image/gif", "a.gif"))
		expect4001(upload(cid, ByteArray(0), "image/jpeg", "empty.jpg"))

		assertThat(photoKey(cid)).isNull()
		assertThat(bonusRows(cid)).isZero()
		assertThat(storedFiles()).isEqualTo(before)
		// 실패 뒤에도 마감 전이면 다시 시도할 수 있다(프론트 참고)
		upload(cid).andExpect(status().isCreated)
	}

	@Test
	@DisplayName("FILE413: 10MB 초과")
	fun tooLarge() {
		val cid = firstVisitNow()
		val big = ByteArray(10 * 1024 * 1024 + 1)
		upload(cid, big).andExpect(status().isPayloadTooLarge).andExpect(jsonPath("$.code").value("FILE413"))
		assertThat(photoKey(cid)).isNull()
	}

	@Test
	@DisplayName("JSON 등 multipart가 아닌 요청은 COMMON415")
	fun notMultipart() {
		val cid = firstVisitNow()
		mvc.perform(
			post("/completions/$cid/photo").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{}"),
		).andExpect(status().isUnsupportedMediaType).andExpect(jsonPath("$.code").value("COMMON415"))
	}

	// ---- 동시성

	@Test
	@DisplayName("같은 완료 기록에 동시에 올리면 한 건만 201, 나머지는 COMPLETION4091이고 사진 1장, 보너스 1건, 파일 1개")
	fun concurrentDoubleUpload() {
		repeat(5) { round ->
			val place = fx.place("CAFE", "경쟁$round")
			val cid = fx.completion(userId, place, LocalDateTime.of(2026, 11, 10, 12, 0))
			val before = storedFiles()
			val threads = 4
			val pool = Executors.newFixedThreadPool(threads)
			val ready = CountDownLatch(threads)
			val go = CountDownLatch(1)
			try {
				val futures = (1..threads).map {
					pool.submit<Int> {
						ready.countDown()
						go.await()
						upload(cid).andReturn().response.status
					}
				}
				assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
				go.countDown()
				val statuses = futures.map { it.get(60, TimeUnit.SECONDS) }
				assertThat(statuses.count { it == 201 }).describedAs("round $round $statuses").isEqualTo(1)
				assertThat(statuses.count { it == 409 }).describedAs("round $round $statuses").isEqualTo(threads - 1)
			} finally {
				pool.shutdownNow()
			}
			assertThat(bonusRows(cid)).isEqualTo(1)
			assertThat(count("SELECT COUNT(*) FROM completions WHERE id = ? AND photo_key IS NOT NULL", cid)).isEqualTo(1)
			// 진 요청이 저장했던 파일은 지워졌다: 새 파일은 정확히 한 개
			assertThat(storedFiles() - before).hasSize(1)
		}
		assertThat(count("SELECT COALESCE(SUM(point), 0) FROM point_histories WHERE user_id = ?", userId)).isEqualTo(5 * 150)
	}
}
