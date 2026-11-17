package com.bbolbbol.server.domain.completion.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 번호는 명세 그대로다(4001~4003은 400, 409는 일련번호 없음, 사진 업로드의 403/404/4004/4091도 명세 번호).
 * 일련번호 없는 403, 404와 4004, 4091은 명세의 "인증 사진 업로드" 에러 표에 있다.
 */
enum class CompletionErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {

	OUT_OF_RADIUS(HttpStatus.BAD_REQUEST, "COMPLETION4001", "장소의 인증 반경 밖에 있습니다."),
	LOW_ACCURACY(HttpStatus.BAD_REQUEST, "COMPLETION4002", "GPS 정확도가 낮습니다. 실외로 이동해서 다시 시도해주세요."),
	MOCK_LOCATION(HttpStatus.BAD_REQUEST, "COMPLETION4003", "모의 위치는 사용할 수 없습니다."),
	ALREADY_COMPLETED_TODAY(HttpStatus.CONFLICT, "COMPLETION409", "오늘 이미 이 장소를 인증했습니다."),

	/** 사진 업로드 마감(첫 방문 인증 시각 + 1시간)이 지남 */
	PHOTO_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "COMPLETION4004", "사진 업로드 마감 시각이 지났습니다."),
	NOT_MY_COMPLETION(HttpStatus.FORBIDDEN, "COMPLETION403", "내 완료 기록이 아닙니다."),
	COMPLETION_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPLETION404", "존재하지 않는 완료 기록입니다."),
	PHOTO_ALREADY_UPLOADED(HttpStatus.CONFLICT, "COMPLETION4091", "이미 사진을 올렸습니다. 보너스는 1회만 받을 수 있습니다."),

	/** NOT IN SPEC: 최소로 추가한 코드, 팀 확인 필요(Gray Area A4: 온보딩을 마치지 않으면 도착 인증 불가). */
	ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "COMPLETION4031", "온보딩을 완료해야 퀘스트를 인증할 수 있습니다."),

	/** NOT IN SPEC: 사진 디코딩 동시 처리 한도 초과(보안 점검). 잠시 뒤 다시 시도하면 된다. 팀 확인 필요. */
	PHOTO_PROCESSING_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "COMPLETION503", "사진 처리 요청이 많습니다. 잠시 후 다시 시도해주세요."),
}
