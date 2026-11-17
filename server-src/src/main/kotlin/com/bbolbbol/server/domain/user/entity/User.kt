package com.bbolbbol.server.domain.user.entity

import com.bbolbbol.server.global.common.BaseTimeEntity
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

/** users 테이블. 다른 도메인은 이 엔티티가 아니라 UserService를 쓴다. */
@Entity
@Table(name = "users")
class User private constructor(
	@Column(name = "email")
	var email: String?,

	@Column(name = "password_hash")
	var passwordHash: String?,

	// enum은 항상 평범한 VARCHAR로 저장함(DB 네이티브 ENUM 매핑과 스키마 검증 차이를 피함)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "login_type", nullable = false)
	var loginType: LoginType,

	@Column(name = "nickname")
	var nickname: String?,

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false)
	var status: UserStatus,

	@Column(name = "onboarding_completed", nullable = false)
	var onboardingCompleted: Boolean,
) : BaseTimeEntity() {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@Column(name = "kakao_id")
	var kakaoId: String? = null
		protected set

	/** 가입/온보딩 때 정한 닉네임은 null(제한에 포함하지 않음). */
	@Column(name = "nickname_changed_at")
	var nicknameChangedAt: LocalDateTime? = null
		protected set

	@Column(name = "withdrawn_at")
	var withdrawnAt: LocalDateTime? = null
		protected set

	/** 온보딩 연령대. null = 아직 입력 전. 추천에만 쓰고 다른 사용자에게 노출하지 않는다. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "age_group")
	var ageGroup: AgeGroup? = null
		protected set

	/** 온보딩 성별. null = 아직 입력 전. 추천에만 쓰고 다른 사용자에게 노출하지 않는다. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "gender")
	var gender: Gender? = null
		protected set

	/** 마지막 변경 시각 + 30일. 한 번도 안 바꿨으면 null(바로 변경 가능). */
	fun nextNicknameChangeableAt(): LocalDateTime? =
		nicknameChangedAt?.plusDays(NICKNAME_CHANGE_INTERVAL_DAYS)

	fun canChangeNickname(now: LocalDateTime): Boolean {
		val next = nextNicknameChangeableAt()
		return next == null || !now.isBefore(next)
	}

	fun changeNickname(newNickname: String, now: LocalDateTime) {
		nickname = newNickname
		nicknameChangedAt = now
	}

	fun isActive(): Boolean = status == UserStatus.ACTIVE

	/** 온보딩에서 정하는 첫 닉네임. 30일 변경 제한에 포함하지 않으므로 nicknameChangedAt은 건드리지 않는다. */
	fun setInitialNickname(newNickname: String) {
		nickname = newNickname
	}

	fun linkKakao(id: String) {
		kakaoId = id
	}

	fun changePasswordHash(newHash: String) {
		passwordHash = newHash
	}

	fun applyOnboarding(ageGroup: AgeGroup?, gender: Gender?) {
		ageGroup?.let { this.ageGroup = it }
		gender?.let { this.gender = it }
	}

	fun completeOnboarding() {
		onboardingCompleted = true
	}

	/**
	 * 탈퇴(soft delete): 상태만 바꾸는 게 아니라 개인정보를 즉시 지운다.
	 * email, nickname, kakao_id는 NULL로 만들어 UNIQUE 제약에서 빠지므로 같은 이메일, 닉네임, 카카오 계정으로 바로 다시 가입할 수 있다.
	 * (PostgreSQL과 H2 모두 UNIQUE 컬럼에서 NULL은 서로 다른 값으로 취급한다.)
	 */
	fun withdraw(now: LocalDateTime) {
		status = UserStatus.WITHDRAWN
		withdrawnAt = now
		email = null
		nickname = null
		passwordHash = null
		kakaoId = null
		nicknameChangedAt = null
		ageGroup = null
		gender = null
	}

	companion object {
		/** 닉네임 변경 간격(일). */
		const val NICKNAME_CHANGE_INTERVAL_DAYS = 30L

		fun createEmailUser(email: String, passwordHash: String, nickname: String): User =
			User(
				email = email,
				passwordHash = passwordHash,
				loginType = LoginType.EMAIL,
				nickname = nickname,
				status = UserStatus.ACTIVE,
				onboardingCompleted = false,
			)

		/** 카카오 가입자. 닉네임은 온보딩에서 받으므로 null. 이메일은 카카오 동의 항목에 동의하지 않았다면 null. */
		fun createKakaoUser(kakaoId: String, email: String?): User =
			User(
				email = email,
				passwordHash = null,
				loginType = LoginType.KAKAO,
				nickname = null,
				status = UserStatus.ACTIVE,
				onboardingCompleted = false,
			).also { it.linkKakao(kakaoId) }
	}
}
