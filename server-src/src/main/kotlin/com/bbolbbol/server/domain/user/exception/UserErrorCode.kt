package com.bbolbbol.server.domain.user.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

/** 코드 번호는 노션 API 명세서의 불규칙한 번호(USER4092 등)를 그대로 따름 */
enum class UserErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "탈퇴했거나 존재하지 않는 사용자"),
	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "USER409", "이미 가입된 이메일"),
	NICKNAME_FORBIDDEN_WORD(HttpStatus.BAD_REQUEST, "USER4001", "닉네임에 금칙어 포함"),
	NICKNAME_CHANGE_LIMIT(HttpStatus.BAD_REQUEST, "USER4002", "닉네임 변경 후 30일이 지나지 않음"),
	NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER4092", "이미 사용 중인 닉네임"),
	KAKAO_EMAIL_REGISTERED_AS_EMAIL(HttpStatus.CONFLICT, "USER4093", "카카오 이메일이 이미 이메일 가입 계정으로 등록됨. 이메일로 로그인해야 함"),
}
