package com.bbolbbol.server.domain.ranking

import com.bbolbbol.server.domain.ranking.service.StreakCalculator
import com.bbolbbol.server.domain.ranking.service.StreakCalculator.Streak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakCalculatorTest {

	private val today = LocalDate.of(2026, 11, 10)
	private fun d(offset: Int) = today.plusDays(offset.toLong())

	@Test
	@DisplayName("기록이 없으면 0, 0")
	fun empty() {
		assertThat(StreakCalculator.compute(emptyList(), today)).isEqualTo(Streak(0, 0))
	}

	@Test
	@DisplayName("오늘만 참여하면 1")
	fun todayOnly() {
		assertThat(StreakCalculator.compute(listOf(d(0)), today)).isEqualTo(Streak(1, 1))
	}

	@Test
	@DisplayName("오늘 아직 참여하지 않았어도 어제까지 이어졌다면 유지된다")
	fun yesterdayStillCounts() {
		assertThat(StreakCalculator.compute(listOf(d(-3), d(-2), d(-1)), today)).isEqualTo(Streak(3, 3))
	}

	@Test
	@DisplayName("그제가 마지막이면(어제도 오늘도 없음) 끊겨서 현재 0, 최장은 남는다")
	fun brokenWhenLastIsTwoDaysAgo() {
		assertThat(StreakCalculator.compute(listOf(d(-4), d(-3), d(-2)), today)).isEqualTo(Streak(0, 3))
	}

	@Test
	@DisplayName("중간에 하루 빠지면 거기서 끊기고 현재는 최근 구간만 센다")
	fun gapResets() {
		val days = listOf(d(-10), d(-9), d(-8), d(-7), d(-3), d(-2), d(-1), d(0))
		assertThat(StreakCalculator.compute(days, today)).isEqualTo(Streak(4, 4))
		val days2 = listOf(d(-10), d(-9), d(-8), d(-7), d(-6), d(-2), d(-1), d(0))
		assertThat(StreakCalculator.compute(days2, today)).isEqualTo(Streak(3, 5))
	}

	@Test
	@DisplayName("같은 날짜가 여러 번 들어와도 하루로 센다(순서도 무관)")
	fun duplicatesAndOrder() {
		assertThat(StreakCalculator.compute(listOf(d(0), d(-1), d(0), d(-1), d(-2)), today)).isEqualTo(Streak(3, 3))
	}

	@Test
	@DisplayName("미래 날짜는 무시한다")
	fun futureIgnored() {
		assertThat(StreakCalculator.compute(listOf(d(1), d(2), d(0)), today)).isEqualTo(Streak(1, 1))
	}

	@Test
	@DisplayName("월과 연의 경계를 넘어도 이어진다")
	fun acrossMonthAndYear() {
		val newYear = LocalDate.of(2027, 1, 2)
		val days = listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), newYear)
		assertThat(StreakCalculator.compute(days, newYear)).isEqualTo(Streak(4, 4))
	}
}
