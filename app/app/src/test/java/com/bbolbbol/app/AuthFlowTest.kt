package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthExtraApi
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.auth.AccountViewModel
import com.bbolbbol.app.ui.auth.DevKakaoTokenProvider
import com.bbolbbol.app.ui.auth.KakaoLoginViewModel
import com.bbolbbol.app.ui.auth.KakaoTokenProvider
import com.bbolbbol.app.ui.auth.KakaoTokenProviders
import com.bbolbbol.app.ui.auth.PasswordResetViewModel
import com.bbolbbol.app.ui.auth.ResetStep
import com.bbolbbol.app.ui.auth.SignupFlowViewModel
import com.bbolbbol.app.ui.auth.SignupStep
import com.bbolbbol.app.ui.auth.UnavailableKakaoTokenProvider
import com.bbolbbol.app.ui.onboarding.OnboardingViewModel
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json as KJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val SEND_OK = """{"expiresIn":600,"resendAvailableIn":60}"""
private const val CONFIRM_OK = """{"verificationToken":"vtoken-1","expiresIn":1800}"""
private const val ME_KAKAO =
    """{"userId":7,"email":"","nickname":"","loginType":"KAKAO","onboardingCompleted":false,"createdAt":"2026-11-14T00:00:00"}"""
private const val ME_EMAIL =
    """{"userId":7,"email":"a@b.com","nickname":"닉","loginType":"EMAIL","onboardingCompleted":false}"""
private const val CATEGORIES =
    """{"categories":[{"code":"PALACE","name":"궁"},{"code":"CAFE","name":"카페"},{"code":"MARKET","name":"시장"},{"code":"MUSEUM","name":"박물관"}]}"""

