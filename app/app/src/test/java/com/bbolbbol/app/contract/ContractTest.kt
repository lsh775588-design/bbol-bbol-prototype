package com.bbolbbol.app.contract

import com.bbolbbol.app.Fixture
import com.bbolbbol.app.data.api.ApiEnvelope
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.AvailabilityResult
import com.bbolbbol.app.data.api.BbolApi
import com.bbolbbol.app.data.api.CategoryListResponse
import com.bbolbbol.app.data.api.CompletionLogPage
import com.bbolbbol.app.data.api.CompletionRequest
import com.bbolbbol.app.data.api.CompletionResponse
import com.bbolbbol.app.data.api.LoginRequest
import com.bbolbbol.app.data.api.MeResponse
import com.bbolbbol.app.data.api.NicknameChangeRequest
import com.bbolbbol.app.data.api.NicknameChangeResponse
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.api.PlaceListResponse
import com.bbolbbol.app.data.api.PointHistoryPage
import com.bbolbbol.app.data.api.PointSummary
import com.bbolbbol.app.data.api.RecommendationListResponse
import com.bbolbbol.app.data.api.SignupRequest
import com.bbolbbol.app.data.api.TokenResponse
import com.bbolbbol.app.data.api.ValidationErrors
import com.bbolbbol.app.data.api.parseFailure
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 앱-서버 계약 테스트(앱 쪽). 서버가 만든 골든(src/test/resources/contract, scripts/sync-contract.mjs로 복사)을
 * 앱의 실제 DTO, Json 설정, Retrofit 인터페이스에 대어 본다. JVM 단위 테스트라 기기가 필요 없다.
 * 사용법과 새 엔드포인트 추가 절차는 docs/contract-testing.md.
 */
class ContractTest {

    // ---- 엔드포인트 -> 앱 DTO 등록. 새 엔드포인트의 골든이 생기면 여기에 등록해야 테스트가 통과한다.
    private val responseDtos: Map<String, KSerializer<ApiEnvelope<*>>> = mapOf(
        "auth-signup" to env(TokenResponse.serializer()),
        "auth-login" to env(TokenResponse.serializer()),
        "users-me" to env(MeResponse.serializer()),
        "users-me-patch" to env(NicknameChangeResponse.serializer()),
        "auth-email-availability" to env(AvailabilityResult.serializer()),
        "auth-nickname-availability" to env(AvailabilityResult.serializer()),
        "place-categories" to env(CategoryListResponse.serializer()),
        "places" to env(PlaceListResponse.serializer()),
        "places-detail" to env(PlaceDetail.serializer()),
        "places-recommendations" to env(RecommendationListResponse.serializer()),
        "places-completions" to env(CompletionResponse.serializer()),
        "users-me-points" to env(PointSummary.serializer()),
        "users-me-point-histories" to env(PointHistoryPage.serializer()),
        "users-me-completions" to env(CompletionLogPage.serializer()),
    )

    // ---- 엔드포인트 -> 앱이 실제로 호출하는 BbolApi 메서드(선택 인자는 모두 채워서 앱이 보낼 수 있는 쿼리를 전부 낸다)
    private val calls: Map<String, suspend (BbolApi) -> Unit> = mapOf(
        "auth-signup" to { it.signup(SignupRequest("tok", "pw", "nick")); Unit },
        "auth-login" to { it.login(LoginRequest("e@x.com", "pw")); Unit },
        "users-me" to { it.me(); Unit },
        "users-me-patch" to { it.changeNickname(NicknameChangeRequest("nick")); Unit },
        "auth-email-availability" to { it.emailAvailability("e@x.com"); Unit },
        "auth-nickname-availability" to { it.nicknameAvailability("nick"); Unit },
        "place-categories" to { it.categories(); Unit },
        "places" to { it.places(1.0, 2.0, 3, "CAFE", 4); Unit },
        "places-detail" to { it.place(5L); Unit },
        "places-recommendations" to { it.recommendations(1.0, 2.0, 3); Unit },
        "places-completions" to { it.complete(5L, CompletionRequest(1.0, 2.0, 3.0, false)); Unit },
        "users-me-points" to { it.points(); Unit },
        "users-me-point-histories" to { it.pointHistories(1L, 2, "QUEST_COMPLETE"); Unit },
        "users-me-completions" to { it.completions("2026-01-01", "2026-01-02", "CAFE", 1L, 2); Unit },
    )

