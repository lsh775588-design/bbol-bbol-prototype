package com.bbolbbol.server.domain.point.service

import com.bbolbbol.server.domain.place.service.PlaceQueryService
import com.bbolbbol.server.domain.point.dto.StatsBar
import com.bbolbbol.server.domain.point.dto.StatsCategory
import com.bbolbbol.server.domain.point.dto.StatsPrevious
import com.bbolbbol.server.domain.point.dto.StatsResponse
import com.bbolbbol.server.domain.point.entity.PointType
import com.bbolbbol.server.domain.point.repository.PointEntryRow
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * GET /users/me/stats. 기준은 포인트 적립 시각(point_histories.earned_at, 한국 시간)이다(Gray Area A5).
 * - completionCount: 기간에 적립된 QUEST_COMPLETE 행 수(완료 하나에 하나). 사진 보너스 행은 세지 않는다.
 * - totalPoint: 기간에 적립된 모든 점수(사진 보너스 포함). 보너스는 업로드한 시각에 적립되므로 인증 다음 날로 넘어갈 수 있다.
 * - placeCount: QUEST_COMPLETE 행의 서로 다른 장소 수.
 * 한 사용자의 기간 행만 읽어서 메모리에서 묶는다(사용자당 연 수백 행 수준). 장소 카테고리는 한 번에 조회한다.
 */
@Service
@Transactional(readOnly = true)
class StatsService(
	private val pointService: PointService,
	private val placeQueryService: PlaceQueryService,
	private val clock: Clock,
) {

	fun stats(userId: Long, period: StatsPeriod, date: LocalDate?): StatsResponse {
		val today = LocalDate.now(clock)
		val base = date ?: today
		if (base.isAfter(today)) {
			throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(listOf(FieldError("date", "미래 날짜는 조회할 수 없습니다."))))
		}
		val range = period.rangeOf(base)
		val previousRange = period.rangeOf(range.start.minusDays(1))

		val rows = pointService.entries(userId, range.start.atStartOfDay(), range.endExclusive.atStartOfDay())
		val previousRows = pointService.entries(userId, previousRange.start.atStartOfDay(), previousRange.endExclusive.atStartOfDay())

		val quests = rows.filter { it.type == PointType.QUEST_COMPLETE }
		val categoryByPlace = placeQueryService.briefs(rows.map { it.placeId }).mapValues { it.value.categoryCode }

		return StatsResponse(
			period = period.name,
			startDate = range.start,
			endDate = range.end,
			completionCount = quests.size,
			totalPoint = rows.sumOf { it.point.toLong() },
			placeCount = quests.map { it.placeId }.toSet().size,
			previous = StatsPrevious(
				completionCount = previousRows.count { it.type == PointType.QUEST_COMPLETE },
				totalPoint = previousRows.sumOf { it.point.toLong() },
			),
			categories = categories(rows, categoryByPlace),
			timeline = timeline(period, range, rows),
		)
	}

	/** 횟수 많은 순(같으면 점수 많은 순, 그다음 코드 순). 장소를 찾지 못하면 빈 코드("")로 묶는다. */
	private fun categories(rows: List<PointEntryRow>, categoryByPlace: Map<Long, String>): List<StatsCategory> =
		rows.groupBy { categoryByPlace[it.placeId] ?: "" }
			.map { (code, list) ->
				StatsCategory(code, list.count { it.type == PointType.QUEST_COMPLETE }, list.sumOf { it.point.toLong() })
			}
			.sortedWith(compareByDescending<StatsCategory> { it.count }.thenByDescending { it.point }.thenBy { it.category })

	/** 활동이 없는 날/달도 0으로 채운다. WEEK는 7개, MONTH는 28~31개, YEAR는 12개. */
	private fun timeline(period: StatsPeriod, range: StatsPeriod.Range, rows: List<PointEntryRow>): List<StatsBar> {
		if (period == StatsPeriod.YEAR) {
			val byMonth = rows.groupBy { YearMonth.from(it.earnedAt) }
			return (1..12).map { m ->
				val ym = YearMonth.of(range.start.year, m)
				val list = byMonth[ym].orEmpty()
				StatsBar(ym.toString(), list.count { it.type == PointType.QUEST_COMPLETE }, list.sumOf { it.point.toLong() })
			}
		}
		val byDay = rows.groupBy { it.earnedAt.toLocalDate() }
		return generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.end) }.map { d ->
			val list = byDay[d].orEmpty()
			StatsBar(d.toString(), list.count { it.type == PointType.QUEST_COMPLETE }, list.sumOf { it.point.toLong() })
		}.toList()
	}
}
