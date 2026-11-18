package com.bbolbbol.server.global.common

import com.bbolbbol.server.global.error.CommonErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiResponseTest {

	private val mapper = JsonMapper.builder().build()

	private fun json(value: Any): JsonNode = mapper.valueToTree(value)

	private fun fields(node: JsonNode): Set<String> = node.propertyNames().asSequence().toSet()

	@Test
	fun `ok는 COMMON200이고 필드는 success code message data 네 개뿐이다`() {
		val node = json(ApiResponse.ok(mapOf("k" to 1)))
		assertEquals(setOf("success", "code", "message", "data"), fields(node))
		assertTrue(node["success"].asBoolean())
		assertEquals("COMMON200", node["code"].asString())
		assertEquals(1, node["data"]["k"].asInt())
	}

	@Test
	fun `created는 COMMON201`() {
		val node = json(ApiResponse.created("x"))
		assertEquals("COMMON201", node["code"].asString())
		assertEquals("x", node["data"].asString())
	}

	@Test
	fun `fail은 에러 코드의 코드와 메시지를 쓰고 data를 전달한다`() {
		val node = json(ApiResponse.fail(CommonErrorCode.INVALID_INPUT, mapOf("a" to "b")))
		assertEquals(false, node["success"].asBoolean())
		assertEquals("COMMON400", node["code"].asString())
		assertEquals(CommonErrorCode.INVALID_INPUT.message, node["message"].asString())
		assertEquals("b", node["data"]["a"].asString())
	}

	@Test
	fun `data가 null이어도 data 키는 유지된다`() {
		listOf(
			json(ApiResponse.fail<Any>(CommonErrorCode.UNAUTHORIZED)),
			json(ApiResponse.ok<Any>()),
			json(ApiResponse.created<Any>()),
		).forEach {
			assertTrue(it.has("data"))
			assertTrue(it["data"].isNull)
			assertEquals(4, fields(it).size)
		}
	}

	@Test
	fun `createdResponse는 HTTP 201과 COMMON201 기본 메시지`() {
		val response = ApiResponse.createdResponse(mapOf("id" to 1))
		assertEquals(HttpStatus.CREATED, response.statusCode)
		assertEquals("COMMON201", response.body!!.code)
		assertEquals("생성 성공", response.body!!.message)
	}

	@Test
	fun `createdResponse는 메시지를 바꿀 수 있고 data는 없어도 된다`() {
		val response = ApiResponse.createdResponse<Unit>(message = "회원가입에 성공했습니다.")
		assertEquals(HttpStatus.CREATED, response.statusCode)
		assertEquals("회원가입에 성공했습니다.", response.body!!.message)
		assertNull(response.body!!.data)
	}
}
