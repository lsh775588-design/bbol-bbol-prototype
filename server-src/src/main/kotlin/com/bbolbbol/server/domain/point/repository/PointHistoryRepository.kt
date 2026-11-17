package com.bbolbbol.server.domain.point.repository

import com.bbolbbol.server.domain.point.entity.PointHistory
import com.bbolbbol.server.domain.point.entity.PointType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/** 사용자별 기간 합계(랭킹). questCount는 QUEST_COMPLETE 행 수(= 완료한 퀘스트 수). */
interface UserPointRow {
	val userId: Long
	val total: Long
	val questCount: Long
}

interface CompletionPointRow {
	val completionId: Long
	val total: Long
}

/** 통계용 한 줄(적립 시각, 장소, 종류, 점수). */
interface PointEntryRow {
	val earnedAt: LocalDateTime
	val placeId: Long
	val type: PointType
	val point: Int
}

interface PointHistoryRepository : JpaRepository<PointHistory, Long> {

	@Query("select coalesce(sum(h.point), 0L) from PointHistory h where h.userId = :userId")
	fun sumByUser(@Param("userId") userId: Long): Long

	/** [from, to) 구간에 적립된 포인트 합계. */
	@Query(
		"select coalesce(sum(h.point), 0L) from PointHistory h " +
			"where h.userId = :userId and h.earnedAt >= :from and h.earnedAt < :to",
	)
	fun sumByUserBetween(
		@Param("userId") userId: Long,
		@Param("from") from: LocalDateTime,
		@Param("to") to: LocalDateTime,
	): Long

	// id 내림차순 커서((user_id, id) 인덱스). 호출 쪽이 size + 1을 요청해 hasNext를 판단한다.
	fun findByUserIdAndIdLessThanOrderByIdDesc(userId: Long, cursor: Long, pageable: Pageable): List<PointHistory>

	fun findByUserIdAndTypeAndIdLessThanOrderByIdDesc(
		userId: Long,
		type: PointType,
		cursor: Long,
		pageable: Pageable,
	): List<PointHistory>

	/** 완료 기록별 적립 합계(기본 점수 + 사진 보너스). 활동 일지의 earnedPoint. */
	@Query("select h.completionId as completionId, sum(h.point) as total from PointHistory h where h.completionId in :ids group by h.completionId")
	fun sumByCompletions(@Param("ids") ids: Collection<Long>): List<CompletionPointRow>

	/** [from, to) 적립 기준 사용자별 합계. 점수 내림차순, 퀘스트 수 내림차순, userId 오름차순(결정적 순서). */
	@Query(
		"""
		select h.userId as userId, sum(h.point) as total,
		       sum(case when h.type = :quest then 1L else 0L end) as questCount
		from PointHistory h
		where h.earnedAt >= :from and h.earnedAt < :to
		group by h.userId
		order by sum(h.point) desc, sum(case when h.type = :quest then 1L else 0L end) desc, h.userId asc
		""",
	)
	fun totalsByUser(
		@Param("from") from: LocalDateTime,
		@Param("to") to: LocalDateTime,
		@Param("quest") quest: PointType,
	): List<UserPointRow>

	@Query(
		"select h.earnedAt as earnedAt, h.placeId as placeId, h.type as type, h.point as point from PointHistory h " +
			"where h.userId = :userId and h.earnedAt >= :from and h.earnedAt < :to",
	)
	fun entries(
		@Param("userId") userId: Long,
		@Param("from") from: LocalDateTime,
		@Param("to") to: LocalDateTime,
	): List<PointEntryRow>
}
