package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.completion.service.PhotoSanitizer
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.support.ImageTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class PhotoSanitizerTest {

	private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

	private fun code(block: () -> Unit): String = assertThrows<BusinessException>(block).errorCode.code

	private fun isRedish(rgb: Int): Boolean {
		val c = Color(rgb)
		return c.red > 150 && c.blue < 120
	}

	private fun isBluish(rgb: Int): Boolean {
		val c = Color(rgb)
		return c.blue > 150 && c.red < 120
	}

	@Test
	@DisplayName("EXIF(GPS 흉내, 설명 문자열, APP1 세그먼트)가 출력 바이트에 하나도 남지 않는다")
	fun exifRemoved() {
		val input = ImageTestSupport.jpegWithExif(orientation = 1)
		// 입력에는 실제로 EXIF가 들어 있어야 테스트가 의미 있다
		assertThat(ImageTestSupport.hasApp1(input)).isTrue()
		assertThat(ImageTestSupport.contains(input, ImageTestSupport.SECRET)).isTrue()
		assertThat(ImageTestSupport.contains(input, "Exif")).isTrue()

		val out = PhotoSanitizer.sanitize(input, "image/jpeg")

		assertThat(ImageTestSupport.hasApp1(out)).isFalse()
		assertThat(ImageTestSupport.contains(out, "Exif")).isFalse()
		assertThat(ImageTestSupport.contains(out, ImageTestSupport.SECRET)).isFalse()
		assertThat(ImageTestSupport.contains(out, "GPS")).isFalse()
		val img = decode(out)
		assertThat(img.width).isEqualTo(40)
		assertThat(img.height).isEqualTo(20)
	}

	@Test
	@DisplayName("EXIF 회전(6 = 시계방향 90도)은 버리기 전에 픽셀에 반영한다")
	fun orientationApplied() {
		val out = PhotoSanitizer.sanitize(ImageTestSupport.jpegWithExif(orientation = 6), "image/jpeg")
		val img = decode(out)
		// 40x20 가로 사진이 20x40 세로로 바뀐다. 왼쪽 위의 빨간 블록은 시계방향 회전 후 오른쪽 위로 간다.
		assertThat(img.width).isEqualTo(20)
		assertThat(img.height).isEqualTo(40)
		assertThat(isRedish(img.getRGB(16, 4))).isTrue()
		assertThat(isBluish(img.getRGB(3, 4))).isTrue()
		assertThat(isBluish(img.getRGB(16, 35))).isTrue()
		assertThat(ImageTestSupport.hasApp1(out)).isFalse()
	}

	@Test
	@DisplayName("빅엔디언 EXIF도 읽고 회전 8(반시계 90도)은 빨간 블록이 왼쪽 아래로 간다")
	fun orientationBigEndianAndRotate270() {
		val out = PhotoSanitizer.sanitize(ImageTestSupport.jpegWithExif(orientation = 8, littleEndian = false), "image/jpeg")
		val img = decode(out)
		assertThat(img.width).isEqualTo(20)
		assertThat(img.height).isEqualTo(40)
		assertThat(isRedish(img.getRGB(3, 35))).isTrue()
		assertThat(isBluish(img.getRGB(16, 4))).isTrue()
	}

	@Test
	@DisplayName("회전 3(180도)과 2(좌우 반전), 4(상하 반전)")
	fun otherOrientations() {
		val r3 = decode(PhotoSanitizer.sanitize(ImageTestSupport.jpegWithExif(orientation = 3), "image/jpeg"))
		assertThat(isRedish(r3.getRGB(36, 16))).isTrue()
		val r2 = decode(PhotoSanitizer.sanitize(ImageTestSupport.jpegWithExif(orientation = 2), "image/jpeg"))
		assertThat(isRedish(r2.getRGB(36, 4))).isTrue()
		val r4 = decode(PhotoSanitizer.sanitize(ImageTestSupport.jpegWithExif(orientation = 4), "image/jpeg"))
		assertThat(isRedish(r4.getRGB(4, 16))).isTrue()
	}

	@Test
	@DisplayName("readExifOrientation: EXIF 없음, 깨진 데이터, 범위 밖 값은 1")
	fun orientationParsing() {
		assertThat(PhotoSanitizer.readExifOrientation(ImageTestSupport.jpeg())).isEqualTo(1)
		assertThat(PhotoSanitizer.readExifOrientation(byteArrayOf(1, 2, 3))).isEqualTo(1)
		assertThat(PhotoSanitizer.readExifOrientation(ImageTestSupport.jpegWithExif(orientation = 9))).isEqualTo(1)
		assertThat(PhotoSanitizer.readExifOrientation(ImageTestSupport.jpegWithExif(orientation = 6))).isEqualTo(6)
		val truncated = ImageTestSupport.jpegWithExif(orientation = 6).copyOf(20)
		assertThat(PhotoSanitizer.readExifOrientation(truncated)).isEqualTo(1)
	}

	@Test
	@DisplayName("PNG는 JPEG로 바뀌고 투명 부분은 흰색이 된다")
	fun pngBecomesJpeg() {
		val src = BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB) // 전부 투명
		val out = PhotoSanitizer.sanitize(ImageTestSupport.encode(src, "png"), "image/png")
		assertThat(out[0]).isEqualTo(0xFF.toByte())
		assertThat(out[1]).isEqualTo(0xD8.toByte())
		val c = Color(decode(out).getRGB(15, 15))
		assertThat(c.red).isGreaterThan(240)
		assertThat(c.green).isGreaterThan(240)
		assertThat(c.blue).isGreaterThan(240)
	}

	@Test
	@DisplayName("긴 변이 1920px을 넘으면 비율을 지켜 줄인다")
	fun downscale() {
		val out = PhotoSanitizer.sanitize(ImageTestSupport.png(3840, 1000), "image/png")
		val img = decode(out)
		assertThat(img.width).isEqualTo(1920)
		assertThat(img.height).isEqualTo(500)
	}

	@Test
	@DisplayName("선언한 타입이 jpeg여도 내용이 이미지가 아니면 FILE4001")
	fun notAnImage() {
		assertThat(code { PhotoSanitizer.sanitize("hello, not an image".toByteArray(), "image/jpeg") }).isEqualTo("FILE4001")
		// 매직 넘버만 흉내 낸 깨진 JPEG
		val fake = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2)
		assertThat(code { PhotoSanitizer.sanitize(fake, "image/jpeg") }).isEqualTo("FILE4001")
	}

	@Test
	@DisplayName("지원하지 않는 선언 타입(text, heic, gif, 없음)과 실제 GIF는 FILE4001")
	fun unsupportedTypes() {
		val jpeg = ImageTestSupport.jpeg()
		assertThat(code { PhotoSanitizer.sanitize(jpeg, "text/plain") }).isEqualTo("FILE4001")
		assertThat(code { PhotoSanitizer.sanitize(jpeg, "image/heic") }).isEqualTo("FILE4001")
		assertThat(code { PhotoSanitizer.sanitize(jpeg, null) }).isEqualTo("FILE4001")
		assertThat(code { PhotoSanitizer.sanitize(jpeg, "application/octet-stream") }).isEqualTo("FILE4001")
		val gif = ImageTestSupport.encode(ImageTestSupport.image(10, 10), "gif")
		assertThat(code { PhotoSanitizer.sanitize(gif, "image/png") }).isEqualTo("FILE4001")
	}

	@Test
	@DisplayName("빈 파일은 FILE4001, 10MB 초과는 FILE413")
	fun emptyAndTooLarge() {
		assertThat(code { PhotoSanitizer.sanitize(ByteArray(0), "image/jpeg") }).isEqualTo("FILE4001")
		assertThat(code { PhotoSanitizer.sanitize(ByteArray((PhotoSanitizer.MAX_BYTES + 1).toInt()), "image/jpeg") }).isEqualTo("FILE413")
	}

	@Test
	@DisplayName("content-type의 파라미터와 대소문자는 무시한다")
	fun contentTypeNormalization() {
		val out = PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "IMAGE/JPEG; charset=binary")
		assertThat(decode(out).width).isEqualTo(40)
	}

	@Test
	@DisplayName("픽셀 수 한도를 넘는 이미지는 디코딩하기 전에 거절한다")
	fun pixelLimit() {
		// 헤더만 큰 PNG: 8000 x 8000(6400만 픽셀) 흰 이미지는 압축하면 작다
		val big = BufferedImage(8000, 8000, BufferedImage.TYPE_BYTE_BINARY)
		val bytes = ImageTestSupport.encode(big, "png")
		assertThat(bytes.size).isLessThan(PhotoSanitizer.MAX_BYTES.toInt())
		assertThat(code { PhotoSanitizer.sanitize(bytes, "image/png") }).isEqualTo("FILE4001")
	}

	// ---- 디코딩 한도(보안 점검): 화소 상한 1600만, 동시 디코딩 3개

	private fun blankPng(width: Int, height: Int): ByteArray =
		ImageTestSupport.encode(BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png")

	@Test
	@DisplayName("화소 상한(1600만) 정확히는 통과하고 1화소만 넘어도 FILE4001")
	fun pixelCapBoundary() {
		assertThat(PhotoSanitizer.MAX_PIXELS).isEqualTo(16_000_000L)
		val ok = PhotoSanitizer.sanitize(blankPng(4000, 4000), "image/png")
		val img = decode(ok)
		assertThat(maxOf(img.width, img.height)).isEqualTo(PhotoSanitizer.MAX_SIDE)
		assertThat(code { PhotoSanitizer.sanitize(blankPng(4001, 4000), "image/png") }).isEqualTo("FILE4001")
	}

	@Test
	@DisplayName("상한을 넘는 이미지는 디코딩 permit을 잡기 전에 거절한다(permit이 모두 점유돼도 503이 아니라 FILE4001)")
	fun overCapRejectedBeforeDecodeWithoutPermit() {
		val permits = PhotoSanitizer.MAX_CONCURRENT_DECODES
		assertThat(PhotoSanitizer.decodePermits.tryAcquire(permits)).isTrue()
		val old = PhotoSanitizer.acquireTimeoutMillis
		PhotoSanitizer.acquireTimeoutMillis = 20
		try {
			assertThat(code { PhotoSanitizer.sanitize(blankPng(8000, 8000), "image/png") }).isEqualTo("FILE4001")
			// 상한 안의 정상 이미지는 permit이 없어서 COMPLETION503
			assertThat(code { PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "image/jpeg") }).isEqualTo("COMPLETION503")
		} finally {
			PhotoSanitizer.acquireTimeoutMillis = old
			PhotoSanitizer.decodePermits.release(permits)
		}
		assertThat(PhotoSanitizer.decodePermits.availablePermits()).isEqualTo(permits)
		assertThat(decode(PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "image/jpeg")).width).isEqualTo(40)
	}

	@Test
	@DisplayName("동시 디코딩 한도: permit이 하나 남아 있으면 통과, 모두 점유되면 COMPLETION503(503)이고 대기 시간은 짧다")
	fun concurrencyCap() {
		val permits = PhotoSanitizer.MAX_CONCURRENT_DECODES
		assertThat(PhotoSanitizer.decodePermits.tryAcquire(permits - 1)).isTrue()
		try {
			assertThat(decode(PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "image/jpeg")).width).isEqualTo(40)
		} finally {
			PhotoSanitizer.decodePermits.release(permits - 1)
		}
		assertThat(PhotoSanitizer.decodePermits.tryAcquire(permits)).isTrue()
		val old = PhotoSanitizer.acquireTimeoutMillis
		PhotoSanitizer.acquireTimeoutMillis = 100
		try {
			val started = System.nanoTime()
			val e = assertThrows<BusinessException> { PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "image/jpeg") }
			assertThat(e.errorCode.code).isEqualTo("COMPLETION503")
			assertThat(e.errorCode.status.value()).isEqualTo(503)
			assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000)
		} finally {
			PhotoSanitizer.acquireTimeoutMillis = old
			PhotoSanitizer.decodePermits.release(permits)
		}
	}

	@Test
	@DisplayName("permit은 성공, 손상된 이미지, 지원하지 않는 형식 뒤에도 반드시 돌려준다")
	fun permitsAlwaysReleased() {
		val permits = PhotoSanitizer.MAX_CONCURRENT_DECODES
		repeat(permits + 2) {
			PhotoSanitizer.sanitize(ImageTestSupport.jpeg(), "image/jpeg")
			code { PhotoSanitizer.sanitize(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2), "image/jpeg") }
			code { PhotoSanitizer.sanitize("not an image".toByteArray(), "image/png") }
		}
		assertThat(PhotoSanitizer.decodePermits.availablePermits()).isEqualTo(permits)
	}

	@Test
	@DisplayName("스레드 여러 개가 동시에 디코딩해도 permit 수를 넘지 않고 모두 끝난 뒤 permit이 복구된다")
	fun parallelDecodesRespectCap() {
		val permits = PhotoSanitizer.MAX_CONCURRENT_DECODES
		val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
		try {
			val bytes = ImageTestSupport.jpeg(800, 600)
			val futures = (1..24).map {
				pool.submit<String> {
					try {
						PhotoSanitizer.sanitize(bytes, "image/jpeg"); "ok"
					} catch (e: BusinessException) {
						e.errorCode.code // 503이 섞일 수는 있지만 다른 실패는 없어야 한다
					}
				}
			}
			val results = futures.map { it.get(60, java.util.concurrent.TimeUnit.SECONDS) }
			assertThat(results).allMatch { it == "ok" || it == "COMPLETION503" }
			assertThat(results).contains("ok")
		} finally {
			pool.shutdownNow()
		}
		assertThat(PhotoSanitizer.decodePermits.availablePermits()).isEqualTo(permits)
	}
}
