package com.bbolbbol.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserStatusCheckerTest.InactiveUserConfig.class)
class UserStatusCheckerTest {

    static final long WITHDRAWN_USER = 999L;

    @TestConfiguration
    static class InactiveUserConfig {
        // 일부러 @Primary 없이 일반 빈으로 등록해도 사용되는지 확인한다.
        @Bean
        @org.springframework.context.annotation.Primary
        UserStatusChecker withdrawnUserChecker() {
            return userId -> userId != WITHDRAWN_USER;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    @DisplayName("탈퇴(비활성) 사용자의 유효한 토큰은 401 AUTH401")
    void inactiveUserRejected() throws Exception {
        mvc.perform(get("/test-support/whoami")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(WITHDRAWN_USER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }

    @Test
    @DisplayName("활성 사용자의 토큰은 통과")
    void activeUserAccepted() throws Exception {
        mvc.perform(get("/test-support/whoami")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }
}
