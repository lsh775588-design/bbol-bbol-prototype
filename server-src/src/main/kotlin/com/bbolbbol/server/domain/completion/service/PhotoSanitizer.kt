package com.bbolbbol.server.domain.completion.service

import com.bbolbbol.server.domain.completion.exception.CompletionErrorCode
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.FileErrorCode
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * 업로드 사진 검증과 정제. 선언된 Content-Type이 아니라 실제로 디코딩해서 이미지인지 확인하고,
 * 픽셀만 다시 JPEG로 인코딩해서 EXIF(GPS 위치, 기기 정보), ICC, 썸네일 같은 메타데이터를 모두 버린다.
 * 회전 정보(EXIF Orientation)는 버리기 전에 픽셀에 반영한다.
 *
 * 지원 형식은 JPEG, PNG뿐이다. 명세는 heic도 받는다고 했지만 JDK ImageIO에는 HEIC/WebP 디코더가 없어서
 * (라이브러리를 추가해야 한다) 이 구현은 FILE4001로 거절한다. 출력은 항상 JPEG(투명 PNG는 흰 배경에 합친다).
 */
object PhotoSanitizer {

	const val MAX_BYTES = 10L * 1024 * 1024

	/**
	 * 디코딩 전에 헤더의 크기로 검사해서 거대한 이미지(decompression bomb)가 메모리를 먹지 못하게 한다.
	 * 1600만 화소(약 4600x3450)는 요즘 폰 카메라 기본 해상도(12MP)보다 크고, 디코드당 힙이 최대 약 50~64MB다.
	 * 이보다 큰 사진은 앱이 줄여서 올려야 한다(명세도 긴 변 1920px 권장).
	 */
	const val MAX_PIXELS = 16_000_000L

	/** 동시에 디코딩할 수 있는 요청 수. 요청 스레드에서 동기로 돌리므로 힙(약 64MB x 개수)을 이 숫자로 제한한다. */
	const val MAX_CONCURRENT_DECODES = 3

	/** 테스트에서 permit을 점유해 동시성 한도를 확인한다. */
	internal val decodePermits = java.util.concurrent.Semaphore(MAX_CONCURRENT_DECODES)

	/** permit을 기다리는 최대 시간(ms). 짧게 기다려 순간적인 몰림은 흡수하고 계속 밀리면 COMPLETION503으로 빠르게 실패한다. */
	@Volatile
	internal var acquireTimeoutMillis = 500L

	/** 긴 변이 이보다 크면 줄인다(명세의 프론트 권장값 1920px). */
	const val MAX_SIDE = 1920
	private const val JPEG_QUALITY = 0.85f

	private val ALLOWED_TYPES = setOf("image/jpeg", "image/jpg", "image/pjpeg", "image/png")
	private val ALLOWED_FORMATS = setOf("jpeg", "png")

	fun sanitize(bytes: ByteArray, declaredContentType: String?): ByteArray {
		if (bytes.isEmpty()) throw invalid()
		if (bytes.size > MAX_BYTES) throw BusinessException(FileErrorCode.FILE_TOO_LARGE)
		val type = declaredContentType?.substringBefore(';')?.trim()?.lowercase()
		if (type == null || type !in ALLOWED_TYPES) throw invalid()

		val decoded = decode(bytes)
		val oriented = if (decoded.format == "jpeg") applyOrientation(decoded.image, readExifOrientation(bytes)) else decoded.image
		return encodeJpeg(oriented)
	}

	private class Decoded(val image: BufferedImage, val format: String)

