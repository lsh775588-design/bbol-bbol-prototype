package com.bbolbbol.server.global.security.jwt

import kotlin.test.Test
import kotlin.test.assertFailsWith

class JwtPropertiesTest {

	@Test
	fun `JWT_SECRET이 설정되지 않으면 서버 시작을 막는다`() {
		// application.yml의 ${JWT_SECRET}이 치환되지 않으면 이 문자열 그대로 들어옴
		assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "\${JWT_SECRET}") }
	}

	@Test
	fun `32바이트보다 짧은 키는 거절한다`() {
		assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "too-short-secret") }
		assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "") }
	}

	@Test
	fun `32바이트 이상이면 통과한다`() {
		JwtProperties(secret = "a".repeat(32))
	}
}
