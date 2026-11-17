package com.bbolbbol.server.global.config

import com.bbolbbol.server.global.security.LocalFixedUserFilter
import com.bbolbbol.server.global.security.SecurityErrorResponder
import com.bbolbbol.server.global.security.UserStatusChecker
import com.bbolbbol.server.global.security.jwt.JwtAuthenticationFilter
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
	private val jwtProvider: JwtProvider,
	private val securityErrorResponder: SecurityErrorResponder,
	private val userStatusChecker: ObjectProvider<UserStatusChecker>,
	private val localFixedUserFilter: ObjectProvider<LocalFixedUserFilter>, // local 프로파일에서만 존재
) {

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.formLogin { it.disable() }
			.httpBasic { it.disable() }
			.logout { it.disable() } // /auth/logout은 직접 구현
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				// 노션 API 명세서 "인증 방식": /auth/logout만 예외로 인증 필요
				it.requestMatchers("/auth/logout").authenticated()
				it.requestMatchers(*PUBLIC_PATHS).permitAll()
				it.anyRequest().authenticated()
			}
			.exceptionHandling {
				it.authenticationEntryPoint(securityErrorResponder)
				it.accessDeniedHandler(securityErrorResponder)
			}
			.addFilterBefore(JwtAuthenticationFilter(jwtProvider, userStatusChecker), UsernamePasswordAuthenticationFilter::class.java)

		// DEV ONLY: JWT 필터 다음에 두어서 유효한 토큰이 없을 때만 고정 사용자로 인증함
		localFixedUserFilter.ifAvailable {
			http.addFilterAfter(it, JwtAuthenticationFilter::class.java)
		}

		return http.build()
	}

	/** 이메일 회원가입 비밀번호 저장용 */
	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	companion object {
		private val PUBLIC_PATHS = arrayOf(
			"/auth/**",
			"/health/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**",
			"/actuator/health",
			"/error",
		)
	}
}
