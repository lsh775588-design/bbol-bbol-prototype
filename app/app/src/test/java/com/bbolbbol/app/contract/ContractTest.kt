package com.bbolbbol.app.contract

import com.bbolbbol.app.Fixture
import com.bbolbbol.app.data.api.ApiEnvelope
import com.bbolbbol.app.data.api.AgeGroups
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthExtraApi
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.EmailConfirmRequest
import com.bbolbbol.app.data.api.EmailConfirmResponse
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.api.EmailSendRequest
import com.bbolbbol.app.data.api.EmailSendResponse
import com.bbolbbol.app.data.api.Genders
import com.bbolbbol.app.data.api.KakaoLoginRequest
import com.bbolbbol.app.data.api.KakaoLoginResponse
import com.bbolbbol.app.data.api.OnboardingInfo
import com.bbolbbol.app.data.api.OnboardingRequest
import com.bbolbbol.app.data.api.OnboardingResult
import com.bbolbbol.app.data.api.PasswordResetRequest
import com.bbolbbol.app.data.api.PhotoUploadResponse
import com.bbolbbol.app.data.api.PlaceRankingResponse
import com.bbolbbol.app.data.api.ReissueApi
import com.bbolbbol.app.data.api.ReissueRequest
import com.bbolbbol.app.data.api.ReissueResponse
import com.bbolbbol.app.data.api.StatsApi
import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.api.StreakRankingResponse
import com.bbolbbol.app.data.api.UserRankingResponse
import com.bbolbbol.app.data.api.apiCallNoData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        "auth-email-verifications" to env(EmailSendResponse.serializer()),
        "auth-email-verifications-confirm" to env(EmailConfirmResponse.serializer()),
        "auth-reissue" to env(ReissueResponse.serializer()),
        "auth-kakao" to env(KakaoLoginResponse.serializer()),
        "users-me-onboarding" to env(OnboardingInfo.serializer()),
        "users-me-onboarding-patch" to env(OnboardingResult.serializer()),
        "completions-photo" to env(PhotoUploadResponse.serializer()),
        "users-me-stats" to env(StatsResponse.serializer()),
        "rankings-users" to env(UserRankingResponse.serializer()),
        "rankings-places" to env(PlaceRankingResponse.serializer()),
        "rankings-streaks" to env(StreakRankingResponse.serializer()),
    )

    /** 성공해도 data가 null이라 DTO가 없는 엔드포인트. 앱은 apiCallNoData로 본문을 읽지 않고 성공 처리한다. */
    private val noDataResponses = setOf("auth-password-reset", "auth-logout", "users-me-delete")

    /** BbolApi 외에 앱이 쓰는 Retrofit 인터페이스들(실제 AppContainer처럼 같은 클라이언트와 Json 설정). */
    private class Apis(fx: Fixture) {
        private val retrofit = Retrofit.Builder()
            .baseUrl(fx.server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(fx.session)).build())
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val bbol: BbolApi = fx.api
        val extra: AuthExtraApi = retrofit.create(AuthExtraApi::class.java)
        val stats: StatsApi = retrofit.create(StatsApi::class.java)
        val reissue: ReissueApi = retrofit.create(ReissueApi::class.java)
    }

    // ---- 엔드포인트 -> 앱이 실제로 호출하는 BbolApi 메서드(선택 인자는 모두 채워서 앱이 보낼 수 있는 쿼리를 전부 낸다)
    private val calls: Map<String, suspend (Apis) -> Unit> = mapOf(
        "auth-signup" to { it.bbol.signup(SignupRequest("tok", "pw", "nick")); Unit },
        "auth-login" to { it.bbol.login(LoginRequest("e@x.com", "pw")); Unit },
        "users-me" to { it.bbol.me(); Unit },
        "users-me-patch" to { it.bbol.changeNickname(NicknameChangeRequest("nick")); Unit },
        "auth-email-availability" to { it.bbol.emailAvailability("e@x.com"); Unit },
        "auth-nickname-availability" to { it.bbol.nicknameAvailability("nick"); Unit },
        "place-categories" to { it.bbol.categories(); Unit },
        "places" to { it.bbol.places(1.0, 2.0, 3, "CAFE", 4); Unit },
        "places-detail" to { it.bbol.place(5L); Unit },
        "places-recommendations" to { it.bbol.recommendations(1.0, 2.0, 3); Unit },
        "places-completions" to { it.bbol.complete(5L, CompletionRequest(1.0, 2.0, 3.0, false)); Unit },
        "users-me-points" to { it.bbol.points(); Unit },
        "users-me-point-histories" to { it.bbol.pointHistories(1L, 2, "QUEST_COMPLETE"); Unit },
        "users-me-completions" to { it.bbol.completions("2026-01-01", "2026-01-02", "CAFE", 1L, 2); Unit },
        "auth-email-verifications" to { it.extra.sendCode(EmailSendRequest("e@x.com", EmailPurpose.SIGNUP.wire)); Unit },
        "auth-email-verifications-confirm" to { it.extra.confirmCode(EmailConfirmRequest("e@x.com", EmailPurpose.SIGNUP.wire, "123456")); Unit },
        "auth-password-reset" to { it.extra.passwordReset(PasswordResetRequest("tok", "pw")); Unit },
        "auth-reissue" to { it.reissue.reissue(ReissueRequest("refresh")).execute(); Unit },
        "auth-logout" to { it.extra.logout(); Unit },
        "auth-kakao" to { it.extra.kakao(KakaoLoginRequest("kakao-token")); Unit },
        "users-me-delete" to { it.extra.withdraw(); Unit },
        "users-me-onboarding" to { it.extra.onboarding(); Unit },
        // nickname은 카카오 첫 온보딩 전용이라 골든(이메일 사용자 표본)에 없다
        "users-me-onboarding-patch" to { it.extra.saveOnboarding(OnboardingRequest(null, "TWENTIES", "FEMALE", listOf("CAFE", "HERITAGE"))); Unit },
        "completions-photo" to {
            it.stats.uploadPhoto(5L, MultipartBody.Part.createFormData("photo", "photo.jpg", byteArrayOf(1, 2, 3).toRequestBody("image/jpeg".toMediaType())))
            Unit
        },
        "users-me-stats" to { it.stats.stats("WEEK", "2026-11-11"); Unit },
        "rankings-users" to { it.stats.userRankings("WEEK", 10); Unit },
        "rankings-places" to { it.stats.placeRankings("WEEK", "CAFE", 10); Unit },
        "rankings-streaks" to { it.stats.streakRankings(10); Unit },
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

    /**
     * 앱 미등록(예정): 서버 골든은 있지만 앱이 아직 DTO와 BbolApi 호출을 만들지 않은 엔드포인트. "엔드포인트 id" -> 사유.
     * 현재 비어 있다: 서버 골든 전부를 앱이 등록했다(사진 업로드는 StatsApi.uploadPhoto로 등록).
     * - 여기 있는 id는 등록 누락으로 실패하지 않고 테스트 출력에 목록으로 찍힌다.
     * - 앱이 DTO를 만들어 responseDtos와 calls에 등록하면 **이 목록에서 그 id를 지워야** 한다(등록과 목록이 겹치면 실패한다).
     * - 골든에서 사라진 id가 여기 남아 있어도 실패한다(서버가 엔드포인트를 지웠거나 이름을 바꿨다).
     * - 골든이 있는데 등록도 목록도 아니면 실패한다(새 엔드포인트를 앱이 모르는 채 지나가지 못하게).
     */
    private val notYetRegistered: Map<String, String> = emptyMap()

    @Test
    fun everyGoldenEndpointIsRegisteredInTheApp() {
        val problems = mutableListOf<String>()
        val goldenIds = (ContractFiles.requests().map { it.id } + ContractFiles.responses().map { it.id })
            .filter { it != "common" }.toSet()
        for (id in goldenIds) {
            val dto = id in responseDtos || id in noDataResponses
            val call = id in calls
            val listed = id in notYetRegistered
            when {
                dto && call && listed -> problems += "'$id'는 앱에 등록됐으니 ContractTest.notYetRegistered에서 지워라"
                dto && call -> {}
                !dto && !call && listed -> {}
                !dto && !call -> problems += "서버 골든 '$id'를 앱이 모른다: DTO와 BbolApi 호출을 만들어 responseDtos, calls에 등록하거나, 아직이면 notYetRegistered에 사유와 함께 추가"
                !dto -> problems += "'$id'는 calls에만 등록됐다. 응답 DTO도 responseDtos에 등록하라"
                else -> problems += "'$id'는 responseDtos에만 등록됐다. BbolApi 호출도 calls에 등록하라"
            }
        }
        for (id in notYetRegistered.keys - goldenIds) problems += "notYetRegistered의 '$id'가 서버 골든에 없다(서버가 지웠거나 이름을 바꿨다). 목록에서 지우거나 이름을 맞춰라"
        for (id in (responseDtos.keys + calls.keys) - goldenIds) {
            problems += "등록된 '$id'에 대한 골든이 서버에서 오지 않았다(scripts/sync-contract.mjs 실행 필요?)"
        }
        // 실패하지 않지만 눈에 띄게 남긴다
        println("[계약] 앱 미등록(예정) 엔드포인트 ${notYetRegistered.size}개: ${notYetRegistered.keys.sorted()}")
        for ((id, why) in notYetRegistered.toSortedMap()) println("[계약]   $id - $why")
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
                "string", "date", "datetime" -> d.kind == PrimitiveKind.STRING || d.kind == SerialKind.ENUM
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
            if (g.code == "EMAIL4001" && f.remainingAttempts == null) problems += "${g.label} data.remainingAttempts를 앱이 읽지 못했다"
            if (g.code == "COMMON400") {
                if (f.fieldErrors.isEmpty()) problems += "${g.label} data.errors[]를 앱이 읽지 못했다(fieldErrors 비어 있음)"
                checkObject(g, ApiEnvelope.serializer(ValidationErrors.serializer()).descriptor, "$", problems)
            }
            if (g.code == "EMAIL4001") {
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
        val apis = Apis(fx)
        fx.session.saveTokens("access-token", "refresh-token")
        try {
            for (g in ContractFiles.requests()) {
                val call = calls[g.id] ?: continue
                fx.enqueue(200, "{}")
                runBlocking { call(apis) }
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
                val mp = g.multipart
                if (mp != null) {
                    val part = mp.getValue("part").jsonPrimitive.content
                    val types = mp.getValue("contentTypes").jsonArray.map { it.jsonPrimitive.content }
                    if (req.getHeader("Content-Type")?.startsWith("multipart/form-data") != true) {
                        problems += "${g.label} Content-Type이 multipart/form-data가 아니다: ${req.getHeader("Content-Type")}"
                    }
                    if (!bodyText.contains("name=\"$part\"")) problems += "${g.label} multipart 파트 이름 '$part'가 없다(앱이 다른 이름으로 보냄)"
                    if (types.none { bodyText.contains("Content-Type: $it") }) problems += "${g.label} 파트 content-type이 골든 허용 목록 $types 에 없다"
                } else if (goldenBody == null) {
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
        val apis = Apis(fx)
        fx.session.saveTokens("a", "r")
        try {
            for (g in ContractFiles.requests().filter { it.requiredQuery.isNotEmpty() }) {
                val call = calls[g.id] ?: continue
                fx.enqueue(200, "{}")
                runBlocking { call(apis) }
                val names = fx.server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!.queryParameterNames
                for (q in g.requiredQuery - names) problems += "${g.label} 서버 필수 쿼리 '$q'를 앱이 보내지 않는다"
            }
        } finally {
            fx.shutdown()
        }
        assertNoProblems("필수 쿼리 누락", problems)
    }

    // ---- data: null 성공 응답(로그아웃, 탈퇴, 비밀번호 재설정)

    @Test
    fun noDataSuccessResponsesAreHandledByApp() {
        val problems = mutableListOf<String>()
        val fx = Fixture()
        fx.session.saveTokens("a", "r")
        val apis = Apis(fx)
        val noData = mapOf<String, suspend () -> retrofit2.Response<okhttp3.ResponseBody>>(
            "auth-password-reset" to { apis.extra.passwordReset(PasswordResetRequest("t", "p")) },
            "auth-logout" to { apis.extra.logout() },
            "users-me-delete" to { apis.extra.withdraw() },
        )
        try {
            for (g in successGoldens.filter { it.id in noDataResponses }) {
                if (g.shape["$.data"] != "null") problems += "${g.label} 서버 data가 null이 아니다(${g.shape["$.data"]}). 앱은 data를 읽지 않으므로 DTO 등록으로 바꿔야 한다"
                fx.enqueue(g.httpStatus, ContractFiles.sample(g, nullVariant = false))
                val r = runBlocking { apiCallNoData(noData.getValue(g.id)) }
                if (r !is ApiResult.Success) problems += "${g.label} data=null 성공 응답을 앱이 성공으로 처리하지 못했다: $r"
            }
            if (noDataResponses.size != successGoldens.count { it.id in noDataResponses }) problems += "noDataResponses에 골든이 없는 id가 있다"
        } finally {
            fx.shutdown()
        }
        assertNoProblems("data: null 응답 처리", problems)
    }

    // ---- enum 값: 서버 DTO의 @Pattern 값과 앱의 값이 같은지(서버 골든에는 enum 목록이 없어 서버 소스 기준으로 고정한다)

    @Test
    fun appEnumWireValuesEqualServerPatterns() {
        // 서버: EmailSendRequest/EmailConfirmRequest PURPOSE_PATTERN = "SIGNUP|PASSWORD_RESET"
        assertEquals(setOf("SIGNUP", "PASSWORD_RESET"), EmailPurpose.values().map { it.wire }.toSet())
        // 서버: OnboardingRequest ageGroup, gender @Pattern
        assertEquals(
            setOf("TEENS", "TWENTIES", "THIRTIES", "FORTIES", "FIFTIES_PLUS", "UNDISCLOSED"),
            AgeGroups.all.map { it.first }.toSet(),
        )
        assertEquals(setOf("MALE", "FEMALE", "UNDISCLOSED"), Genders.all.map { it.first }.toSet())
    }

    // ---- 온보딩 PATCH: 생략(바꾸지 않음)과 빈 배열(모두 삭제)이 앱 직렬화에서 구분되는지

    @Test
    fun onboardingPatchKeepsOmittedAndEmptyArrayApart() {
        fun enc(r: OnboardingRequest) = ApiJson.encodeToString(OnboardingRequest.serializer(), r)
        assertEquals("{}", enc(OnboardingRequest()))
        assertEquals("""{"interests":[]}""", enc(OnboardingRequest(interests = emptyList())))
        assertEquals("""{"ageGroup":"TEENS"}""", enc(OnboardingRequest(ageGroup = "TEENS")))
        // 서버(OnboardingRequest)는 interests=null/생략을 "바꾸지 않음", []를 "모두 삭제"로 읽는다
    }
}
