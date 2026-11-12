package com.bbolbbol.common.security;

import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** STATELESS + JWT. 공개 경로 외에는 인증이 필요하고, 실패하면 AUTH401 공통 응답을 돌려준다. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider,
                                           ObjectProvider<UserStatusChecker> userStatusChecker,
                                           ObjectProvider<LocalFixedUserFilter> localFixedUserFilter,
                                           ObjectMapper objectMapper) throws Exception {
        // user 도메인이 UserStatusChecker를 구현하기 전까지 모든 사용자를 활성으로 본다.
        // 구현 빈이 등록되면 자동으로 그것을 쓴다(기본 빈을 두지 않아 구현 빈과 충돌하지 않는다).
        UserStatusChecker checker = userStatusChecker.getIfAvailable(() -> userId -> true);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtProvider, checker);
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .requestMatchers("/auth/**", "/health/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                // 403은 명세에 코드가 없어 Spring 기본 동작을 유지한다.
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // DEV ONLY: local 프로파일에서만 존재한다. JWT 필터 뒤에 두어 유효 토큰이 우선한다.
        localFixedUserFilter.ifAvailable(f -> http.addFilterAfter(f, JwtAuthenticationFilter.class));
        return http.build();
    }

    private AuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            // 실패 사유는 응답에 노출하지 않는다.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.AUTH401));
        };
    }
}
