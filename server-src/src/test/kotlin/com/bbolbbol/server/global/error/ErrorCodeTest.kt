package com.bbolbbol.server.global.error

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 팀 저장소는 에러 코드를 도메인별 enum으로 나눠서 관리함
 * 새 도메인의 enum을 만들면 all에 추가해서 코드 중복과 형식을 함께 검사할 것
 */
class ErrorCodeTest {

	private val all: List<ErrorCode> = CommonErrorCode.entries + FileErrorCode.entries

	@Test
	fun `모든 에러 코드 문자열은 유일하다`() {
		val duplicates = all.groupBy { it.code }.filterValues { it.size > 1 }.keys
		assertTrue(duplicates.isEmpty(), "중복된 에러 코드: $duplicates")
	}

	@Test
	fun `코드는 대문자 도메인과 숫자로 이루어지고 메시지는 비어 있지 않다`() {
		all.forEach {
			assertTrue(Regex("[A-Z]+[0-9]{3,4}").matches(it.code), "형식 오류: ${it.code}")
			assertTrue(it.message.isNotBlank(), "메시지 없음: ${it.code}")
		}
	}

	@Test
	fun `공통 코드는 노션 명세의 상태 코드와 매핑된다`() {
		val expected = mapOf(
			"AUTH401" to 401, "AUTH403" to 403, "COMMON400" to 400, "COMMON404" to 404,
			"COMMON405" to 405, "COMMON415" to 415, "COMMON500" to 500, "FILE4001" to 400, "FILE413" to 413,
		)
		assertEquals(expected, all.associate { it.code to it.status.value() })
	}
}
