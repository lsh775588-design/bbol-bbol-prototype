package com.bbolbbol.server.global.security

/**
 * JWT가 유효해도 탈퇴한 사용자처럼 비활성 상태면 인증하지 않기 위한 확장 지점
 *
 * user 도메인이 이 인터페이스를 구현한 빈을 등록하면 JwtAuthenticationFilter가 매 요청마다 호출함
 * 비활성이면 토큰이 없는 것과 같게 처리되어 AUTH401로 응답함
 * 구현 빈이 없으면 모든 사용자를 활성으로 봄 (JwtAuthenticationFilter가 ObjectProvider로 확인)
 */
fun interface UserStatusChecker {
	fun isActive(userId: Long): Boolean
}
