package com.bbolbbol.common.response;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreatedResponseTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    @Test
    @DisplayName("createdResponse: HTTP 201 이고 body code는 COMMON201")
    void httpStatus201AndBodyCode() throws Exception {
        mvc.perform(post("/test-support/created")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(1L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
