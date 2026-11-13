package com.bbolbbol.app.ui.stats

import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.repository.StatsPeriod
import com.bbolbbol.app.util.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** 막대 하나. [axisLabel]이 빈 문자열이면 축에 글자를 그리지 않는다(월간 31칸 등). */
data class ChartBar(
    val label: String,
    val axisLabel: String,
    val count: Int,
    val point: Long,
    val isToday: Boolean,
    val isFuture: Boolean,
)

enum class Direction { UP, DOWN, SAME }

data class Delta(val diff: Long, val direction: Direction)

data class CategorySlice(val code: String, val name: String, val count: Int, val point: Long, val fraction: Float)

/** 서버 stats 응답을 화면 모델로 바꾸는 순수 함수들. */
object StatsChart {
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "월", DayOfWeek.TUESDAY to "화", DayOfWeek.WEDNESDAY to "수", DayOfWeek.THURSDAY to "목",
        DayOfWeek.FRIDAY to "금", DayOfWeek.SATURDAY to "토", DayOfWeek.SUNDAY to "일",
    )

    /** 서버가 활동 없는 날도 0으로 채워 주므로(주 7칸, 월 28~31칸, 년 12칸) 그대로 그린다. [today]는 Asia/Seoul 오늘. */
    fun bars(stats: StatsResponse, today: LocalDate): List<ChartBar> = stats.timeline.map { b ->
        when (stats.period) {
            "YEAR" -> {
                val ym = parseYearMonth(b.label)
                ChartBar(
                    b.label, ym?.let { "${it.monthValue}월" } ?: b.label, b.count, b.point,
                    isToday = ym == YearMonth.from(today),
                    isFuture = ym != null && ym.isAfter(YearMonth.from(today)),
                )
            }
            else -> {
                val d = parseDate(b.label)
                val axis = when {
                    d == null -> b.label
                    stats.period == "WEEK" -> WEEKDAYS.getValue(d.dayOfWeek)
                    // 월간은 1, 6, 11, ... 일만 축에 쓴다.
                    d.dayOfMonth % 5 == 1 -> "${d.dayOfMonth}"
                    else -> ""
                }
                ChartBar(b.label, axis, b.count, b.point, isToday = d == today, isFuture = d != null && d.isAfter(today))
            }
        }
    }

    /** 가장 큰 막대의 높이를 1로 하는 비율(전부 0이면 모두 0). */
    fun heightFractions(bars: List<ChartBar>): List<Float> {
        val max = bars.maxOfOrNull { it.count } ?: 0
        return bars.map { if (max == 0) 0f else it.count.toFloat() / max }
    }

    fun delta(current: Long, previous: Long): Delta = when {
        current > previous -> Delta(current - previous, Direction.UP)
        current < previous -> Delta(previous - current, Direction.DOWN)
        else -> Delta(0, Direction.SAME)
    }

    fun previousLabel(period: StatsPeriod) = when (period) {
        StatsPeriod.WEEK -> "지난주"
        StatsPeriod.MONTH -> "지난달"
        StatsPeriod.YEAR -> "작년"
    }

    /**
     * 카테고리별 인증 횟수 비율. 횟수가 0인 묶음(그 기간에 받은 사진 보너스만 있는 카테고리)은 뺀다.
     * 코드가 빈 문자열이면 "기타".
     */
    fun categories(stats: StatsResponse): List<CategorySlice> {
        val shown = stats.categories.filter { it.count > 0 }
        val total = shown.sumOf { it.count }
        return shown.map {
            CategorySlice(
                it.category,
                if (it.category.isBlank()) "기타" else Formatters.categoryName(it.category),
                it.count,
                it.point,
                if (total == 0) 0f else it.count.toFloat() / total,
            )
        }
    }

    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseYearMonth(s: String): YearMonth? = try {
        YearMonth.parse(s)
    } catch (e: DateTimeParseException) {
        null
    }
}
