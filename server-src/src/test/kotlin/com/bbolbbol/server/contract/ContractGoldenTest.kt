package com.bbolbbol.server.contract

import com.bbolbbol.server.contract.ContractSupport.Golden
import com.bbolbbol.server.contract.ContractSupport.RequestSpec
import com.bbolbbol.server.domain.place.entity.HotType
import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.security.jwt.JwtProvider
import com.bbolbbol.server.support.AcceptingVerifierConfig
import com.bbolbbol.server.support.ImageTestSupport
import com.bbolbbol.server.support.MutableClockConfig
import com.bbolbbol.server.support.MutableClockConfig.MutableClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * 앱-서버 계약 테스트(서버 쪽, 권위 있는 쪽).
 *
 * - 요청 골든(`contract/requests 폴더의 JSON`, 앱이 보내는 JSON/쿼리)을 그대로 보내 서버가 받아들이는지 확인한다.
 * - 응답은 모양(shape)으로 줄여 `contract/responses 폴더의 JSON`과 비교한다. 의도한 변경이면 `-Dcontract.update=true`로 갱신한다.
 * - 새 엔드포인트 추가 절차는 docs/contract-testing.md.
 *
 * 한 번 만든 데이터로 순서대로 호출하는 흐름이라 @TestFactory로 단계마다 동적 테스트 하나씩 돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClockConfig::class, AcceptingVerifierConfig::class)
class ContractGoldenTest {

	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jwt: JwtProvider
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var places: PlaceRepository
	@Autowired lateinit var categories: PlaceCategoryRepository
	@Autowired lateinit var userService: UserService
	@Autowired lateinit var clock: MutableClock
	@Autowired lateinit var verifier: AcceptingVerifierConfig.FakeVerifier

	private val mapper = ContractSupport.mapper
	private val baseLat = 37.5796
	private val baseLng = 126.9770
	private val seq = AtomicInteger((System.nanoTime() % 100000).toInt())

	private val createdPlaces = mutableListOf<Long>()
	private val touched = linkedSetOf<String>()
	private var heritageId = 0L
	private var cafeId = 0L
	private var dormantId = 0L
	private var otherNickname = "계약타인"
	private var otherCompletionId = 0L

