package com.bbolbbol.server.domain.point.service

import com.bbolbbol.server.domain.place.service.PlaceQueryService
import com.bbolbbol.server.domain.point.dto.PointHistoryItem
import com.bbolbbol.server.domain.point.dto.PointHistoryPage
import com.bbolbbol.server.domain.point.entity.PointHistory
import com.bbolbbol.server.domain.point.entity.PointType
import com.bbolbbol.server.domain.point.repository.PointEntryRow
import com.bbolbbol.server.domain.point.repository.PointHistoryRepository
import com.bbolbbol.server.domain.point.repository.UserPointRow
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** point 도메인의 공개 진입점. point_histories는 이 서비스만 읽고 쓴다. */
@Service
@Transactional(readOnly = true)
class PointService(
	private val histories: PointHistoryRepository,
	private val placeQueryService: PlaceQueryService,
) {

	/** 호출하는 쪽의 트랜잭션에 참여한다(도착 인증과 한 트랜잭션). */
	@Transactional
	fun record(
		userId: Long,
		completionId: Long,
		placeId: Long,
		type: PointType,
		point: Int,
		earnedAt: LocalDateTime,
	): PointHistory {
		require(point >= 0) { "포인트는 쌓기만 한다(음수 불가)" }
		return histories.save(PointHistory(userId, completionId, placeId, type, point, earnedAt))
	}

	/** 완료 기록별 적립 합계(기본 점수 + 사진 보너스). 내역이 없는 id는 결과에 없다. */
	fun pointByCompletions(completionIds: Collection<Long>): Map<Long, Int> {
		if (completionIds.isEmpty()) return emptyMap()
		return histories.sumByCompletions(completionIds.toSet()).associate { it.completionId to it.total.toInt() }
	}

	/** [from, to) 적립 기준 사용자별 합계(랭킹용, 점수 내림차순). 탈퇴/온보딩 제외는 호출하는 쪽이 한다. */
	fun totalsByUser(from: LocalDateTime, to: LocalDateTime): List<UserPointRow> =
		histories.totalsByUser(from, to, PointType.QUEST_COMPLETE)

	/** 한 사용자의 [from, to) 적립 줄(통계용). */
	fun entries(userId: Long, from: LocalDateTime, to: LocalDateTime): List<PointEntryRow> =
		histories.entries(userId, from, to)

	fun totalPoint(userId: Long): Long = histories.sumByUser(userId)

	/** earned_at이 [from, to)인 포인트 합계(오늘 획득 포인트 등). */
	fun pointBetween(userId: Long, from: LocalDateTime, to: LocalDateTime): Long =
		histories.sumByUserBetween(userId, from, to)

	/** 최신순 커서 페이지. cursor는 이전 응답의 nextCursor(없으면 첫 페이지). */
	fun page(userId: Long, cursor: Long?, size: Int, type: PointType?): PointHistoryPage {
		val c = cursor ?: Long.MAX_VALUE
		val pageable = Pageable.ofSize(size + 1)
		val rows = if (type == null) {
			histories.findByUserIdAndIdLessThanOrderByIdDesc(userId, c, pageable)
		} else {
			histories.findByUserIdAndTypeAndIdLessThanOrderByIdDesc(userId, type, c, pageable)
		}
		val hasNext = rows.size > size
		val pageRows = rows.take(size)
		val places = placeQueryService.briefs(pageRows.map { it.placeId })
		val items = pageRows.map {
			val place = places[it.placeId]
			PointHistoryItem(
				historyId = it.id!!,
				type = it.type,
				point = it.point,
				completionId = it.completionId,
				placeId = it.placeId,
				placeName = place?.name ?: UNKNOWN_PLACE,
				category = place?.categoryCode ?: "",
				createdAt = it.earnedAt,
			)
		}
		return PointHistoryPage(items, if (hasNext) items.last().historyId else null, hasNext)
	}

	companion object {
		const val UNKNOWN_PLACE = "알 수 없는 장소"
	}
}
