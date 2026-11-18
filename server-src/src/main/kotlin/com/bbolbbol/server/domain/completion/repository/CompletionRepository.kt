package com.bbolbbol.server.domain.completion.repository

import com.bbolbbol.server.domain.completion.entity.Completion
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

/** 장소별 내 인증 요약(집계 한 번으로 방문 여부, 오늘 인증 여부, 마지막 인증 시각을 얻는다). */
interface MyPlaceVisit {
	val placeId: Long
	val lastCompletedAt: LocalDateTime
	val lastCompletionDate: LocalDate
}

interface PlaceCompletionCount {
	val placeId: Long
	val total: Long
}

/** 장소 랭킹용: 기간 내 인증 횟수와 서로 다른 사용자 수. */
interface PlaceRankingRow {
	val placeId: Long
	val total: Long
	val users: Long
}

/** 연속 참여 계산용: 사용자가 참여한 날짜(중복 없음). */
interface ParticipationDay {
	val userId: Long
	val day: LocalDate
}

/** 탈퇴 정리용: 사진이 붙은 완료 기록의 id와 저장 key. */
interface PhotoRef {
	val id: Long
	val photoKey: String
}

interface CompletionRepository : JpaRepository<Completion, Long> {

	fun existsByUserIdAndPlaceIdAndCompletionDate(userId: Long, placeId: Long, completionDate: LocalDate): Boolean

	fun existsByUserIdAndPlaceId(userId: Long, placeId: Long): Boolean

	fun countByUserId(userId: Long): Long

	@Query(
		"""
		select c.placeId as placeId, max(c.completedAt) as lastCompletedAt, max(c.completionDate) as lastCompletionDate
		from Completion c
		where c.userId = :userId and c.placeId in :placeIds
		group by c.placeId
		""",
	)
	fun summarizeMyVisits(@Param("userId") userId: Long, @Param("placeIds") placeIds: Collection<Long>): List<MyPlaceVisit>

	@Query("select c.placeId as placeId, count(c) as total from Completion c where c.placeId in :placeIds group by c.placeId")
	fun countByPlaces(@Param("placeIds") placeIds: Collection<Long>): List<PlaceCompletionCount>

	/** 활동 일지: id 내림차순 커서. allPlaces=false면 placeIds로 카테고리 필터. 호출 쪽이 size + 1을 요청한다. */
	@Query(
		"""
		select c from Completion c
		where c.userId = :userId and c.id < :cursor
		  and c.completedAt >= :from and c.completedAt < :to
		  and (:allPlaces = true or c.placeId in :placeIds)
		order by c.id desc
		""",
	)
	fun findPage(
		@Param("userId") userId: Long,
		@Param("cursor") cursor: Long,
		@Param("from") from: LocalDateTime,
		@Param("to") to: LocalDateTime,
		@Param("allPlaces") allPlaces: Boolean,
		@Param("placeIds") placeIds: Collection<Long>,
		pageable: Pageable,
	): List<Completion>

	/** 사진을 한 번만 붙이는 조건부 갱신(photo_key IS NULL). 반환값 0이면 이미 누가 붙인 것이다(동시 업로드 방어). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Completion c set c.photoKey = :key, c.photoUploadedAt = :at where c.id = :id and c.photoKey is null")
	fun attachPhoto(@Param("id") id: Long, @Param("key") key: String, @Param("at") at: LocalDateTime): Int

	@Query(
		"""
		select c.placeId as placeId, count(c) as total, count(distinct c.userId) as users
		from Completion c
		where c.completedAt >= :from and c.completedAt < :to
		  and (:allPlaces = true or c.placeId in :placeIds)
		group by c.placeId
		""",
	)
	fun rankPlaces(
		@Param("from") from: LocalDateTime,
		@Param("to") to: LocalDateTime,
		@Param("allPlaces") allPlaces: Boolean,
		@Param("placeIds") placeIds: Collection<Long>,
	): List<PlaceRankingRow>

	@Query("select distinct c.userId as userId, c.completionDate as day from Completion c order by c.userId, c.completionDate")
	fun participationDays(): List<ParticipationDay>

	@Query("select c.id as id, c.photoKey as photoKey from Completion c where c.userId = :userId and c.photoKey is not null")
	fun findPhotoRefs(@Param("userId") userId: Long): List<PhotoRef>

	/** 파일 삭제에 성공한(또는 이미 없는) 기록의 사진 정보만 비운다. 반환값은 바뀐 행 수. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Completion c set c.photoKey = null, c.photoUploadedAt = null where c.userId = :userId and c.id in :ids")
	fun clearPhotos(@Param("userId") userId: Long, @Param("ids") ids: Collection<Long>): Int

	/** 제출 좌표와 정확도를 지운다. 이미 지워진 기록은 건드리지 않아서 여러 번 불러도 같다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(
		"update Completion c set c.lat = null, c.lng = null, c.accuracyM = null where c.userId = :userId " +
			"and (c.lat is not null or c.lng is not null or c.accuracyM is not null)",
	)
	fun anonymizeCoordinates(@Param("userId") userId: Long): Int

	/** ids 중 아직 지울 개인정보(사진 key, 좌표)가 남은 사용자. 재시도 점검용. */
	@Query(
		"select distinct c.userId from Completion c where c.userId in :ids " +
			"and (c.photoKey is not null or c.lat is not null or c.lng is not null or c.accuracyM is not null)",
	)
	fun userIdsWithPersonalData(@Param("ids") ids: Collection<Long>): List<Long>
}