    /**
     * 서버가 보내지만 앱 DTO가 일부러 선언하지 않는 키. "엔드포인트 id:경로" -> 이유.
     * 목록에 있는데 더 이상 필요 없으면(앱이 선언했거나 서버가 안 보냄) 테스트가 실패한다.
     */
    private val intentionallyIgnored: Map<String, String> = emptyMap()

    /**
     * 서버가 null을 보낼 수 있는데 앱 DTO는 non-null + 기본값인 키. ApiJson의 coerceInputValues=true 때문에
     * null이 기본값으로 바뀌어 읽힌다("" 같은 값이 서버의 null을 가린다). "엔드포인트 id:경로" -> 이유.
     */
    private val coercedNulls: Map<String, String> = mapOf(
        "users-me:\$.data.email" to "서버는 소셜 가입자 등에서 email=null 가능. 앱은 \"\"로 읽는다(화면에서 빈 값 처리).",
        "users-me:\$.data.nickname" to "서버는 닉네임 미설정 사용자에게 nickname=null. 앱은 \"\"로 읽고 닉네임 입력 흐름으로 보낸다.",
    )

    private val usedIgnored = mutableSetOf<String>()
    private val usedCoerced = mutableSetOf<String>()

    private fun env(serializer: KSerializer<*>): KSerializer<ApiEnvelope<*>> {
        @Suppress("UNCHECKED_CAST")
        return ApiEnvelope.serializer(serializer as KSerializer<Any>) as KSerializer<ApiEnvelope<*>>
    }

    private val successGoldens get() = ContractFiles.responses().filter { !it.isError }
    private val errorGoldens get() = ContractFiles.responses().filter { it.isError }

