package com.bbolbbol.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY: 0-4 단계(보안) 전까지만 모든 요청을 허용한다.
 * 0-4에서 JWT 필터 및 인증/인가 규칙(AUTH401 처리 포함)으로 교체할 것.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain temporaryFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
