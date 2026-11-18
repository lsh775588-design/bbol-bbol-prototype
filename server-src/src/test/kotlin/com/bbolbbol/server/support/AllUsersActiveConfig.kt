package com.bbolbbol.server.support

import com.bbolbbol.server.global.security.UserStatusChecker
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 공통 보안 테스트용: DB에 없는 userId의 토큰도 통과시킨다.
 * user 도메인의 UserStatusChecker(DB 조회)를 대체하며, user/auth 통합 테스트에서는 쓰지 않는다.
 */
@TestConfiguration
class AllUsersActiveConfig {

	@Bean
	@Primary
	fun allUsersActive(): UserStatusChecker = UserStatusChecker { true }
}
