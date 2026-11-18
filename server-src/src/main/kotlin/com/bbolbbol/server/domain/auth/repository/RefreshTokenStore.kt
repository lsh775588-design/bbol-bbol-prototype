package com.bbolbbol.server.domain.auth.repository

import java.time.Duration

/**
 * refresh token 저장소 포트. 사용자당 현재 토큰은 1개이며 save는 이전 값을 덮어쓴다(새 기기 로그인 시 이전 기기 로그아웃).
 * 저장하는 값은 토큰 원문이 아니라 SHA-256 해시다.
 *
 * 재발급(rotate)으로 밀려난 토큰은 유예 시간 동안만 한 번 더 받아 준다(동시 재발급 요청, 응답 유실 복구).
 * 유예 안에 쓰인 옛 토큰으로 재발급이 또 일어나면 그 시점의 현재 토큰도 유예 대상이 되므로, 같은 시각에 도착한 요청 여러 개가
 * 모두 성공하고 그 응답으로 받은 토큰들이 각자 유예 시간 동안 살아 있다.
 */
interface RefreshTokenStore {

	/** 현재 토큰을 통째로 교체한다. 유예 중이던 옛 토큰도 모두 버린다(로그인, 다른 기기 로그아웃 효과). */
	fun save(userId: Long, tokenHash: String, ttl: Duration)

	/** 현재 토큰의 해시. */
	fun find(userId: Long): String?

	/** 현재 토큰과 유예 중인 토큰을 모두 삭제한다(로그아웃, 탈퇴, 비밀번호 재설정). */
	fun delete(userId: Long)

	/**
	 * presentedHash가 현재 토큰이거나 유예 안의 옛 토큰이면, newHash를 현재 토큰으로 바꾸고 true를 돌려준다.
	 * 이때 바뀌기 전 현재 토큰은 grace 동안 유예 토큰이 된다. 아니면 아무것도 바꾸지 않고 false.
	 * 판단과 교체는 원자적이어야 한다(동시 호출 안전).
	 */
	fun rotate(userId: Long, presentedHash: String, newHash: String, ttl: Duration, grace: Duration): Boolean
}
