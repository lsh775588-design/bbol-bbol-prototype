package com.bbolbbol.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * !!! DEV ONLY !!! 임시 고정 userId 필터. local 프로파일에서만 로드된다.
 * auth 도메인이 완성되기 전에 다른 도메인 개발자가 인증된 사용자로 작업하도록, 유효한 Bearer 토큰이
 * 없으면 bbolbbol.local.fixed-user-id의 사용자로 인증한다. auth 도메인이 끝나면 삭제한다.
 * 프로퍼티가 비어 있으면 빈이 만들어지지 않는다.
 */
@Component
@Profile("local")
@ConditionalOnExpression("'${bbolbbol.local.fixed-user-id:}' != ''")
public class LocalFixedUserFilter extends OncePerRequestFilter {

    private final Long fixedUserId;

    public LocalFixedUserFilter(@Value("${bbolbbol.local.fixed-user-id}") Long fixedUserId) {
        this.fixedUserId = fixedUserId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(fixedUserId, null, List.of()));
        }
        chain.doFilter(request, response);
    }

    /** 보안 필터 체인 안에서만 동작하도록 서블릿 컨테이너 자동 등록을 끈다. */
    @Bean
    static FilterRegistrationBean<LocalFixedUserFilter> localFixedUserFilterRegistration(
            LocalFixedUserFilter filter) {
        FilterRegistrationBean<LocalFixedUserFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