	private fun decode(bytes: ByteArray): Decoded {
		try {
			ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
				val readers = ImageIO.getImageReaders(input)
				if (!readers.hasNext()) throw invalid()
				val reader = readers.next()
				try {
					val format = reader.formatName.lowercase()
					if (format !in ALLOWED_FORMATS) throw invalid()
					reader.setInput(input, true, true)
					// 헤더만 읽은 크기 검사: permit을 잡기 전, 디코딩 전에 거절한다.
					val width = reader.getWidth(0)
					val height = reader.getHeight(0)
					if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) throw invalid()
					// 긴 변이 MAX_SIDE의 2배 이상이면 읽는 단계에서 건너뛰며 읽어(subsampling) 메모리를 줄인다.
					val step = maxOf(1, maxOf(width, height) / MAX_SIDE)
					val param = reader.defaultReadParam.apply { if (step > 1) setSourceSubsampling(step, step, 0, 0) }
					if (!decodePermits.tryAcquire(acquireTimeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
						throw BusinessException(CompletionErrorCode.PHOTO_PROCESSING_BUSY)
					}
					try {
						return Decoded(fitInto(reader.read(0, param)), format)
					} finally {
						decodePermits.release()
					}
				} finally {
					reader.dispose()
				}
			}
		} catch (e: BusinessException) {
			throw e
		} catch (e: Exception) {
			// 손상된 파일, CMYK 같은 지원하지 않는 JPEG 등 디코딩 실패는 모두 지원하지 않는 형식이다.
			throw invalid()
		}
	}

	/** RGB로 바꾸면서(투명은 흰 배경) 긴 변을 MAX_SIDE 이하로 줄인다. */
	private fun fitInto(src: BufferedImage): BufferedImage {
		val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(src.width, src.height))
		val w = maxOf(1, (src.width * scale).toInt())
		val h = maxOf(1, (src.height * scale).toInt())
		val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
		val g = out.createGraphics()
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
			g.color = java.awt.Color.WHITE
			g.fillRect(0, 0, w, h)
			g.drawImage(src, 0, 0, w, h, null)
		} finally {
			g.dispose()
		}
		return out
	}

	private fun encodeJpeg(image: BufferedImage): ByteArray {
		val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
		try {
			val param = writer.defaultWriteParam.apply {
				compressionMode = ImageWriteParam.MODE_EXPLICIT
				compressionQuality = JPEG_QUALITY
			}
			val out = ByteArrayOutputStream()
			ImageIO.createImageOutputStream(out).use { ios ->
				writer.output = ios
				// 메타데이터를 null로 넘기면 JFIF 기본 헤더만 쓰고 원본의 EXIF는 쓰지 않는다.
				writer.write(null, IIOImage(image, null, null), param)
			}
			return out.toByteArray()
		} finally {
			writer.dispose()
		}
	}

	/** EXIF Orientation(1~8)을 픽셀에 반영한다. 1이거나 알 수 없으면 그대로. */
	internal fun applyOrientation(src: BufferedImage, orientation: Int): BufferedImage {
		if (orientation !in 2..8) return src
		val w = src.width
		val h = src.height
		val swap = orientation >= 5
		val out = BufferedImage(if (swap) h else w, if (swap) w else h, BufferedImage.TYPE_INT_RGB)
		for (y in 0 until h) {
			for (x in 0 until w) {
				val rgb = src.getRGB(x, y)
				when (orientation) {
					2 -> out.setRGB(w - 1 - x, y, rgb)
					3 -> out.setRGB(w - 1 - x, h - 1 - y, rgb)
					4 -> out.setRGB(x, h - 1 - y, rgb)
					5 -> out.setRGB(y, x, rgb)
					6 -> out.setRGB(h - 1 - y, x, rgb)
					7 -> out.setRGB(h - 1 - y, w - 1 - x, rgb)
					8 -> out.setRGB(y, w - 1 - x, rgb)
				}
			}
		}
		return out
	}

	/** JPEG APP1(Exif)의 IFD0에서 Orientation(0x0112)을 읽는다. 없거나 깨졌으면 1. */
	internal fun readExifOrientation(b: ByteArray): Int = runCatching {
		if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return@runCatching 1
		var i = 2
		while (i + 4 <= b.size) {
			if (u8(b, i) != 0xFF) { i++; continue }
			val marker = u8(b, i + 1)
			if (marker == 0xFF) { i++; continue }
			if (marker == 0xD9 || marker == 0xDA) return@runCatching 1
			if (marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
			val len = (u8(b, i + 2) shl 8) or u8(b, i + 3)
			if (marker == 0xE1 && len >= 16 && String(b, i + 4, 4, Charsets.ISO_8859_1) == "Exif") {
				return@runCatching parseTiffOrientation(b, i + 10)
			}
			i += 2 + len
		}
		1
	}.getOrDefault(1)

	private fun parseTiffOrientation(b: ByteArray, tiff: Int): Int {
		val little = when (String(b, tiff, 2, Charsets.ISO_8859_1)) {
			"II" -> true
			"MM" -> false
			else -> return 1
		}
		fun u16(p: Int) = if (little) u8(b, p) or (u8(b, p + 1) shl 8) else (u8(b, p) shl 8) or u8(b, p + 1)
		fun u32(p: Int) = if (little) u16(p) or (u16(p + 2) shl 16) else (u16(p) shl 16) or u16(p + 2)
		if (u16(tiff + 2) != 0x002A) return 1
		val ifd = tiff + u32(tiff + 4)
		val count = u16(ifd)
		for (k in 0 until count) {
			val entry = ifd + 2 + 12 * k
			if (u16(entry) == 0x0112) return u16(entry + 8).takeIf { it in 1..8 } ?: 1
		}
		return 1
	}

	private fun u8(b: ByteArray, p: Int) = b[p].toInt() and 0xFF

	private fun invalid() = BusinessException(FileErrorCode.INVALID_FILE)
}
