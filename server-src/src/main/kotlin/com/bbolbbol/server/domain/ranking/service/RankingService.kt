package com.bbolbbol.server.domain.ranking.service

import com.bbolbbol.server.domain.completion.repository.PlaceRankingRow
import com.bbolbbol.server.domain.completion.service.CompletionService
import com.bbolbbol.server.domain.place.service.PlaceQueryService
import com.bbolbbol.server.domain.point.service.PointService
import com.bbolbbol.server.domain.ranking.dto.PlaceRankingItem
import com.bbolbbol.server.domain.ranking.dto.PlaceRankingResponse
import com.bbolbbol.server.domain.ranking.dto.StreakRankingItem
import com.bbolbbol.server.domain.ranking.dto.StreakRankingMe
import com.bbolbbol.server.domain.ranking.dto.StreakRankingResponse
import com.bbolbbol.server.domain.ranking.dto.UserRankingItem
import com.bbolbbol.server.domain.ranking.dto.UserRankingMe
import com.bbolbbol.server.domain.ranking.dto.UserRankingResponse
import com.bbolbbol.server.domain.user.service.UserQueryService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 랭킹 3종. 별도 테이블 없이 point_histories, completions를 기간으로 묶어서 계산한다(캐시 없음).
 * 기준 시각은 적립 시각(earned_at, 한국 시간)이고 주는 월요일 시작이다(Gray Area A4, A5).
 *
 * 순위는 "표준 경쟁 순위"다: 같은 값이면 같은 순위이고 다음 순위는 건너뛴다(1, 2, 2, 4).
 * 같은 값끼리의 표시 순서는 결정적이다(아래 각 메서드 KDoc).
 *
 * 비용: 유저 랭킹과 연속 랭킹은 해당 기간(또는 전체)의 사용자 집계를 모두 읽고 UserQueryService로 노출 가능 여부를 걸러낸 뒤
 * 메모리에서 순위를 매긴다. 탈퇴/온보딩 미완료를 SQL 한 번으로 거를 수 없는(도메인 경계상 users를 조인하지 않는) 대가다.
 * 사용자가 많아지면 Redis ZSET이나 5분 캐시로 바꾼다(명세 권장).
 */
