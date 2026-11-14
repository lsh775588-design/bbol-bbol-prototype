package com.bbolbbol.app

import com.bbolbbol.app.data.local.InMemoryQuestStorage
import com.bbolbbol.app.data.local.QuestBook
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.ui.home.HomeViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 홈의 "진행 중인 퀘스트": QuestBook + 장소 상세로 만든 목록. */
class HomeOngoingTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = object : Clock() {
            override fun getZone() = zone
            override fun withZone(zone: ZoneId) = this
            override fun instant() = this@MutableClock.instant
        }
        override fun instant(): Instant = instant
    }

    private val clock = MutableClock(Instant.parse("2026-11-14T03:00:00Z"))
    private val book = QuestBook(InMemoryQuestStorage(), clock)

    private fun detail(id: Long, name: String, lat: Double, lng: Double, completed: Boolean = false) =
        """{"placeId":$id,"name":"$name","category":"CAFE","lat":$lat,"lng":$lng,"rewardPoint":${id * 10},"isCompleted":$completed}"""

    private fun vm(): HomeViewModel {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me", 200, Json.ok(HomeTest.ME))
        fx.route("/users/me/points", 200, Json.ok(HomeTest.POINTS))
        fx.route("/places/recommendations", 200, Json.ok(HomeTest.RECOMMEND))
        fx.route("/users/me/completions", 200, Json.ok(HomeTest.LOG))
        return HomeViewModel(
            UserRepository(fx.api), PointRepository(fx.api), PlaceRepository(fx.api), CompletionRepository(fx.api),
            book, { 7L },
        )
    }

    private fun waitUntil(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("시간 초과")
            Thread.sleep(10)
        }
    }

    @Test
    fun listsAcceptedQuestsWithPlaceDataAndDistance() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        fx.route("/places/2", 200, Json.ok(detail(2, "먼곳", 37.6000, 126.9780)))
        book.accept(7, 2); book.accept(7, 1); book.accept(8, 1)
        val vm = vm()
        vm.start(37.5796, 126.9780)
        waitUntil { vm.state.value.ongoing.size == 2 }
        val o = vm.state.value.ongoing
        assertEquals(listOf("가까운곳", "먼곳"), o.map { it.name }) // 가까운 순
        assertEquals(0L, o[0].distance)
        assertTrue(o[1].distance!! > 2000)
        assertEquals(20, o[1].rewardPoint)
        assertEquals("CAFE", o[0].category)
        assertTrue(vm.state.value.ongoingHasLocation)
    }

    @Test
    fun emptyWhenNothingAccepted() {
        val vm = vm()
        vm.start(null, null)
        waitUntil { !vm.state.value.points.loading }
        assertTrue(vm.state.value.ongoing.isEmpty())
    }

    @Test
    fun withoutLocationShowsNoDistance() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        book.accept(7, 1)
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 1 }
        assertNull(vm.state.value.ongoing[0].distance)
        assertFalse(vm.state.value.ongoingHasLocation)
    }

    @Test
    fun dropsExpiredQuestsAtMidnightAndRefreshOnReentry() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        book.accept(7, 1)
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 1 }
        clock.instant = Instant.parse("2026-11-14T16:00:00Z") // KST 다음 날 01:00
        vm.start(null, null) // 탭 재진입
        waitUntil { vm.state.value.ongoing.isEmpty() }
    }

    @Test
    fun picksUpQuestAcceptedOnMapWhenReentering() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        fx.route("/places/2", 200, Json.ok(detail(2, "먼곳", 37.6, 126.97)))
        book.accept(7, 1)
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 1 }
        book.accept(7, 2)
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 2 }
        book.remove(7, 1) // 지도에서 인증/포기
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.map { it.placeId } == listOf(2L) }
    }

    @Test
    fun dropsQuestAlreadyCompletedOnServer() {
        fx.route("/places/1", 200, Json.ok(detail(1, "완료됨", 37.5, 127.0, completed = true)))
        book.accept(7, 1)
        val vm = vm()
        vm.start(null, null)
        waitUntil { !vm.state.value.points.loading && book.acceptedIds(7).isEmpty() }
        assertTrue(vm.state.value.ongoing.isEmpty())
    }

    @Test
    fun lookupFailureShowsOnlyResolvableQuests() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        fx.route("/places/2", 500, Json.error("COMMON500"))
        book.accept(7, 1); book.accept(7, 2)
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 1 }
        assertEquals(1L, vm.state.value.ongoing[0].placeId)
        assertEquals(setOf(1L, 2L), book.acceptedIds(7)) // 실패한 것은 기록에 남겨 다음에 다시 시도
        fx.route("/places/2", 200, Json.ok(detail(2, "먼곳", 37.6, 126.97)))
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 2 }
    }

    @Test
    fun abandonRemovesFromBookAndList() {
        fx.route("/places/1", 200, Json.ok(detail(1, "가까운곳", 37.5796, 126.9780)))
        book.accept(7, 1)
        val vm = vm()
        vm.start(null, null)
        waitUntil { vm.state.value.ongoing.size == 1 }
        vm.abandon(1)
        waitUntil { vm.state.value.ongoing.isEmpty() }
        assertFalse(book.isAccepted(7, 1))
    }
}
