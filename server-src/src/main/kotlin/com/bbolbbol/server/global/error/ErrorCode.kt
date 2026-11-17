package com.bbolbbol.server.global.error

import org.springframework.http.HttpStatus

/**
 * 에러 코드 형식: 도메인 + HTTP 상태 코드 (+ 일련번호).
 * ex) USER409, COMPLETION4001
 *
 * 도메인마다 이 인터페이스를 구현하는 enum을 만들어서 쓰면 됨
 * ex) domain/user/exception/UserErrorCode.kt
 *
 * enum class UserErrorCode(...) : ErrorCode {
 *     USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "탈퇴했거나 존재하지 않는 사용자입니다."),
 * }
 */
interface ErrorCode {
	val status: HttpStatus
	val code: String
	val message: String
}
