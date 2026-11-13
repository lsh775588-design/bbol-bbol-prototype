package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.auth.AuthRoutes
import com.bbolbbol.app.ui.auth.CodeStepState
import com.bbolbbol.app.ui.map.MapUiState
import com.bbolbbol.app.ui.nav.Routes
import com.bbolbbol.app.ui.nav.loginStateTarget
import com.bbolbbol.app.ui.nav.postLoginRoute
import com.bbolbbol.app.ui.onboarding.OnboardingUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 3단계 E2E에서 찾은 앱 버그 수정 검증. */
class Stage3FixTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    @Test
    fun newUserLoginRoutesToOnboardingImmediately() = runBlocking {
        fx.route("/auth/login", 200, Json.TOKENS.replace("\"isNewUser\":false", "\"isNewUser\":true"))
        AuthRepository(fx.api, fx.session).login("a@b.com", "pw")
        // 로그인 상태가 켜진 바로 그 시점에 현재 값으로 갈 곳이 정해진다.
        assertTrue(fx.session.loggedIn.value)
        assertEquals(AuthRoutes.ONBOARDING, postLoginRoute(fx.session))
    }

    @Test
    fun returningUserLoginRoutesHome() = runBlocking {
        fx.route("/auth/login", 200, Json.TOKENS)
        AuthRepository(fx.api, fx.session).login("a@b.com", "pw")
        assertEquals(Routes.HOME, postLoginRoute(fx.session))
    }

    @Test
    fun expiredSessionSetsNoticeAndNextLoginClearsIt() {
        fx.session.saveTokens("a", "r")
        fx.session.expire()
        assertFalse(fx.session.loggedIn.value)
        assertTrue(fx.session.sessionExpired.value)
        fx.session.saveTokens("b", "r2")
        assertFalse(fx.session.sessionExpired.value)
    }

    @Test
    fun explicitLogoutDoesNotShowExpiredNotice() {
        fx.session.saveTokens("a", "r")
        fx.session.clear()
        assertFalse(fx.session.sessionExpired.value)
    }

    @Test
    fun expireWhileLoggedOutDoesNotShowNotice() {
        fx.session.expire()
        assertFalse(fx.session.sessionExpired.value)
    }

    @Test
    fun auth401ResponseExpiresSession() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me", 401, Json.error("AUTH401"))
        UserRepository(fx.api).me()
        assertFalse(fx.session.loggedIn.value)
        assertTrue(fx.session.sessionExpired.value)
    }

    @Test
    fun sendCodeNeedsEmail() {
        assertFalse(CodeStepState().canSend)
        assertFalse(CodeStepState(email = "  ").canSend)
        assertTrue(CodeStepState(email = "a@b.com").canSend)
        assertFalse(CodeStepState(email = "a@b.com", sending = true).canSend)
    }

    @Test
    fun onboardingSaveNeedsRequiredFields() {
        val ready = OnboardingUiState(loading = false, ageGroup = "TWENTIES", gender = "FEMALE")
        assertFalse(OnboardingUiState(loading = false).canSubmit)
        assertFalse(ready.copy(gender = null).canSubmit)
        assertTrue(ready.canSubmit)
        assertFalse(ready.copy(saving = true).canSubmit)
        // 카카오 신규 사용자는 닉네임도 필요하다.
        assertFalse(ready.copy(showNickname = true, nickname = " ").canSubmit)
        assertTrue(ready.copy(showNickname = true, nickname = "닉").canSubmit)
        // 이미 완료한 사용자가 수정할 때는 바꾼 것이 없어도 저장할 수 있다.
        assertTrue(OnboardingUiState(loading = false, alreadyCompleted = true).canSubmit)
    }

    @Test
    fun completedPlaceIsMarkedDoneForToday() {
        fun detail(done: Boolean) = ApiJson.decodeFromString(
            PlaceDetail.serializer(),
            """{"placeId":5,"name":"x","category":"PALACE","lat":37.5,"lng":127.0,"isCompleted":$done}""",
        )
        assertFalse(MapUiState().detailCompletedToday)
        assertFalse(MapUiState(detail = detail(false)).detailCompletedToday)
        assertTrue(MapUiState(detail = detail(true)).detailCompletedToday)
    }
}

/** 재검증에서 찾은 버그: 내비게이션 결정, 마이 재진입, 가입 버튼. */
class Stage3Fix2Test {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private fun waitUntil(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("시간 초과")
            Thread.sleep(10)
        }
    }

    @Test
    fun signupLandsOnOnboardingFromSignupScreen() {
        fx.session.markOnboarding(true)
        fx.session.saveTokens("a", "r")
        assertEquals(AuthRoutes.ONBOARDING, loginStateTarget(true, AuthRoutes.SIGNUP_FLOW, fx.session))
        assertEquals(AuthRoutes.ONBOARDING, loginStateTarget(true, Routes.LOGIN, fx.session))
    }

    @Test
    fun returningUserLandsOnHomeAndOthersStay() {
        fx.session.markOnboarding(false)
        fx.session.saveTokens("a", "r")
        assertEquals(Routes.HOME, loginStateTarget(true, Routes.LOGIN, fx.session))
        assertEquals(null, loginStateTarget(true, Routes.HOME, fx.session))
        assertEquals(null, loginStateTarget(true, AuthRoutes.ONBOARDING, fx.session))
    }

    @Test
    fun loggedOutOutsideLoginGoesToLogin() {
        assertEquals(Routes.LOGIN, loginStateTarget(false, Routes.ME, fx.session))
        assertEquals(null, loginStateTarget(false, Routes.LOGIN, fx.session))
        assertEquals(null, loginStateTarget(false, null, fx.session))
    }

    @Test
    fun meRefreshesPointsOnReentry() {
        fx.session.saveTokens("a", "r")
        val me = """{"userId":1,"email":"a@b.com","nickname":"닉","loginType":"EMAIL","onboardingCompleted":true,"createdAt":"2026-11-01T10:00:00"}"""
        fx.route("/users/me", 200, Json.ok(me))
        fx.route("/users/me/points", 200, Json.ok("""{"totalPoint":0,"todayPoint":0,"completionCount":0}"""))
        val vm = com.bbolbbol.app.ui.me.MeViewModel(UserRepository(fx.api), AuthRepository(fx.api, fx.session), com.bbolbbol.app.data.repository.PointRepository(fx.api))
        waitUntil { vm.state.value.points != null }
        vm.onEnter() // 첫 진입: init이 이미 불렀다
        fx.route("/users/me/points", 200, Json.ok("""{"totalPoint":350,"todayPoint":350,"completionCount":1}"""))
        vm.onEnter() // 재진입
        waitUntil { vm.state.value.points?.totalPoint == 350L }
        assertFalse(vm.state.value.pointsLoading)
    }

    @Test
    fun signupButtonNeedsPasswordAndNickname() {
        assertFalse(com.bbolbbol.app.ui.auth.SignupProfileState().canSubmit)
        assertFalse(com.bbolbbol.app.ui.auth.SignupProfileState(password = "abcd1234!").canSubmit)
        assertTrue(com.bbolbbol.app.ui.auth.SignupProfileState(password = "abcd1234!", nickname = "닉").canSubmit)
        assertFalse(com.bbolbbol.app.ui.auth.SignupProfileState(password = "x", nickname = "닉", loading = true).canSubmit)
    }
}
