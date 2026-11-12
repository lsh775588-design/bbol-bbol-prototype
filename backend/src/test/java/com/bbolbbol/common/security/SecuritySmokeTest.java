package com.bbolbbol.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 최소 스모크 테스트. 전체 보안 테스트는 0-5에서 확장한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuritySmokeTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired ApplicationContext ctx;

    @Test
    void tokenRoundTrip() {
        String token = jwtProvider.createAccessToken(42L);
        assertThat(jwtProvider.parseUserId(token)).contains(42L);
        assertThat(jwtProvider.parseUserId(token + "x")).isEmpty();
    }

    @Test
    void protectedPathWithoutTokenReturnsAuth401() throws Exception {
        mockMvc.perform(get("/places"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void garbageTokenReturnsAuth401() throws Exception {
        mockMvc.perform(get("/places").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }

    @Test
    void localFixedUserFilterIsNotLoadedOutsideLocal() {
        assertThat(ctx.getBeansOfType(LocalFixedUserFilter.class)).isEmpty();
    }
}
