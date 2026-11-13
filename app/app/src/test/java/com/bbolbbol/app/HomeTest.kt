package com.bbolbbol.app

import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.home.HomeViewModel
import com.bbolbbol.app.ui.me.RecentActivityViewModel
import com.bbolbbol.app.util.Formatters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 새 UI: 홈 화면 ViewModel, 마이 최근 기록, 표시용 포맷. */
class HomeTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private fun vm(): HomeViewModel {
        fx.session.saveTokens("a", "r")
        return HomeViewModel(
            UserRepository(fx.api),
            PointRepository(fx.api),
            PlaceRepository(fx.api),
            CompletionRepository(fx.api),
        )
    }

    private fun routeAllOk() {
        fx.route("/users/me", 200, Json.ok(ME))
        fx.route("/users/me/points", 200, Json.ok(POINTS))
        fx.route("/places/recommendations", 200, Json.ok(RECOMMEND))
        fx.route("/users/me/completions", 200, Json.ok(LOG))
    }

    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }

    private fun HomeViewModel.loaded() = state.value.let {
        !it.nickname.loading && !it.points.loading && !it.recommendations.loading && !it.recent.loading
    }

    @Test
    fun loadsNicknamePointsRecommendationsAndRecent() {
        routeAllOk()
        val vm = vm()
        vm.start(37.5, 127.0)
        waitUntil { vm.loaded() }
        val s = vm.state.value
        assertEquals("뽈뽈이", s.nickname.data)
        assertEquals(120L, s.points.data!!.totalPoint)
        assertEquals(30L, s.points.data!!.todayPoint)
        assertEquals(4L, s.points.data!!.completionCount)
        assertEquals(2, s.recommendations.data!!.size)
        assertEquals("가까운 곳이에요", s.recommendations.data!![0].reason)
        assertFalse(s.browse)
        assertEquals(37.5, s.lat, 0.0)
    }

    @Test
    fun recentKeepsOnlyLatestThreeAndAsksServerForThree() {
        routeAllOk()
        fx.route("/users/me/completions", 200, Json.ok(logOf(5)))
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.loaded() }
        assertEquals(3, vm.state.value.recent.data!!.size)
        assertEquals(1L, vm.state.value.recent.data!![0].completionId)
        val paths = generateSequence { fx.server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .map { it.path }.toList()
        assertTrue(paths.toString(), paths.any { it == "/users/me/completions?size=3" })
    }

    @Test
    fun browseModeUsesJongnoCenter() {
        routeAllOk()
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.loaded() }
        val s = vm.state.value
        assertTrue(s.browse)
        assertEquals(37.5729, s.lat, 0.0)
        assertEquals(126.9794, s.lng, 0.0)
        val paths = generateSequence { fx.server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .map { it.path }.toList()
        assertTrue(paths.toString(), paths.any { it!!.startsWith("/places/recommendations?lat=37.5729&lng=126.9794") })
    }

    @Test
    fun partialFailureKeepsOtherSections() {
        routeAllOk()
        fx.route("/users/me/points", 500, Json.error("COMMON500"))
        fx.route("/places/recommendations", 500, Json.error("COMMON500"))
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.loaded() }
        val s = vm.state.value
        assertEquals("뽈뽈이", s.nickname.data)
        assertNull(s.points.data)
        assertNotNull(s.points.error)
        assertNull(s.recommendations.data)
        assertNotNull(s.recommendations.error)
        assertEquals(2, s.recent.data!!.size)
    }

    @Test
    fun emptyStatesAreEmptyListsNotErrors() {
        routeAllOk()
        fx.route("/places/recommendations", 200, Json.ok("""{"places":[]}"""))
        fx.route("/users/me/completions", 200, Json.ok("""{"completions":[],"hasNext":false,"nextCursor":null}"""))
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.loaded() }
        val s = vm.state.value
        assertTrue(s.recommendations.data!!.isEmpty())
        assertNull(s.recommendations.error)
        assertTrue(s.recent.data!!.isEmpty())
        assertNull(s.recent.error)
    }

    @Test
    fun retryReloadsOnlyThatSection() {
        routeAllOk()
        fx.route("/users/me/points", 500, Json.error("COMMON500"))
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.loaded() }
        assertNotNull(vm.state.value.points.error)
        fx.route("/users/me/points", 200, Json.ok(POINTS))
        vm.loadPoints()
        waitUntil { vm.state.value.points.data != null }
        assertNull(vm.state.value.points.error)
        assertEquals("뽈뽈이", vm.state.value.nickname.data)
    }

    @Test
    fun startRunsOnlyOnce() {
        routeAllOk()
        val vm = vm()
        vm.start(37.5, 127.0)
        waitUntil { vm.loaded() }
        vm.start(null, null)
        assertFalse(vm.state.value.browse)
        assertEquals(37.5, vm.state.value.lat, 0.0)
    }

    @Test
    fun recentActivityViewModelLoadsAndHandlesError() {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me/completions", 200, Json.ok(LOG))
        val ok = RecentActivityViewModel(CompletionRepository(fx.api))
        waitUntil { !ok.state.value.loading }
        assertEquals(2, ok.state.value.data!!.size)

        fx.route("/users/me/completions", 500, Json.error("COMMON500"))
        ok.load()
        waitUntil { !ok.state.value.loading }
        assertNull(ok.state.value.data)
        assertNotNull(ok.state.value.error)
    }

    // ---- 포맷 ----

    @Test
    fun formattersForNewUi() {
        assertEquals("2026.11.14", Formatters.date("2026-11-14T10:30:00"))
        assertEquals("-", Formatters.date(null))
        assertEquals("-", Formatters.date("garbage"))
        assertEquals("카페", Formatters.categoryName("CAFE"))
        assertEquals("유적지", Formatters.categoryName("HERITAGE"))
        assertEquals("NEW_ONE", Formatters.categoryName("NEW_ONE"))
        assertEquals("-", Formatters.categoryName(null))
        assertEquals("뽈", Formatters.initial("  뽈뽈이"))
        assertEquals("A", Formatters.initial("abc"))
        assertEquals("?", Formatters.initial(""))
        assertEquals("?", Formatters.initial(null))
    }

    private fun logOf(n: Int): String {
        val items = (1..n).joinToString(",") {
            """{"completionId":$it,"placeId":$it,"placeName":"장소$it","category":"CAFE","address":"서울","earnedPoint":30,"completedAt":"2026-11-14T10:00:00"}"""
        }
        return """{"completions":[$items],"hasNext":false,"nextCursor":null}"""
    }

    private companion object {
        const val ME =
            """{"userId":1,"email":"a@b.com","nickname":"뽈뽈이","loginType":"EMAIL","onboardingCompleted":false,"createdAt":"2026-11-01T10:00:00"}"""
        const val POINTS = """{"totalPoint":120,"todayPoint":30,"completionCount":4}"""
        const val RECOMMEND =
            """{"places":[{"placeId":1,"name":"경복궁","category":"HERITAGE","lat":37.58,"lng":126.97,"distance":350,"rewardPoint":20,"expectedPoint":30,"isHot":true,"reason":"가까운 곳이에요"},{"placeId":2,"name":"북촌","category":"CAFE","lat":37.57,"lng":126.98,"distance":1200,"rewardPoint":20,"expectedPoint":20,"isHot":false,"reason":"아직 안 가본 곳이에요"}]}"""
        const val LOG =
            """{"completions":[{"completionId":2,"placeId":2,"placeName":"북촌","category":"CAFE","address":"서울","earnedPoint":20,"completedAt":"2026-11-14T10:00:00"},{"completionId":1,"placeId":1,"placeName":"경복궁","category":"HERITAGE","address":"서울","earnedPoint":30,"completedAt":"2026-11-13T10:00:00"}],"hasNext":false,"nextCursor":null}"""
    }
}
