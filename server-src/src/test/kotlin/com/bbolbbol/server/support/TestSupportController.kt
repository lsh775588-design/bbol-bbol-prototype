package com.bbolbbol.server.support

import com.bbolbbol.server.global.common.ApiResponse
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** 테스트 전용 컨트롤러. src/test에만 있어서 운영 클래스패스에는 포함되지 않음 */
@RestController
@RequestMapping("/test-support")
class TestSupportController {

	data class Body(
		@field:NotBlank(message = "이름은 필수입니다.") val name: String,
		@field:Min(value = 1, message = "1 이상이어야 합니다.") val age: Int,
	)

	enum class SampleErrorCode(
		override val status: HttpStatus,
		override val code: String,
		override val message: String,
	) : ErrorCode {
		DUPLICATE(HttpStatus.CONFLICT, "SAMPLE4092", "이미 사용 중입니다."),
	}

	@GetMapping("/whoami")
	fun whoami(): ApiResponse<Any> = ApiResponse.ok(SecurityContextHolder.getContext().authentication?.principal)

	@GetMapping("/principal-type")
	fun principalType(): ApiResponse<String> =
		ApiResponse.ok(SecurityContextHolder.getContext().authentication?.principal?.javaClass?.name)

	@PostMapping("/body")
	fun body(@Valid @RequestBody body: Body): ApiResponse<Unit> = ApiResponse.ok()

	@GetMapping("/param")
	fun param(@RequestParam @Min(1) @Max(10) size: Int): ApiResponse<Unit> = ApiResponse.ok()

	@GetMapping("/business")
	fun business(@RequestParam(defaultValue = "false") withData: Boolean): ApiResponse<Unit> =
		throw BusinessException(SampleErrorCode.DUPLICATE, if (withData) mapOf("nickname" to "dup") else null)

	@GetMapping("/constraint")
	fun constraint(): ApiResponse<Unit> =
		throw org.springframework.dao.DataIntegrityViolationException(
			"could not execute statement [ERROR: duplicate key value violates unique constraint \"uk_users_email\"" +
				"  Detail: Key (email)=(victim.person@example.com) already exists.]; nested exception is org.postgresql.util.PSQLException",
			IllegalStateException("ERROR: duplicate key value  Detail: Key (nickname)=(비밀닉네임) already exists."),
		)

	@GetMapping("/boom")
	fun boom(): ApiResponse<Unit> = throw IllegalStateException("SECRET-INTERNAL-DETAIL-xyz")

	@PostMapping("/json-only", consumes = ["application/json"])
	fun jsonOnly(@RequestBody body: Map<String, Any>): ApiResponse<Unit> = ApiResponse.ok()

	@GetMapping("/status/{code}")
	fun status(@PathVariable code: Int): ApiResponse<Unit> =
		throw ResponseStatusException(HttpStatus.valueOf(code), "reason-should-not-leak")

	@PostMapping("/created")
	fun created(): ResponseEntity<ApiResponse<Map<String, Int>>> = ApiResponse.createdResponse(mapOf("id" to 1))

	@PostMapping("/created-message")
	fun createdWithMessage(): ResponseEntity<ApiResponse<Unit>> =
		ApiResponse.createdResponse(message = "회원가입에 성공했습니다.")
}
