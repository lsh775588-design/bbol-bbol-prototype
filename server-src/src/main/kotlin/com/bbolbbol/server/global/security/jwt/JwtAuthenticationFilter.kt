package com.bbolbbol.server.global.security.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.bbolbbol.server.global.security.UserStatusChecker
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer {accessToken} 헤더를 읽어서 로그인한 사용자로 등록
 *
 * 토큰이 없거나 유효하지 않으면 그냥 통과시키고,
 * 인증이 필요한 경로라면 JwtAuthenticationEntryPoint가 AUTH401을 응답함
 *
 * 컨트롤러에서는 이렇게 userId를 꺼내 쓰면 됨
 *   fun getMe(@AuthenticationPrincipal userId: Long) { ... }
 *
 * 토큰이 유효해도 UserStatusChecker가 비활성(탈퇴 등)이라고 하면 인증하지 않음(AUTH401)
 *
 * @Component를 붙이지 않은 이유: 붙이면 Spring Boot가 일반 서블릿 필터로도 한 번 더 등록함
 */
class JwtAuthenticationFilter(
	private val jwtProvider: JwtProvider,
	private val userStatusChecker: ObjectProvider<UserStatusChecker>,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val token = request.getHeader(HttpHeaders.AUTHORIZATION)
			?.takeIf { it.startsWith(BEARER_PREFIX) }
			?.removePrefix(BEARER_PREFIX)

		val userId = token?.let { jwtProvider.getUserIdFromAccessToken(it) }
		// 구현 빈이 없으면(user 도메인 전) 모두 활성으로 봄. 빈은 요청마다 조회해서 늦게 등록돼도 반영됨
		if (userId != null && userStatusChecker.getIfAvailable()?.isActive(userId) != false) {
			val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
			SecurityContextHolder.getContext().authentication = authentication
		}

		filterChain.doFilter(request, response)
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
