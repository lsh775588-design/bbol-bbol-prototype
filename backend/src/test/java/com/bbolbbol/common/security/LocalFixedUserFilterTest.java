package com.bbolbbol.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** local 프로파일(H2, application-local.yml)로 기동해 임시 고정 userId 필터를 검증한다. */
class LocalFixedUserFilterTest {

    @Nested
    @SpringBootTest(properties = "bbolbbol.local.fixed-user-id=77")
    @AutoConfigureMockMvc
    @ActiveProfiles("local")
    class WithFixedUserId {

        @Autowired MockMvc mvc;
        @Autowired JwtProvider jwt;
        @Autowired ApplicationContext ctx;

        @Test
        @DisplayName("고정 userId가 있으면 토큰 없는 요청이 그 id로 인증된다")
        void tokenlessAuthenticatedAsFixedUser() throws Exception {
            assertThat(ctx.getBeansOfType(LocalFixedUserFilter.class)).hasSize(1);
            mvc.perform(get("/test-support/whoami"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(77));
        }

        @Test
        @DisplayName("유효한 토큰이 있으면 토큰의 userId가 우선한다")
        void validTokenWins() throws Exception {
            mvc.perform(get("/test-support/whoami")
                            .header("Authorization", "Bearer " + jwt.createAccessToken(5L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(5));
        }
    }

    @Nested
    @SpringBootTest(properties = "bbolbbol.local.fixed-user-id=")
    @AutoConfigureMockMvc
    @ActiveProfiles("local")
    class WithEmptyFixedUserId {

        @Autowired MockMvc mvc;
        @Autowired ApplicationContext ctx;

        @Test
        @DisplayName("고정 userId가 비어 있으면 필터가 없고 토큰 없는 요청은 401")
        void emptyDisablesFilter() throws Exception {
            assertThat(ctx.getBeansOfType(LocalFixedUserFilter.class)).isEmpty();
            mvc.perform(get("/test-support/whoami"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401"));
        }
    }
}
