package com.bbolbbol.server.support

import com.bbolbbol.server.domain.place.entity.Place
import com.bbolbbol.server.domain.place.repository.PlaceCategoryRepository
import com.bbolbbol.server.domain.place.repository.PlaceRepository
import com.bbolbbol.server.domain.user.service.UserService
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * 통계/랭킹/사진 테스트용 데이터 도우미. 완료 기록과 포인트 내역을 시각을 지정해서 DB에 직접 넣는다
 * (API 흐름은 Clock 한 개만 쓰므로 과거 시각 데이터를 만들기 어렵다). [cleanUp]이 만든 것만 지운다.
 */
class DbFixture(
	private val jdbc: JdbcTemplate,
	private val userService: UserService,
	private val places: PlaceRepository,
	private val categories: PlaceCategoryRepository,
) {
	private val users = mutableListOf<Long>()
	private val placeIds = mutableListOf<Long>()
	private val seq = AtomicInteger((System.nanoTime() % 100000).toInt())

	/** 온보딩을 마친 활성 사용자(기본). */
	fun user(nickname: String? = null, onboarded: Boolean = true, withdrawn: Boolean = false): Long {
		val n = seq.incrementAndGet()
		val id = userService.createEmailUser("fx$n@example.com", "x", nickname ?: "fx$n").id!!
		users += id
		jdbc.update("UPDATE users SET onboarding_completed = ? WHERE id = ?", onboarded, id)
		if (withdrawn) jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?", id)
		return id
	}

	fun place(category: String = "CAFE", name: String = "장소", reward: Int? = null, active: Boolean = true): Long {
		val n = seq.incrementAndGet()
		val c = categories.findAll().first { it.code == category }
		val place = places.saveAndFlush(
			Place(
				category = c, name = "$name$n", address = "서울 종로구 $name$n", roadAddress = null, phone = null,
				lat = 37.58, lng = 126.98, radiusM = 100, notifyRadiusM = 200, rewardPoint = reward,
				kakaoPlaceId = "fx-$n", kakaoPlaceUrl = "https://example.com/fx$n", hotType = null, active = active,
			),
		)
		return place.id!!.also { placeIds += it }
	}

	/**
	 * [at](한국 시간)에 한 인증과 QUEST_COMPLETE 내역을 넣는다. photoBonusAt이 있으면 그 시각에 PHOTO_BONUS(50)도 넣는다.
	 * 반환값은 completion id. (user, place, 날짜)가 겹치면 DB 유니크에 걸리니 날짜나 장소를 다르게 줘야 한다.
	 */
	fun completion(
		userId: Long,
		placeId: Long,
		at: LocalDateTime,
		point: Int = 100,
		firstVisit: Boolean = true,
		photoBonusAt: LocalDateTime? = null,
	): Long {
		jdbc.update(
			"INSERT INTO completions (user_id, place_id, completion_date, completed_at, first_visit, earned_point, lat, lng, accuracy_m) " +
				"VALUES (?, ?, ?, ?, ?, ?, 37.58, 126.98, 5.0)",
			userId, placeId, at.toLocalDate(), at, firstVisit, point,
		)
		val id = jdbc.queryForObject(
			"SELECT id FROM completions WHERE user_id = ? AND place_id = ? AND completion_date = ?",
			Long::class.java, userId, placeId, at.toLocalDate(),
		)!!
		jdbc.update(
			"INSERT INTO point_histories (user_id, completion_id, place_id, type, point, earned_at) VALUES (?, ?, ?, 'QUEST_COMPLETE', ?, ?)",
			userId, id, placeId, point, at,
		)
		if (photoBonusAt != null) {
			jdbc.update(
				"INSERT INTO point_histories (user_id, completion_id, place_id, type, point, earned_at) VALUES (?, ?, ?, 'PHOTO_BONUS', 50, ?)",
				userId, id, placeId, photoBonusAt,
			)
		}
		return id
	}

	fun cleanUp() {
		for (id in users) {
			jdbc.update("DELETE FROM point_histories WHERE user_id = ?", id)
			jdbc.update("DELETE FROM completions WHERE user_id = ?", id)
		}
		for (id in placeIds) jdbc.update("DELETE FROM places WHERE id = ?", id)
		for (id in users) jdbc.update("DELETE FROM users WHERE id = ?", id)
		users.clear()
		placeIds.clear()
	}
}
