package com.bbolbbol.server.domain.user

import com.bbolbbol.server.domain.user.dto.NicknamePolicy
import com.bbolbbol.server.domain.user.entity.User
import com.bbolbbol.server.domain.user.exception.UserErrorCode
import com.bbolbbol.server.domain.user.repository.UserRepository
import com.bbolbbol.server.domain.user.service.ForbiddenWordFilter
import com.bbolbbol.server.domain.user.service.UserService
import com.bbolbbol.server.global.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.core.io.ClassPathResource
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional

class NicknameRulesTest {

	@ParameterizedTest
	@ValueSource(strings = ["뽈뽈", "뽈뽈이", "abc", "Abc123", "가나다라마바사아자차", "ab"])
	@DisplayName("형식 통과: 2~10자 한글/영문/숫자")
	fun validFormat(nickname: String) {
		assertThat(NicknamePolicy.isValidFormat(nickname)).isTrue()
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "a", "가", "가나다라마바사아자차카", "뽈 뽈", "뽈뽈!", "ㅋㅋㅋ", "뽈뽈😀", "abc_d", " 뽈뽈"])
	@DisplayName("형식 위반: 길이, 공백, 특수문자, 자모, 이모지")
	fun invalidFormat(nickname: String) {
		assertThat(NicknamePolicy.isValidFormat(nickname)).isFalse()
	}

	@Test
	@DisplayName("null 닉네임은 형식 위반")
	fun nullInvalid() {
		assertThat(NicknamePolicy.isValidFormat(null)).isFalse()
	}

	@Test
	@DisplayName("금칙어: 포함 여부, 대소문자, 공백/특수문자 우회 모두 걸린다")
	fun forbiddenWords() {
		val f = ForbiddenWordFilter(listOf("금지", "Bad Word", "# 아님", ""))
		assertThat(f.containsForbiddenWord("금지어")).isTrue()
		assertThat(f.containsForbiddenWord("착한금지")).isTrue()
		assertThat(f.containsForbiddenWord("BADWORD")).isTrue()
		assertThat(f.containsForbiddenWord("b a d-w.o_r d")).isTrue()
		assertThat(f.containsForbiddenWord("금 지")).isTrue()
		assertThat(f.containsForbiddenWord("금.지")).isTrue()
		assertThat(f.containsForbiddenWord("깨끗한이름")).isFalse()
		assertThat(f.containsForbiddenWord(null)).isFalse()
	}

	@Test
	@DisplayName("NFKC 정규화: 전각 문자로 쓴 금칙어도 걸린다")
	fun nfkcNormalization() {
		val f = ForbiddenWordFilter(listOf("admin"))
		assertThat(f.containsForbiddenWord("ａｄｍｉｎ")).isTrue()
		assertThat(f.containsForbiddenWord("ＡＤＭＩＮ１")).isTrue()
	}

	@Test
	@DisplayName("리소스 파일 금칙어 목록을 읽는다(주석과 빈 줄 제외)")
	fun loadsResourceFile() {
		val f = ForbiddenWordFilter(ClassPathResource("forbidden-words.txt"))
		assertThat(f.containsForbiddenWord("관리자")).isTrue()
		assertThat(f.containsForbiddenWord("Admin")).isTrue()
		assertThat(f.containsForbiddenWord("PLACEHOLDER")).isFalse()
		assertThat(f.containsForbiddenWord("뽈뽈이")).isFalse()
	}

	@Test
	@DisplayName("30일 규칙: 한 번도 안 바꿨으면 가능, 29일 23시간은 불가, 정확히 30일이면 가능")
	fun thirtyDayRule() {
		val fresh = User.createEmailUser("a@a.com", "h", "처음닉")
		assertThat(fresh.nextNicknameChangeableAt()).isNull()
		assertThat(fresh.canChangeNickname(NOW)).isTrue()

		val u = User.createEmailUser("a@a.com", "h", "처음닉")
		u.changeNickname("바뀐닉", NOW.minusDays(30).plusHours(1))
		assertThat(u.canChangeNickname(NOW)).isFalse()
		assertThat(u.nextNicknameChangeableAt()).isEqualTo(NOW.plusHours(1))

		val exact = User.createEmailUser("a@a.com", "h", "처음닉")
		exact.changeNickname("바뀐닉", NOW.minusDays(30))
		assertThat(exact.canChangeNickname(NOW)).isTrue()
	}

	private fun service(repo: UserRepository) = UserService(repo, ForbiddenWordFilter(listOf("금지")), CLOCK)

	@Test
	@DisplayName("서비스: 고정 Clock 기준 nextChangeableAt = 변경 시각 + 30일, USER4002 data에 담긴다")
	fun serviceUsesClock() {
		val repo = mock(UserRepository::class.java)
		val u = User.createEmailUser("a@a.com", "h", "처음닉")
		`when`(repo.findById(1L)).thenReturn(Optional.of(u))
		`when`(repo.existsByNicknameIgnoreCase("새닉네임")).thenReturn(false)
		`when`(repo.saveAndFlush(ArgumentMatchers.any(User::class.java))).thenAnswer { it.getArgument<User>(0) }

		val changed = service(repo).changeNickname(1L, "새닉네임")
		assertThat(changed.nicknameChangedAt).isEqualTo(NOW)
		assertThat(changed.nextNicknameChangeableAt()).isEqualTo(NOW.plusDays(30))

		val e = assertThrows(BusinessException::class.java) { service(repo).changeNickname(1L, "또바꿈") }
		assertThat(e.errorCode).isEqualTo(UserErrorCode.NICKNAME_CHANGE_LIMIT)
		assertThat(e.data).isEqualTo(mapOf("nextChangeableAt" to NOW.plusDays(30)))
	}

	@Test
	@DisplayName("서비스: 금칙어는 30일/중복 검사보다 먼저, 중복은 저장 전에 거부")
	fun serviceOrder() {
		val repo = mock(UserRepository::class.java)
		val u = User.createEmailUser("a@a.com", "h", "처음닉")
		`when`(repo.findById(1L)).thenReturn(Optional.of(u))
		`when`(repo.existsByNicknameIgnoreCase("있는닉네임")).thenReturn(true)

		val forbidden = assertThrows(BusinessException::class.java) { service(repo).changeNickname(1L, "금지닉") }
		assertThat(forbidden.errorCode).isEqualTo(UserErrorCode.NICKNAME_FORBIDDEN_WORD)
		val duplicated = assertThrows(BusinessException::class.java) { service(repo).changeNickname(1L, "있는닉네임") }
		assertThat(duplicated.errorCode).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED)
		verify(repo, never()).saveAndFlush(ArgumentMatchers.any(User::class.java))
	}

	@Test
	@DisplayName("ErrorCode 번호는 명세 그대로(USER404, USER409, USER4001, USER4002, USER4092, USER4093)")
	fun errorCodeNumbers() {
		assertThat(UserErrorCode.entries.map { it.code })
			.containsExactly("USER404", "USER409", "USER4001", "USER4002", "USER4092", "USER4093")
		assertThat(UserErrorCode.entries.map { it.status.value() }).containsExactly(404, 409, 400, 400, 409, 409)
	}

	companion object {
		val KST: ZoneId = ZoneId.of("Asia/Seoul")
		val CLOCK: Clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), KST) // 2026-11-10T12:00 KST
		val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
	}
}
