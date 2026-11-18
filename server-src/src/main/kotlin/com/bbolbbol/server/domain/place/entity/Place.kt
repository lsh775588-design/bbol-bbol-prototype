package com.bbolbbol.server.domain.place.entity

import com.bbolbbol.server.global.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "places")
class Place(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	val category: PlaceCategory,

	@Column(nullable = false, length = 100)
	val name: String,

	@Column(nullable = false)
	val address: String,

	@Column(name = "road_address")
	val roadAddress: String?,

	@Column(length = 30)
	val phone: String?,

	/** 위도. PostGIS 도입 전까지 평범한 DOUBLE 컬럼이다. */
	@Column(nullable = false)
	val lat: Double,

	@Column(nullable = false)
	val lng: Double,

	@Column(name = "radius_m", nullable = false)
	val radiusM: Int = 100,

	@Column(name = "notify_radius_m", nullable = false)
	val notifyRadiusM: Int = 200,

	/** null이면 카테고리 기본 점수를 쓴다(장소 값이 카테고리 값을 덮는다, 쟁점 7 가정). */
	@Column(name = "reward_point")
	val rewardPoint: Int? = null,

	@Column(name = "kakao_place_id", nullable = false, length = 30, unique = true)
	val kakaoPlaceId: String,

	@Column(name = "kakao_place_url", nullable = false)
	val kakaoPlaceUrl: String,

	/** 뽈뽈 추천이면 CURATED, 아니면 null. */
	@Enumerated(EnumType.STRING)
	@Column(name = "hot_type", length = 20)
	val hotType: HotType? = null,

	@Column(nullable = false)
	val active: Boolean = true,
) : BaseTimeEntity() {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	/** 첫 방문 기준 점수: 장소 값이 있으면 그것, 없으면 카테고리 기본 점수. */
	fun effectiveRewardPoint(): Int = RewardPolicy.effective(rewardPoint, category.rewardPoint)
}