	@BeforeEach
	fun setUp() {
		clock.setKst("2026-11-10T12:00:00")
		verifier.consumed.clear()
		cleanUp()
		val other = userService.createEmailUser("contract-other@example.com", "x", otherNickname)
		// 도착 인증은 온보딩 완료자만 가능하다(COMPLETION4031). 계약 테스트 사용자는 지원되는 경로로 온보딩을 마친다.
		val otherBearer = "Bearer " + jwt.createAccessToken(other.id!!)
		val onb = mvc.perform(
			request(HttpMethod.PATCH, "/users/me/onboarding").header("Authorization", otherBearer)
				.contentType(MediaType.APPLICATION_JSON).content("""{"ageGroup":"THIRTIES","gender":"MALE"}"""),
		).andReturn().response
		check(onb.status == 200) { "온보딩 준비 실패: ${onb.status} ${onb.getContentAsString(Charsets.UTF_8)}" }
		heritageId = newPlace("HERITAGE", "계약궁", null, null, 300, 0.0, null)
		// 핫플(CURATED) + 도로명주소/전화번호가 있는 장소: null 가능 필드의 "값이 있는" 표본
		cafeId = newPlace("CAFE", "계약카페", "서울 종로구 계약로 1", "02-000-0000", 100, 0.001, HotType.CURATED)
		dormantId = newPlace("CAFE", "계약휴게소", null, null, 100, 0.002, null)
		// 다른 사용자의 완료 기록(사진 업로드의 COMPLETION403, 랭킹의 isMe=false 표본)
		val otherDone = request(HttpMethod.POST, "/places/$dormantId/completions")
			.header("Authorization", otherBearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""{"lat":${baseLat + 0.002},"lng":$baseLng,"accuracy":10.0,"isMockLocation":false}""")
		val r = mvc.perform(otherDone).andReturn().response
		check(r.status == 201) { "다른 사용자 완료 준비 실패: ${r.status} ${r.getContentAsString(Charsets.UTF_8)}" }
		otherCompletionId = mapper.readTree(r.getContentAsString(Charsets.UTF_8)).get("data").get("completionId").asLong()
	}

	@AfterEach
	fun cleanUp() {
		val ids = "SELECT id FROM users WHERE email LIKE 'contract-%' OR nickname LIKE '계약%' OR nickname LIKE '새이름%'"
		jdbc.update("DELETE FROM point_histories WHERE user_id IN ($ids)")
		jdbc.update("DELETE FROM completions WHERE user_id IN ($ids)")
		for (id in createdPlaces) jdbc.update("DELETE FROM places WHERE id = ?", id)
		createdPlaces.clear()
		jdbc.update("DELETE FROM user_interests WHERE user_id IN ($ids)")
		jdbc.update("DELETE FROM users WHERE email LIKE 'contract-%' OR nickname LIKE '계약%' OR nickname LIKE '새이름%'")
	}

	private fun newPlace(code: String, name: String, road: String?, phone: String?, radius: Int, dLat: Double, hot: HotType?): Long {
		val c = categories.findAll().first { it.code == code }
		val n = seq.incrementAndGet()
		val p = places.saveAndFlush(
			Place(
				category = c, name = name, address = "서울 종로구 $name 1", roadAddress = road, phone = phone,
				lat = baseLat + dLat, lng = baseLng, radiusM = radius, notifyRadiusM = radius + 100, rewardPoint = null,
				kakaoPlaceId = "contract-$n", kakaoPlaceUrl = "https://example.com/contract-$n",
				hotType = hot, active = true,
			),
		)
		return p.id!!.also { createdPlaces += it }
	}

	// ---- 호출 도우미

	private class Res(val status: Int, val body: String) {
		val node: JsonNode by lazy { ContractSupport.mapper.readTree(body) }
		val code: String get() = node.get("code").asString()
		fun data(name: String): JsonNode = node.get("data").get(name)
	}

	private fun send(
		spec: RequestSpec,
		token: String?,
		body: String? = spec.bodyJson(),
		query: Map<String, String> = spec.query(),
		vars: Map<String, Any> = emptyMap(),
	): Res {
		var path = spec.path
		for ((k, v) in vars) path = path.replace("{$k}", v.toString())
		val b = request(HttpMethod.valueOf(spec.method), path)
		for ((k, v) in query) b.param(k, v)
		if (token != null) b.header("Authorization", "Bearer $token")
		if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body)
		val r = mvc.perform(b).andReturn().response
		return Res(r.status, r.getContentAsString(Charsets.UTF_8))
	}

	private fun setBody(body: String, key: String, value: Any?): String {
		val n = mapper.readTree(body) as ObjectNode
		n.set(key, mapper.valueToTree<JsonNode>(value))
		return mapper.writeValueAsString(n)
	}

	/** 표본들의 상태/코드가 기대와 같은지 확인하고, 합친 shape를 골든과 비교한다. */
	private fun golden(endpoint: String, id: String, case: String, status: Int, code: String, vararg samples: Res) {
		for (s in samples) {
			if (s.status != status || s.code != code) {
				throw AssertionError("[$endpoint / $case] 기대 HTTP $status $code, 실제 HTTP ${s.status} ${s.body}")
			}
		}
		touched += "$id.$case.json"
		val shape = ContractSupport.shapeOf(*samples.map { it.body }.toTypedArray())
		ContractSupport.verify(id, Golden(endpoint, case, status, code, shape))?.let { throw AssertionError(it) }
	}

	private fun golden(spec: RequestSpec, case: String, status: Int, code: String, vararg samples: Res) =
		golden("${spec.method} ${spec.path}", spec.id, case, status, code, *samples)

	// ---- 계약 흐름

	@TestFactory
	fun responseContracts(): List<DynamicTest> {
		val steps = mutableListOf<DynamicTest>()
		fun step(name: String, block: () -> Unit) { steps += DynamicTest.dynamicTest(name, block) }

		val signup = ContractSupport.request("auth-signup")
		val login = ContractSupport.request("auth-login")
		val me = ContractSupport.request("users-me")
		val patch = ContractSupport.request("users-me-patch")
		val email = ContractSupport.request("auth-email-availability")
		val nick = ContractSupport.request("auth-nickname-availability")
		val cats = ContractSupport.request("place-categories")
		val search = ContractSupport.request("places")
		val detail = ContractSupport.request("places-detail")
		val recs = ContractSupport.request("places-recommendations")
		val complete = ContractSupport.request("places-completions")
		val points = ContractSupport.request("users-me-points")
		val histories = ContractSupport.request("users-me-point-histories")
		val log = ContractSupport.request("users-me-completions")
		val onbGet = ContractSupport.request("users-me-onboarding")
		val onbPatch = ContractSupport.request("users-me-onboarding-patch")
		val photo = ContractSupport.request("completions-photo")
		val stats = ContractSupport.request("users-me-stats")
		val rankUsers = ContractSupport.request("rankings-users")
		val rankPlaces = ContractSupport.request("rankings-places")
		val rankStreaks = ContractSupport.request("rankings-streaks")

		var token = ""
		var nullUserId = 0L
		var nullToken = "" // 닉네임/이메일이 없고 온보딩 전인 사용자(온보딩 게이트, 랭킹 me=null 표본)
		var firstCompletionId = 0L
		var revisitCompletionId = 0L
		var cafeCompletionId = 0L

		step("auth-signup") {
			val ok = send(signup, null)
			golden(signup, "success", 201, "COMMON201", ok)
			token = ok.data("accessToken").asString()
			val badToken = send(signup, null, setBody(signup.bodyJson()!!, "verificationToken", "garbage"))
			golden(signup, "error-EMAIL4003", 400, "EMAIL4003", badToken)
			// 같은 이메일(대소문자만 다름) + 다른 닉네임
			val dupEmail = send(signup, null, setBody(setBody(signup.bodyJson()!!, "verificationToken", "ok:Contract-New@example.com"), "nickname", "계약가입2"))
			golden(signup, "error-USER409", 409, "USER409", dupEmail)
			val dupNick = send(signup, null, setBody(signup.bodyJson()!!, "verificationToken", "ok:contract-new2@example.com"))
			golden(signup, "error-USER4092", 409, "USER4092", dupNick)
			val forbidden = send(signup, null, setBody(setBody(signup.bodyJson()!!, "verificationToken", "ok:contract-new3@example.com"), "nickname", "관리자님"))
			golden(signup, "error-USER4001", 400, "USER4001", forbidden)
			golden(signup, "error-COMMON400", 400, "COMMON400", send(signup, null, signup.bodyWithout("password")))
		}

		step("auth-login") {
			golden(login, "success", 200, "COMMON200", send(login, null))
			golden(login, "error-AUTH4011", 401, "AUTH4011", send(login, null, setBody(login.bodyJson()!!, "password", "Wrong1234!x")))
			golden(login, "error-COMMON400", 400, "COMMON400", send(login, null, login.bodyWithout("email")))
		}

		step("users-me") {
			val a = send(me, token)
			// 이메일/닉네임이 비어 있는(카카오 가입 직후 같은) 사용자: nullable 필드의 null 표본
			val u = userService.createEmailUser("contract-null@example.com", "x", "계약널")
			jdbc.update("UPDATE users SET email = NULL, nickname = NULL, onboarding_completed = FALSE WHERE id = ?", u.id)
			nullUserId = u.id!!
			nullToken = jwt.createAccessToken(nullUserId)
			val b = send(me, nullToken)
			golden(me, "success", 200, "COMMON200", a, b)
		}

		step("users-me-onboarding") {
			val before = send(onbGet, token)
			golden(onbPatch, "error-COMMON400", 400, "COMMON400", send(onbPatch, token, setBody(onbPatch.bodyJson()!!, "ageGroup", "NOPE")))
			val done = send(onbPatch, token)
			val after = send(onbGet, token)
			golden(onbGet, "success", 200, "COMMON200", before, after)
			// 닉네임이 없는 사용자의 온보딩 결과(nickname=null 표본)
			val u = userService.createEmailUser("contract-onb2@example.com", "x", "계약온보")
			jdbc.update("UPDATE users SET nickname = NULL WHERE id = ?", u.id)
			val noNick = send(onbPatch, jwt.createAccessToken(u.id!!))
			golden(onbPatch, "success", 200, "COMMON200", done, noNick)
		}

		step("users-me-patch") {
			golden(patch, "error-COMMON400", 400, "COMMON400", send(patch, token, "{}"))
			golden(patch, "error-USER4001", 400, "USER4001", send(patch, token, setBody(patch.bodyJson()!!, "nickname", "관리자님")))
			golden(patch, "error-USER4092", 409, "USER4092", send(patch, token, setBody(patch.bodyJson()!!, "nickname", otherNickname)))
			golden(patch, "success", 200, "COMMON200", send(patch, token))
			golden(patch, "error-USER4002", 400, "USER4002", send(patch, token, setBody(patch.bodyJson()!!, "nickname", "새이름2")))
		}

		step("auth-email-availability") {
			val taken = send(email, null)
			val free = send(email, null, query = mapOf("email" to "contract-free@example.com"))
			golden(email, "success", 200, "COMMON200", taken, free)
			golden(email, "error-COMMON400", 400, "COMMON400", send(email, null, query = mapOf("email" to "not-an-email")))
		}

		step("auth-nickname-availability") {
			val free = send(nick, null)
			val dup = send(nick, null, query = mapOf("nickname" to otherNickname))
			golden(nick, "success", 200, "COMMON200", free, dup)
			golden(nick, "error-COMMON400", 400, "COMMON400", send(nick, null, query = mapOf("nickname" to "")))
		}

		step("place-categories") {
			golden(cats, "success", 200, "COMMON200", send(cats, token))
		}

		step("places-completions") {
			val v = mapOf("placeId" to heritageId)
			val far = setBody(complete.bodyJson()!!, "lat", baseLat + 0.01)
			golden(complete, "error-COMPLETION4031", 403, "COMPLETION4031", send(complete, nullToken, vars = v))
			golden(complete, "error-PLACE404", 404, "PLACE404", send(complete, token, vars = mapOf("placeId" to 999_999_999L)))
			golden(complete, "error-COMPLETION4003", 400, "COMPLETION4003", send(complete, token, setBody(complete.bodyJson()!!, "isMockLocation", true), vars = v))
			golden(complete, "error-COMPLETION4002", 400, "COMPLETION4002", send(complete, token, setBody(complete.bodyJson()!!, "accuracy", 1000.0), vars = v))
			golden(complete, "error-COMPLETION4001", 400, "COMPLETION4001", send(complete, token, far, vars = v))
			golden(complete, "error-COMMON400", 400, "COMMON400", send(complete, token, complete.bodyWithout("accuracy"), vars = v))
			val first = send(complete, token, vars = v)
			golden(complete, "error-COMPLETION409", 409, "COMPLETION409", send(complete, token, vars = v))
			// 다음 날 재방문(isFirstVisit=false, photoDeadline=null) + 핫플 첫 방문(photoDeadline 있음)
			clock.setKst("2026-11-11T12:00:00")
			// 액세스 토큰은 Clock 기준으로 만료되므로 하루 뒤에는 다시 로그인한다
			token = send(login, null).data("accessToken").asString()
			nullToken = jwt.createAccessToken(nullUserId)
			val revisit = send(complete, token, vars = v)
			val cafe = send(complete, token, setBody(complete.bodyJson()!!, "lat", baseLat + 0.001), vars = mapOf("placeId" to cafeId))
			golden(complete, "success", 201, "COMMON201", first, revisit, cafe)
			firstCompletionId = first.data("completionId").asLong()
			revisitCompletionId = revisit.data("completionId").asLong()
			cafeCompletionId = cafe.data("completionId").asLong()
		}

		step("completions-photo") {
			fun upload(id: Long, bytes: ByteArray? = ImageTestSupport.jpeg(), type: String = "image/jpeg", part: String = photo.multipart!!.get("part").asString(), bearer: String? = token): Res {
				val b = multipart(photo.path.replace("{completionId}", id.toString()))
				if (bytes != null) b.file(MockMultipartFile(part, "quest.jpg", type, bytes))
				if (bearer != null) b.header("Authorization", "Bearer $bearer")
				val r = mvc.perform(b).andReturn().response
				return Res(r.status, r.getContentAsString(Charsets.UTF_8))
			}
			// 마감(인증 후 1시간)이 지난 어제 첫 방문 기록
			golden(photo, "error-COMPLETION4004", 400, "COMPLETION4004", upload(firstCompletionId))
			golden(photo, "error-COMPLETION404", 404, "COMPLETION404", upload(999_999_999L))
			golden(photo, "error-COMPLETION403", 403, "COMPLETION403", upload(otherCompletionId))
			golden(
				photo, "error-FILE4001", 400, "FILE4001",
				upload(cafeCompletionId, bytes = null), upload(cafeCompletionId, part = "file"),
				upload(cafeCompletionId, "plain text".toByteArray()), upload(cafeCompletionId, type = "image/gif"),
			)
			golden(photo, "error-FILE413", 413, "FILE413", upload(cafeCompletionId, ByteArray(10 * 1024 * 1024 + 1)))
			// 첫 방문(보너스 50)과 재방문(보너스 0) 표본
			val bonus = upload(cafeCompletionId)
			val noBonus = upload(revisitCompletionId)
			golden(photo, "success", 201, "COMMON201", bonus, noBonus)
			golden(photo, "error-COMPLETION4091", 409, "COMPLETION4091", upload(cafeCompletionId))
		}

		step("places") {
			val withCategory = send(search, token)
			val all = send(search, token, query = search.query() - "category")
			golden(search, "success", 200, "COMMON200", withCategory, all)
			golden(search, "error-COMMON400", 400, "COMMON400", send(search, token, query = search.query() - "lat"))
		}

		step("places-detail") {
			val samples = listOf(heritageId, cafeId, dormantId).map { send(detail, token, vars = mapOf("placeId" to it)) }
			golden(detail, "success", 200, "COMMON200", *samples.toTypedArray())
			golden(detail, "error-PLACE404", 404, "PLACE404", send(detail, token, vars = mapOf("placeId" to 999_999_999L)))
		}

		step("places-recommendations") {
			golden(recs, "success", 200, "COMMON200", send(recs, token))
			golden(recs, "error-COMMON400", 400, "COMMON400", send(recs, token, query = recs.query() - "lat"))
		}

		step("users-me-points") {
			golden(points, "success", 200, "COMMON200", send(points, token))
		}

		step("users-me-point-histories") {
			val full = send(histories, token)
			val paged = send(histories, token, query = mapOf("size" to "1"))
			golden(histories, "success", 200, "COMMON200", full, paged)
			golden(histories, "error-COMMON400", 400, "COMMON400", send(histories, token, query = histories.query() + ("size" to "0")))
		}

		step("users-me-completions") {
			val full = send(log, token)
			val paged = send(log, token, query = mapOf("size" to "1"))
			golden(log, "success", 200, "COMMON200", full, paged)
			golden(log, "error-COMMON400", 400, "COMMON400", send(log, token, query = log.query() + ("size" to "0")))
		}

		step("users-me-stats") {
			val week = send(stats, token)
			val month = send(stats, token, query = mapOf("period" to "MONTH"))
			val year = send(stats, token, query = mapOf("period" to "YEAR"))
			golden(stats, "success", 200, "COMMON200", week, month, year)
			golden(stats, "error-COMMON400", 400, "COMMON400", send(stats, token, query = mapOf("period" to "WEEK", "date" to "2099-01-01")))
		}

		step("rankings-users") {
			golden(rankUsers, "success", 200, "COMMON200", send(rankUsers, token), send(rankUsers, nullToken))
			golden(rankUsers, "error-COMMON400", 400, "COMMON400", send(rankUsers, token, query = rankUsers.query() + ("size" to "0")))
		}

		step("rankings-places") {
			val withCategory = send(rankPlaces, token)
			val noQuery = send(rankPlaces, token, query = emptyMap())
			golden(rankPlaces, "success", 200, "COMMON200", withCategory, noQuery)
			golden(rankPlaces, "error-COMMON400", 400, "COMMON400", send(rankPlaces, token, query = mapOf("category" to "NOPE")))
		}

		step("rankings-streaks") {
			golden(rankStreaks, "success", 200, "COMMON200", send(rankStreaks, token), send(rankStreaks, nullToken))
			golden(rankStreaks, "error-COMMON400", 400, "COMMON400", send(rankStreaks, token, query = mapOf("size" to "0")))
		}

		step("AUTH401: 토큰 없이 인증 엔드포인트 호출") {
			val samples = ContractSupport.requestIds().filter { it !in ContractSupport.authFlowIds }.map { ContractSupport.request(it) }.filter { it.auth }.map { spec ->
				val r = send(spec, null, vars = mapOf("placeId" to heritageId, "completionId" to cafeCompletionId))
				if (r.status != 401 || r.code != "AUTH401") {
					throw AssertionError("[${spec.method} ${spec.path}] 토큰 없이 호출했는데 AUTH401이 아니다: HTTP ${r.status} ${r.body}")
				}
				r
			}
			golden("(auth: true 인 모든 엔드포인트)", "common", "error-AUTH401", 401, "AUTH401", *samples.toTypedArray())
		}

		step("auth: false 엔드포인트는 토큰 없이 401이 아니다") {
			for (spec in ContractSupport.requestIds().filter { it !in ContractSupport.authFlowIds }.map { ContractSupport.request(it) }.filter { !it.auth }) {
				val r = send(spec, null)
				if (r.status == 401 && r.code == "AUTH401") throw AssertionError("[${spec.method} ${spec.path}] auth=false 인데 AUTH401")
			}
		}

		step("고아 골든 없음(코드가 더 이상 만들지 않는 응답 골든)") {
			val onDisk = Files.list(ContractSupport.responsesDir).use { s -> s.map { it.fileName.toString() }.toList() }
			val owned = onDisk.filter { it.substringBefore('.') !in ContractSupport.authFlowIds } // 인증 흐름 골든은 ContractAuthFlowTest가 관리
			val orphans = owned.filter { it !in touched }
			if (orphans.isNotEmpty()) {
				if (ContractSupport.updateMode) {
					orphans.forEach { Files.delete(ContractSupport.responsesDir.resolve(it)) }
				} else {
					throw AssertionError("테스트가 더 이상 만들지 않는 응답 골든: $orphans (엔드포인트를 지웠으면 -Dcontract.update=true 로 정리)")
				}
			}
			val unknownIds = onDisk.map { it.substringBefore('.') }.toSet() - ContractSupport.requestIds().toSet() - "common"
			if (unknownIds.isNotEmpty()) throw AssertionError("요청 골든(requests 폴더의 JSON)이 없는 응답 골든 id: $unknownIds")
		}
		return steps
	}

	// ---- 요청 계약: 필수 키가 빠지면 COMMON400 + data.errors[].field

	@TestFactory
	fun requiredFieldsAreEnforced(): List<DynamicTest> {
		val tests = mutableListOf<DynamicTest>()
		val u = userService.createEmailUser("contract-required@example.com", "x", "계약필수")
		val token = jwt.createAccessToken(u.id!!)
		val vars = mapOf("placeId" to heritageId, "completionId" to cafeId)

		fun expect400(spec: RequestSpec, what: String, r: Res, field: String) {
			val fields = r.node.get("data")?.get("errors")?.let { e -> with(ContractSupport) { e.items() } }?.map { it.get("field").asString() } ?: emptyList()
			if (r.status != 400 || r.code != "COMMON400" || field !in fields) {
				throw AssertionError("[${spec.method} ${spec.path}] $what 빠졌는데 COMMON400 + errors[].field=$field 가 아니다: HTTP ${r.status} ${r.body}")
			}
		}

		for (id in ContractSupport.requestIds()) {
			val spec = ContractSupport.request(id)
			spec.requiredBody.forEach { key ->
				tests += DynamicTest.dynamicTest("$id: 본문 $key 누락 -> COMMON400") {
					val r = send(spec, token, body = spec.bodyWithout(key), vars = vars)
					expect400(spec, "본문 키 '$key' 가", r, key)
				}
			}
			for (q in spec.requiredQuery) {
				tests += DynamicTest.dynamicTest("$id: 쿼리 $q 누락 -> COMMON400") {
					val r = send(spec, token, query = spec.query() - q, vars = vars)
					expect400(spec, "쿼리 '$q' 가", r, q)
				}
			}
		}
		return tests
	}
}
