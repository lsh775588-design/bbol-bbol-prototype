package com.bbolbbol.server.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * !!! DEV ONLY !!! 로컬 개발용 고정 userId 필터. local 프로파일에서만 만들어짐 (다른 프로파일에는 없음)
 *
 * 로그인 기능이 완성되기 전에도 인증된 사용자로 API를 개발하고 확인할 수 있게,
 * 유효한 Bearer 토큰이 없으면 bbolbbol.local.fixed-user-id 의 사용자로 인증함
 * 프로퍼티가 비어 있으면 빈이 만들어지지 않음. 로그인 기능이 끝나면 지워도 됨
 */
@Component
@Profile("local")
@ConditionalOnExpression("'\${bbolbbol.local.fixed-user-id:}' != ''")
class LocalFixedUserFilter(
	@Value("\${bbolbbol.local.fixed-user-id}") private val fixedUserId: Long,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		if (SecurityContextHolder.getContext().authentication == null) {
			SecurityContextHolder.getContext().authentication =
				UsernamePasswordAuthenticationToken(fixedUserId, null, emptyList())
		}
		filterChain.doFilter(request, response)
	}

	companion object {
		/** 보안 필터 체인 안에서만 동작하도록 서블릿 컨테이너 자동 등록을 끔 */
		@Bean
		@JvmStatic
		fun localFixedUserFilterRegistration(filter: LocalFixedUserFilter): FilterRegistrationBean<LocalFixedUserFilter> =
			FilterRegistrationBean(filter).apply { isEnabled = false }
	}
}
