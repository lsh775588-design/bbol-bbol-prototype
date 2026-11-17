package com.bbolbbol.server.domain.user

import com.bbolbbol.server.domain.auth.service.RefreshTokenService
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.domain.user.service.UserWithdrawalService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.support.AuthFlowTest
import com.bbolbbol.server.support.MutableClockConfig
import com.bbolbbol.server.support.RecordingEmailConfig
import com.bbolbbol.server.support.WithdrawalListenersConfig
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.LocalDateTime

@Import(MutableClockConfig::class, RecordingEmailConfig::class, WithdrawalListenersConfig::class)
class WithdrawalApiTest : AuthFlowTest() {

	@Autowired lateinit var userService: UserService
	@Autowired lateinit var withdrawalService: UserWithdrawalService
	@Autowired lateinit var recorder: WithdrawalListenersConfig.Recorder

	@BeforeEach
	fun clearRecorder() = recorder.userIds.clear()

	@Test
	@DisplayName("탈퇴 성공: 200, 명세 문구, data null. 행은 남고 개인정보(이메일, 닉네임, 비밀번호, 카카오 ID, 연령대, 성별)는 지워지며 withdrawn_at은 Clock 시각")
	fun withdrawErasesPersonalData() {
		val s = registerEmailUser("bye@example.com", "탈퇴맨")
		patchJson(
			"/users/me/onboarding",
			mapOf("ageGroup" to "TWENTIES", "gender" to "FEMALE", "interests" to listOf("CAFE", "LIBRARY")),
			s.bearer,
		).andExpect(status().isOk)
		assertThat(count("SELECT COUNT(*) FROM user_interests")).isEqualTo(2)

		deleteAuth("/users/me", s.bearer)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."))
			.andExpect(jsonPath("$.data").value(nullValue()))

		val row = jdbc.queryForMap(
			"SELECT email, nickname, password_hash, kakao_id, age_group, gender, status, withdrawn_at, onboarding_completed FROM users WHERE id = ?",
			s.userId,
		)
		assertThat(row["status"]).isEqualTo("WITHDRAWN")
		for (erased in listOf("email", "nickname", "password_hash", "kakao_id", "age_group", "gender")) {
			assertThat(row[erased]).`as`(erased).isNull()
		}
		assertThat(row["withdrawn_at"].toString().replace(' ', 'T')).startsWith("2026-11-10T12:00:00")
		assertThat(count("SELECT COUNT(*) FROM user_interests")).isZero()
		assertThat(refreshStore.find(s.userId)).isNull()
		assertThat(userService.getDisplayNickname(s.userId)).isEqualTo("탈퇴한 사용자")
		assertThat(userService.isActive(s.userId)).isFalse()
		assertThat(recorder.userIds).containsExactly(s.userId)
	}

	@Test
	@DisplayName("탈퇴 후: 이미 받은 access token은 AUTH401, 같은 토큰으로 다시 탈퇴해도 AUTH401, refresh는 AUTH4012, 로그인은 AUTH4011")
	fun afterWithdrawal() {
		val s = registerEmailUser("after@example.com", "탈퇴후")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		getAuth("/users/me", s.bearer).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		deleteAuth("/users/me", s.bearer).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
		postJson("/auth/reissue", mapOf("refreshToken" to s.refreshToken)).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4012"))
		postJson("/auth/login", mapOf("email" to "after@example.com", "password" to PW)).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011"))
	}

	@Test
	@DisplayName("USER404: 이미 탈퇴했거나 없는 사용자의 탈퇴 처리(서비스 직접 호출, HTTP로는 토큰 단계에서 AUTH401이 먼저다)")
	fun alreadyWithdrawn() {
		val s = registerEmailUser("twice@example.com", "두번탈퇴")
		withdrawalService.withdraw(s.userId)
		val e = assertThrows<BusinessException> { withdrawalService.withdraw(s.userId) }
		assertThat(e.errorCode.code).isEqualTo("USER404")
		assertThrows<BusinessException> { withdrawalService.withdraw(99999L) }
	}

