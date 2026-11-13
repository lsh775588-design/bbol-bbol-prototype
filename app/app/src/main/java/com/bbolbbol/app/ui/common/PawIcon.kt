package com.bbolbbol.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 발바닥 아이콘(직접 그린 벡터). 큰 발바닥 하나와 발가락 네 개. 색은 Icon의 tint로 바꾼다. */
val PawIcon: ImageVector by lazy {
    ImageVector.Builder("Paw", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            circle(12f, 16f, 4.6f) // 발바닥
            circle(5.2f, 11f, 2.2f)
            circle(9.2f, 6.4f, 2.3f)
            circle(14.8f, 6.4f, 2.3f)
            circle(18.8f, 11f, 2.2f)
        }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = cx - r, y1 = cy)
    close()
}
