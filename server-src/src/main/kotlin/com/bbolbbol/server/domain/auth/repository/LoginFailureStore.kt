package com.bbolbbol.server.domain.auth.repository

import java.time.Duration

/**
 * 로그인 실패 횟수 저장소 포트(IP 하나당 카운터, Redis 운영, 인메모리 local/test).
 * 고정 창 방식이다: 첫 실패 때 창이 시작되고 window가 지나면 카운터가 사라진다.
 */
interface LoginFailureStore {

	/** 현재 창의 실패 횟수. 없으면 0. */
	fun count(key: String): Long

	/** 실패를 1 늘린다. 첫 실패이면 window 동안 유지되는 창을 시작한다. 늘어난 값을 돌려준다(원자적). */
	fun increment(key: String, window: Duration): Long

	/** 카운터를 지운다(로그인 성공 시). */
	fun reset(key: String)
}
