package com.bbolbbol.app.ui.map

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import com.bbolbbol.app.ui.theme.BbolColors
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

enum class PinState { NORMAL, ACCEPTED, COMPLETED }

/** 코드로 그린 보라색 핀. 같은 모양은 한 번만 그려 재사용한다. */
object PinFactory {
    private val cache = HashMap<String, BitmapDrawable>()

    fun emoji(category: String?): String = when (category) {
        "CAFE" -> "☕"
        "PARK" -> "🌳"
        "SHOPPING" -> "🛍"
        "MOVIE" -> "🎬"
        "LIBRARY" -> "📚"
        "STUDY_CAFE" -> "✏"
        "MUSEUM" -> "🏛"
        "HERITAGE" -> "🏯"
        else -> "📍"
    }

    fun get(context: Context, category: String?, state: PinState, selected: Boolean, hot: Boolean): BitmapDrawable {
        val key = "$category|$state|$selected|$hot"
        return cache.getOrPut(key) {
            BitmapDrawable(context.resources, draw(context.resources.displayMetrics.density, category, state, selected, hot))
        }
    }

    private fun draw(d: Float, category: String?, state: PinState, selected: Boolean, hot: Boolean): Bitmap {
        val scale = if (selected) 1.35f else 1f
        val pinW = 34f * d * scale
        val pinH = 44f * d * scale
        val pad = 8f * d
        val bmp = Bitmap.createBitmap((pinW + pad * 2).toInt(), (pinH + pad * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = pad + pinW / 2
        val r = pinW / 2
        val cy = pad + r
        val tipY = pad + pinH

        val fill = when {
            state == PinState.COMPLETED -> 0xFFB7B3C6.toInt()
            selected -> BbolColors.VioletDeep.toArgb()
            else -> BbolColors.Violet.toArgb()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 받은 퀘스트는 금색 고리로 눈에 띄게 한다.
        if (state == PinState.ACCEPTED) {
            p.style = Paint.Style.FILL
            p.color = BbolColors.Gold.toArgb()
            c.drawCircle(cx, cy, r + 3.5f * d, p)
        }

        val pin = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.78f, cy + r * 0.62f)
            lineTo(cx, tipY)
            lineTo(cx + r * 0.78f, cy + r * 0.62f)
            close()
        }
        p.style = Paint.Style.FILL
        p.color = fill
        p.setShadowLayer(4f * d, 0f, 2f * d, 0x55000000)
        c.drawPath(pin, p)
        p.clearShadowLayer()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(pin, p)

        // 안쪽 흰 원 + 카테고리 아이콘(이모지) 또는 완료 체크.
        val ir = r * 0.66f
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, ir, p)
        if (state == PinState.COMPLETED) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3.2f * d * scale
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            p.color = BbolColors.Success.toArgb()
            val check = Path().apply {
                moveTo(cx - ir * 0.45f, cy + ir * 0.02f)
                lineTo(cx - ir * 0.1f, cy + ir * 0.38f)
                lineTo(cx + ir * 0.5f, cy - ir * 0.32f)
            }
            c.drawPath(check, p)
        } else {
            val t = Paint(Paint.ANTI_ALIAS_FLAG)
            t.textSize = ir * 1.15f
            t.textAlign = Paint.Align.CENTER
            val fm = t.fontMetrics
            c.drawText(emoji(category), cx, cy - (fm.ascent + fm.descent) / 2, t)
        }

        // HOT 표시는 분홍 점, 받은 퀘스트는 금색 느낌표 배지.
        if (state != PinState.COMPLETED && (hot || state == PinState.ACCEPTED)) {
            val bx = cx + r * 0.85f
            val by = cy - r * 0.85f
            val br = 6.5f * d * scale
            p.style = Paint.Style.FILL
            p.color = if (state == PinState.ACCEPTED) BbolColors.Gold.toArgb() else BbolColors.PawPink.toArgb()
            c.drawCircle(bx, by, br, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.6f * d
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(bx, by, br, p)
            if (state == PinState.ACCEPTED) {
                val t = Paint(Paint.ANTI_ALIAS_FLAG)
                t.color = 0xFFFFFFFF.toInt()
                t.isFakeBoldText = true
                t.textSize = br * 1.5f
                t.textAlign = Paint.Align.CENTER
                val fm = t.fontMetrics
                c.drawText("!", bx, by - (fm.ascent + fm.descent) / 2, t)
            }
        }
        return bmp
    }
}

/** 내 위치 점과 오차 반경 후광. */
class MyLocationDot(private val lat: Double, private val lng: Double, private val accuracyMeters: Float?) : Overlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        projection.toPixels(GeoPoint(lat, lng), point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        val density = Resources.getSystem().displayMetrics.density
        val acc = (accuracyMeters ?: 40f).coerceAtLeast(25f)
        val haloPx = projection.metersToPixels(acc).coerceIn(18f * density, 90f * density)
        paint.style = Paint.Style.FILL
        paint.color = 0x2E7B5CE6
        canvas.drawCircle(x, y, haloPx, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0x667B5CE6
        canvas.drawCircle(x, y, haloPx, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        paint.setShadowLayer(3f * density, 0f, 1f * density, 0x44000000)
        canvas.drawCircle(x, y, 9f * density, paint)
        paint.clearShadowLayer()
        paint.color = 0xFF4F7CF0.toInt()
        canvas.drawCircle(x, y, 6.5f * density, paint)
    }
}

/** 베이스 지도를 옅고 차분하게: 채도를 낮추고 밝게 해서 핀이 눈에 띄게 한다. */
fun softTileFilter(): ColorMatrixColorFilter {
    val m = ColorMatrix().apply { setSaturation(0.28f) }
    val lift = ColorMatrix(
        floatArrayOf(
            0.78f, 0f, 0f, 0f, 52f,
            0f, 0.78f, 0f, 0f, 50f,
            0f, 0f, 0.78f, 0f, 62f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    m.postConcat(lift)
    return ColorMatrixColorFilter(m)
}
