package com.bbolbbol.server.contract

import com.bbolbbol.server.contract.ContractSupport.Golden
import com.bbolbbol.server.contract.ContractSupport.RequestSpec
import com.bbolbbol.server.support.AuthFlowTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.time.Duration

/**
 * 앱-서버 계약 테스트 중 상태를 바꾸는 인증 흐름(이메일 인증, 비밀번호 재설정, 재발급, 로그아웃, 카카오, 탈퇴).
 * 메일 기록, 인메모리 저장소, 가짜 카카오가 있는 [AuthFlowTest] 틀에서 실제 흐름(발송 -> 확인 -> 토큰)을 거쳐 표본을 만든다.
 * 골든 규칙은 [ContractGoldenTest]와 같다(docs/contract-testing.md). 이 클래스가 맡는 id는 [ContractSupport.authFlowIds].
 */
class ContractAuthFlowTest : AuthFlowTest() {

	private val touched = linkedSetOf<String>()
	private val mapper = ContractSupport.mapper

	@AfterEach
	fun removeUsers() {
		jdbc.update("DELETE FROM user_interests")
		jdbc.update("DELETE FROM users")
	}

	private class Res(val status: Int, val body: String) {
		val node: JsonNode by lazy { ContractSupport.mapper.readTree(body) }
		val code: String get() = node.get("code").asString()
	}

