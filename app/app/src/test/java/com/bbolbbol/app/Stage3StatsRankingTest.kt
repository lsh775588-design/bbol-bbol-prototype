package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiEnvelope
import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.StatsApi
import com.bbolbbol.app.data.api.StatsBar
import com.bbolbbol.app.data.api.StatsCategory
import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.api.StreakRankingResponse
import com.bbolbbol.app.data.api.UserRankingResponse
import com.bbolbbol.app.data.repository.RankingPeriod
import com.bbolbbol.app.data.repository.RankingRepository
import com.bbolbbol.app.data.repository.StatsPeriod
import com.bbolbbol.app.data.repository.StatsRepository
import com.bbolbbol.app.ui.ranking.RankingMapper
import com.bbolbbol.app.ui.ranking.RankingTab
import com.bbolbbol.app.ui.ranking.RankingViewModel
import com.bbolbbol.app.ui.ranking.hasPeriod
import com.bbolbbol.app.ui.stats.Direction
import com.bbolbbol.app.ui.stats.StatsChart
import com.bbolbbol.app.ui.stats.StatsViewModel
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

/** 3단계: 통계 파싱과 차트 데이터, 랭킹 파싱(공동 순위, me, 빈 목록)과 탭/기간 상태. */
class Stage3StatsRankingTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private val api: StatsApi = Retrofit.Builder()
        .baseUrl(fx.server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(fx.session)).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StatsApi::class.java)

    private fun week(vararg counts: Int) = counts.mapIndexed { i, c ->
        StatsBar(LocalDate.of(2026, 11, 9).plusDays(i.toLong()).toString(), c, c * 100L)
    }

    private val weekJson = """
        {"period":"WEEK","startDate":"2026-11-09","endDate":"2026-11-15","completionCount":4,"totalPoint":450,"placeCount":3,
         "previous":{"completionCount":1,"totalPoint":100},
         "categories":[{"category":"CAFE","count":3,"point":300},{"category":"PARK","count":1,"point":150},{"category":"","count":0,"point":50}],
         "timeline":[
          {"label":"2026-11-09","count":0,"point":0},{"label":"2026-11-10","count":2,"point":200},{"label":"2026-11-11","count":0,"point":0},
          {"label":"2026-11-12","count":1,"point":50},{"label":"2026-11-13","count":0,"point":0},{"label":"2026-11-14","count":1,"point":200},
          {"label":"2026-11-15","count":0,"point":0}]}
    """.trimIndent()

    // ---- 통계 파싱 ----

    @Test
    fun statsRequestUsesPeriodAndOptionalDateAndParsesAllFields() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(weekJson))
        val r = StatsRepository(api).stats(StatsPeriod.WEEK) as ApiResult.Success
        val req = fx.server.takeRequest()
        assertEquals("/users/me/stats?period=WEEK", req.path)
        assertEquals("Bearer a", req.getHeader("Authorization"))
        assertEquals(4, r.data.completionCount)
        assertEquals(450L, r.data.totalPoint)
        assertEquals(3, r.data.placeCount)
        assertEquals(1, r.data.previous.completionCount)
        assertEquals(7, r.data.timeline.size)
        assertEquals("CAFE", r.data.categories.first().category)
    }

    @Test
    fun statsMissingOptionalKeysFallBackToDefaults() {
        val s = ApiJson.decodeFromString<ApiEnvelope<StatsResponse>>(
            Json.ok("""{"period":"YEAR","startDate":"2026-01-01","endDate":"2026-12-31","extra":1}"""),
        ).data!!
        assertTrue(s.timeline.isEmpty())
        assertEquals(0, s.previous.completionCount)
    }

    @Test
    fun statsFutureDateErrorShowsServerReason() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(400, Json.error("COMMON400", """{"errors":[{"field":"date","reason":"미래 날짜는 조회할 수 없습니다."}]}"""))
        val r = StatsRepository(api).stats(StatsPeriod.WEEK, "2099-01-01")
        assertEquals("미래 날짜는 조회할 수 없습니다.", ErrorMessages.message(r))
    }

    // ---- 차트 데이터 ----

    @Test
    fun weekBarsLabelMondayToSundayMarkTodayAndFuture() {
        val stats = ApiJson.decodeFromString<ApiEnvelope<StatsResponse>>(Json.ok(weekJson)).data!!
        val bars = StatsChart.bars(stats, today = LocalDate.of(2026, 11, 12)) // 목요일
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), bars.map { it.axisLabel })
        assertEquals(listOf(false, false, false, true, false, false, false), bars.map { it.isToday })
        assertEquals(listOf(false, false, false, false, true, true, true), bars.map { it.isFuture })
        assertEquals(listOf(0, 2, 0, 1, 0, 1, 0), bars.map { it.count })
    }

    @Test
    fun weekBarsForAPastWeekHaveNoTodayAndNoFuture() {
        val stats = ApiJson.decodeFromString<ApiEnvelope<StatsResponse>>(Json.ok(weekJson)).data!!
        val bars = StatsChart.bars(stats, today = LocalDate.of(2026, 12, 1))
        assertTrue(bars.none { it.isToday || it.isFuture })
    }

    @Test
    fun weekStartingMondayEvenWhenTodayIsSunday() {
        val stats = ApiJson.decodeFromString<ApiEnvelope<StatsResponse>>(Json.ok(weekJson)).data!!
        val bars = StatsChart.bars(stats, today = LocalDate.of(2026, 11, 15))
        assertEquals("일", bars.single { it.isToday }.axisLabel)
        assertEquals("월", bars.first().axisLabel)
    }

    @Test
    fun emptyDaysStayAsZeroBarsAndFractionsAreRelativeToMax() {
        val stats = StatsResponse(period = "WEEK", timeline = week(0, 2, 0, 1, 0, 4, 0))
        val fr = StatsChart.heightFractions(StatsChart.bars(stats, LocalDate.of(2026, 11, 20)))
        assertEquals(listOf(0f, 0.5f, 0f, 0.25f, 0f, 1f, 0f), fr)
        assertEquals(List(7) { 0f }, StatsChart.heightFractions(StatsChart.bars(StatsResponse(period = "WEEK", timeline = week(0, 0, 0, 0, 0, 0, 0)), LocalDate.of(2026, 11, 20))))
        assertTrue(StatsChart.heightFractions(emptyList()).isEmpty())
    }

    @Test
    fun monthBarsLabelOnlyEveryFifthDay() {
        val days = (1..30).map { StatsBar("2026-11-%02d".format(it), 0, 0) }
        val bars = StatsChart.bars(StatsResponse(period = "MONTH", timeline = days), LocalDate.of(2026, 11, 14))
        assertEquals(listOf("1", "6", "11", "16", "21", "26"), bars.map { it.axisLabel }.filter { it.isNotEmpty() })
        assertEquals(30, bars.size)
        assertEquals("2026-11-14", bars.single { it.isToday }.label)
    }

    @Test
    fun yearBarsUseMonthLabelsAndMarkCurrentMonth() {
        val months = (1..12).map { StatsBar("2026-%02d".format(it), it, 0) }
        val bars = StatsChart.bars(StatsResponse(period = "YEAR", timeline = months), LocalDate.of(2026, 11, 14))
        assertEquals("1월", bars.first().axisLabel)
        assertEquals("12월", bars.last().axisLabel)
        assertEquals("2026-11", bars.single { it.isToday }.label)
        assertEquals(listOf(false, true), bars.takeLast(2).map { it.isFuture }.let { listOf(it[0], it[1]) })
    }

    @Test
    fun unparsableLabelsDoNotCrash() {
        val bars = StatsChart.bars(StatsResponse(period = "WEEK", timeline = listOf(StatsBar("???", 1, 1))), LocalDate.now())
        assertEquals("???", bars.single().axisLabel)
    }

    @Test
    fun deltaAgainstPreviousPeriod() {
        assertEquals(Direction.UP, StatsChart.delta(5, 2).direction)
        assertEquals(3L, StatsChart.delta(5, 2).diff)
        assertEquals(Direction.DOWN, StatsChart.delta(1, 4).direction)
        assertEquals(3L, StatsChart.delta(1, 4).diff)
        assertEquals(Direction.SAME, StatsChart.delta(0, 0).direction)
        assertEquals(Direction.UP, StatsChart.delta(3, 0).direction)
    }

    @Test
    fun categorySlicesDropZeroCountGroupsAndNameUnknownAsEtc() {
        val s = StatsResponse(
            categories = listOf(
                StatsCategory("CAFE", 3, 300), StatsCategory("PARK", 1, 150),
                StatsCategory("", 0, 50), StatsCategory("MUSEUM", 0, 10), StatsCategory("", 4, 1),
            ),
        )
        val slices = StatsChart.categories(s)
        assertEquals(listOf("카페", "공원", "기타"), slices.map { it.name })
        assertEquals(1f, slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        assertEquals(0.5f, slices.last().fraction, 0.0001f)
        assertTrue(StatsChart.categories(StatsResponse(categories = listOf(StatsCategory("CAFE", 0, 50)))).isEmpty())
    }

    // ---- 통계 ViewModel ----

    @Test
    fun statsViewModelLoadsWeekThenSwitchesPeriod() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(weekJson))
        val vm = StatsViewModel(StatsRepository(api)) { LocalDate.of(2026, 11, 12) }
        waitUntil { !vm.state.value.loading }
        assertEquals(StatsPeriod.WEEK, vm.state.value.period)
        assertEquals(4, vm.state.value.stats!!.completionCount)
        assertFalse(vm.state.value.isEmpty)
        fx.enqueue(200, Json.ok("""{"period":"MONTH","startDate":"2026-11-01","endDate":"2026-11-30"}"""))
        vm.selectPeriod(StatsPeriod.MONTH)
        waitUntil { !vm.state.value.loading }
        fx.server.takeRequest()
        assertEquals("/users/me/stats?period=MONTH", fx.server.takeRequest().path)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun statsViewModelErrorThenRetry() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(500, Json.error("COMMON500"))
        val vm = StatsViewModel(StatsRepository(api))
        waitUntil { !vm.state.value.loading }
        assertNull(vm.state.value.stats)
        assertNotNull(vm.state.value.error)
        fx.enqueue(200, Json.ok(weekJson))
        vm.load()
        waitUntil { !vm.state.value.loading }
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.stats)
    }

    // ---- 랭킹 파싱 ----

    private val usersJson = """
        {"period":"WEEK","startDate":"2026-11-09","endDate":"2026-11-15","updatedAt":"2026-11-14T10:00:00",
         "rankings":[
          {"rank":1,"nickname":"뽈이","point":500,"completionCount":9,"isMe":false},
          {"rank":2,"nickname":"나","point":300,"completionCount":5,"isMe":true},
          {"rank":2,"nickname":"쭈니","point":300,"completionCount":4,"isMe":false},
          {"rank":4,"nickname":"하나","point":100,"completionCount":1,"isMe":false}],
         "me":{"rank":2,"nickname":"나","point":300,"completionCount":5}}
    """.trimIndent()

    @Test
    fun userRankingKeepsTiesAndMarksMe() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(200, Json.ok(usersJson))
        val r = RankingRepository(api).users(RankingPeriod.WEEK) as ApiResult.Success
        val req = fx.server.takeRequest()
        assertEquals("/rankings/users?period=WEEK&size=50", req.path)
        val c = RankingMapper.users(r.data)
        assertEquals(listOf(1, 2, 2, 4), c.rows.map { it.rank })
        assertEquals(listOf(false, true, false, false), c.rows.map { it.isMe })
        assertEquals("5회", c.rows[1].primary)
        assertEquals("300P", c.rows[1].secondary)
        assertEquals(2, c.me!!.rank)
    }

    @Test
    fun userRankingWithoutMeAndEmptyList() {
        val r = ApiJson.decodeFromString<ApiEnvelope<UserRankingResponse>>(
            Json.ok("""{"period":"DAY","startDate":"2026-11-14","endDate":"2026-11-14","rankings":[],"me":null,"updatedAt":"2026-11-14T10:00:00"}"""),
        ).data!!
        val c = RankingMapper.users(r)
        assertTrue(c.rows.isEmpty())
        assertNull(c.me)
    }

    @Test
    fun streakAndPlaceMapping() {
        val st = ApiJson.decodeFromString<ApiEnvelope<StreakRankingResponse>>(
            Json.ok("""{"rankings":[{"rank":1,"nickname":"뽈이","streakDays":7,"isMe":true}],"me":{"rank":1,"nickname":"뽈이","streakDays":7,"longestStreakDays":9}}"""),
        ).data!!
        val c = RankingMapper.streaks(st)
        assertEquals("7일", c.rows.single().primary)
        assertEquals("최장 9일", c.me!!.secondary)
        val pl = ApiJson.decodeFromString<ApiEnvelope<com.bbolbbol.app.data.api.PlaceRankingResponse>>(
            Json.ok("""{"period":"WEEK","startDate":"a","endDate":"b","rankings":[{"rank":1,"placeId":3,"placeName":"북촌","category":"HERITAGE","address":"x","completionCount":12,"rewardPoint":100}],"updatedAt":"t"}"""),
        ).data!!
        val pc = RankingMapper.places(pl)
        assertEquals("북촌", pc.rows.single().name)
        assertEquals("12회", pc.rows.single().primary)
        assertEquals("유적지", pc.rows.single().secondary)
        assertNull(pc.me)
    }

    // ---- 랭킹 ViewModel: 탭/기간 상태 ----

    @Test
    fun rankingViewModelStartsOnUsersWeekAndSwitchesTabAndPeriod() {
        fx.session.saveTokens("a", "r")
        fx.route("/rankings/users", 200, Json.ok(usersJson))
        fx.route("/rankings/places", 200, Json.ok("""{"period":"WEEK","startDate":"a","endDate":"b","rankings":[],"updatedAt":"t"}"""))
        fx.route("/rankings/streaks", 200, Json.ok("""{"rankings":[],"me":null}"""))
        val vm = RankingViewModel(RankingRepository(api))
        waitUntil { !vm.state.value.loading }
        assertEquals(RankingTab.USERS, vm.state.value.tab)
        assertEquals(RankingPeriod.WEEK, vm.state.value.period)
        assertEquals(4, vm.state.value.content!!.rows.size)

        vm.selectPeriod(RankingPeriod.MONTH)
        waitUntil { !vm.state.value.loading }
        assertEquals(RankingPeriod.MONTH, vm.state.value.period)
        vm.selectTab(RankingTab.PLACES)
        waitUntil { !vm.state.value.loading }
        assertTrue(vm.state.value.isEmpty)
        vm.selectTab(RankingTab.STREAKS)
        waitUntil { !vm.state.value.loading }
        assertFalse(vm.state.value.tab.hasPeriod)
        assertTrue(RankingTab.USERS.hasPeriod && RankingTab.PLACES.hasPeriod)

        val paths = generateSequence { fx.server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS) }.map { it.path }.toList()
        assertEquals(
            listOf("/rankings/users?period=WEEK&size=50", "/rankings/users?period=MONTH&size=50", "/rankings/places?period=MONTH&size=30", "/rankings/streaks?size=30"),
            paths,
        )
    }

    @Test
    fun rankingErrorThenRetryAndSameSelectionDoesNotReload() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(500, Json.error("COMMON500"))
        val vm = RankingViewModel(RankingRepository(api))
        waitUntil { !vm.state.value.loading }
        assertNull(vm.state.value.content)
        assertNotNull(vm.state.value.error)
        fx.enqueue(200, Json.ok(usersJson))
        vm.load()
        waitUntil { !vm.state.value.loading }
        assertNotNull(vm.state.value.content)
        val before = fx.server.requestCount
        vm.selectTab(RankingTab.USERS)
        vm.selectPeriod(RankingPeriod.WEEK)
        assertEquals(before, fx.server.requestCount)
    }

    @Test
    fun rankingSizeLimitErrorFromServerIsShown() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(400, Json.error("COMMON400", """{"errors":[{"field":"size","reason":"size는 1 이상 100 이하여야 합니다."}]}"""))
        val r = RankingRepository(api).users(RankingPeriod.DAY, 500)
        assertEquals("size는 1 이상 100 이하여야 합니다.", ErrorMessages.message(r))
    }

    // ---- ErrorMessages ----

    @Test
    fun photoAndFileCodesAreMapped() {
        for (code in listOf("COMPLETION403", "COMPLETION404", "COMPLETION4004", "COMPLETION4091", "FILE4001", "FILE413")) {
            assertNotNull(code, ErrorMessages.forCode(code))
        }
        assertTrue(ErrorMessages.forCode("COMPLETION4004")!!.contains("1시간"))
    }
    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }
}
