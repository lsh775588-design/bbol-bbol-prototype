package com.bbolbbol.app.ui.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Photo Picker가 준 content:// Uri를 읽는 [ImageOps]. 저장소 권한이 필요 없다. */
class AndroidImageOps(private val resolver: ContentResolver, private val uri: Uri) : ImageOps<Bitmap> {

    override fun readSize(): Pair<Int, Int>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    } catch (e: Exception) {
        null
    }

    override fun orientationDegrees(): Int = try {
        resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0 // 좌우 반전(MIRROR) 방향은 처리하지 않는다(프로토타입).
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    override fun decode(sampleSize: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    override fun transform(src: Bitmap, degrees: Int, width: Int, height: Int): Bitmap {
        val swap = degrees == 90 || degrees == 270
        val rw = if (swap) src.height else src.width
        val rh = if (swap) src.width else src.height
        if (degrees % 360 == 0 && rw == width && rh == height && !src.hasAlpha()) return src
        // 회전한 경계의 왼쪽 위를 원점으로 되돌린 뒤 목표 크기로 늘이고 줄인다.
        val bounds = android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        Matrix().apply { postRotate(degrees.toFloat()) }.mapRect(bounds)
        val m = Matrix().apply {
            postRotate(degrees.toFloat())
            postTranslate(-bounds.left, -bounds.top)
            postScale(width.toFloat() / rw, height.toFloat() / rh)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 투명 PNG를 JPEG로 바꾸면 검게 나오므로 흰 배경 위에 그린다.
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    override fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    override fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/** 화면 쪽이 부르는 진입점. 디코드와 인코딩은 백그라운드에서 한다. */
fun interface PhotoPrepareService {
    suspend fun prepare(uri: String): PrepareResult
}

class AndroidPhotoPrepareService(
    private val resolver: ContentResolver,
    private val preparer: PhotoPreparer = PhotoPreparer(),
) : PhotoPrepareService {
    override suspend fun prepare(uri: String): PrepareResult = withContext(Dispatchers.Default) {
        try {
            preparer.prepare(AndroidImageOps(resolver, Uri.parse(uri)))
        } catch (e: OutOfMemoryError) {
            PrepareResult.TooLarge
        }
    }
}
