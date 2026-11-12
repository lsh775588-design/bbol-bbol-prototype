package com.bbolbbol.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {token}을 검증해 principal = userId(Long)로 인증한다.
 * 실패하면 컨텍스트를 비워 두고, 보호 경로에서는 AuthenticationEntryPoint가 AUTH401을 돌려준다.
 * SecurityConfig에서 직접 생성한다(빈으로 두면 서블릿 필터로 중복 등록된다).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserStatusChecker userStatusChecker;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserStatusChecker userStatusChecker) {
        this.jwtProvider = jwtProvider;
        this.userStatusChecker = userStatusChecker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            jwtProvider.parseUserId(header.substring(PREFIX.length()).trim())
                    .filter(userStatusChecker::isActive)
                    .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
