package com.bbolbbol.server.domain.completion.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 도착 인증 기록. user, place는 다른 도메인이라 연관관계 대신 id만 가진다(FK는 DB가 건다).
 * (user_id, place_id, completion_date)는 DB 유니크다.
 */
@Entity
@Table(name = "completions")
class Completion(
	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: Long,

	@Column(name = "place_id", nullable = false, updatable = false)
	val placeId: Long,

	/** 한국 시간 기준 날짜(Clock). */
	@Column(name = "completion_date", nullable = false, updatable = false)
	val completionDate: LocalDate,

	@Column(name = "completed_at", nullable = false, updatable = false)
	val completedAt: LocalDateTime,

	@Column(name = "first_visit", nullable = false, updatable = false)
	val firstVisit: Boolean,

	/** 기본 점수(재방문 50%). 사진 보너스가 생기면 합계는 point_histories 쪽이다. */
	@Column(name = "earned_point", nullable = false, updatable = false)
	val earnedPoint: Int,

	/** 제출 좌표. 탈퇴한 사용자의 기록은 개인정보 삭제로 NULL이 된다(V303). */
	@Column(updatable = false)
	val lat: Double?,

	@Column(updatable = false)
	val lng: Double?,

	@Column(name = "accuracy_m", updatable = false)
	val accuracyM: Double?,

	@Column(name = "photo_key")
	var photoKey: String? = null,

	@Column(name = "photo_uploaded_at")
	var photoUploadedAt: LocalDateTime? = null,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null
}