	@Test
	@DisplayName("AUTH401: 토큰 없이 탈퇴 불가")
	fun requiresAuth() {
		deleteAuth("/users/me").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("AUTH401"))
	}

	@Test
	@DisplayName("같은 이메일, 같은 닉네임으로 바로 다시 가입할 수 있고 새 계정이다(옛 행은 그대로 WITHDRAWN)")
	fun reSignupWithSameEmailAndNickname() {
		val old = registerEmailUser("again@example.com", "다시가입")
		deleteAuth("/users/me", old.bearer).andExpect(status().isOk)
		mvc.perform(
			org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/email-availability").param("email", "again@example.com"),
		).andExpect(jsonPath("$.data.available").value(true))
		mvc.perform(
			org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/nickname-availability").param("nickname", "다시가입"),
		).andExpect(jsonPath("$.data.available").value(true))

		advance(Duration.ofSeconds(61))
		val fresh = registerEmailUser("again@example.com", "다시가입")
		assertThat(fresh.userId).isNotEqualTo(old.userId)
		assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(2)
		assertThat(count("SELECT COUNT(*) FROM users WHERE status = 'WITHDRAWN'")).isEqualTo(1)
		getAuth("/users/me", fresh.bearer).andExpect(status().isOk).andExpect(jsonPath("$.data.email").value("again@example.com"))
		// 탈퇴한 여러 계정이 NULL 이메일, 닉네임으로 공존해도 유니크 제약에 걸리지 않는다
		deleteAuth("/users/me", fresh.bearer).andExpect(status().isOk)
		assertThat(count("SELECT COUNT(*) FROM users WHERE email IS NULL AND nickname IS NULL")).isEqualTo(2)
	}

	@Test
	@DisplayName("카카오 사용자 탈퇴: 카카오 연결 끊기를 kakaoId로 호출하고, 같은 카카오 계정으로 다시 로그인하면 새 계정이 만들어진다")
	fun kakaoUserWithdrawal() {
		val s = kakaoSession("DEV-KAKAO|8001|kw@example.com|카카오탈퇴")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		assertThat(kakao.unlinkedIds).containsExactly("8001")
		assertThat(jdbc.queryForObject("SELECT kakao_id FROM users WHERE id = ?", String::class.java, s.userId)).isNull()
		val again = kakaoLogin("DEV-KAKAO|8001|kw@example.com|카카오탈퇴").andExpect(status().isOk)
		val node = tree(again).get("data")
		assertThat(node.get("userId").asLong()).isNotEqualTo(s.userId)
		assertThat(node.get("isNewUser").asBoolean()).isTrue()
	}

	@Test
	@DisplayName("카카오 연결 끊기가 실패해도 탈퇴는 완료된다(로그만 남김)")
	fun unlinkFailureDoesNotBlockWithdrawal() {
		val s = kakaoSession("DEV-KAKAO|unlink-fail-1|uf@example.com|연결끊기실패")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String::class.java, s.userId)).isEqualTo("WITHDRAWN")
		assertThat(kakao.unlinkedIds).isEmpty()
		assertThat(refreshStore.find(s.userId)).isNull()
	}

	@Test
	@DisplayName("이메일 가입자는 카카오 연결 끊기를 호출하지 않는다")
	fun emailUserNoUnlink() {
		val s = registerEmailUser("nounlink@example.com", "연결없음")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		assertThat(kakao.unlinkedIds).isEmpty()
	}

	@Test
	@DisplayName("탈퇴 이벤트 리스너가 예외를 던져도 탈퇴 응답은 200이다")
	fun listenerFailureIsSwallowed() {
		val s = registerEmailUser("listener@example.com", "리스너실패")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		assertThat(recorder.userIds).contains(s.userId)
	}

	@Test
	@DisplayName("탈퇴 시각은 Clock 기준이다(Clock을 옮기면 withdrawn_at이 따라간다)")
	fun withdrawnAtUsesClock() {
		val s = registerEmailUser("clock@example.com", "시계맨")
		clock.setKst("2026-12-25T09:30:15")
		withdrawalService.withdraw(s.userId)
		val stored = jdbc.queryForObject("SELECT withdrawn_at FROM users WHERE id = ?", java.sql.Timestamp::class.java, s.userId)!!
		assertThat(stored.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 12, 25, 9, 30, 15))
	}

	@Test
	@DisplayName("RefreshTokenService.delete와 탈퇴 정리가 사용자 단위다(다른 사용자의 로그인은 유지)")
	fun otherUsersUntouched() {
		val a = registerEmailUser("a1@example.com", "사용자가")
		val b = registerEmailUser("b1@example.com", "사용자나")
		deleteAuth("/users/me", a.bearer).andExpect(status().isOk)
		assertThat(refreshStore.find(b.userId)).isEqualTo(RefreshTokenService.hash(b.refreshToken))
		getAuth("/users/me", b.bearer).andExpect(status().isOk)
	}
}
