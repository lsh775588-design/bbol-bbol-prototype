package com.bbolbbol.server.global.error

import com.bbolbbol.server.global.common.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

	private val log = LoggerFactory.getLogger(javaClass)

	/** COMMON400의 data.errors 항목 */
	data class FieldError(val field: String, val reason: String)
	data class ValidationErrors(val errors: List<FieldError>)

	@ExceptionHandler(BusinessException::class)
	fun handleBusiness(e: BusinessException): ResponseEntity<ApiResponse<Any>> =
		respond(e.errorCode, e.data)

	// @Valid 요청 바디 검증 실패
	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleInvalidBody(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Any>> {
		val errors = e.bindingResult.fieldErrors.map {
			FieldError(it.field, it.defaultMessage ?: "올바르지 않은 값입니다.")
		}
		return respond(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))
	}

	// @RequestParam, @PathVariable 등 메서드 파라미터 검증 실패
	@ExceptionHandler(HandlerMethodValidationException::class)
	fun handleInvalidParam(e: HandlerMethodValidationException): ResponseEntity<ApiResponse<Any>> {
		val errors = e.parameterValidationResults.flatMap { result ->
			result.resolvableErrors.map {
				FieldError(result.methodParameter.parameterName ?: "unknown", it.defaultMessage ?: "올바르지 않은 값입니다.")
			}
		}
		return respond(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))
	}

	@ExceptionHandler(MissingServletRequestParameterException::class)
	fun handleMissingParam(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.INVALID_INPUT, ValidationErrors(listOf(FieldError(e.parameterName, "필수 값입니다."))))

	@ExceptionHandler(MethodArgumentTypeMismatchException::class)
	fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.INVALID_INPUT, ValidationErrors(listOf(FieldError(e.name, "형식이 올바르지 않습니다."))))

	// JSON 형식 오류, 필수 필드 누락(Kotlin non-null) 등
	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.INVALID_INPUT, ValidationErrors(listOf(FieldError("body", "요청 본문을 읽을 수 없습니다. 필드와 형식을 확인하세요."))))

	@ExceptionHandler(NoResourceFoundException::class)
	fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.NOT_FOUND)

	@ExceptionHandler(HttpRequestMethodNotSupportedException::class)
	fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.METHOD_NOT_ALLOWED)

	// ex) JSON을 받는 API에 multipart나 text/plain으로 요청
	@ExceptionHandler(HttpMediaTypeNotSupportedException::class)
	fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResponse<Any>> =
		respond(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE)

	// @RequestPart("file") 등 multipart 파트 누락 → FILE4001
	@ExceptionHandler(MissingServletRequestPartException::class)
	fun handleMissingPart(e: MissingServletRequestPartException): ResponseEntity<ApiResponse<Any>> =
		respond(FileErrorCode.INVALID_FILE, ValidationErrors(listOf(FieldError(e.requestPartName, "필수 값입니다."))))

	// spring.servlet.multipart.max-file-size(10MB) 초과 → FILE413
	@ExceptionHandler(MaxUploadSizeExceededException::class)
	fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Any>> =
		respond(FileErrorCode.FILE_TOO_LARGE)

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
		// 위에서 따로 처리하지 않은 Spring MVC 기본 예외(406 등)는 500 대신 원래 4xx 상태 코드로 응답
		if (e is ErrorResponse && e.statusCode.is4xxClientError) {
			log.warn("처리되지 않은 클라이언트 요청 오류: {} path={}", describe(e), request.requestURI)
			return respond(SpringClientError(HttpStatus.valueOf(e.statusCode.value())))
		}
		// 개인정보 보호: PostgreSQL 제약 위반 메시지 등에는 이메일, 닉네임 같은 값이 들어 있다.
		// ERROR에는 예외 클래스, 값을 지운 짧은 메시지, 요청 경로만 남기고 스택(원문 메시지 포함)은 DEBUG에서만 남긴다.
		log.error("처리되지 않은 예외: {} path={}", describe(e), request.requestURI)
		log.debug("처리되지 않은 예외 상세(원문 메시지 포함, 개인정보 가능)", e)
		return respond(CommonErrorCode.INTERNAL_SERVER_ERROR)
	}

	/** 예외 클래스와 가장 안쪽 원인의 클래스, 값을 지운 메시지(최대 200자). */
	private fun describe(e: Throwable): String {
		val root = generateSequence(e) { it.cause }.last()
		val rootPart = if (root !== e) " <- ${root.javaClass.name}" else ""
		return "${e.javaClass.name}$rootPart: ${sanitize(root.message ?: e.message)}"
	}

	private fun respond(errorCode: ErrorCode, data: Any? = null): ResponseEntity<ApiResponse<Any>> =
		ResponseEntity.status(errorCode.status).body(ApiResponse.fail(errorCode, data))

	/** CommonErrorCode에 없는 4xx를 COMMON + 상태 코드 형식으로 응답 (ex. COMMON406) */
	private class SpringClientError(override val status: HttpStatus) : ErrorCode {
		override val code = "COMMON${status.value()}"
		override val message = "요청을 처리할 수 없습니다."
	}
}

private val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")
private val KEY_VALUE_PATTERN = Regex("[(]([^()]*)[)]=[(]([^()]*)[)]") // PostgreSQL: Key (email)=(a@b.com)
private val QUOTED_PATTERN = Regex("'[^']*'|\"[^\"]*\"")

/** 로그에 남길 예외 메시지에서 값으로 보이는 부분(이메일, Key (col)=(value), 따옴표 문자열)을 지우고 200자로 자른다. */
internal fun sanitize(message: String?): String {
	if (message.isNullOrBlank()) return "(no message)"
	return message
		.replace(KEY_VALUE_PATTERN) { "(${it.groupValues[1]})=(?)" }
		.replace(EMAIL_PATTERN, "<email>")
		.replace(QUOTED_PATTERN, "'?'")
		.map { if (it.isWhitespace()) ' ' else it }
		.joinToString("")
		.take(200)
}
