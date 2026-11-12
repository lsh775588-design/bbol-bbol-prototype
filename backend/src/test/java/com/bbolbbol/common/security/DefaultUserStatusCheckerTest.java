package com.bbolbbol.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/** UserStatusChecker 구현 빈이 없을 때의 기본 동작(모두 활성). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DefaultUserStatusCheckerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired ApplicationContext ctx;

    @Test
    @DisplayName("구현 빈이 없으면 어떤 사용자의 유효 토큰도 활성으로 통과")
    void noImplementationTreatsUsersAsActive() throws Exception {
        assertThat(ctx.getBeanNamesForType(UserStatusChecker.class)).isEmpty();
        mvc.perform(get("/test-support/whoami")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(999));
    }
}
