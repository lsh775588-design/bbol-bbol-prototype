package com.bbolbbol.app

import com.bbolbbol.app.data.local.InMemoryQuestStorage
import com.bbolbbol.app.data.local.QuestBook
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.ui.map.ArrivalFix
import com.bbolbbol.app.ui.map.ArrivalOutcome
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.map.distanceMeters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 받은 퀘스트 기록(QuestBook)과 지도 ViewModel의 퀘스트 받기 흐름. */
class QuestBookTest {
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

    // 2026-11-14 23:30 KST = 14:30 UTC
    private val clock = MutableClock(Instant.parse("2026-11-14T14:30:00Z"))
    private val book = QuestBook(InMemoryQuestStorage(), clock)

    @Test
    fun acceptThenAcceptedAndListed() {
        assertFalse(book.isAccepted(1, 5))
        book.accept(1, 5)
        book.accept(1, 7)
        assertTrue(book.isAccepted(1, 5))
        assertEquals(setOf(5L, 7L), book.acceptedIds(1))
    }

    @Test
    fun expiresAtMidnightKst() {
        book.accept(1, 5)
        // 23:59:59 KST: 아직 오늘
        clock.instant = Instant.parse("2026-11-14T14:59:59Z")
        assertTrue(book.isAccepted(1, 5))
        // 00:00:00 KST(다음 날): 사라진다. UTC 날짜는 아직 같은 날이라 KST 기준임을 확인한다.
        clock.instant = Instant.parse("2026-11-14T15:00:00Z")
        assertFalse(book.isAccepted(1, 5))
        assertTrue(book.acceptedIds(1).isEmpty())
        // 다음 날 다시 받을 수 있다.
        book.accept(1, 5)
        assertTrue(book.isAccepted(1, 5))
    }

    @Test
    fun perUserIsolation() {
        book.accept(1, 5)
        assertFalse(book.isAccepted(2, 5))
        assertTrue(book.acceptedIds(2).isEmpty())
        book.accept(2, 9)
        book.remove(1, 5)
        assertEquals(setOf(9L), book.acceptedIds(2))
    }

    @Test
    fun removeAndClearAll() {
        book.accept(1, 5)
        book.accept(2, 6)
        book.remove(1, 5)
        assertFalse(book.isAccepted(1, 5))
        assertTrue(book.isAccepted(2, 6))
        book.clearAll()
        assertTrue(book.acceptedIds(2).isEmpty())
    }

    @Test
    fun sessionClearWipesBookViaCallback() {
        fx.session.onCleared = { book.clearAll() }
        fx.session.saveTokens("a", "r")
        book.accept(1, 5)
        fx.session.clear()
        assertFalse(book.isAccepted(1, 5))
    }

    @Test
    fun distanceIsRoughlyRight() {
        // 위도 0.01도 차이 = 약 1.1km
        val m = distanceMeters(37.57, 126.97, 37.58, 126.97)
        assertTrue(m in 1100..1120)
        assertEquals(0L, distanceMeters(37.5, 127.0, 37.5, 127.0))
    }

    // ---- 지도 ViewModel ----

    private val fix = ArrivalFix(37.58, 126.97, 10.0, false)

    private fun vm(): MapViewModel {
        fx.session.saveTokens("a", "r")
        fx.route("/place-categories", 200, Json.ok("""{"categories":[]}"""))
        fx.route("/places", 200, Json.ok("""{"places":[]}"""))
        fx.route("/places/5", 200, Json.ok(DETAIL))
        return MapViewModel(PlaceRepository(fx.api), CompletionRepository(fx.api), book) { 1L }
    }

    private fun waitUntil(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("시간 초과")
            Thread.sleep(10)
        }
    }

    @Test
    fun arriveIsIgnoredUntilQuestAccepted() {
        val vm = vm()
        vm.onLocation(37.58, 126.97)
        vm.openDetail(5)
        waitUntil { vm.state.value.detail != null }
        assertFalse(vm.state.value.detailAccepted)
        val before = fx.server.requestCount
        vm.arrive(5, fix)
        Thread.sleep(150)
        assertEquals(before, fx.server.requestCount)
    }

    @Test
    fun acceptThenArriveClearsAcceptedAndCancelWorks() {
        val vm = vm()
        vm.onLocation(37.58, 126.97)
        vm.openDetail(5)
        waitUntil { vm.state.value.detail != null }
        vm.acceptQuest(5)
        waitUntil { vm.state.value.detailAccepted }
        vm.cancelQuest(5)
        waitUntil { !vm.state.value.detailAccepted }
        vm.acceptQuest(5)
        waitUntil { vm.state.value.detailAccepted }

        fx.route("/places/5/completions", 201, Json.ok(COMPLETION))
        fx.route("/places/5", 200, Json.ok(DETAIL.replace("\"isCompleted\":false", "\"isCompleted\":true")))
        vm.arrive(5, fix)
        waitUntil { vm.state.value.arrivalOutcome is ArrivalOutcome.Success }
        waitUntil { vm.state.value.acceptedIds.isEmpty() }
        assertFalse(book.isAccepted(1, 5))
    }

    @Test
    fun completedTodayPlaceCannotBeAccepted() {
        val vm = vm()
        fx.route("/places/5", 200, Json.ok(DETAIL.replace("\"isCompleted\":false", "\"isCompleted\":true")))
        vm.onLocation(37.58, 126.97)
        vm.openDetail(5)
        waitUntil { vm.state.value.detail?.isCompleted == true }
        vm.acceptQuest(5)
        Thread.sleep(150)
        assertFalse(book.isAccepted(1, 5))
    }

    private companion object {
        const val DETAIL =
            """{"placeId":5,"name":"경복궁","category":"CAFE","categoryName":"카페","address":"서울","lat":37.58,"lng":126.97,"radius":100,"rewardPoint":20,"expectedPoint":30,"isHot":false,"isCompleted":false,"completedCount":3}"""
        const val COMPLETION =
            """{"completionId":11,"placeId":5,"placeName":"경복궁","isFirstVisit":true,"earnedPoint":30,"totalPoint":130,"completedAt":"2026-11-14T10:00:00","photoBonusPoint":10,"photoDeadline":"2026-11-15T10:00:00"}"""
    }
}