	private fun call(spec: RequestSpec, bearer: String? = null, body: String? = spec.bodyJson()): Res {
		val b = request(HttpMethod.valueOf(spec.method), spec.path)
		for ((k, v) in spec.query()) b.param(k, v)
		if (bearer != null) b.header("Authorization", "Bearer $bearer")
		if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body)
		val r = mvc.perform(b).andReturn().response
		return Res(r.status, r.getContentAsString(Charsets.UTF_8))
	}

	private fun setBody(body: String, key: String, value: Any?): String {
		val n = mapper.readTree(body) as ObjectNode
		n.set(key, mapper.valueToTree<JsonNode>(value))
		return mapper.writeValueAsString(n)
	}

	private fun golden(spec: RequestSpec, case: String, status: Int, code: String, vararg samples: Res) {
		val endpoint = "${spec.method} ${spec.path}"
		for (s in samples) {
			if (s.status != status || s.code != code) {
				throw AssertionError("[$endpoint / $case] 기대 HTTP $status $code, 실제 HTTP ${s.status} ${s.body}")
			}
		}
		touched += "${spec.id}.$case.json"
		val shape = ContractSupport.shapeOf(*samples.map { it.body }.toTypedArray())
		ContractSupport.verify(spec.id, Golden(endpoint, case, status, code, shape))?.let { throw AssertionError(it) }
	}

	@TestFactory
	fun authFlowContracts(): List<DynamicTest> {
		val steps = mutableListOf<DynamicTest>()
		fun step(name: String, block: () -> Unit) { steps += DynamicTest.dynamicTest(name, block) }

		val send = ContractSupport.request("auth-email-verifications")
		val confirm = ContractSupport.request("auth-email-verifications-confirm")
		val reset = ContractSupport.request("auth-password-reset")
		val reissue = ContractSupport.request("auth-reissue")
		val logout = ContractSupport.request("auth-logout")
		val kakaoSpec = ContractSupport.request("auth-kakao")
		val delete = ContractSupport.request("users-me-delete")

		val evEmail = send.body!!.get("email").asString()

		step("auth-email-verifications") {
			golden(send, "success", 200, "COMMON200", call(send))
			golden(send, "error-EMAIL429", 429, "EMAIL429", call(send)) // 1분 안에 다시 보냄
			golden(send, "error-COMMON400", 400, "COMMON400", call(send, body = send.bodyWithout("email")))
			registerEmailUser("contract-reg@example.com", "계약가입자")
			golden(send, "error-USER409", 409, "USER409", call(send, body = setBody(send.bodyJson()!!, "email", "contract-reg@example.com")))
			mail.failing = true
			golden(send, "error-EMAIL502", 502, "EMAIL502", call(send, body = setBody(send.bodyJson()!!, "email", "contract-fail@example.com")))
			mail.failing = false
		}

		step("auth-email-verifications-confirm") {
			// 위 단계에서 evEmail로 보낸 코드가 아직 유효하다
			val real = mail.lastCode(evEmail)
			val wrong = if (real == "123456") "000000" else "123456"
			golden(confirm, "error-EMAIL4001", 400, "EMAIL4001", call(confirm, body = setBody(confirm.bodyJson()!!, "code", wrong)))
			golden(confirm, "error-COMMON400", 400, "COMMON400", call(confirm, body = confirm.bodyWithout("code")))
			golden(confirm, "success", 200, "COMMON200", call(confirm, body = setBody(confirm.bodyJson()!!, "code", real)))
			// 5번 틀리면 코드 무효(EMAIL4002)
			val ev2 = "contract-ev2@example.com"
			call(send, body = setBody(send.bodyJson()!!, "email", ev2))
			val wrong2 = if (mail.lastCode(ev2) == "123456") "000000" else "123456"
			val wrongBody = setBody(setBody(confirm.bodyJson()!!, "email", ev2), "code", wrong2)
			repeat(4) { call(confirm, body = wrongBody) }
			golden(confirm, "error-EMAIL4002", 400, "EMAIL4002", call(confirm, body = wrongBody))
		}

		step("auth-password-reset") {
			val email = "contract-pw@example.com"
			registerEmailUser(email, "계약비번")
			advance(Duration.ofSeconds(61)) // 이메일 하나에 1분 쿨다운
			val token = verifiedToken(email, "PASSWORD_RESET")
			golden(reset, "error-COMMON400", 400, "COMMON400", call(reset, body = setBody(reset.bodyJson()!!, "newPassword", "weak")))
			golden(reset, "error-EMAIL4003", 400, "EMAIL4003", call(reset)) // 인증 흐름을 거치지 않은 토큰
			golden(reset, "success", 200, "COMMON200", call(reset, body = setBody(reset.bodyJson()!!, "verificationToken", token)))
		}

		step("auth-reissue") {
			val s = registerEmailUser("contract-ri@example.com", "계약재발급")
			golden(reissue, "success", 200, "COMMON200", call(reissue, body = setBody(reissue.bodyJson()!!, "refreshToken", s.refreshToken)))
			golden(reissue, "error-AUTH4012", 401, "AUTH4012", call(reissue))
			golden(reissue, "error-COMMON400", 400, "COMMON400", call(reissue, body = "{}"))
		}

		step("auth-logout") {
			val s = registerEmailUser("contract-lo@example.com", "계약로그아웃")
			golden(logout, "success", 200, "COMMON200", call(logout, s.accessToken))
		}

		step("auth-kakao") {
			val newUser = call(kakaoSpec)
			val noSuggestion = call(kakaoSpec, body = setBody(kakaoSpec.bodyJson()!!, "kakaoAccessToken", "DEV-KAKAO|9002||가"))
			golden(kakaoSpec, "success", 200, "COMMON200", newUser, noSuggestion)
			golden(kakaoSpec, "error-KAKAO401", 401, "KAKAO401", call(kakaoSpec, body = setBody(kakaoSpec.bodyJson()!!, "kakaoAccessToken", "garbage")))
			golden(kakaoSpec, "error-KAKAO502", 502, "KAKAO502", call(kakaoSpec, body = setBody(kakaoSpec.bodyJson()!!, "kakaoAccessToken", "DEV-KAKAO-DOWN")))
			registerEmailUser("contract-kk@example.com", "계약카카오")
			golden(
				kakaoSpec, "error-USER4093", 409, "USER4093",
				call(kakaoSpec, body = setBody(kakaoSpec.bodyJson()!!, "kakaoAccessToken", "DEV-KAKAO|9003|contract-kk@example.com|카카오")),
			)
			golden(kakaoSpec, "error-COMMON400", 400, "COMMON400", call(kakaoSpec, body = "{}"))
		}

		step("users-me-delete") {
			val s = registerEmailUser("contract-del@example.com", "계약탈퇴")
			golden(delete, "success", 200, "COMMON200", call(delete, s.accessToken))
		}

		step("AUTH401: 토큰 없이 호출") {
			for (spec in listOf(logout, delete)) {
				val r = call(spec)
				if (r.status != 401 || r.code != "AUTH401") {
					throw AssertionError("[${spec.method} ${spec.path}] 토큰 없이 호출했는데 AUTH401이 아니다: HTTP ${r.status} ${r.body}")
				}
			}
			for (spec in ContractSupport.authFlowIds.map { ContractSupport.request(it) }.filter { !it.auth }) {
				val r = call(spec)
				if (r.status == 401 && r.code == "AUTH401") throw AssertionError("[${spec.method} ${spec.path}] auth=false 인데 AUTH401")
			}
		}

		step("고아 골든 없음(인증 흐름)") {
			val onDisk = Files.list(ContractSupport.responsesDir).use { s -> s.map { it.fileName.toString() }.toList() }
			val orphans = onDisk.filter { it.substringBefore('.') in ContractSupport.authFlowIds && it !in touched }
			if (orphans.isNotEmpty()) {
				if (ContractSupport.updateMode) {
					orphans.forEach { Files.delete(ContractSupport.responsesDir.resolve(it)) }
				} else {
					throw AssertionError("테스트가 더 이상 만들지 않는 응답 골든: $orphans (엔드포인트를 지웠으면 -Dcontract.update=true 로 정리)")
				}
			}
		}
		return steps
	}
}
