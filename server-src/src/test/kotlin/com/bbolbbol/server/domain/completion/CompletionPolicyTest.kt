package com.bbolbbol.server.domain.completion

import com.bbolbbol.server.domain.completion.service.CompletionPolicy
import com.bbolbbol.server.domain.place.service.GeoDistance
import com.bbolbbol.server.domain.place.service.PlaceUserState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CompletionPolicyTest {

	private val kst = ZoneId.of("Asia/Seoul")

	@Test
	@DisplayName("점수: 첫 방문 100%, 재방문 50%(내림)")
	fun earnedPoint() {
		assertThat(CompletionPolicy.earnedPoint(300, true)).isEqualTo(300)
		assertThat(CompletionPolicy.earnedPoint(300, false)).isEqualTo(150)
		assertThat(CompletionPolicy.earnedPoint(100, false)).isEqualTo(50)
		assertThat(CompletionPolicy.earnedPoint(150, false)).isEqualTo(75)
		assertThat(CompletionPolicy.earnedPoint(101, false)).isEqualTo(50) // 홀수는 내림
		assertThat(CompletionPolicy.earnedPoint(1, false)).isEqualTo(0)
	}

	@Test
	@DisplayName("재방문 점수는 /places의 expectedPoint와 같은 규칙이다")
	fun sameRuleAsExpectedPoint() {
		val revisit = PlaceUserState(true, false, 0, null, false)
		val first = PlaceUserState(false, false, 0, null, false)
		for (reward in listOf(0, 1, 99, 100, 101, 150, 300)) {
			assertThat(CompletionPolicy.earnedPoint(reward, false)).isEqualTo(revisit.expectedPoint(reward))
			assertThat(CompletionPolicy.earnedPoint(reward, true)).isEqualTo(first.expectedPoint(reward))
		}
	}

	@Test
	@DisplayName("날짜 경계: 한국 시간 23:59와 00:01은 다른 날, UTC 기준이 아니다")
	fun kstDayBoundary() {
		fun date(utc: String): LocalDate = CompletionPolicy.completionDate(LocalDateTime.now(Clock.fixed(Instant.parse(utc), kst)))
		// 2026-11-09T14:59:59Z = 한국 23:59:59, 2026-11-09T15:00:01Z = 한국 11/10 00:00:01
		assertThat(date("2026-11-09T14:59:59Z")).isEqualTo(LocalDate.of(2026, 11, 9))
		assertThat(date("2026-11-09T15:00:00Z")).isEqualTo(LocalDate.of(2026, 11, 10))
		assertThat(date("2026-11-09T15:01:00Z")).isEqualTo(LocalDate.of(2026, 11, 10))
		// UTC로는 같은 11/09이지만 한국으로는 11/10이다
		assertThat(date("2026-11-09T16:30:00Z")).isEqualTo(LocalDate.of(2026, 11, 10))
	}

	@Test
	@DisplayName("사진 보너스: 첫 방문만 50점과 1시간 마감, 재방문은 0점과 null")
	fun photoBonus() {
		val at = LocalDateTime.of(2026, 11, 9, 15, 20)
		assertThat(CompletionPolicy.photoBonusPoint(true)).isEqualTo(50)
		assertThat(CompletionPolicy.photoDeadline(at, true)).isEqualTo(LocalDateTime.of(2026, 11, 9, 16, 20))
		assertThat(CompletionPolicy.photoBonusPoint(false)).isEqualTo(0)
		assertThat(CompletionPolicy.photoDeadline(at, false)).isNull()
	}

	@Test
	@DisplayName("정확도 한도는 100m(이하 허용)")
	fun accuracyLimit() {
		assertThat(CompletionPolicy.MAX_ACCURACY_M).isEqualTo(100.0)
	}

	@Test
	@DisplayName("반경 경계: 99m는 100m 반경 안, 101m는 밖(위도 방향)")
	fun radiusEdges() {
		val lat = 37.5796
		val lng = 126.977
		val metersPerDegLat = GeoDistance.EARTH_RADIUS_M * Math.PI / 180.0
		val d99 = GeoDistance.haversineMeters(lat + 99 / metersPerDegLat, lng, lat, lng)
		val d101 = GeoDistance.haversineMeters(lat + 101 / metersPerDegLat, lng, lat, lng)
		assertThat(d99).isBetween(98.9, 99.1).isLessThanOrEqualTo(100.0)
		assertThat(d101).isBetween(100.9, 101.1).isGreaterThan(100.0)
		assertThat(GeoDistance.haversineMeters(lat, lng, lat, lng)).isZero()
	}
}
