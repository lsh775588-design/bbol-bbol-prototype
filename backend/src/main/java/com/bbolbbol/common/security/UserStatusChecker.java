package com.bbolbbol.common.security;

/**
 * 토큰이 유효한 사용자가 여전히 활성 상태인지 확인하는 훅.
 * 탈퇴한 사용자의 access token은 최대 1시간 동안 서명상 유효하므로, user 도메인이 생기면
 * 이 인터페이스를 구현한 빈을 등록해 탈퇴 사용자를 걸러낸다(false 반환 시 AUTH401로 처리).
 * 구현 빈이 없으면 SecurityConfig가 항상 true(모두 활성)로 처리한다.
 */
public interface UserStatusChecker {

    boolean isActive(Long userId);
}
