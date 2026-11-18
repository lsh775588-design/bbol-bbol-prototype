package com.bbolbbol.server.global

import com.bbolbbol.server.domain.health.HealthController
import com.bbolbbol.server.global.common.ApiResponse
import com.bbolbbol.server.global.config.ClockConfig
import com.bbolbbol.server.global.config.SecurityConfig
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.ErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler
import com.bbolbbol.server.global.security.SecurityErrorResponder
import com.bbolbbol.server.global.security.jwt.JwtProperties
import com.bbolbbol.server.global.security.jwt.JwtProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile

/**
 * 공통 뼈대(보안 설정, JWT 필터, 공통 응답, 에러 처리)가 노션 API 명세서대로 동작하는지 확인
 * DB나 Redis 없이 웹 계층만 띄워서 테스트함
 */
@WebMvcTest(controllers = [HealthController::class, SecurityAndErrorResponseTest.SampleController::class])
@Import(
	SecurityConfig::class,
	SecurityErrorResponder::class,
	JwtProvider::class,
	ClockConfig::class,
	GlobalExceptionHandler::class,
	SecurityAndErrorResponseTest.SampleController::class,
)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(properties = ["jwt.secret=test-secret-key-for-web-tests-0123456789abcdef"])
class SecurityAndErrorResponseTest {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var jwtProvider: JwtProvider

	@Test
	fun `health는 토큰 없이 호출할 수 있다`() {
		mockMvc.get("/health").andExpect {
			status { isOk() }
			jsonPath("$.success") { value(true) }
			jsonPath("$.code") { value("COMMON200") }
			jsonPath("$.data.status") { value("UP") }
		}
	}

	@Test
	fun `보호된 API를 토큰 없이 호출하면 AUTH401`() {
		mockMvc.get("/sample/me").andExpect {
			status { isUnauthorized() }
			jsonPath("$.success") { value(false) }
			jsonPath("$.code") { value("AUTH401") }
		}
	}

	@Test
	fun `잘못된 토큰이나 refresh token으로 호출하면 AUTH401`() {
		mockMvc.get("/sample/me") { header("Authorization", "Bearer broken.token.value") }
			.andExpect { jsonPath("$.code") { value("AUTH401") } }

		mockMvc.get("/sample/me") { header("Authorization", "Bearer ${jwtProvider.createRefreshToken(7L)}") }
			.andExpect { jsonPath("$.code") { value("AUTH401") } }
	}

	@Test
	fun `logout은 auth 경로지만 인증이 필요하다`() {
		mockMvc.post("/auth/logout").andExpect {
			status { isUnauthorized() }
			jsonPath("$.code") { value("AUTH401") }
		}
	}

	@Test
	fun `유효한 access token이면 컨트롤러에서 userId를 받을 수 있다`() {
		mockMvc.get("/sample/me") { header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}") }
			.andExpect {
				status { isOk() }
				jsonPath("$.data.userId") { value(7) }
			}
	}

	@Test
	fun `요청 값 검증에 실패하면 COMMON400과 필드별 오류`() {
		mockMvc.post("/sample/nickname") {
			header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}")
			contentType = MediaType.APPLICATION_JSON
			content = """{"nickname": ""}"""
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("COMMON400") }
			jsonPath("$.data.errors[0].field") { value("nickname") }
			jsonPath("$.data.errors[0].reason") { value("닉네임을 입력하세요.") }
		}
	}

	@Test
	fun `BusinessException은 해당 에러 코드와 data로 응답한다`() {
		mockMvc.get("/sample/business-error") { header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}") }
			.andExpect {
				status { isConflict() }
				jsonPath("$.success") { value(false) }
				jsonPath("$.code") { value("USER4092") }
				jsonPath("$.data.hint") { value("다른 닉네임을 써 주세요") }
			}
	}

	@Test
	fun `없는 경로는 로그인한 상태에서 COMMON404`() {
		mockMvc.get("/no-such-path") { header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}") }
			.andExpect {
				status { isNotFound() }
				jsonPath("$.code") { value("COMMON404") }
			}
	}

	@Test
	fun `파일을 첨부하면 업로드 API가 정상 응답한다`() {
		mockMvc.multipart("/sample/upload") {
			file(MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3)))
			header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}")
		}.andExpect {
			status { isOk() }
			jsonPath("$.data.size") { value(3) }
		}
	}

	@Test
	fun `업로드할 파일이 없으면 FILE4001`() {
		mockMvc.multipart("/sample/upload") {
			header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}")
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.code") { value("FILE4001") }
			jsonPath("$.data.errors[0].field") { value("file") }
		}
	}

	@Test
	fun `파일 용량 제한을 넘으면 FILE413`() {
		mockMvc.get("/sample/too-large") { header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}") }
			.andExpect {
				status { isPayloadTooLarge() }
				jsonPath("$.code") { value("FILE413") }
			}
	}

	@Test
	fun `지원하지 않는 Content-Type이면 COMMON415`() {
		mockMvc.post("/sample/nickname") {
			header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}")
			contentType = MediaType.TEXT_PLAIN
			content = "nickname"
		}.andExpect {
			status { isUnsupportedMediaType() }
			jsonPath("$.code") { value("COMMON415") }
		}
	}

	@Test
	fun `따로 처리하지 않은 Spring 4xx 예외는 500이 아니라 원래 상태 코드로 응답한다`() {
		mockMvc.get("/sample/spring-4xx") { header("Authorization", "Bearer ${jwtProvider.createAccessToken(7L)}") }
			.andExpect {
				status { isGone() }
				jsonPath("$.success") { value(false) }
				jsonPath("$.code") { value("COMMON410") }
			}
	}

	/** 테스트 전용 컨트롤러 */
	@RestController
	class SampleController {
		data class NicknameRequest(@field:NotBlank(message = "닉네임을 입력하세요.") val nickname: String)

		enum class SampleErrorCode(
			override val status: HttpStatus,
			override val code: String,
			override val message: String,
		) : ErrorCode {
			DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER4092", "이미 사용 중인 닉네임입니다."),
		}

		@GetMapping("/sample/me")
		fun me(@AuthenticationPrincipal userId: Long) = ApiResponse.ok(mapOf("userId" to userId))

		@PostMapping("/sample/nickname")
		fun nickname(@Valid @RequestBody request: NicknameRequest) = ApiResponse.ok(request)

		@PostMapping("/sample/upload")
		fun upload(@RequestPart("file") file: MultipartFile) = ApiResponse.ok(mapOf("size" to file.size))

		// 실제 용량 초과는 Tomcat의 multipart 처리 단계에서 나서 MockMvc로는 재현되지 않음. 같은 예외를 직접 던져서 확인
		@GetMapping("/sample/too-large")
		fun tooLarge(): ApiResponse<Unit> = throw MaxUploadSizeExceededException(10L * 1024 * 1024)

		@GetMapping("/sample/spring-4xx")
		fun spring4xx(): ApiResponse<Unit> = throw ErrorResponseException(HttpStatus.GONE)

		@GetMapping("/sample/business-error")
		fun businessError(): ApiResponse<Unit> =
			throw BusinessException(SampleErrorCode.DUPLICATE_NICKNAME, mapOf("hint" to "다른 닉네임을 써 주세요"))
	}
}