/** 새 인증 흐름(이메일 인증 가입, 재설정, 카카오, 온보딩, 로그아웃, 탈퇴)의 ViewModel/저장소 테스트. */
class AuthFlowTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()
    private val extraApi: AuthExtraApi = Retrofit.Builder()
        .baseUrl(fx.server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(fx.session)).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthExtraApi::class.java)
    private val extra = AuthExtraRepository(extraApi, fx.session)
    private val auth = AuthRepository(fx.api, fx.session)

    private val methodRoutes = java.util.concurrent.ConcurrentHashMap<String, MockResponse>()

    init {
        // 같은 경로를 메서드별로 다르게 답해야 할 때(GET/PATCH /users/me/onboarding). 없으면 Fixture의 경로 라우팅으로 넘긴다.
        val base = fx.server.dispatcher
        fx.server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val key = request.method + " " + request.path.orEmpty().substringBefore("?")
                return methodRoutes[key] ?: base.dispatch(request)
            }
        }
    }

    private fun routeM(method: String, path: String, status: Int, body: String) {
        methodRoutes["$method $path"] =
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    }

    @After
    fun tearDown() = fx.shutdown()

    private fun body(): JsonObject = KJson.parseToJsonElement(fx.server.takeRequest().body.readUtf8()).jsonObject

    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }

    // ---------------- 가입 흐름 ----------------

    private fun signupVm() = SignupFlowViewModel(auth, extra)

    private fun SignupFlowViewModel.sendTo(email: String) {
        codeFlow.onEmail(email)
        codeFlow.send()
        waitUntil { !codeFlow.state.value.sending }
    }

    @Test
    fun sendCodeSendsSignupPurposeAndStartsCooldown() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        val b = body()
        assertEquals(setOf("email", "purpose"), b.keys)
        assertEquals("SIGNUP", b["purpose"].toString().trim('"'))
        assertEquals("new@example.com", b["email"].toString().trim('"'))
        val s = vm.state.value
        assertEquals(SignupStep.CODE, s.step)
        assertEquals(60, s.code.resendIn)
        assertEquals(600, s.code.expiresIn)
        assertFalse(s.code.canResend)
        repeat(59) { vm.codeFlow.tick() }
        assertFalse(vm.codeFlow.state.value.canResend)
        vm.codeFlow.tick()
        assertTrue(vm.codeFlow.state.value.canResend)
        assertEquals(540, vm.codeFlow.state.value.expiresIn)
    }

    @Test
    fun resendIsBlockedDuringCooldownWithoutCallingServer() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        vm.codeFlow.send() // 쿨다운 중
        assertEquals(1, fx.server.requestCount)
    }

    @Test
    fun invalidEmailIsRejectedLocally() {
        val vm = signupVm()
        vm.sendTo("not-an-email")
        assertEquals(0, fx.server.requestCount)
        assertEquals("이메일 형식이 올바르지 않아요.", vm.state.value.code.emailError)
    }

    @Test
    fun sendCodeErrorsMapToMessages() {
        val cases = listOf(
            Triple(409, "USER409", "이미 가입된 이메일이에요."),
            Triple(429, "EMAIL429", ErrorMessages.forCode("EMAIL429")!!),
            Triple(502, "EMAIL502", ErrorMessages.forCode("EMAIL502")!!),
        )
        for ((status, code, msg) in cases) {
            fx.route("/auth/email-verifications", status, Json.error(code))
            val vm = signupVm()
            vm.sendTo("new@example.com")
            val c = vm.state.value.code
            assertFalse("$code: 보내지지 않았으니 코드 단계로 가면 안 된다", c.sent)
            assertEquals(msg, if (code == "USER409") c.emailError else c.error)
            if (code == "EMAIL429") assertEquals(60, c.resendIn)
        }
    }

    @Test
    fun confirmWrongCodeShowsRemainingAttemptsThenInvalidates() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        fx.server.takeRequest()
        fx.route("/auth/email-verifications/confirm", 400, Json.error("EMAIL4001", """{"remainingAttempts":4}"""))
        vm.codeFlow.onCode("12a3456")
        assertEquals("123456", vm.codeFlow.state.value.code) // 숫자만, 6자리
        vm.codeFlow.confirm()
        waitUntil { !vm.codeFlow.state.value.confirming }
        val b = body()
        assertEquals(setOf("email", "purpose", "code"), b.keys)
        assertEquals("123456", b["code"].toString().trim('"'))
        var c = vm.codeFlow.state.value
        assertEquals(4, c.remainingAttempts)
        assertTrue(c.error!!.contains("남은 시도 4번"))
        assertEquals("", c.code)
        assertFalse(c.codeInvalid)

        fx.route("/auth/email-verifications/confirm", 400, Json.error("EMAIL4002"))
        vm.codeFlow.onCode("111111")
        vm.codeFlow.confirm()
        waitUntil { !vm.codeFlow.state.value.confirming }
        c = vm.codeFlow.state.value
        assertTrue(c.codeInvalid)
        assertFalse(c.canConfirm)
        assertEquals(ErrorMessages.forCode("EMAIL4002"), c.error)
    }

    @Test
    fun fullSignupFlowSendsVerificationTokenAndRequiresOnboarding() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        fx.route("/auth/email-verifications/confirm", 200, Json.ok(CONFIRM_OK))
        fx.route("/auth/signup", 201, Json.TOKENS.replace("\"isNewUser\":false", "\"isNewUser\":true"))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        vm.codeFlow.onCode("123456")
        vm.codeFlow.confirm()
        waitUntil { vm.state.value.step == SignupStep.PROFILE }
        vm.onPassword("abcd1234!")
        vm.onNickname("새닉네임")
        vm.submit()
        waitUntil { fx.session.loggedIn.value }
        fx.server.takeRequest(); fx.server.takeRequest()
        val signup = body()
        assertEquals(setOf("verificationToken", "password", "nickname"), signup.keys)
        assertEquals("vtoken-1", signup["verificationToken"].toString().trim('"'))
        assertTrue(fx.session.onboardingRequired.value)
    }

    @Test
    fun signupWithExpiredVerificationTokenGoesBackToEmailStep() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        fx.route("/auth/email-verifications/confirm", 200, Json.ok(CONFIRM_OK))
        fx.route("/auth/signup", 400, Json.error("EMAIL4003"))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        vm.codeFlow.onCode("123456")
        vm.codeFlow.confirm()
        waitUntil { vm.state.value.step == SignupStep.PROFILE }
        vm.onPassword("abcd1234!")
        vm.onNickname("새닉네임")
        vm.submit()
        waitUntil { vm.state.value.step == SignupStep.EMAIL && vm.state.value.code.error != null }
        assertEquals(ErrorMessages.forCode("EMAIL4003"), vm.state.value.code.error)
        assertFalse(fx.session.loggedIn.value)
    }

    @Test
    fun signupFieldErrorsAreShownPerField() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        fx.route("/auth/email-verifications/confirm", 200, Json.ok(CONFIRM_OK))
        fx.route("/auth/signup", 409, Json.error("USER4092"))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        vm.codeFlow.onCode("123456")
        vm.codeFlow.confirm()
        waitUntil { vm.state.value.step == SignupStep.PROFILE }
        vm.onPassword("abcd1234!")
        vm.onNickname("중복닉")
        vm.submit()
        waitUntil { vm.state.value.profile.nicknameError != null }
        assertEquals("이미 사용 중인 닉네임이에요.", vm.state.value.profile.nicknameError)
    }

    @Test
    fun devTokenShortcutSkipsCodeStepWithEmailAppended() {
        val vm = signupVm()
        vm.codeFlow.onEmail("dev@example.com")
        vm.useDevToken("DEV-ONLY-FAKE-verification-token")
        assertEquals(SignupStep.PROFILE, vm.state.value.step)
        assertEquals("DEV-ONLY-FAKE-verification-token:dev@example.com", vm.codeFlow.state.value.verificationToken)
        assertEquals(0, fx.server.requestCount)
    }

    @Test
    fun changeEmailResetsToEmailStep() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        val vm = signupVm()
        vm.sendTo("new@example.com")
        vm.codeFlow.changeEmail()
        assertEquals(SignupStep.EMAIL, vm.state.value.step)
        assertEquals("new@example.com", vm.state.value.email)
    }

    // ---------------- 비밀번호 재설정 ----------------

    @Test
    fun passwordResetUsesPasswordResetPurposeAndReturnsToLogin() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        fx.route("/auth/email-verifications/confirm", 200, Json.ok(CONFIRM_OK))
        fx.route("/auth/password-reset", 200, Json.ok("null"))
        val vm = PasswordResetViewModel(extra)
        vm.codeFlow.onEmail("a@b.com")
        vm.codeFlow.send()
        waitUntil { vm.state.value.step == ResetStep.CODE }
        val send = body()
        assertEquals("PASSWORD_RESET", send["purpose"].toString().trim('"'))
        vm.codeFlow.onCode("654321")
        vm.codeFlow.confirm()
        waitUntil { vm.state.value.step == ResetStep.PASSWORD }
        assertEquals("PASSWORD_RESET", body()["purpose"].toString().trim('"'))
        vm.onPassword("short")
        vm.submit()
        assertEquals("비밀번호는 8~20자이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 해요.", vm.state.value.reset.passwordError)
        vm.onPassword("newPass123!")
        vm.submit()
        waitUntil { vm.state.value.reset.done }
        val reset = body()
        assertEquals(setOf("verificationToken", "newPassword"), reset.keys)
        assertFalse(fx.session.loggedIn.value)
    }

    @Test
    fun passwordResetWithExpiredTokenGoesBackToEmail() {
        fx.route("/auth/email-verifications", 200, Json.ok(SEND_OK))
        fx.route("/auth/email-verifications/confirm", 200, Json.ok(CONFIRM_OK))
        fx.route("/auth/password-reset", 400, Json.error("EMAIL4003"))
        val vm = PasswordResetViewModel(extra)
        vm.codeFlow.onEmail("a@b.com")
        vm.codeFlow.send()
        waitUntil { vm.state.value.step == ResetStep.CODE }
        vm.codeFlow.onCode("654321")
        vm.codeFlow.confirm()
        waitUntil { vm.state.value.step == ResetStep.PASSWORD }
        vm.onPassword("newPass123!")
        vm.submit()
        waitUntil { vm.state.value.step == ResetStep.EMAIL }
        assertEquals(ErrorMessages.forCode("EMAIL4003"), vm.state.value.code.error)
        assertFalse(vm.state.value.reset.done)
    }

    // ---------------- 온보딩 ----------------

    private fun onboardingVm() = OnboardingViewModel(extra, UserRepository(fx.api), PlaceRepository(fx.api), fx.session)

    private fun routeOnboardingLoad(me: String, info: String) {
        fx.route("/users/me", 200, Json.ok(me))
        fx.route("/users/me/onboarding", 200, Json.ok(info))
        fx.route("/place-categories", 200, Json.ok(CATEGORIES))
    }

    @Test
    fun onboardingRequiresAgeAndGenderAndLimitsInterestsToThree() {
        routeOnboardingLoad(ME_EMAIL, """{"ageGroup":null,"gender":null,"interests":[],"onboardingCompleted":false}""")
        val vm = onboardingVm()
        waitUntil { !vm.state.value.loading }
        assertFalse(vm.state.value.showNickname) // 이메일 가입자는 닉네임 칸 없음
        val before = fx.server.requestCount
        vm.submit()
        assertEquals("연령대를 골라 주세요.", vm.state.value.ageGroupError)
        assertEquals("성별을 골라 주세요.", vm.state.value.genderError)
        assertEquals(before, fx.server.requestCount)

        vm.toggleInterest("PALACE"); vm.toggleInterest("CAFE"); vm.toggleInterest("MARKET")
        vm.toggleInterest("MUSEUM")
        assertEquals(listOf("PALACE", "CAFE", "MARKET"), vm.state.value.interests)
        assertEquals("관심 카테고리는 최대 3개까지 고를 수 있어요.", vm.state.value.interestsError)
        vm.toggleInterest("CAFE") // 해제하면 다시 고를 수 있다
        assertEquals(listOf("PALACE", "MARKET"), vm.state.value.interests)
        vm.toggleInterest("MUSEUM")
        assertEquals(3, vm.state.value.interests.size)
    }

    @Test
    fun onboardingSaveSendsExpectedBodyAndClearsRequiredFlag() {
        routeOnboardingLoad(ME_EMAIL, """{"ageGroup":null,"gender":null,"interests":[],"onboardingCompleted":false}""")
        routeM(
            "PATCH", "/users/me/onboarding", 200,
            Json.ok("""{"nickname":"닉","ageGroup":"TWENTIES","gender":"FEMALE","interests":["PALACE"],"onboardingCompleted":true}"""),
        )
        fx.session.markOnboarding(true)
        fx.session.saveTokens("a", "r")
        val vm = onboardingVm()
        waitUntil { !vm.state.value.loading }
        vm.onAgeGroup("TWENTIES"); vm.onGender("FEMALE"); vm.toggleInterest("PALACE")
        vm.submit()
        waitUntil { vm.state.value.done }
        // 마지막 요청이 PATCH 본문. 닉네임은 이메일 가입자에게는 보내지 않는다.
        var last: okhttp3.mockwebserver.RecordedRequest? = null
        repeat(fx.server.requestCount) { last = fx.server.takeRequest() }
        assertEquals("PATCH", last!!.method)
        val b = KJson.parseToJsonElement(last!!.body.readUtf8()).jsonObject
        assertEquals(setOf("ageGroup", "gender", "interests"), b.keys)
        assertFalse(fx.session.onboardingRequired.value)
    }

    @Test
    fun kakaoFirstOnboardingShowsPrefilledNicknameAndSendsIt() {
        routeOnboardingLoad(ME_KAKAO, """{"ageGroup":null,"gender":null,"interests":[],"onboardingCompleted":false}""")
        routeM("PATCH", "/users/me/onboarding", 400, Json.error("COMMON400", """{"errors":[{"field":"nickname","reason":"서버 닉네임 오류"}]}"""))
        fx.session.markOnboarding(true, "뽈뽈탐험")
        val vm = onboardingVm()
        waitUntil { !vm.state.value.loading }
        assertTrue(vm.state.value.showNickname)
        assertEquals("뽈뽈탐험", vm.state.value.nickname)
        vm.onAgeGroup("TEENS"); vm.onGender("UNDISCLOSED")
        vm.submit()
        waitUntil { !vm.state.value.saving && vm.state.value.nicknameError != null }
        assertEquals("서버 닉네임 오류", vm.state.value.nicknameError)
        var last: okhttp3.mockwebserver.RecordedRequest? = null
        repeat(fx.server.requestCount) { last = fx.server.takeRequest() }
        val b = KJson.parseToJsonElement(last!!.body.readUtf8()).jsonObject
        assertEquals("뽈뽈탐험", b["nickname"].toString().trim('"'))
        // 로컬 검증: 잘못된 닉네임은 보내지 않는다
        vm.onNickname("a")
        val count = fx.server.requestCount
        vm.submit()
        assertEquals(count, fx.server.requestCount)
        assertEquals("닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있어요.", vm.state.value.nicknameError)
    }

    // ---------------- 카카오 ----------------

    @Test
    fun kakaoLoginNewUserGoesToOnboardingWithSuggestedNickname() {
        fx.route(
            "/auth/kakao", 200,
            Json.ok("""{"userId":9,"accessToken":"ka","refreshToken":"kr","isNewUser":true,"suggestedNickname":"카카오닉"}"""),
        )
        val vm = KakaoLoginViewModel(extra, DevKakaoTokenProvider())
        vm.onButton()
        assertTrue(vm.state.value.showDevDialog)
        vm.login("DEV-KAKAO|1001|k@example.com|카카오닉")
        waitUntil { fx.session.loggedIn.value }
        val b = body()
        assertEquals(setOf("kakaoAccessToken"), b.keys)
        assertEquals("DEV-KAKAO|1001|k@example.com|카카오닉", b["kakaoAccessToken"].toString().trim('"'))
        assertTrue(fx.session.onboardingRequired.value)
        assertEquals("카카오닉", fx.session.suggestedNickname)
    }

    @Test
    fun kakaoLoginExistingUserSkipsOnboarding() {
        fx.route("/auth/kakao", 200, Json.ok("""{"userId":9,"accessToken":"ka","refreshToken":"kr","isNewUser":false,"suggestedNickname":null}"""))
        val vm = KakaoLoginViewModel(extra, DevKakaoTokenProvider())
        vm.login("DEV-KAKAO|1|x@example.com|nick")
        waitUntil { fx.session.loggedIn.value }
        assertFalse(fx.session.onboardingRequired.value)
        assertNull(fx.session.suggestedNickname)
    }

    @Test
    fun kakaoErrorsMapToMessages() {
        for ((status, code) in listOf(401 to "KAKAO401", 502 to "KAKAO502", 409 to "USER4093")) {
            fx.route("/auth/kakao", status, Json.error(code))
            val vm = KakaoLoginViewModel(extra, DevKakaoTokenProvider())
            vm.login("DEV-KAKAO-EXPIRED")
            waitUntil { !vm.state.value.loading && vm.state.value.error != null }
            assertEquals(ErrorMessages.forCode(code), vm.state.value.error)
            assertFalse(fx.session.loggedIn.value)
        }
        assertTrue(ErrorMessages.forCode("USER4093")!!.contains("이메일로 로그인"))
    }

    @Test
    fun releaseProviderIsUnavailableAndNeverCallsServer() {
        val provider = KakaoTokenProviders.create(debug = false)
        assertTrue(provider is UnavailableKakaoTokenProvider)
        assertEquals(KakaoTokenProvider.Mode.UNAVAILABLE, provider.mode)
        assertTrue(KakaoTokenProviders.create(debug = true) is DevKakaoTokenProvider)
        val vm = KakaoLoginViewModel(extra, provider)
        assertFalse(vm.enabled)
        vm.onButton()
        vm.login("DEV-KAKAO|1|x@example.com|nick")
        assertFalse(vm.state.value.showDevDialog)
        assertEquals(0, fx.server.requestCount)
    }

    // ---------------- 로그아웃, 탈퇴 ----------------

    @Test
    fun logoutCallsServerWithTokenAndClears() {
        fx.session.saveTokens("acc", "ref")
        fx.route("/auth/logout", 200, Json.ok("null"))
        val vm = AccountViewModel(extra, UserRepository(fx.api))
        vm.logout()
        waitUntil { !fx.session.loggedIn.value }
        assertEquals("Bearer acc", fx.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun logoutClearsTokensEvenWhenServerFailsOrNetworkDown() {
        for (mode in listOf("500", "network")) {
            fx.session.saveTokens("acc", "ref")
            if (mode == "500") fx.route("/auth/logout", 500, Json.error("COMMON500"))
            else fx.server.shutdown() // 연결 거부 -> NetworkError
            val r = runBlocking { extra.logout() }
            assertFalse("$mode: 실패해도 토큰은 지워진다", r is ApiResult.Success)
            assertFalse(fx.session.loggedIn.value)
            assertNull(fx.session.accessToken)
            assertNull(fx.session.refreshToken)
        }
    }

    @Test
    fun withdrawalClearsTokensOnSuccessAndKeepsThemOnFailure() {
        fx.session.saveTokens("acc", "ref")
        fx.route("/users/me", 500, Json.error("COMMON500"))
        val vm = AccountViewModel(extra, UserRepository(fx.api))
        vm.askWithdraw()
        assertTrue(vm.state.value.showWithdrawDialog)
        vm.confirmWithdraw()
        waitUntil { !vm.state.value.busy }
        assertTrue(fx.session.loggedIn.value) // 실패: 로그인 유지, 오류 표시
        assertTrue(vm.state.value.error != null)

        fx.route("/users/me", 200, Json.ok("null"))
        vm.confirmWithdraw()
        waitUntil { !fx.session.loggedIn.value }
        assertNull(fx.session.refreshToken)
    }

    // ---------------- 시작 시 온보딩 확인 ----------------

    private fun startCheck() = runBlocking {
        com.bbolbbol.app.ui.auth.checkOnboardingOnStart(UserRepository(fx.api), fx.session)
    }

    @Test
    fun startupCheckRoutesToOnboardingWhenIncomplete() {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me", 200, Json.ok(ME_EMAIL))
        assertTrue(startCheck())
        assertTrue(fx.session.onboardingRequired.value)
    }

    @Test
    fun startupCheckDoesNothingWhenCompletedOrLoggedOut() {
        fx.route("/users/me", 200, Json.ok(ME_EMAIL.replace("false", "true")))
        assertFalse(startCheck()) // 로그아웃 상태: 서버를 부르지 않는다
        assertEquals(0, fx.server.requestCount)
        fx.session.saveTokens("a", "r")
        assertFalse(startCheck())
        assertFalse(fx.session.onboardingRequired.value)
        assertTrue(fx.session.loggedIn.value)
    }

    @Test
    fun startupCheckFailureNeverBlocksOrChangesSession() {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me", 503, Json.error("COMMON500"))
        assertFalse(startCheck())
        fx.route("/users/me", 401, Json.error("COMMON401"))
        assertFalse(startCheck())
        fx.server.shutdown() // 네트워크 오류
        assertFalse(startCheck())
        assertFalse(fx.session.onboardingRequired.value)
        assertTrue(fx.session.loggedIn.value)
    }

    // ---------------- 오류 문구 ----------------

    @Test
    fun errorMessagesCoverNewCodes() {
        for (code in listOf(
            "AUTH4012", "AUTH429", "EMAIL4001", "EMAIL4002", "EMAIL4003", "EMAIL429", "EMAIL502",
            "KAKAO401", "KAKAO502", "USER4093", "USER409", "COMPLETION4031",
        )) assertTrue("$code 문구 없음", !ErrorMessages.forCode(code).isNullOrBlank())
        assertTrue(ErrorMessages.forCode("AUTH4012")!!.contains("다시 로그인"))
        assertTrue(ErrorMessages.forCode("COMPLETION4031")!!.contains("추가 정보"))
    }

    @Test
    fun purposeWireValuesMatchServerEnum() {
        assertEquals("SIGNUP", EmailPurpose.SIGNUP.wire)
        assertEquals("PASSWORD_RESET", EmailPurpose.PASSWORD_RESET.wire) // "RESET"이 아니다
    }
}
