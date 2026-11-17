package com.bbolbbol.server.domain.completion.service

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 도착 인증의 순수 규칙(점수, 날짜 경계, 사진 보너스 값). */
object CompletionPolicy {

	/** GPS 정확도가 이 값을 넘으면 거절(100m 이하는 허용). */
	const val MAX_ACCURACY_M = 100.0

	const val PHOTO_BONUS_POINT = 50
	val PHOTO_DEADLINE: Duration = Duration.ofHours(1)

	/** 첫 방문은 전액, 재방문은 50%(내림). PlaceUserState.expectedPoint와 같은 규칙이다. */
	fun earnedPoint(rewardPoint: Int, firstVisit: Boolean): Int = if (firstVisit) rewardPoint else rewardPoint / 2

	/** 한국 시간 날짜(Clock이 Asia/Seoul). 23:59와 00:01은 서로 다른 날이다. */
	fun completionDate(now: LocalDateTime): LocalDate = now.toLocalDate()

	fun photoBonusPoint(firstVisit: Boolean): Int = if (firstVisit) PHOTO_BONUS_POINT else 0

	fun photoDeadline(completedAt: LocalDateTime, firstVisit: Boolean): LocalDateTime? =
		if (firstVisit) completedAt.plus(PHOTO_DEADLINE) else null

	/** 마감은 첫 방문만(인증 + 1시간, 정각까지 허용). 재방문은 마감이 없다(명세). */
	fun photoDeadlinePassed(completedAt: LocalDateTime, firstVisit: Boolean, now: LocalDateTime): Boolean =
		firstVisit && now.isAfter(completedAt.plus(PHOTO_DEADLINE))
}
