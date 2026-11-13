package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.auth.AuthRoutes
import com.bbolbbol.app.ui.auth.CodeStepState
import com.bbolbbol.app.ui.map.MapUiState
import com.bbolbbol.app.ui.nav.Routes
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
