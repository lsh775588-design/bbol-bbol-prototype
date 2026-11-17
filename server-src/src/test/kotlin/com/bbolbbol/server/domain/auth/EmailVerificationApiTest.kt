package com.bbolbbol.server.domain.auth

import com.bbolbbol.server.support.AuthFlowTest
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.Base64

class EmailVerificationApiTest : AuthFlowTest() {

	private fun wrongCodeFor(email: String): String {
		val real = mail.lastCode(email)
		return if (real == "000000") "000001" else "000000"
	}

	// ---------- 발송 ----------

	@Test
	@DisplayName("발송 성공: 200, 명세 문구, data {expiresIn:600, resendAvailableIn:60}, 이메일은 소문자로 정규화해서 6자리 코드 메일 1통")
	fun sendSuccess() {
		sendCode("Bbol@Example.COM")
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("인증 코드를 보냈습니다."))
			.andExpect(jsonPath("$.data.expiresIn").value(600))
			.andExpect(jsonPath("$.data.resendAvailableIn").value(60))
		assertThat(mail.mails).hasSize(1)
		assertThat(mail.mails[0].to).isEqualTo("bbol@example.com")
		assertThat(mail.mails[0].text).containsPattern("[0-9]{6}")
	}

	@Test
	@DisplayName("발송 응답 키가 계약과 같다")
	fun sendKeys() {
		val node = tree(sendCode("keys@example.com"))
		assertThat(keysOf(node)).containsExactlyInAnyOrder("success", "code", "message", "data")
		assertThat(keysOf(node.get("data"))).containsExactlyInAnyOrder("expiresIn", "resendAvailableIn")
	}

	@Test
	@DisplayName("발송 COMMON400: 이메일 형식 오류, 없는 purpose, 필드 누락")
	fun sendValidation() {
		sendCode("not-an-email").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("email"))
		sendCode("a@example.com", "LOGIN").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.data.errors[0].field").value("purpose"))
		postJson("/auth/email-verifications", mapOf("purpose" to "SIGNUP")).andExpect(status().isBadRequest)
		postJson("/auth/email-verifications", mapOf("email" to "a@example.com")).andExpect(status().isBadRequest)
		assertThat(mail.mails).isEmpty()
	}

	@Test
	@DisplayName("SIGNUP인데 이미 가입된 이메일은 USER409이고 메일을 보내지 않으며 발송 횟수를 쓰지 않는다")
	fun signupRegisteredEmail() {
		registerEmailUser("taken@example.com", "먼저가입")
		mail.clear()
		sendCode("taken@example.com").andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER409"))
		assertThat(mail.mails).isEmpty()
		// 쿨다운도 쓰지 않았다(가입 때 쓴 1분이 지나면 다른 주소와 똑같이 보낼 수 있는지가 아니라, 409가 429로 바뀌지 않는지 확인)
		sendCode("taken@example.com").andExpect(status().isConflict)
	}

	@Test
	@DisplayName("SIGNUP인데 카카오로 가입한 이메일도 USER409")
	fun signupKakaoRegisteredEmail() {
		kakaoSession("DEV-KAKAO|7001|kakaomail@example.com|카카오")
		sendCode("kakaomail@example.com").andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("USER409"))
	}

	@Test
	@DisplayName("EMAIL429: 1분 안에 다시 보내면 거절, 60초가 지나면 허용")
	fun resendCooldown() {
		sendCode("cool@example.com").andExpect(status().isOk)
		advance(Duration.ofSeconds(59))
		sendCode("cool@example.com").andExpect(status().isTooManyRequests)
			.andExpect(jsonPath("$.code").value("EMAIL429"))
		assertThat(mail.mails).hasSize(1)
		advance(Duration.ofSeconds(1))
		sendCode("cool@example.com").andExpect(status().isOk)
		assertThat(mail.mails).hasSize(2)
	}

	@Test
	@DisplayName("EMAIL429: 하루 5번까지, 6번째는 거절하고 다음 날(KST)에는 다시 허용")
	fun dailyLimit() {
		repeat(5) {
			sendCode("daily@example.com").andExpect(status().isOk)
			advance(Duration.ofSeconds(61))
		}
		sendCode("daily@example.com").andExpect(status().isTooManyRequests)
			.andExpect(jsonPath("$.code").value("EMAIL429"))
		assertThat(mail.mails).hasSize(5)
		clock.setKst("2026-11-11T00:00:05")
		sendCode("daily@example.com").andExpect(status().isOk)
	}

	@Test
	@DisplayName("발송 제한은 purpose와 상관없이 이메일 하나에 건다(SIGNUP 5 + PASSWORD_RESET 5로 우회 불가)")
	fun limitSharedAcrossPurposes() {
		sendCode("both@example.com", "SIGNUP").andExpect(status().isOk)
		advance(Duration.ofSeconds(30))
		sendCode("both@example.com", "PASSWORD_RESET").andExpect(status().isTooManyRequests)
	}

	@Test
	@DisplayName("EMAIL502: 메일 발송 실패는 502이고 쿨다운과 일일 횟수를 쓰지 않아 바로 다시 보낼 수 있다")
	fun sendFailureReleasesPermit() {
		mail.failing = true
		repeat(6) {
			sendCode("fail@example.com").andExpect(status().isBadGateway)
				.andExpect(jsonPath("$.code").value("EMAIL502"))
		}
		mail.failing = false
		sendCode("fail@example.com").andExpect(status().isOk)
		// 못 받은 코드는 남아 있지 않다: 실패 직후의 코드로 확인할 수 없다
		assertThat(mail.mails).hasSize(1)
	}

	// ---------- 확인 ----------

	@Test
	@DisplayName("확인 성공: 200, 명세 문구, data {verificationToken, expiresIn:1800}, 토큰은 이메일에서 유도되지 않는 43자 base64url")
	fun confirmSuccess() {
		sendCode("ok@example.com").andExpect(status().isOk)
		val code = mail.lastCode("ok@example.com")
		val res = confirmCode("ok@example.com", code)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."))
			.andExpect(jsonPath("$.data.expiresIn").value(1800))
		val node = tree(res)
		assertThat(keysOf(node.get("data"))).containsExactlyInAnyOrder("verificationToken", "expiresIn")
		val token = node.get("data").get("verificationToken").asString()
		assertThat(token).matches("[A-Za-z0-9_-]{43}")
		val emailB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("signup:ok@example.com".toByteArray())
		assertThat(token).isNotEqualTo(emailB64).doesNotContain("ok@example.com")
	}

	@Test
	@DisplayName("같은 이메일로 인증을 두 번 거쳐도 토큰이 서로 다르다(추측 불가)")
	fun tokensAreRandom() {
		val t1 = verifiedToken("rand@example.com")
		advance(Duration.ofSeconds(61))
		val t2 = verifiedToken("rand@example.com")
		assertThat(t1).isNotEqualTo(t2)
	}

	@Test
	@DisplayName("발송과 확인의 이메일 대소문자는 구분하지 않는다")
	fun confirmNormalizesEmail() {
		sendCode("Case@Example.com").andExpect(status().isOk)
		confirmCode("CASE@example.com", mail.lastCode("case@example.com")).andExpect(status().isOk)
	}

	@Test
	@DisplayName("EMAIL4001: 틀린 코드는 data.remainingAttempts가 4,3,2,1로 줄고 5번째는 EMAIL4002(무효), 이후엔 맞는 코드도 EMAIL4002")
	fun wrongAttemptsInvalidate() {
		sendCode("wrong@example.com")
		val wrong = wrongCodeFor("wrong@example.com")
		for (remaining in listOf(4, 3, 2, 1)) {
			confirmCode("wrong@example.com", wrong).andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("EMAIL4001"))
				.andExpect(jsonPath("$.data.remainingAttempts").value(remaining))
		}
		confirmCode("wrong@example.com", wrong).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
		confirmCode("wrong@example.com", mail.lastCode("wrong@example.com")).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
	}

	@Test
	@DisplayName("4번 틀린 뒤 맞는 코드는 성공한다(5번째 시도까지 유효)")
	fun correctOnFifthTry() {
		sendCode("fifth@example.com")
		val wrong = wrongCodeFor("fifth@example.com")
		repeat(4) { confirmCode("fifth@example.com", wrong).andExpect(status().isBadRequest) }
		confirmCode("fifth@example.com", mail.lastCode("fifth@example.com")).andExpect(status().isOk)
	}

	@Test
	@DisplayName("EMAIL4002: 10분이 지나면 코드 만료(9분 59초까지는 유효)")
	fun codeExpires() {
		sendCode("exp@example.com")
		val code = mail.lastCode("exp@example.com")
		advance(Duration.ofMinutes(10).minusSeconds(1))
		confirmCode("exp@example.com", code).andExpect(status().isOk)

		sendCode("exp2@example.com")
		val code2 = mail.lastCode("exp2@example.com")
		advance(Duration.ofMinutes(10))
		confirmCode("exp2@example.com", code2).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
	}

	@Test
	@DisplayName("보낸 적 없는 이메일 확인은 EMAIL4002, 코드 형식 오류와 필드 누락은 COMMON400")
	fun confirmErrors() {
		confirmCode("never@example.com", "123456").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
		confirmCode("never@example.com", "12345").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("code"))
		confirmCode("never@example.com", "12345a").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
		postJson("/auth/email-verifications/confirm", mapOf("email" to "a@example.com", "purpose" to "SIGNUP"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("COMMON400"))
		confirmCode("a@example.com", "123456", "BAD").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
	}

	@Test
	@DisplayName("맞는 코드도 한 번만 쓸 수 있다(성공 뒤 같은 코드는 EMAIL4002)")
	fun codeSingleUse() {
		sendCode("once@example.com")
		val code = mail.lastCode("once@example.com")
		confirmCode("once@example.com", code).andExpect(status().isOk)
		confirmCode("once@example.com", code).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
	}

	@Test
	@DisplayName("purpose가 다르면 코드가 통하지 않는다(SIGNUP 코드로 PASSWORD_RESET 확인 불가)")
	fun codeBoundToPurpose() {
		sendCode("purp@example.com", "SIGNUP")
		val code = mail.lastCode("purp@example.com")
		confirmCode("purp@example.com", code, "PASSWORD_RESET").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4002"))
		confirmCode("purp@example.com", code, "SIGNUP").andExpect(status().isOk)
	}

	@Test
	@DisplayName("재발송하면 새 코드로 바뀌고 틀린 횟수가 0으로 돌아간다(옛 코드는 무효)")
	fun resendResetsAttempts() {
		sendCode("resend@example.com")
		val first = mail.lastCode("resend@example.com")
		val wrong = wrongCodeFor("resend@example.com")
		repeat(3) { confirmCode("resend@example.com", wrong).andExpect(status().isBadRequest) }
		advance(Duration.ofSeconds(61))
		sendCode("resend@example.com")
		val second = mail.lastCode("resend@example.com")
		if (first != second) {
			confirmCode("resend@example.com", first).andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.data.remainingAttempts").value(4))
		}
		confirmCode("resend@example.com", second).andExpect(status().isOk)
	}

	// ---------- 토큰을 쓰는 가입 ----------

	@Test
	@DisplayName("전체 흐름: 코드 발송 -> 확인 -> 가입(토큰의 이메일로 가입, 소문자) -> 같은 토큰 재사용은 EMAIL4003")
	fun signupFlowAndTokenSingleUse() {
		val token = verifiedToken("Flow@Example.com")
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "흐름맨"))
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.isNewUser").value(true))
		assertThat(jdbc.queryForObject("SELECT email FROM users", String::class.java)).isEqualTo("flow@example.com")
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "두번째"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
	}

	@Test
	@DisplayName("가입이 닉네임 중복 등으로 실패하면 토큰은 소비되지 않아 닉네임만 바꿔 다시 가입할 수 있다")
	fun tokenSurvivesFailedSignup() {
		registerEmailUser("dupnick@example.com", "겹치는닉")
		val token = verifiedToken("second@example.com")
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "겹치는닉"))
			.andExpect(status().isConflict).andExpect(jsonPath("$.code").value("USER4092"))
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "다른닉네임"))
			.andExpect(status().isCreated)
	}

	@Test
	@DisplayName("EMAIL4003: 토큰 만료(30분), 지어낸 토큰, 이메일에서 유도한 토큰, 빈 토큰")
	fun invalidSignupTokens() {
		val token = verifiedToken("tokexp@example.com")
		advance(Duration.ofMinutes(30))
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "만료됨"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))

		val derived = Base64.getEncoder().encodeToString("signup:forge@example.com".toByteArray())
		for (bad in listOf(derived, "garbage", "a".repeat(43))) {
			postJson("/auth/signup", mapOf("verificationToken" to bad, "password" to PW, "nickname" to "위조됨"))
				.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
		}
		assertThat(count("SELECT COUNT(*) FROM users")).isZero()
	}

	@Test
	@DisplayName("토큰은 29분 59초까지 유효하다")
	fun tokenValidUntilThirtyMinutes() {
		val token = verifiedToken("tokok@example.com")
		advance(Duration.ofMinutes(30).minusSeconds(1))
		postJson("/auth/signup", mapOf("verificationToken" to token, "password" to PW, "nickname" to "아슬아슬"))
			.andExpect(status().isCreated)
	}

	@Test
	@DisplayName("용도가 다른 토큰은 거절하되 토큰을 태우지 않는다(SIGNUP 토큰으로 재설정 불가, 이후 가입은 가능)")
	fun wrongPurposeToken() {
		val signupToken = verifiedToken("wp@example.com", "SIGNUP")
		postJson("/auth/password-reset", mapOf("verificationToken" to signupToken, "newPassword" to "newBbol5678!"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
		postJson("/auth/signup", mapOf("verificationToken" to signupToken, "password" to PW, "nickname" to "용도맞음"))
			.andExpect(status().isCreated)

		registerEmailUser("wp2@example.com", "재설정계정")
		advance(Duration.ofSeconds(61))
		val resetToken = verifiedToken("wp2@example.com", "PASSWORD_RESET")
		postJson("/auth/signup", mapOf("verificationToken" to resetToken, "password" to PW, "nickname" to "엉뚱한용도"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
		postJson("/auth/password-reset", mapOf("verificationToken" to resetToken, "newPassword" to "newBbol5678!"))
			.andExpect(status().isOk)
	}

	// ---------- 비밀번호 재설정 ----------

	@Test
	@DisplayName("비밀번호 재설정 전체 흐름: 새 비밀번호로 로그인, 옛 비밀번호 거절, 모든 기기 로그아웃(refresh AUTH4012), 토큰은 한 번만")
	fun passwordResetFlow() {
		val old = registerEmailUser("reset@example.com", "재설정맨")
		advance(Duration.ofSeconds(61))
		val token = verifiedToken("reset@example.com", "PASSWORD_RESET")
		postJson("/auth/password-reset", mapOf("verificationToken" to token, "newPassword" to "newBbol5678!"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200"))
			.andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."))
			.andExpect(jsonPath("$.data").value(nullValue()))
		postJson("/auth/login", mapOf("email" to "reset@example.com", "password" to "newBbol5678!")).andExpect(status().isOk)
		postJson("/auth/login", mapOf("email" to "reset@example.com", "password" to PW)).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011"))
		// 옛 refresh token은 비밀번호를 바꾸면 끊긴다(위에서 새로 로그인하기 전에 이미 지워졌고, 로그인으로 새 토큰이 생겼을 뿐이다)
		postJson("/auth/reissue", mapOf("refreshToken" to old.refreshToken)).andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4012"))
		postJson("/auth/password-reset", mapOf("verificationToken" to token, "newPassword" to "another9999!"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
	}

	@Test
	@DisplayName("비밀번호 재설정 COMMON400: 비밀번호 규칙 위반과 필드 누락(토큰은 소비되지 않는다)")
	fun passwordResetValidation() {
		registerEmailUser("rv@example.com", "검증맨")
		advance(Duration.ofSeconds(61))
		val token = verifiedToken("rv@example.com", "PASSWORD_RESET")
		postJson("/auth/password-reset", mapOf("verificationToken" to token, "newPassword" to "short1!"))
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("COMMON400"))
			.andExpect(jsonPath("$.data.errors[0].field").value("newPassword"))
		postJson("/auth/password-reset", mapOf("newPassword" to "newBbol5678!"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.data.errors[0].field").value("verificationToken"))
		postJson("/auth/password-reset", mapOf("verificationToken" to token, "newPassword" to "newBbol5678!"))
			.andExpect(status().isOk)
	}

	@Test
	@DisplayName("재설정 토큰을 받은 뒤 탈퇴한 사용자는 비밀번호를 바꿀 수 없다(EMAIL4003)")
	fun resetTokenForWithdrawnUser() {
		val s = registerEmailUser("gone@example.com", "탈퇴맨")
		advance(Duration.ofSeconds(61))
		val token = verifiedToken("gone@example.com", "PASSWORD_RESET")
		deleteAuth("/users/me", s.bearer).andExpect(status().isOk)
		postJson("/auth/password-reset", mapOf("verificationToken" to token, "newPassword" to "newBbol5678!"))
			.andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("EMAIL4003"))
	}

	@Test
	@DisplayName("PASSWORD_RESET: 가입 안 된 이메일도 200이고 메일은 없다. 코드 확인 단계도 가입자와 똑같이 EMAIL4001 -> EMAIL4002로 보인다")
	fun resetUnregisteredLooksSame() {
		registerEmailUser("real@example.com", "진짜계정")
		advance(Duration.ofSeconds(61))
		mail.clear()

		val real = sendCode("real@example.com", "PASSWORD_RESET").andExpect(status().isOk)
		val ghost = sendCode("ghost@example.com", "PASSWORD_RESET").andExpect(status().isOk)
		assertThat(tree(real).get("data").toString()).isEqualTo(tree(ghost).get("data").toString())
		assertThat(mail.mailsTo("real@example.com")).hasSize(1)
		assertThat(mail.mailsTo("ghost@example.com")).isEmpty()

		// 쿨다운(429)도 똑같이 걸린다
		sendCode("real@example.com", "PASSWORD_RESET").andExpect(status().isTooManyRequests)
		sendCode("ghost@example.com", "PASSWORD_RESET").andExpect(status().isTooManyRequests)

		// 틀린 코드의 응답이 같다
		val realWrong = wrongCodeFor("real@example.com")
		for (remaining in listOf(4, 3, 2, 1)) {
			confirmCode("real@example.com", realWrong, "PASSWORD_RESET").andExpect(jsonPath("$.data.remainingAttempts").value(remaining))
			confirmCode("ghost@example.com", "123456", "PASSWORD_RESET").andExpect(status().isBadRequest)
				.andExpect(jsonPath("$.code").value("EMAIL4001"))
				.andExpect(jsonPath("$.data.remainingAttempts").value(remaining))
		}
		confirmCode("real@example.com", realWrong, "PASSWORD_RESET").andExpect(jsonPath("$.code").value("EMAIL4002"))
		confirmCode("ghost@example.com", "123456", "PASSWORD_RESET").andExpect(jsonPath("$.code").value("EMAIL4002"))
	}

	@Test
	@DisplayName("PASSWORD_RESET: 카카오로만 가입한 이메일에는 코드 대신 안내 메일을 보내고 응답은 똑같다")
	fun resetKakaoOnlyEmail() {
		kakaoSession("DEV-KAKAO|7002|kk@example.com|카카오")
		sendCode("kk@example.com", "PASSWORD_RESET").andExpect(status().isOk)
			.andExpect(jsonPath("$.data.expiresIn").value(600))
		assertThat(mail.mailsTo("kk@example.com")).hasSize(1)
		assertThat(mail.mailsTo("kk@example.com")[0].text).contains("카카오로 가입한 계정").doesNotContainPattern("[0-9]{6}")
		confirmCode("kk@example.com", "123456", "PASSWORD_RESET").andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("EMAIL4001"))
	}

	@Test
	@DisplayName("PASSWORD_RESET: 안내 메일 발송 실패는 응답을 바꾸지 않는다(가입 여부 노출 방지)")
	fun kakaoNoticeFailureIsSilent() {
		kakaoSession("DEV-KAKAO|7003|kk3@example.com|카카오")
		mail.failing = true
		sendCode("kk3@example.com", "PASSWORD_RESET").andExpect(status().isOk)
	}
}
