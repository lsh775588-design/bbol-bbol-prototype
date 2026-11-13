package com.bbolbbol.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 디자인 토큰. docs/UI 프로토타입의 4장 이미지에서 눈대중으로 추정한 값이다.
 *  - Violet      #7B5CE6  버튼, 진행 막대, 선택된 탭/칩, 강조 숫자
 *  - VioletDeep  #5B3FC4  진한 강조(포디움 1위, 제목 강조)
 *  - Lavender    #EEE8FF  선택된 칩/아바타 배경/아이콘 배경
 *  - Background  #F6F3FF  화면 배경(연한 라벤더)
 *  - Ink         #1F1A3D  본문/제목 글자
 *  - InkSoft     #8B87A0  보조 글자
 *  - PawPink     #FF8FAB  발바닥 아이콘 포인트
 *  - Success     #2FA866 / SuccessBg #DDF6E6  "완료", "진행 중" 칩
 *  - Neutral     #8A8794 / NeutralBg #ECEAF1  "시작 전" 칩
 *  - Gold/Silver/Bronze  포디움 메달 색
 */
object BbolColors {
    val Violet = Color(0xFF7B5CE6)
    val VioletDeep = Color(0xFF5B3FC4)
    val Lavender = Color(0xFFEEE8FF)
    val LavenderStrong = Color(0xFFD9CEFF)
    val Background = Color(0xFFF6F3FF)
    val Ink = Color(0xFF1F1A3D)
    val InkSoft = Color(0xFF8B87A0)
    val PawPink = Color(0xFFFF8FAB)
    val PawPinkBg = Color(0xFFFFE3EC)
    val Success = Color(0xFF2FA866)
    val SuccessBg = Color(0xFFDDF6E6)
    val Neutral = Color(0xFF8A8794)
    val NeutralBg = Color(0xFFECEAF1)
    val Gold = Color(0xFFF2B431)
    val Silver = Color(0xFF9AA6C4)
    val Bronze = Color(0xFFE58A5A)
    val Outline = Color(0xFFE3DDF5)
}

object BbolShapes {
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = RoundedCornerShape(50)
    val Tile = RoundedCornerShape(16.dp)
}

private val Colors = lightColorScheme(
    primary = BbolColors.Violet,
    onPrimary = Color.White,
    primaryContainer = BbolColors.Lavender,
    onPrimaryContainer = BbolColors.VioletDeep,
    secondary = BbolColors.InkSoft,
    onSecondary = Color.White,
    secondaryContainer = BbolColors.Lavender,
    onSecondaryContainer = BbolColors.VioletDeep,
    tertiary = BbolColors.PawPink,
    background = BbolColors.Background,
    onBackground = BbolColors.Ink,
    surface = Color.White,
    onSurface = BbolColors.Ink,
    surfaceVariant = BbolColors.Lavender,
    onSurfaceVariant = BbolColors.InkSoft,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = BbolColors.Lavender,
    outline = BbolColors.Outline,
    outlineVariant = BbolColors.Outline,
    error = Color(0xFFD64550),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Type = Typography(
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = BbolColors.Ink),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BbolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = Shapes, typography = Type, content = content)
}
