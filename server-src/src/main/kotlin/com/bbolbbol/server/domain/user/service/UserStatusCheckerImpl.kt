package com.bbolbbol.server.domain.user.service

import com.bbolbbol.server.global.security.UserStatusChecker
import org.springframework.stereotype.Component

/** ACTIVE 사용자만 통과. 탈퇴/없는 사용자의 유효 토큰은 JwtAuthenticationFilter에서 AUTH401이 된다. */
@Component
class UserStatusCheckerImpl(
	private val userService: UserService,
) : UserStatusChecker {

	override fun isActive(userId: Long): Boolean = userService.isActive(userId)
}
