package com.bbolbbol.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.context.annotation.Import(com.bbolbbol.support.AllUsersActiveConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrameworkErrorTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void unknownPathWithTokenReturns404() throws Exception {
        mockMvc.perform(get("/no-such-path")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON404"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(post("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthenticatedUnknownPathStill401() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }
}
