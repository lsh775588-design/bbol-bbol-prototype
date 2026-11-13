package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CompletionRequest
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.ui.history.ActivityLogViewModel
import com.bbolbbol.app.ui.history.PointHistoryViewModel
import com.bbolbbol.app.ui.map.ArrivalFix
import com.bbolbbol.app.ui.map.ArrivalOutcome
import com.bbolbbol.app.ui.map.LocationMode
import com.bbolbbol.app.ui.map.MapViewModel
import com.bbolbbol.app.ui.me.MeViewModel
import com.bbolbbol.app.ui.recommend.RecommendViewModel
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 2단계: 도착 인증, 포인트, 활동 일지, 추천. */
class Stage2Test {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private fun mapVm(): MapViewModel {
        fx.session.saveTokens("a", "r")
        fx.route("/place-categories", 200, Json.ok("""{"categories":[]}"""))
        fx.route("/places", 200, Json.ok("""{"places":[]}"""))
        fx.route("/places/5", 200, Json.ok(DETAIL_JSON))
        return MapViewModel(PlaceRepository(fx.api), CompletionRepository(fx.api))
    }

    private val fix = ArrivalFix(37.57, 126.97, 12.5, false)

    // ---- 도착 인증 ----

    @Test
    fun arrivalRequestSendsExactJsonKeys() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(201, Json.ok(COMPLETION_JSON))
        val r = CompletionRepository(fx.api).complete(5, CompletionRequest(37.57, 126.97, 12.5, true))
        assertTrue(r is ApiResult.Success)
        val req = fx.server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/places/5/completions", req.path)
        assertEquals("Bearer a", req.getHeader("Authorization"))
        assertEquals("""{"lat":37.57,"lng":126.97,"accuracy":12.5,"isMockLocation":true}""", req.body.readUtf8())
    }

    @Test
    fun arrivalResponseParsesAllFields() = runBlocking {
        fx.enqueue(201, Json.ok(COMPLETION_JSON))
        val d = (CompletionRepository(fx.api).complete(5, CompletionRequest(1.0, 2.0, 3.0, false)) as ApiResult.Success).data
        assertEquals(11L, d.completionId)
        assertEquals("경복궁", d.placeName)
        assertTrue(d.isFirstVisit)
        assertEquals(30, d.earnedPoint)
        assertEquals(130L, d.totalPoint)
        assertEquals(10, d.photoBonusPoint)
        assertEquals("2026-11-15T10:00:00", d.photoDeadline)
    }

    @Test
    fun arrivalSuccessSetsOutcomeAndRefreshesPlacesAndDetail() {
        val vm = mapVm()
        vm.onLocation(37.57, 126.97)
        waitUntil { !vm.state.value.loading }
        vm.openDetail(5)
        waitUntil { vm.state.value.detail != null }
        val before = fx.server.requestCount
        fx.route("/places/5/completions", 201, Json.ok(COMPLETION_JSON))
        fx.route("/places/5", 200, Json.ok(DETAIL_JSON.replace("\"isCompleted\":false", "\"isCompleted\":true")))
        vm.arrive(5, fix)
        waitUntil { vm.state.value.arrivalOutcome != null && vm.state.value.detail?.isCompleted == true }
        val outcome = vm.state.value.arrivalOutcome as ArrivalOutcome.Success
        assertEquals(30, outcome.result.earnedPoint)
        assertTrue(outcome.result.isFirstVisit)
        assertFalse(vm.state.value.arriving)
        // 인증 1회 + 목록 1회 + 상세 1회
        waitUntil { fx.server.requestCount >= before + 3 }
    }

    @Test
    fun arrivalErrorCodesShowKoreanMessages() {
        val cases = mapOf(
            "COMPLETION4001" to Pair(400, "인증 반경 밖이에요"),
            "COMPLETION4002" to Pair(400, "GPS 정확도가 낮아요"),
            "COMPLETION4003" to Pair(400, "모의 위치는 사용할 수 없어요"),
            "COMPLETION409" to Pair(409, "오늘 이미 인증했어요"),
            "PLACE404" to Pair(404, "존재하지 않는 장소예요"),
        )
        for ((code, expected) in cases) {
            val vm = mapVm()
            vm.onLocation(37.57, 126.97)
            fx.route("/places/5/completions", expected.first, Json.error(code))
            vm.arrive(5, fix)
            waitUntil { vm.state.value.arrivalOutcome != null }
            val o = vm.state.value.arrivalOutcome as ArrivalOutcome.Error
            assertTrue("$code -> ${o.message}", o.message.contains(expected.second))
            assertFalse(vm.state.value.arriving)
        }
    }

    @Test
    fun arrivalAuth401ClearsSessionSoNavGoesToLogin() {
        val vm = mapVm()
        vm.onLocation(37.57, 126.97)
        fx.route("/places/5/completions", 401, Json.error("AUTH401"))
        vm.arrive(5, fix)
        waitUntil { vm.state.value.arrivalOutcome != null }
        assertFalse(fx.session.loggedIn.value)
        assertEquals(ErrorMessages.forCode("AUTH401"), (vm.state.value.arrivalOutcome as ArrivalOutcome.Error).message)
    }

    @Test
    fun arrivalNetworkError() {
        val vm = mapVm()
        vm.onLocation(37.57, 126.97)
        fx.server.shutdown()
        vm.arrive(5, fix)
        waitUntil { vm.state.value.arrivalOutcome != null }
        assertEquals(ErrorMessages.NETWORK, (vm.state.value.arrivalOutcome as ArrivalOutcome.Error).message)
    }

    @Test
    fun browseModeBlocksArrivalAndSendsNothing() {
        val vm = mapVm()
        vm.enterBrowseMode()
        waitUntil { !vm.state.value.loading }
        assertEquals(LocationMode.BROWSE, vm.state.value.mode)
        assertNotNull(vm.state.value.arrivalBlockedReason(hasLocationPermission = true))
        assertTrue(vm.state.value.arrivalBlockedReason(true)!!.contains("둘러보기"))
        val before = fx.server.requestCount
        vm.arrive(5, fix)
        Thread.sleep(200)
        assertEquals(before, fx.server.requestCount)
        assertNull(vm.state.value.arrivalOutcome)
    }

    @Test
    fun arrivalRequiresLocationPermission() {
        val vm = mapVm()
        vm.onLocation(37.57, 126.97)
        assertNotNull(vm.state.value.arrivalBlockedReason(hasLocationPermission = false))
        assertNull(vm.state.value.arrivalBlockedReason(hasLocationPermission = true))
    }

    @Test
    fun locationFailureShowsMessage() {
        val vm = mapVm()
        vm.onLocation(37.57, 126.97)
        vm.onArrivalLocationFailed()
        assertTrue((vm.state.value.arrivalOutcome as ArrivalOutcome.Error).message.contains("위치"))
        vm.dismissArrivalOutcome()
        assertNull(vm.state.value.arrivalOutcome)
    }

    @Test
    fun openDetailFromRecommendationCentersMap() {
        val vm = mapVm()
        vm.onLocation(37.0, 127.0)
        vm.openDetail(5, centerOnPlace = true)
        waitUntil { vm.state.value.detail != null }
        assertEquals(37.58, vm.state.value.focusLat!!, 0.0)
        assertEquals(126.97, vm.state.value.focusLng!!, 0.0)
    }

    // ---- 포인트 요약 ----

    @Test
    fun pointSummaryParses() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(POINTS_JSON))
        val d = (PointRepository(fx.api).summary() as ApiResult.Success).data
        assertEquals(120L, d.totalPoint)
        assertEquals(30L, d.todayPoint)
        assertEquals(4L, d.completionCount)
        assertEquals("/users/me/points", fx.server.takeRequest().path)
    }

    @Test
    fun meViewModelShowsPointsAndRetriesOnFailure() {
        fx.session.saveTokens("a", "r")
        fx.route("/users/me", 200, Json.ok(ME_JSON))
        fx.route("/users/me/points", 500, Json.error("COMMON500"))
        val vm = MeViewModel(
            com.bbolbbol.app.data.repository.UserRepository(fx.api),
            com.bbolbbol.app.data.repository.AuthRepository(fx.api, fx.session),
            PointRepository(fx.api),
        )
        waitUntil { !vm.state.value.pointsLoading }
        assertNull(vm.state.value.points)
        assertNotNull(vm.state.value.pointsError)
        fx.route("/users/me/points", 200, Json.ok(POINTS_JSON))
        vm.loadPoints()
        waitUntil { vm.state.value.points != null }
        assertEquals(120L, vm.state.value.points!!.totalPoint)
        assertNull(vm.state.value.pointsError)
    }

    // ---- 포인트 내역 / 활동 일지 페이징 ----

    @Test
    fun pointHistoryPagination() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(historyPage(listOf(30, 29), hasNext = true, next = 29)))
        val vm = PointHistoryViewModel(PointRepository(fx.api))
        waitUntil { !vm.state.value.loading }
        assertEquals(2, vm.state.value.items.size)
        assertTrue(vm.state.value.hasNext)
        assertEquals("QUEST_COMPLETE", vm.state.value.items[0].type)

        fx.enqueue(200, Json.ok(historyPage(listOf(28), hasNext = false, next = null)))
        vm.loadMore()
        waitUntil { !vm.state.value.loadingMore }
        assertEquals(3, vm.state.value.items.size)
        assertFalse(vm.state.value.hasNext)

        // 끝에서는 더 요청하지 않는다.
        val count = fx.server.requestCount
        vm.loadMore()
        Thread.sleep(100)
        assertEquals(count, fx.server.requestCount)

        val first = fx.server.takeRequest()
        assertEquals("/users/me/point-histories?size=20", first.path)
        assertEquals("/users/me/point-histories?cursor=29&size=20", fx.server.takeRequest().path)
    }

    @Test
    fun pointHistoryEmptyAndErrorStates() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok("""{"histories":[],"hasNext":false,"nextCursor":null}"""))
        val empty = PointHistoryViewModel(PointRepository(fx.api))
        waitUntil { !empty.state.value.loading }
        assertTrue(empty.state.value.isEmpty)

        fx.enqueue(500, Json.error("COMMON500"))
        val failing = PointHistoryViewModel(PointRepository(fx.api))
        waitUntil { !failing.state.value.loading }
        assertNotNull(failing.state.value.error)
        assertFalse(failing.state.value.isEmpty)
        fx.enqueue(200, Json.ok(historyPage(listOf(5), false, null)))
        failing.refresh()
        waitUntil { failing.state.value.items.isNotEmpty() }
        assertNull(failing.state.value.error)
    }

    @Test
    fun loadMoreFailureKeepsItemsAndAllowsRetry() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(historyPage(listOf(30), true, 30)))
        val vm = PointHistoryViewModel(PointRepository(fx.api))
        waitUntil { !vm.state.value.loading }
        fx.enqueue(500, Json.error("COMMON500"))
        vm.loadMore()
        waitUntil { vm.state.value.moreError != null }
        assertEquals(1, vm.state.value.items.size)
        assertTrue(vm.state.value.hasNext)
        fx.enqueue(200, Json.ok(historyPage(listOf(20), false, null)))
        vm.loadMore()
        waitUntil { vm.state.value.items.size == 2 }
        assertNull(vm.state.value.moreError)
    }

    @Test
    fun activityLogPagination() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(logPage(listOf(9, 8), hasNext = true, next = 8)))
        val vm = ActivityLogViewModel(CompletionRepository(fx.api))
        waitUntil { !vm.state.value.loading }
        val item = vm.state.value.items[0]
        assertEquals("경복궁", item.placeName)
        assertEquals("CAFE", item.category)
        assertEquals(30, item.earnedPoint)
        assertEquals("2026-11-14T10:00:00", item.completedAt)

        fx.enqueue(200, Json.ok(logPage(listOf(7), hasNext = false, next = null)))
        vm.loadMore()
        waitUntil { vm.state.value.items.size == 3 }
        assertFalse(vm.state.value.hasNext)
        assertEquals("/users/me/completions?size=20", fx.server.takeRequest().path)
        assertEquals("/users/me/completions?cursor=8&size=20", fx.server.takeRequest().path)
    }

    // ---- 추천 ----

    @Test
    fun recommendationsParseReasonAndSendQuery() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(RECOMMEND_JSON))
        val list = (PlaceRepository(fx.api).recommendations(37.57, 126.97, 5) as ApiResult.Success).data
        assertEquals(2, list.size)
        assertEquals("가까운 곳이에요", list[0].reason)
        assertEquals(350L, list[0].distance)
        assertTrue(list[0].isHot)
        assertEquals("HOT_TIME", list[0].hotType)
        assertEquals("/places/recommendations?lat=37.57&lng=126.97&size=5", fx.server.takeRequest().path)
    }

    @Test
    fun recommendViewModelLoadsAndHandlesError() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(RECOMMEND_JSON))
        val vm = RecommendViewModel(PlaceRepository(fx.api), 37.57, 126.97)
        waitUntil { !vm.state.value.loading }
        assertEquals(2, vm.state.value.places.size)

        fx.enqueue(400, Json.error("COMMON400", """{"errors":[{"field":"lat","reason":"lat은 필수입니다."}]}"""))
        val bad = RecommendViewModel(PlaceRepository(fx.api), 0.0, 0.0)
        waitUntil { !bad.state.value.loading }
        assertEquals("lat은 필수입니다.", bad.state.value.error)
    }

    // ---- ErrorMessages ----

    @Test
    fun errorMessagesCoverStage2Codes() {
        assertEquals("인증 반경 밖이에요. 장소에 더 가까이 가서 다시 시도해 주세요.", ErrorMessages.forCode("COMPLETION4001"))
        assertTrue(ErrorMessages.forCode("COMPLETION4002")!!.startsWith("GPS 정확도가 낮아요"))
        assertEquals("모의 위치는 사용할 수 없어요.", ErrorMessages.forCode("COMPLETION4003"))
        assertTrue(ErrorMessages.forCode("COMPLETION409")!!.startsWith("오늘 이미 인증했어요"))
        assertEquals("존재하지 않는 장소예요.", ErrorMessages.forCode("PLACE404"))
        val common400 = ApiResult.Failure(
            400, "COMMON400", "x", listOf(com.bbolbbol.app.data.api.FieldError("size", "size는 1 이상 50 이하여야 합니다.")),
        )
        assertEquals("size는 1 이상 50 이하여야 합니다.", ErrorMessages.message(common400))
    }

    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }

    private fun historyPage(ids: List<Int>, hasNext: Boolean, next: Int?): String {
        val items = ids.joinToString(",") {
            """{"historyId":$it,"type":"QUEST_COMPLETE","point":30,"completionId":1,"placeId":5,"placeName":"경복궁","category":"CAFE","createdAt":"2026-11-14T10:00:00"}"""
        }
        return """{"histories":[$items],"hasNext":$hasNext,"nextCursor":${next ?: "null"}}"""
    }

    private fun logPage(ids: List<Int>, hasNext: Boolean, next: Int?): String {
        val items = ids.joinToString(",") {
            """{"completionId":$it,"placeId":5,"placeName":"경복궁","category":"CAFE","address":"서울 종로구","earnedPoint":30,"photoUrl":null,"completedAt":"2026-11-14T10:00:00"}"""
        }
        return """{"completions":[$items],"hasNext":$hasNext,"nextCursor":${next ?: "null"}}"""
    }

    private companion object {
        const val COMPLETION_JSON =
            """{"completionId":11,"placeId":5,"placeName":"경복궁","isFirstVisit":true,"earnedPoint":30,"totalPoint":130,"completedAt":"2026-11-14T10:00:00","photoBonusPoint":10,"photoDeadline":"2026-11-15T10:00:00"}"""
        const val POINTS_JSON = """{"totalPoint":120,"todayPoint":30,"completionCount":4}"""
        const val DETAIL_JSON =
            """{"placeId":5,"name":"경복궁","category":"CAFE","categoryName":"카페","address":"서울","lat":37.58,"lng":126.97,"radius":100,"rewardPoint":20,"expectedPoint":30,"isHot":false,"isCompleted":false,"completedCount":3}"""
        const val ME_JSON =
            """{"userId":1,"email":"a@b.com","nickname":"닉","loginType":"EMAIL","onboardingCompleted":false,"createdAt":"2026-11-01T10:00:00"}"""
        const val RECOMMEND_JSON =
            """{"places":[{"placeId":1,"name":"경복궁","category":"CAFE","lat":37.58,"lng":126.97,"distance":350,"radius":100,"notifyRadius":300,"rewardPoint":20,"expectedPoint":30,"isHot":true,"hotType":"HOT_TIME","visitedBefore":false,"isCompleted":false,"reason":"가까운 곳이에요"},{"placeId":2,"name":"북촌","category":"CAFE","lat":37.57,"lng":126.98,"distance":1200,"radius":100,"notifyRadius":300,"rewardPoint":20,"expectedPoint":20,"isHot":false,"visitedBefore":true,"isCompleted":false,"reason":"아직 안 가본 곳이에요"}]}"""
    }
}
