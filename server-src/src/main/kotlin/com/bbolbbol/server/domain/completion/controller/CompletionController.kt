package com.bbolbbol.server.domain.completion.controller

import com.bbolbbol.server.domain.completion.dto.CompletionLogPage
import com.bbolbbol.server.domain.completion.dto.CompletionRequest
import com.bbolbbol.server.domain.completion.dto.CompletionResponse
import com.bbolbbol.server.domain.completion.service.CompletionService
import com.bbolbbol.server.global.common.ApiResponse
import jakarta.validation.Valid
import com.bbolbbol.server.domain.completion.dto.PhotoUploadResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** 모든 엔드포인트는 인증이 필요하다(SecurityConfig의 anyRequest().authenticated()). */
@RestController
class CompletionController(private val completionService: CompletionService) {

	@PostMapping("/places/{placeId}/completions")
	fun complete(
		@AuthenticationPrincipal userId: Long,
		@PathVariable placeId: Long,
		@Valid @RequestBody request: CompletionRequest,
	): ResponseEntity<ApiResponse<CompletionResponse>> {
		val result = completionService.complete(
			userId, placeId, request.lat!!, request.lng!!, request.accuracy!!, request.isMockLocation!!,
		)
		return ApiResponse.createdResponse(result, "퀘스트를 완료했습니다.")
	}

	/** multipart 파트 이름은 명세대로 photo. 파트가 없으면 GlobalExceptionHandler가 FILE4001로 응답한다. */
	@PostMapping("/completions/{completionId}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun uploadPhoto(
		@AuthenticationPrincipal userId: Long,
		@PathVariable completionId: Long,
		@RequestPart("photo") photo: MultipartFile,
	): ResponseEntity<ApiResponse<PhotoUploadResponse>> =
		ApiResponse.createdResponse(completionService.uploadPhoto(userId, completionId, photo), "사진이 등록되었습니다.")

	@GetMapping("/users/me/completions")
	fun log(
		@AuthenticationPrincipal userId: Long,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
		@RequestParam(required = false) category: String?,
		@RequestParam(required = false) cursor: Long?,
		@RequestParam(required = false) size: Int?,
	): ApiResponse<CompletionLogPage> =
		ApiResponse(true, "COMMON200", "요청에 성공했습니다.", completionService.log(userId, from, to, category, cursor, size))
}
