package com.bbolbbol.app

import com.bbolbbol.app.data.api.ApiJson
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.AuthInterceptor
import com.bbolbbol.app.data.api.StatsApi
import com.bbolbbol.app.data.repository.PhotoRepository
import com.bbolbbol.app.ui.photo.ImageOps
import com.bbolbbol.app.ui.photo.PhotoPolicy
import com.bbolbbol.app.ui.photo.PhotoPrepareService
import com.bbolbbol.app.ui.photo.PhotoPreparer
import com.bbolbbol.app.ui.photo.PhotoUploadViewModel
import com.bbolbbol.app.ui.photo.PhotoUrls
import com.bbolbbol.app.ui.photo.PrepareResult
import com.bbolbbol.app.ui.photo.TOO_LARGE_MESSAGE
import com.bbolbbol.app.ui.photo.UNREADABLE_MESSAGE
import com.bbolbbol.app.ui.photo.UploadStatus
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.time.LocalDateTime

/** 3단계: 사진 업로드(요청 모양, 에러 코드), 이미지 준비, 인증 이미지 주소. */
class Stage3PhotoTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val fx = Fixture()

    @After
    fun tearDown() = fx.shutdown()

    private val statsApi: StatsApi = Retrofit.Builder()
        .baseUrl(fx.server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(fx.session)).build())
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StatsApi::class.java)

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())

    // ---- 업로드 요청 모양과 응답 ----

    @Test
    fun uploadSendsMultipartPartNamedPhotoAsJpegWithBearer() = runBlocking {
        fx.session.saveTokens("acc", "ref")
        fx.enqueue(201, Json.ok("""{"completionId":9,"photoUrl":"/files/completions/a.jpg","bonusPoint":50,"totalPoint":170}"""))
        val r = PhotoRepository(statsApi).upload(9, jpeg)
        val ok = r as ApiResult.Success
        assertEquals(50, ok.data.bonusPoint)
        assertEquals(170L, ok.data.totalPoint)
        assertEquals("/files/completions/a.jpg", ok.data.photoUrl)

        val req = fx.server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/completions/9/photo", req.path)
        assertEquals("Bearer acc", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readByteString().utf8()
        assertTrue(body.contains("""name="photo""""))
        assertTrue(body.contains("""filename="photo.jpg""""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertEquals("photo 파트가 하나만 있어야 한다", 1, Regex("form-data; name=").findAll(body).count())
    }

    @Test
    fun everyPhotoErrorCodeHasItsOwnKoreanMessage() = runBlocking {
        fx.session.saveTokens("a", "r")
        val cases = listOf(
            403 to "COMPLETION403", 404 to "COMPLETION404", 400 to "COMPLETION4004", 409 to "COMPLETION4091",
            400 to "FILE4001", 413 to "FILE413",
        )
        val seen = mutableSetOf<String>()
        for ((status, code) in cases) {
            fx.enqueue(status, Json.error(code))
            val r = PhotoRepository(statsApi).upload(1, jpeg)
            assertTrue(r is ApiResult.Failure)
            assertEquals(code, (r as ApiResult.Failure).code)
            val msg = ErrorMessages.message(r)
            assertFalse("$code 메시지가 기본 문구: $msg", msg == ErrorMessages.UNKNOWN)
            seen += msg
        }
        assertEquals("코드마다 다른 문구", cases.size, seen.size)
    }

    @Test
    fun http413WithoutJsonBodyStillGivesFileTooLargeMessage() = runBlocking {
        fx.session.saveTokens("a", "r")
        fx.enqueue(413, "")
        val r = PhotoRepository(statsApi).upload(1, jpeg)
        assertTrue(ErrorMessages.message(r).contains("너무 커요"))
    }

    // ---- ViewModel ----

    private fun uploadVm(prepare: PhotoPrepareService) = PhotoUploadViewModel(PhotoRepository(statsApi), prepare)

    private val readyPrepare = PhotoPrepareService { PrepareResult.Ready(jpeg, 10, 10, 80) }

    @Test
    fun viewModelUploadSuccessStoresBonusAndTotal() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(201, Json.ok("""{"completionId":9,"photoUrl":"/files/completions/a.jpg","bonusPoint":50,"totalPoint":170}"""))
        val vm = uploadVm(readyPrepare)
        vm.upload(9, "content://x/1")
        waitUntil { vm.statuses.value[9L] is UploadStatus.Done }
        val st = vm.statuses.value[9L] as UploadStatus.Done
        assertEquals(50, st.bonusPoint)
        assertEquals(170L, st.totalPoint)
    }

    @Test
    fun viewModelMarksFinalErrorsAsNotRetryable() {
        fx.session.saveTokens("a", "r")
        val vm = uploadVm(readyPrepare)
        for ((code, status) in listOf("COMPLETION4004" to 400, "COMPLETION4091" to 409, "COMPLETION403" to 403, "COMPLETION404" to 404)) {
            fx.enqueue(status, Json.error(code))
            vm.clear(1)
            vm.upload(1, "content://x")
            waitUntil { vm.statuses.value[1L] is UploadStatus.Failed }
            assertFalse(code, (vm.statuses.value[1L] as UploadStatus.Failed).retryable)
        }
        fx.enqueue(413, Json.error("FILE413"))
        vm.clear(1)
        vm.upload(1, "content://x")
        waitUntil { vm.statuses.value[1L] is UploadStatus.Failed }
        assertTrue((vm.statuses.value[1L] as UploadStatus.Failed).retryable)
    }

    @Test
    fun viewModelReportsUnreadableAndTooLargeWithoutCallingServer() {
        val before = fx.server.requestCount
        val vm = uploadVm { PrepareResult.Unreadable }
        vm.upload(1, "content://x")
        assertEquals(UNREADABLE_MESSAGE, (vm.statuses.value[1L] as UploadStatus.Failed).message)
        val vm2 = uploadVm { PrepareResult.TooLarge }
        vm2.upload(2, "content://x")
        assertEquals(TOO_LARGE_MESSAGE, (vm2.statuses.value[2L] as UploadStatus.Failed).message)
        assertEquals(before, fx.server.requestCount)
    }

    @Test
    fun viewModelNetworkErrorIsRetryable() {
        fx.session.saveTokens("a", "r")
        fx.server.shutdown()
        val vm = uploadVm(readyPrepare)
        vm.upload(1, "content://x")
        waitUntil { vm.statuses.value[1L] is UploadStatus.Failed }
        val st = vm.statuses.value[1L] as UploadStatus.Failed
        assertEquals(ErrorMessages.NETWORK, st.message)
        assertTrue(st.retryable)
    }

    @Test
    fun alreadyUploadedRecordIsNotUploadedTwice() {
        fx.session.saveTokens("a", "r")
        fx.enqueue(201, Json.ok("""{"completionId":9,"photoUrl":"/files/completions/a.jpg","bonusPoint":0,"totalPoint":1}"""))
        val vm = uploadVm(readyPrepare)
        vm.upload(9, "content://x")
        waitUntil { vm.statuses.value[9L] is UploadStatus.Done }
        vm.upload(9, "content://y")
        assertEquals(1, fx.server.requestCount)
    }

    // ---- 보너스 표시 판단 ----

    @Test
    fun bonusOpenUsesDeadlineAndBonus() {
        val now = LocalDateTime.of(2026, 11, 14, 10, 0, 0)
        assertTrue(PhotoPolicy.bonusOpen(50, "2026-11-14T10:30:00", now))
        assertTrue("정각까지 허용", PhotoPolicy.bonusOpen(50, "2026-11-14T10:00:00", now))
        assertFalse(PhotoPolicy.bonusOpen(50, "2026-11-14T09:59:59", now))
        assertFalse("재방문(보너스 0)", PhotoPolicy.bonusOpen(0, null, now))
        assertTrue("시각을 못 읽으면 서버에 맡긴다", PhotoPolicy.bonusOpen(50, "garbage", now))
    }

    // ---- 이미지 준비(순수 로직) ----

    /** 비트맵 대신 크기와 "JPEG 크기 = 픽셀 수 * quality / 100 / divisor"로 흉내 낸다. */
    private class FakeOps(
        private val size: Pair<Int, Int>?,
        private val degrees: Int = 0,
        private val bytesPerPixelPct: Double = 0.1,
        private val decodable: Boolean = true,
    ) : ImageOps<IntArray> {
        var decodedSample = 0
        var lastTransform: Triple<Int, Int, Int>? = null
        val recycled = mutableListOf<IntArray>()
        override fun readSize() = size
        override fun orientationDegrees() = degrees
        override fun decode(sampleSize: Int): IntArray? {
            decodedSample = sampleSize
            return if (decodable) intArrayOf(size!!.first / sampleSize, size.second / sampleSize) else null
        }
        override fun transform(src: IntArray, degrees: Int, width: Int, height: Int): IntArray {
            lastTransform = Triple(degrees, width, height)
            return intArrayOf(width, height)
        }
        override fun encodeJpeg(bitmap: IntArray, quality: Int) =
            ByteArray((bitmap[0].toLong() * bitmap[1] * bytesPerPixelPct * quality / 100).toInt())
        override fun recycle(bitmap: IntArray) { recycled += bitmap }
    }

    @Test
    fun largePhotoIsDownscaledToMaxEdgeKeepingAspect() {
        val ops = FakeOps(4000 to 3000)
        val r = PhotoPreparer(maxEdge = 2048).prepare(ops) as PrepareResult.Ready
        assertEquals(2048, r.width)
        assertEquals(1536, r.height)
        assertEquals(Triple(0, 2048, 1536), ops.lastTransform)
        assertEquals("긴 변이 목표 이상 남는 가장 큰 2의 거듭제곱 샘플", 1, ops.decodedSample)
    }

    @Test
    fun smallPhotoIsNotUpscaled() {
        val r = PhotoPreparer(maxEdge = 2048).prepare(FakeOps(800 to 600)) as PrepareResult.Ready
        assertEquals(800, r.width)
        assertEquals(600, r.height)
    }

    @Test
    fun exifRotationSwapsDimensionsAndIsPassedToTransform() {
        val ops = FakeOps(4000 to 3000, degrees = 90)
        val r = PhotoPreparer(maxEdge = 2048).prepare(ops) as PrepareResult.Ready
        assertEquals(1536, r.width)
        assertEquals(2048, r.height)
        assertEquals(90, ops.lastTransform!!.first)
    }

    @Test
    fun negativeOrLargeDegreesAreNormalized() {
        val ops = FakeOps(100 to 50, degrees = -90)
        val r = PhotoPreparer().prepare(ops) as PrepareResult.Ready
        assertEquals(50, r.width)
        assertEquals(100, r.height)
        assertEquals(270, ops.lastTransform!!.first)
    }

    @Test
    fun hugeSourceUsesSubsamplingToSaveMemory() {
        val ops = FakeOps(12000 to 9000)
        PhotoPreparer(maxEdge = 2048).prepare(ops)
        assertEquals(4, ops.decodedSample) // 12000/4=3000 >= 2048, 12000/8=1500 < 2048
    }

    @Test
    fun qualityStepsDownUntilUnderCap() {
        // 1000x1000 * 0.1 = 100_000 * q/100: q=88 -> 88000, q=78 -> 78000 ...
        val r = PhotoPreparer(maxEdge = 2048, maxBytes = 80_000).prepare(FakeOps(1000 to 1000)) as PrepareResult.Ready
        assertEquals(78, r.quality)
        assertTrue(r.jpeg.size <= 80_000)
    }

    @Test
    fun shrinksDimensionsWhenLowestQualityIsStillTooBig() {
        val r = PhotoPreparer(maxEdge = 2000, maxBytes = 300_000).prepare(FakeOps(2000 to 2000, bytesPerPixelPct = 0.5)) as PrepareResult.Ready
        assertTrue(r.jpeg.size <= 300_000)
        assertTrue("크기가 줄어야 한다: ${r.width}", r.width < 2000)
    }

    @Test
    fun tooLargeWhenNothingFits() {
        val r = PhotoPreparer(maxEdge = 2000, maxBytes = 10).prepare(FakeOps(2000 to 2000))
        assertTrue(r is PrepareResult.TooLarge)
    }

    @Test
    fun unreadableWhenBoundsOrDecodeFail() {
        assertTrue(PhotoPreparer().prepare(FakeOps(null)) is PrepareResult.Unreadable)
        assertTrue(PhotoPreparer().prepare(FakeOps(0 to 0)) is PrepareResult.Unreadable)
        assertTrue(PhotoPreparer().prepare(FakeOps(100 to 100, decodable = false)) is PrepareResult.Unreadable)
    }

    @Test
    fun finalBitmapIsRecycledAndSizeFunctionsAreExact() {
        val ops = FakeOps(100 to 100)
        PhotoPreparer().prepare(ops)
        assertTrue(ops.recycled.isNotEmpty())
        assertEquals(2048 to 1024, PhotoPreparer.fit(4096, 2048, 2048))
        assertEquals(1 to 2048, PhotoPreparer.fit(21, 100000, 2048).let { 1 to it.second })
        assertEquals(1, PhotoPreparer.sampleSizeFor(2048, 2048))
        assertEquals(2, PhotoPreparer.sampleSizeFor(4096, 2048))
        assertEquals(1, PhotoPreparer.sampleSizeFor(4095, 2048))
    }

    // ---- 인증 이미지 주소 ----

    @Test
    fun relativePhotoUrlGetsApiBaseUrlPrefix() {
        assertEquals(
            "http://10.0.2.2:8080/files/completions/a.jpg",
            PhotoUrls.resolve("http://10.0.2.2:8080/", "/files/completions/a.jpg"),
        )
        assertEquals(
            "https://api.example.com/files/x.jpg",
            PhotoUrls.resolve("https://api.example.com/", "/files/x.jpg"),
        )
        assertNull(PhotoUrls.resolve("http://h/", null))
        assertNull(PhotoUrls.resolve("http://h/", ""))
        assertNull("//evil.com/x 은 프로토콜 상대 주소라 거부", PhotoUrls.resolve("http://h/", "//evil.com/x.jpg"))
    }

    @Test
    fun absolutePhotoUrlOnOtherHostIsRejectedSoTokenDoesNotLeak() {
        assertNull(PhotoUrls.resolve("http://10.0.2.2:8080/", "https://cdn.evil.com/a.jpg"))
        assertEquals("http://10.0.2.2:8080/files/a.jpg", PhotoUrls.resolve("http://10.0.2.2:8080/", "http://10.0.2.2:8080/files/a.jpg"))
        assertNull("포트가 다르면 거부", PhotoUrls.resolve("http://10.0.2.2:8080/", "http://10.0.2.2:9999/files/a.jpg"))
    }

    @Test
    fun photoRequestThroughAppClientCarriesBearerToken() {
        fx.session.saveTokens("tok-77", "r")
        fx.enqueue(200, "JPEGDATA")
        // 서버 계약: 사진 경로는 인증 필요. 앱의 클라이언트(AuthInterceptor 포함)가 같은 호스트의 사진 요청에 헤더를 붙이는지 본다.
        val base = fx.server.url("/").toString()
        val url = PhotoUrls.resolve(base, "/files/completions/1b4e28ba-2fa1-11d2-883f-0016d3cca427.jpg")!!
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(fx.session)).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals("JPEGDATA", it.body!!.string()) }
        val req = fx.server.takeRequest()
        assertEquals("/files/completions/1b4e28ba-2fa1-11d2-883f-0016d3cca427.jpg", req.path)
        assertEquals("Bearer tok-77", req.getHeader("Authorization"))
        assertNotNull(req)
    }
    private fun waitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("조건이 시간 안에 충족되지 않음")
            Thread.sleep(10)
        }
    }
}
