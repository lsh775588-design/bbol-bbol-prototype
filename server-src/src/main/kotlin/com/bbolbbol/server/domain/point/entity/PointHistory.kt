package com.bbolbbol.server.domain.point.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/** 포인트 적립 내역. 누적 포인트는 이 표의 합계다. 적립만 있고 차감은 없다. */
@Entity
@Table(name = "point_histories")
class PointHistory(
	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: Long,

	@Column(name = "completion_id", nullable = false, updatable = false)
	val completionId: Long,

	@Column(name = "place_id", nullable = false, updatable = false)
	val placeId: Long,

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "type", nullable = false, updatable = false)
	val type: PointType,

	@Column(name = "point", nullable = false, updatable = false)
	val point: Int,

	/** 적립 시각(한국 시간). 일/주 집계의 기준. */
	@Column(name = "earned_at", nullable = false, updatable = false)
	val earnedAt: LocalDateTime,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null
}
