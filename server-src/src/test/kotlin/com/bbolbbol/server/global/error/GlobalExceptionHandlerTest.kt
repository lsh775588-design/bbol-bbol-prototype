package com.bbolbbol.server.global.error

import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import com.bbolbbol.server.support.AllUsersActiveConfig
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 실제 컨텍스트로 GlobalExceptionHandler를 확인함 (SecurityAndErrorResponseTest에 없는 경우 위주)
 * 이미 있는 것: @Valid 바디, BusinessException, 404, 415, 업로드 오류, Spring 4xx
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AllUsersActiveConfig::class) // 토큰의 userId가 users 테이블에 없어도 공통 동작을 검증함
class GlobalExceptionHandlerTest {

	@Autowired
	lateinit var mvc: MockMvc

	@Autowired
	lateinit var jwt: JwtProvider

	private fun bearer() = "Bearer ${jwt.createAccessToken(1L)}"

	@Test
	fun `RequestBody 검증 실패는 필드별 오류 두 개`() {
		mvc.post("/test-support/body") {
			header("Authorization", bearer())
			contentType = MediaType.APPLICATION_JSON
			content = """{"name":"","age":0}"""
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors", hasSize<Any>(2))
			jsonPath("$.data.errors[?(@.field=='name')].reason") { value("이름은 필수입니다.") }
			jsonPath("$.data.errors[?(@.field=='age')].reason") { value("1 이상이어야 합니다.") }
		}
	}

	@Test
	fun `RequestParam 범위 검증 실패는 COMMON400과 파라미터 이름`() {
		mvc.get("/test-support/param?size=99") { header("Authorization", bearer()) }.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors[0].field") { value("size") }
		}
	}

	@Test
	fun `필수 파라미터 누락과 타입 불일치는 COMMON400`() {
		mvc.get("/test-support/param") { header("Authorization", bearer()) }.andExpect {
			status { isBadRequest() }
			jsonPath("$.data.errors[0].field") { value("size") }
		}
		mvc.get("/test-support/param?size=abc") { header("Authorization", bearer()) }.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors[0].field") { value("size") }
		}
	}

	@Test
	fun `깨진 JSON과 본문 누락은 COMMON400`() {
		mvc.post("/test-support/body") {
			header("Authorization", bearer())
			contentType = MediaType.APPLICATION_JSON
			content = "{not json"
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
		}
		mvc.post("/test-support/body") {
			header("Authorization", bearer())
			contentType = MediaType.APPLICATION_JSON
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
		}
	}

	@Test
	fun `BusinessException은 코드와 상태 data를 그대로 응답하고 data가 없으면 null`() {
		mvc.get("/test-support/business?withData=true") { header("Authorization", bearer()) }.andExpect {
			status { isConflict() }
			jsonPath("$.code") { value("SAMPLE4092") }
			jsonPath("$.data.nickname") { value("dup") }
		}
		mvc.get("/test-support/business") { header("Authorization", bearer()) }.andExpect {
			status { isConflict() }
			jsonPath("$.data") { value(null) }
		}
	}

	@Test
	fun `예상 못한 예외는 COMMON500이고 내부 정보를 노출하지 않는다`() {
		mvc.get("/test-support/boom") { header("Authorization", bearer()) }.andExpect {
			status { isInternalServerError() }
			jsonPath("$.success") { value(false) }
			jsonPath("$.code") { value("COMMON500") }
			content { string(not(containsString("SECRET-INTERNAL-DETAIL"))) }
			content { string(not(containsString("IllegalStateException"))) }
		}
	}

	@Test
	fun `허용하지 않는 Content-Type은 COMMON415`() {
		mvc.post("/test-support/json-only") {
			header("Authorization", bearer())
			contentType = MediaType.TEXT_PLAIN
			content = "x"
		}.andExpect {
			status { isUnsupportedMediaType() }
			jsonPath("$.code") { value("COMMON415") }
		}
	}

	@Test
	fun `ResponseStatusException 4xx는 상태를 유지하고 사유를 노출하지 않는다`() {
		mvc.get("/test-support/status/409") { header("Authorization", bearer()) }.andExpect {
			status { isConflict() }
			jsonPath("$.code") { value("COMMON409") }
			content { string(not(containsString("reason-should-not-leak"))) }
		}
	}

	@Test
	fun `ResponseStatusException 5xx는 COMMON500`() {
		mvc.get("/test-support/status/503") { header("Authorization", bearer()) }.andExpect {
			status { isInternalServerError() }
			jsonPath("$.code") { value("COMMON500") }
		}
	}

	@Test
	fun `잘못된 HTTP 메서드는 COMMON405`() {
		mvc.post("/health").andExpect {
			status { isMethodNotAllowed() }
			jsonPath("$.code") { value("COMMON405") }
			jsonPath("$.success") { value(false) }
		}
	}

	@Test
	fun `createdResponse는 실제 HTTP 201이고 메시지를 바꿀 수 있다`() {
		mvc.post("/test-support/created") { header("Authorization", bearer()) }.andExpect {
			status { isCreated() }
			jsonPath("$.code") { value("COMMON201") }
			jsonPath("$.data.id") { value(1) }
		}
		mvc.post("/test-support/created-message") { header("Authorization", bearer()) }.andExpect {
			status { isCreated() }
			jsonPath("$.message") { value("회원가입에 성공했습니다.") }
			jsonPath("$.data") { value(null) }
		}
	}
}
