package com.bbolbbol.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.repository.StatsPeriod
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.PillChip
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.StatTile
import com.bbolbbol.app.ui.theme.BbolColors

private val PERIOD_LABELS = listOf(StatsPeriod.WEEK to "주", StatsPeriod.MONTH to "월", StatsPeriod.YEAR to "년")

private val SLICE_COLORS = listOf(
    Color(0xFF7B5CE6), Color(0xFFFF8FAB), Color(0xFF5B3FC4), Color(0xFFF2B431),
    Color(0xFF2FA866), Color(0xFF9AA6C4), Color(0xFFE58A5A), Color(0xFFB9A6F5),
)

@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().background(BbolColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("퀘스트 통계") { TextButton(onClick = onBack) { Text("뒤로", color = BbolColors.Violet) } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERIOD_LABELS.forEach { (p, label) -> PillChip(label, s.period == p, { vm.selectPeriod(p) }) }
        }

        val stats = s.stats
        when {
            s.loading -> LoadingState()
            stats == null -> ErrorState(s.error ?: "불러오지 못했어요.", vm::load)
            s.isEmpty -> EmptyState("이 기간에는 아직 도착 인증이 없어요.")
            else -> StatsContent(stats, s.period, vm.today())
        }
    }
}

@Composable
private fun StatsContent(stats: StatsResponse, period: StatsPeriod, today: java.time.LocalDate) {
    Text("${stats.startDate} ~ ${stats.endDate}", style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("인증 횟수", "${stats.completionCount}회", Modifier.weight(1f), Icons.Filled.CheckCircle)
        StatTile("포인트", "${stats.totalPoint}P", Modifier.weight(1f), Icons.Filled.Star)
        StatTile("방문 장소", "${stats.placeCount}곳", Modifier.weight(1f), Icons.Filled.Place)
    }

    BbolCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val name = StatsChart.previousLabel(period)
            DeltaLine("인증 횟수", name, StatsChart.delta(stats.completionCount.toLong(), stats.previous.completionCount.toLong()), "회")
            DeltaLine("포인트", name, StatsChart.delta(stats.totalPoint, stats.previous.totalPoint), "P")
        }
    }

    BbolCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("활동 추이", style = MaterialTheme.typography.titleMedium)
            BarChart(StatsChart.bars(stats, today))
        }
    }

    val slices = StatsChart.categories(stats)
    if (slices.isNotEmpty()) {
        BbolCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("카테고리별 인증", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(slices, Modifier.size(110.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        slices.forEachIndexed { i, sl ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(SLICE_COLORS[i % SLICE_COLORS.size]))
                                Spacer(Modifier.width(8.dp))
                                Text(sl.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${sl.count}회", color = BbolColors.InkSoft, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeltaLine(what: String, previousName: String, delta: Delta, unit: String) {
    val (text, color) = when (delta.direction) {
        Direction.UP -> "${previousName}보다 +${delta.diff}$unit 늘었어요" to BbolColors.Success
        Direction.DOWN -> "${previousName}보다 ${delta.diff}$unit 줄었어요" to MaterialTheme.colorScheme.error
        Direction.SAME -> "${previousName}와 같아요" to BbolColors.InkSoft
    }
    Row {
        Text(what, Modifier.width(72.dp), color = BbolColors.InkSoft, style = MaterialTheme.typography.bodyMedium)
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 보라 막대 차트. 오늘은 진한 보라, 나머지는 보라, 아직 안 온 날은 연한 색. 높이는 인증 횟수 기준. */
@Composable
private fun BarChart(bars: List<ChartBar>) {
    val fractions = StatsChart.heightFractions(bars)
    val maxCount = bars.maxOfOrNull { it.count } ?: 0
    val labelColor = BbolColors.InkSoft.toArgb()
    Column {
        Text("최대 ${maxCount}회", style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val axisH = 22.dp.toPx()
            val chartH = size.height - axisH
            val slot = size.width / bars.size.coerceAtLeast(1)
            val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 11.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                color = labelColor
            }
            drawLine(BbolColors.Outline, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, b ->
                val h = (chartH * fractions[i]).let { if (b.count > 0) it.coerceAtLeast(4.dp.toPx()) else 2.dp.toPx() }
                val x = slot * i + (slot - barW) / 2
                val color = when {
                    b.isToday -> BbolColors.VioletDeep
                    b.isFuture -> BbolColors.Lavender
                    b.count == 0 -> BbolColors.LavenderStrong
                    else -> BbolColors.Violet
                }
                drawRoundRect(color, Offset(x, chartH - h), Size(barW, h), CornerRadius(4.dp.toPx()))
                if (b.axisLabel.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(b.axisLabel, slot * i + slot / 2, size.height - 4.dp.toPx(), paint)
                }
            }
        }
    }
}

@Composable
private fun Donut(slices: List<CategorySlice>, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 22.dp.toPx()
        val inset = stroke / 2
        var start = -90f
        slices.forEachIndexed { i, sl ->
            val sweep = 360f * sl.fraction
            drawArc(
                SLICE_COLORS[i % SLICE_COLORS.size], start, sweep.coerceAtLeast(0.5f), useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke),
            )
            start += sweep
        }
    }
}
