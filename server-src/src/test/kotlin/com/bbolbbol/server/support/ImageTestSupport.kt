package com.bbolbbol.server.support

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** 테스트용 이미지 도우미: JPEG/PNG를 만들고, JPEG에 EXIF(회전, GPS 흉내, 설명 문자열)를 직접 끼워 넣는다. */
object ImageTestSupport {

	const val SECRET = "SECRET-GPS-MARKER-37.5796N-126.9770E"

	/** 왼쪽 위 1/4 블록은 빨강, 나머지는 파랑. 압축 후에도 색을 구분할 수 있게 큼직하게 칠한다. */
	fun image(width: Int, height: Int): BufferedImage {
		val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
		val g = img.createGraphics()
		g.color = Color.BLUE
		g.fillRect(0, 0, width, height)
		g.color = Color.RED
		g.fillRect(0, 0, width / 2, height / 2)
		g.dispose()
		return img
	}

	fun encode(img: BufferedImage, format: String): ByteArray {
		val out = ByteArrayOutputStream()
		check(ImageIO.write(img, format, out)) { "$format 인코더가 없다" }
		return out.toByteArray()
	}

	fun jpeg(width: Int = 40, height: Int = 20): ByteArray = encode(image(width, height), "jpeg")

	fun png(width: Int = 40, height: Int = 20): ByteArray = encode(image(width, height), "png")

	/** SOI 바로 뒤에 APP1(Exif) 세그먼트를 끼운 JPEG. Orientation, 설명 문자열(SECRET), GPS IFD 포인터를 담는다. */
	fun jpegWithExif(width: Int = 40, height: Int = 20, orientation: Int = 1, littleEndian: Boolean = true): ByteArray {
		val base = jpeg(width, height)
		val app1 = exifSegment(orientation, littleEndian)
		return base.copyOfRange(0, 2) + app1 + base.copyOfRange(2, base.size)
	}

	private fun exifSegment(orientation: Int, little: Boolean): ByteArray {
		fun u16(v: Int) = if (little) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())
		fun u32(v: Int) = if (little) {
			byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
		} else {
			byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
		}
		fun entry(tag: Int, type: Int, count: Int, value: ByteArray) = u16(tag) + u16(type) + u32(count) + value.copyOf(4)

		val description = (SECRET + "\u0000").toByteArray(Charsets.US_ASCII)
		// TIFF 레이아웃: 헤더 8 + IFD0(개수 2 + 엔트리 4개 x 12 + 다음 IFD 4) = 62, 그 뒤에 설명 문자열, 그 뒤에 GPS IFD
		val ifd0Size = 2 + 4 * 12 + 4
		val descOffset = 8 + ifd0Size
		val gpsOffset = descOffset + description.size
		val orientationValue = u16(orientation)

		val ifd0 = u16(4) +
			entry(0x010E, 2, description.size, u32(descOffset)) +
			entry(0x0112, 3, 1, orientationValue) +
			entry(0x8825, 4, 1, u32(gpsOffset)) +
			entry(0x0131, 2, 4, "abc\u0000".toByteArray(Charsets.US_ASCII)) +
			u32(0)
		// GPS IFD: 위도 참조 'N' 한 개
		val gps = u16(1) + entry(0x0001, 2, 2, "N\u0000".toByteArray(Charsets.US_ASCII)) + u32(0)
		val tiff = (if (little) "II".toByteArray() else "MM".toByteArray()) + u16(0x002A) + u32(8) + ifd0 + description + gps
		val payload = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
		val length = payload.size + 2
		return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
	}

	fun contains(haystack: ByteArray, needle: String): Boolean = String(haystack, Charsets.ISO_8859_1).contains(needle)

	/** APP1(0xFFE1) 마커가 있는지 */
	fun hasApp1(jpeg: ByteArray): Boolean {
		var i = 2
		while (i + 4 <= jpeg.size) {
			if ((jpeg[i].toInt() and 0xFF) != 0xFF) return false
			val marker = jpeg[i + 1].toInt() and 0xFF
			if (marker == 0xDA || marker == 0xD9) return false
			if (marker == 0xE1) return true
			i += 2 + (((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF))
		}
		return false
	}
}
