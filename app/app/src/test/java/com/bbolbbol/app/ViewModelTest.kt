package com.bbolbbol.app

import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.login.LoginViewModel
import com.bbolbbol.app.ui.map.JONGNO_LAT
import com.bbolbbol.app.ui.map.JONGNO_LNG
import com.bbolbbol.app.ui.map.LocationMode
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.me.MeViewModel
import com.bbolbbol.app.ui.signup.SignupUiState
import com.bbolbbol.app.ui.signup.SignupViewModel
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()
    private val auth get() = AuthRepository(fx.api, fx.session)

    @After
    fun tearDown() = fx.shutdown()

    @Test
    fun loginViewModelShowsKoreanErrorOnFailure() {
        fx.enqueue(401, Json.error("AUTH4011"))
        val vm = LoginViewModel(auth)
        vm.onEmail("a@b.com")
        vm.onPassword("pw")
        vm.submit()
        waitUntil { !vm.state.value.loading && vm.state.value.error != null }
        assertEquals("이메일 또는 비밀번호가 일치하지 않아요.", vm.state.value.error)
        assertFalse(fx.session.loggedIn.value)
    }

    @Test
    fun loginViewModelSuccessLogsIn() {
        fx.enqueue(200, Json.TOKENS)
        val vm = LoginViewModel(auth)
        vm.onEmail("a@b.com")
        vm.onPassword("pw")
        vm.submit()
        waitUntil { fx.session.loggedIn.value }
        assertNull(vm.state.value.error)
    }

    @Test
    fun signupMapsFieldErrorsFromCommon400() {
        fx.enqueue(
            400,
            Json.error(
                "COMMON400",
                """{"errors":[{"field":"nickname","reason":"닉네임 오류"},{"field":"password","reason":"비밀번호 오류"}]}""",
            ),
        )
        val vm = signupVm()
        vm.submit()
        waitUntil { !vm.state.value.loading && vm.state.value.nicknameError != null }
        assertEquals("닉네임 오류", vm.state.value.nicknameError)
        assertEquals("비밀번호 오류", vm.state.value.passwordError)
        assertNull(vm.state.value.formError)
        assertEquals(
            """{"verificationToken":"dev:a@b.com","password":"Passw0rd!","nickname":"뽈뽈이"}""",
            fx.server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun signupMapsBusinessCodes() {
        val cases = listOf<Pair<String, (SignupUiState) -> String?>>(
            "USER409" to { it.emailError },
            "USER4092" to { it.nicknameError },
            "USER4001" to { it.nicknameError },
            "EMAIL4003" to { it.tokenError },
        )
        for ((code, pick) in cases) {
            fx.enqueue(400, Json.error(code))
            val vm = signupVm()
            vm.submit()
            waitUntil { !vm.state.value.loading }
            assertEquals(code, ErrorMessages.forCode(code), pick(vm.state.value))
        }
    }

    @Test
    fun signupSuccessStoresTokens() {
        fx.enqueue(201, Json.TOKENS)
        val vm = signupVm()
        vm.submit()
        waitUntil { fx.session.loggedIn.value }
    }

    @Test
    fun signupValidatesLocallyWithoutCallingServer() {
        val vm = SignupViewModel(auth)
        vm.submit()
        val s = vm.state.value
        assertNotNull(s.emailError)
        assertNotNull(s.passwordError)
        assertNotNull(s.nicknameError)
        assertNotNull(s.tokenError)
        assertEquals(0, fx.server.requestCount)
    }

    @Test
    fun emailAvailabilityOnBlur() {
        fx.enqueue(200, Json.ok("""{"available":false,"reason":"이미 가입된 이메일입니다."}"""))
        val vm = SignupViewModel(auth)
        vm.onEmail("a@b.com")
        vm.checkEmail()
        waitUntil { vm.state.value.emailError != null }
        assertEquals("이미 가입된 이메일입니다.", vm.state.value.emailError)
        assertFalse(vm.state.value.emailOk)
        assertTrue(fx.server.takeRequest().path!!.startsWith("/auth/email-availability?email=a%40b.com"))
    }

    @Test
    fun nicknameAvailabilityOk() {
        fx.enqueue(200, Json.ok("""{"available":true,"reason":null}"""))
        val vm = SignupViewModel(auth)
        vm.onNickname("뽈뽈이")
        vm.checkNickname()
        waitUntil { vm.state.value.nicknameOk }
    }

    @Test
    fun buildTokenAppendsEmailOnlyWhenMissing() {
        assertEquals("dev:a@b.com", SignupViewModel.buildToken(" dev ", "a@b.com"))
        assertEquals("dev:x@y.com", SignupViewModel.buildToken("dev:x@y.com", "a@b.com"))
    }

    @Test
    fun validatorsMirrorBackendRules() {
        assertTrue(Validators.isValidPassword("Passw0rd!"))
        assertFalse(Validators.isValidPassword("password1"))
        assertFalse(Validators.isValidPassword("Pw0!"))
        assertTrue(Validators.isValidNickname("뽈뽈이"))
        assertFalse(Validators.isValidNickname("a"))
        assertFalse(Validators.isValidNickname("뽈 뽈"))
    }

    @Test
    fun meViewModelLoadsAndChangesNickname() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(ME_JSON))
        val vm = MeViewModel(UserRepository(fx.api), auth)
        waitUntil { vm.state.value.me != null }
        vm.onNickname("새닉네임")
        fx.enqueue(200, Json.ok("""{"nickname":"새닉네임","nextChangeableAt":"2026-12-13T10:00:00"}"""))
        vm.saveNickname()
        waitUntil { !vm.state.value.saving && vm.state.value.me?.nickname == "새닉네임" }
        assertFalse(vm.state.value.nicknameIsError)
        vm.logout()
        assertFalse(fx.session.loggedIn.value)
    }

    @Test
    fun meNicknameCooldownErrorMessage() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(ME_JSON))
        val vm = MeViewModel(UserRepository(fx.api), auth)
        waitUntil { vm.state.value.me != null }
        vm.onNickname("새닉네임")
        fx.enqueue(400, Json.error("USER4002", """{"nextChangeableAt":"2026-12-13T10:00:00"}"""))
        vm.saveNickname()
        waitUntil { !vm.state.value.saving && vm.state.value.nicknameMessage != null }
        assertTrue(vm.state.value.nicknameIsError)
        assertTrue(vm.state.value.nicknameMessage!!.contains("30일"))
    }

    @Test
    fun mapBrowseModeCentersOnJongnoAndLoadsPlaces() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok("""{"categories":[{"code":"CAFE","name":"카페","rewardPoint":10}]}"""))
        val vm = MapViewModel(PlaceRepository(fx.api))
        waitUntil { vm.state.value.categories.isNotEmpty() }
        fx.enqueue(
            200,
            Json.ok("""{"places":[{"placeId":1,"name":"경복궁","category":"CAFE","lat":37.58,"lng":126.97,"isHot":true,"isCompleted":false}]}"""),
        )
        vm.enterBrowseMode()
        waitUntil { vm.state.value.places.isNotEmpty() }
        val s = vm.state.value
        assertEquals(LocationMode.BROWSE, s.mode)
        assertEquals(JONGNO_LAT, s.lat, 0.0)
        assertEquals(JONGNO_LNG, s.lng, 0.0)
        assertTrue(s.places[0].isHot)
        fx.server.takeRequest()
        assertTrue(fx.server.takeRequest().path!!.contains("lat=37.5729&lng=126.9794"))
    }

    private fun signupVm(): SignupViewModel = SignupViewModel(auth).apply {
        onEmail("a@b.com")
        onPassword("Passw0rd!")
        onNickname("뽈뽈이")
        onDevToken("dev")
    }

    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }

    private companion object {
        const val ME_JSON =
            """{"userId":1,"email":"a@b.com","nickname":"기존","loginType":"EMAIL","onboardingCompleted":false,"createdAt":"2026-11-01T10:00:00"}"""
    }
}