    private fun assertNoProblems(title: String, problems: List<String>) {
        assertTrue("$title\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }

    // ---- (0) 등록 누락 점검

    @Test
    fun everyGoldenEndpointIsRegisteredInTheApp() {
        val problems = mutableListOf<String>()
        val requestIds = ContractFiles.requests().map { it.id }.toSet()
        for (id in successGoldens.map { it.id }.toSet()) {
            if (id !in responseDtos) problems += "응답 골든 '$id'에 대응하는 앱 DTO가 ContractTest.responseDtos에 등록되지 않았다(앱이 아직 이 응답을 모른다)"
        }
        for (id in requestIds) {
            if (id !in calls) problems += "요청 골든 '$id'를 보내는 BbolApi 호출이 ContractTest.calls에 등록되지 않았다"
        }
        for (id in responseDtos.keys + calls.keys) {
            if (id !in requestIds) problems += "등록된 '$id'에 대한 골든이 서버에서 오지 않았다(scripts/sync-contract.mjs 실행 필요?)"
        }
        assertNoProblems("골든과 앱 등록이 어긋남", problems)
    }

    // ---- (a) 골든 모양의 표본을 앱의 실제 DTO와 Json 설정으로 읽는다

    @Test
    fun responseSamplesParseWithRealDtos() {
        val problems = mutableListOf<String>()
        for (g in successGoldens) {
            val ser = responseDtos[g.id] ?: continue
            for (nulls in listOf(false, true)) {
                val text = ContractFiles.sample(g, nullVariant = nulls)
                val which = if (nulls) "null 표본" else "값 표본"
                try {
                    val env = ApiJson.decodeFromString(ser, text)
                    if (env.data == null) problems += "${g.label} data가 null로 읽혔다($which)"
                    if (env.code != g.code) problems += "${g.label} code가 '${g.code}'로 읽혀야 하는데 '${env.code}'"
                } catch (e: Exception) {
                    problems += "${g.label} 파싱 실패($which): ${e.message?.lineSequence()?.first()}"
                }
            }
        }
        assertNoProblems("앱 DTO가 서버 응답 모양을 읽지 못한다", problems)
    }

    // ---- (b) 키 단위 양방향 점검(kotlinx-serialization 디스크립터)

    @Test
    fun dtoFieldsAndGoldenKeysMatchInBothDirections() {
        val problems = mutableListOf<String>()
        for (g in successGoldens) {
            val ser = responseDtos[g.id] ?: continue
            checkObject(g, ser.descriptor, "$", problems)
        }
        for (k in intentionallyIgnored.keys - usedIgnored) problems += "무시 목록 항목이 더 이상 필요 없다(지워라): $k"
        for (k in coercedNulls.keys - usedCoerced) problems += "coerce 허용 목록 항목이 더 이상 필요 없다(지워라): $k"
        assertNoProblems("서버 응답 키와 앱 DTO가 어긋남", problems)
    }

    private fun checkObject(g: GoldenResponse, d: SerialDescriptor, path: String, problems: MutableList<String>) {
        val names = g.childNames(path)
        for (name in names) {
            val child = "$path.$name"
            val key = "${g.id}:$child"
            val idx = d.getElementIndex(name)
            if (idx == CompositeDecoder.UNKNOWN_NAME) {
                if (key in intentionallyIgnored) usedIgnored += key
                else problems += "${g.label} 서버 키 '$child'를 앱 DTO(${d.serialName})가 선언하지 않는다. 쓸 키면 DTO에 추가, 일부러 안 쓰면 intentionallyIgnored에 이유와 함께 등록"
                continue
            }
            checkValue(g, d.getElementDescriptor(idx), child, d.isElementOptional(idx), problems)
        }
        for (i in 0 until d.elementsCount) {
            val name = d.getElementName(i)
            if (name in names) continue
            val ed = d.getElementDescriptor(i)
            if (!d.isElementOptional(i) && !ed.isNullable) {
                problems += "${g.label} 앱 DTO(${d.serialName})의 필수 프로퍼티 '$name'(기본값 없는 non-null)이 서버 응답 '$path.$name'에 없다 -> 서버가 이 키를 안 주면 앱 파싱이 깨진다"
            }
        }
    }

    private fun checkValue(g: GoldenResponse, d: SerialDescriptor, path: String, optional: Boolean, problems: MutableList<String>) {
        val types = g.types(path)
        val nonNull = types - "null" - "absent"
        val serverNullable = "null" in types || "absent" in types
        val key = "${g.id}:$path"
        if (serverNullable && !d.isNullable) {
            if (optional && key in coercedNulls) {
                usedCoerced += key
            } else {
                problems += "${g.label} 서버가 '$path'에 null/생략을 줄 수 있는데(${g.shape[path]}) 앱 프로퍼티는 non-null" +
                    if (optional) "(기본값만 있어 ApiJson.coerceInputValues에 의존한다). 의도면 coercedNulls에 이유와 함께 등록, 아니면 DTO를 nullable로"
                    else ", 기본값도 없어 파싱이 깨진다"
            }
        }
        for (t in nonNull) {
            val ok = when (t) {
                "string", "datetime" -> d.kind == PrimitiveKind.STRING || d.kind == SerialKind.ENUM
                "integer" -> d.kind in setOf(PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG)
                "decimal" -> d.kind in setOf(PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT)
                "boolean" -> d.kind == PrimitiveKind.BOOLEAN
                "object" -> d.kind == StructureKind.CLASS || d.kind == StructureKind.MAP
                "array", "array:empty" -> d.kind == StructureKind.LIST
                else -> false
            }
            if (!ok) problems += "${g.label} '$path' 서버 타입 $t 인데 앱은 ${d.serialName}(${d.kind})"
        }
        if ("object" in nonNull && d.kind == StructureKind.CLASS) checkObject(g, d, path, problems)
        if ("array" in nonNull && d.kind == StructureKind.LIST) {
            val el = d.getElementDescriptor(0)
            val elTypes = g.types("$path[]") - "null" - "absent"
            if ("object" in elTypes && el.kind == StructureKind.CLASS) checkObject(g, el, "$path[]", problems)
            else if (elTypes.isNotEmpty()) checkValue(g, el, "$path[]", false, problems)
        }
    }

    // ---- 에러 응답: 앱의 실제 parseFailure가 골든 모양에서 코드와 필드 오류를 읽어내는지

    @Test
    fun errorSamplesAreUnderstoodByParseFailure() {
        val problems = mutableListOf<String>()
        for (g in errorGoldens) {
            val text = ContractFiles.sample(g, nullVariant = false)
            val f = parseFailure(g.httpStatus, text)
            if (f.code != g.code) problems += "${g.label} code '${g.code}'를 앱이 '${f.code}'로 읽었다"
            if (f.httpStatus != g.httpStatus) problems += "${g.label} HTTP 상태 불일치"
            if (g.code == "COMMON400") {
                if (f.fieldErrors.isEmpty()) problems += "${g.label} data.errors[]를 앱이 읽지 못했다(fieldErrors 비어 있음)"
                checkObject(g, ApiEnvelope.serializer(ValidationErrors.serializer()).descriptor, "$", problems)
            }
        }
        assertNoProblems("앱이 서버 에러 응답을 읽지 못한다", problems)
    }

    /** 앱이 사용자 문구를 매핑해 둔 서버 코드가 서버 골든에 실제로 있는지(오타, 서버의 코드 변경 감지). */
    @Test
    fun errorCodesTheAppHandlesExistInGoldens() {
        val serverCodes = ContractFiles.responses().map { it.code }.toSet()
        // 골든이 다루지 않는 코드: 이유를 적어 둔다
        val notInScope = mapOf(
            "USER404" to "탈퇴/없는 사용자. 정상 흐름으로 만들기 어렵다(상태 조작 필요)",
            "COMMON404" to "존재하지 않는 경로. 앱이 호출하는 경로에서는 발생하지 않는다",
            "COMMON500" to "서버 내부 오류. 의도적으로 만들지 않는다",
        )
        val handled = listOf(
            "AUTH401", "AUTH4011", "USER404", "USER409", "USER4001", "USER4002", "USER4092", "EMAIL4003", "PLACE404",
            "COMPLETION4001", "COMPLETION4002", "COMPLETION4003", "COMPLETION409", "COMMON400", "COMMON404", "COMMON500",
        )
        val missing = handled.filter { it !in serverCodes && it !in notInScope }
            .map { "앱 ErrorMessages가 '$it'를 처리하지만 서버 응답 골든에 없다(서버가 코드를 바꿨거나 골든에 케이스 추가 필요)" }
        val unmapped = handled.filter { ErrorMessages.forCode(it) == null }
            .map { "테스트의 handled 목록에 있는 '$it'를 ErrorMessages가 모른다(목록 갱신)" }
        assertNoProblems("에러 코드 불일치", missing + unmapped)
    }

    // ---- (c) 앱의 실제 요청이 요청 골든과 같은 모양인지

    @Test
    fun appRequestsMatchRequestGoldens() {
        val problems = mutableListOf<String>()
        val fx = Fixture()
        fx.session.saveTokens("access-token", "refresh-token")
        try {
            for (g in ContractFiles.requests()) {
                val call = calls[g.id] ?: continue
                fx.enqueue(200, "{}")
                runBlocking { call(fx.api) }
                val req = fx.server.takeRequest(2, TimeUnit.SECONDS)
                if (req == null) {
                    problems += "${g.label} 앱이 요청을 보내지 않았다"
                    continue
                }
                if (req.method != g.method) problems += "${g.label} HTTP 메서드: 골든 ${g.method}, 앱 ${req.method}"
                val actualPath = req.path.orEmpty().substringBefore("?")
                val pathRegex = Regex(g.path.split(Regex("\\{[^}]+}")).joinToString("[^/]+") { Regex.escape(it) })
                if (!pathRegex.matches(actualPath)) problems += "${g.label} 경로: 골든 ${g.path}, 앱 $actualPath"
                val appQuery = req.requestUrl!!.queryParameterNames
                for (q in g.query.keys - appQuery) problems += "${g.label} 쿼리 '$q'를 앱이 보내지 않는다(골든에는 있음)"
                for (q in appQuery - g.query.keys) problems += "${g.label} 쿼리 '$q'를 앱이 보내지만 골든에 없다(서버가 모르는 파라미터일 수 있음)"
                val hasAuth = req.getHeader("Authorization")?.startsWith("Bearer ") == true
                if (g.auth && !hasAuth) problems += "${g.label} 골든은 인증 필요인데 앱이 Authorization: Bearer를 붙이지 않는다"
                if (!g.auth && hasAuth) problems += "${g.label} 골든은 인증 불필요인데 앱이 토큰을 보낸다"
                val goldenBody = g.body
                val bodyText = req.body.readUtf8()
                if (goldenBody == null) {
                    if (bodyText.isNotEmpty()) problems += "${g.label} 골든에는 본문이 없는데 앱이 본문을 보낸다: $bodyText"
                } else {
                    val appBody = try {
                        Json.parseToJsonElement(bodyText).jsonObject
                    } catch (e: Exception) {
                        null
                    }
                    if (appBody == null) {
                        problems += "${g.label} 앱 본문이 JSON 객체가 아니다: $bodyText"
                    } else {
                        val want = ContractFiles.bodyShape(goldenBody)
                        val got = ContractFiles.bodyShape(appBody)
                        for ((k, t) in want) {
                            val a = got[k]
                            if (a == null) problems += "${g.label} 본문 키 '$k'($t)를 앱이 보내지 않는다"
                            else if (a != t) problems += "${g.label} 본문 키 '$k' 타입: 골든 $t, 앱 $a"
                        }
                        for (k in got.keys - want.keys) problems += "${g.label} 본문 키 '$k'를 앱이 보내지만 골든에 없다(서버가 무시하거나 거부할 수 있음)"
                        if (req.getHeader("Content-Type")?.startsWith("application/json") != true) {
                            problems += "${g.label} Content-Type이 application/json이 아니다: ${req.getHeader("Content-Type")}"
                        }
                    }
                }
            }
        } finally {
            fx.shutdown()
        }
        assertNoProblems("앱이 보내는 요청이 서버 요청 골든과 다르다", problems)
    }

    /** 서버 골든의 필수 쿼리(없으면 COMMON400)를 앱이 항상 보내는지. */
    @Test
    fun appAlwaysSendsServerRequiredQueryParams() {
        val problems = mutableListOf<String>()
        val fx = Fixture()
        fx.session.saveTokens("a", "r")
        try {
            for (g in ContractFiles.requests().filter { it.requiredQuery.isNotEmpty() }) {
                val call = calls[g.id] ?: continue
                fx.enqueue(200, "{}")
                runBlocking { call(fx.api) }
                val names = fx.server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!.queryParameterNames
                for (q in g.requiredQuery - names) problems += "${g.label} 서버 필수 쿼리 '$q'를 앱이 보내지 않는다"
            }
        } finally {
            fx.shutdown()
        }
        assertNoProblems("필수 쿼리 누락", problems)
    }
}
