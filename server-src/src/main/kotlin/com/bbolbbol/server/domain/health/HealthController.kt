package com.bbolbbol.server.domain.health

import com.bbolbbol.server.global.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 서버가 켜져 있는지 확인하는 공개 API
 * (프론트에서 연결 테스트할 때 쓰면 됨) */
@RestController
@RequestMapping("/health")
class HealthController {

	@GetMapping
	fun health(): ApiResponse<Map<String, String>> = ApiResponse.ok(mapOf("status" to "UP"))
}