@Service
@Transactional(readOnly = true)
class RankingService(
	private val pointService: PointService,
	private val completionService: CompletionService,
	private val placeQueryService: PlaceQueryService,
	private val userQueryService: UserQueryService,
	private val clock: Clock,
) {

	private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)

	/** 이미 정렬된 값들의 표준 경쟁 순위. */
	private fun <T> rankOf(values: List<T>): List<Int> {
		val ranks = ArrayList<Int>(values.size)
		values.forEachIndexed { i, v -> ranks += if (i > 0 && values[i - 1] == v) ranks[i - 1] else i + 1 }
		return ranks
	}

	/**
	 * 유저 포인트 랭킹. 점수 내림차순이고 같은 점수는 같은 순위다(표시 순서는 완료 수 많은 순, 그다음 가입 순서(id)).
	 * 탈퇴 사용자와 온보딩 미완료 사용자는 목록과 me에서 모두 빠진다(me는 null).
	 */
	fun userRanking(userId: Long, period: RankingPeriod, size: Int?): UserRankingResponse {
		val s = size ?: DEFAULT_USER_SIZE
		if (s < 1 || s > MAX_USER_SIZE) throw invalid("size", "size는 1 이상 $MAX_USER_SIZE 이하여야 합니다.")
		val range = period.rangeOf(LocalDate.now(clock))

		val totals = pointService.totalsByUser(range.start.atStartOfDay(), range.endExclusive.atStartOfDay())
		val rankable = userQueryService.filterRankable(totals.map { it.userId })
		val list = totals.filter { it.userId in rankable }
		val ranks = rankOf(list.map { it.total })

		val top = list.indices.take(s)
		val meIndex = list.indexOfFirst { it.userId == userId }
		val names = userQueryService.displayNicknames(top.map { list[it].userId } + listOfNotNull(list.getOrNull(meIndex)?.userId))

		return UserRankingResponse(
			period = period.name,
			startDate = range.start,
			endDate = range.end,
			rankings = top.map {
				UserRankingItem(ranks[it], names.getValue(list[it].userId), list[it].total, list[it].questCount, list[it].userId == userId)
			},
			me = if (meIndex < 0) null else UserRankingMe(ranks[meIndex], names.getValue(userId), list[meIndex].total, list[meIndex].questCount),
			updatedAt = now(),
		)
	}

	/**
	 * 인기 장소 랭킹. 기간 내 인증 횟수 내림차순, 같으면 서로 다른 사용자 수 내림차순(명세), 그래도 같으면 같은 순위
	 * (표시 순서는 placeId). 최소 횟수 조건은 없다(명세에 없음, 핫플 표시의 "최소 2회"는 지도 쪽 규칙).
	 * 탈퇴 사용자의 기록도 센다(D4). 폐업(비활성) 장소는 뺀다.
	 */
	fun placeRanking(period: RankingPeriod?, category: String?, size: Int?): PlaceRankingResponse {
		val p = period ?: RankingPeriod.WEEK
		val s = size ?: DEFAULT_PLACE_SIZE
		if (s < 1 || s > MAX_PLACE_SIZE) throw invalid("size", "size는 1 이상 $MAX_PLACE_SIZE 이하여야 합니다.")
		if (category != null && !placeQueryService.isKnownCategory(category)) {
			throw invalid("category", "존재하지 않는 카테고리 코드입니다: $category")
		}
		val range = p.rangeOf(LocalDate.now(clock))
		val placeIds = category?.let { placeQueryService.placeIdsByCategory(it) }

		val rows = completionService.placeRanking(range.start.atStartOfDay(), range.endExclusive.atStartOfDay(), placeIds)
		val briefs = placeQueryService.rankingBriefs(rows.map { it.placeId })
		val sorted = rows
			.filter { briefs[it.placeId]?.active == true }
			.sortedWith(
				compareByDescending<PlaceRankingRow> { it.total }.thenByDescending { it.users }.thenBy { it.placeId },
			)
		val ranks = rankOf(sorted.map { it.total to it.users })

		return PlaceRankingResponse(
			period = p.name,
			startDate = range.start,
			endDate = range.end,
			rankings = sorted.indices.take(s).map {
				val row = sorted[it]
				val brief = briefs.getValue(row.placeId)
				PlaceRankingItem(ranks[it], row.placeId, brief.name, brief.categoryCode, brief.address, row.total, brief.rewardPoint)
			},
			updatedAt = now(),
		)
	}

	/**
	 * 연속 참여 랭킹. 현재 연속 일수 내림차순이고 같으면 같은 순위(표시 순서는 최장 기록 많은 순, 그다음 id).
	 * 현재 연속이 0인 사용자는 목록에 없고 me도 null이다. 탈퇴/온보딩 미완료 사용자는 제외한다.
	 * 연속 일수는 완료 기록의 한국 시간 날짜로 매번 계산한다(결정 B2 기본값: 저장하지 않음).
	 */
	fun streakRanking(userId: Long, size: Int?): StreakRankingResponse {
		val s = size ?: DEFAULT_STREAK_SIZE
		if (s < 1 || s > MAX_STREAK_SIZE) throw invalid("size", "size는 1 이상 $MAX_STREAK_SIZE 이하여야 합니다.")
		val today = LocalDate.now(clock)

		val streaks = completionService.participationDays()
			.groupBy({ it.userId }, { it.day })
			.mapValues { StreakCalculator.compute(it.value, today) }
			.filterValues { it.current > 0 }
		val rankable = userQueryService.filterRankable(streaks.keys)
		val list = streaks.entries.filter { it.key in rankable }
			.sortedWith(
				compareByDescending<Map.Entry<Long, StreakCalculator.Streak>> { it.value.current }
					.thenByDescending { it.value.longest }.thenBy { it.key },
			)
		val ranks = rankOf(list.map { it.value.current })

		val top = list.indices.take(s)
		val meIndex = list.indexOfFirst { it.key == userId }
		val names = userQueryService.displayNicknames(top.map { list[it].key } + listOfNotNull(list.getOrNull(meIndex)?.key))

		return StreakRankingResponse(
			rankings = top.map { StreakRankingItem(ranks[it], names.getValue(list[it].key), list[it].value.current, list[it].key == userId) },
			me = if (meIndex < 0) null else StreakRankingMe(
				ranks[meIndex], names.getValue(userId), list[meIndex].value.current, list[meIndex].value.longest,
			),
		)
	}

	private fun invalid(field: String, reason: String) =
		BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(listOf(FieldError(field, reason))))

	companion object {
		const val DEFAULT_USER_SIZE = 50
		const val MAX_USER_SIZE = 100
		const val DEFAULT_PLACE_SIZE = 10
		const val MAX_PLACE_SIZE = 50
		const val DEFAULT_STREAK_SIZE = 10
		const val MAX_STREAK_SIZE = 50
	}
}
