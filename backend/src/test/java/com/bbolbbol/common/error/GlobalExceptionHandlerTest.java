package com.bbolbbol.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + jwt.createAccessToken(1L));
    }

    @Test
    @DisplayName("@RequestBody 검증 실패 -> 400 COMMON400, data.errors[{field, reason}]")
    void bodyValidation() throws Exception {
        mvc.perform(auth(post("/test-support/body")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors", hasSize(2)))
                .andExpect(jsonPath("$.data.errors[?(@.field=='name')].reason").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.data.errors[?(@.field=='age')].reason").value("1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("@RequestParam 범위 검증 실패 -> COMMON400 + errors")
    void paramValidation() throws Exception {
        mvc.perform(auth(get("/test-support/param")).param("size", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors", hasSize(1)))
                .andExpect(jsonPath("$.data.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("필수 파라미터 누락 -> COMMON400, 누락된 이름")
    void missingParam() throws Exception {
        mvc.perform(auth(get("/test-support/param")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("파라미터 타입 불일치 -> COMMON400")
    void typeMismatch() throws Exception {
        mvc.perform(auth(get("/test-support/param")).param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("깨진 JSON -> COMMON400, errors는 빈 리스트")
    void malformedJson() throws Exception {
        mvc.perform(auth(post("/test-support/body")).contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors", hasSize(0)));
    }

    @Test
    @DisplayName("본문 누락 -> COMMON400")
    void missingBody() throws Exception {
        mvc.perform(auth(post("/test-support/body")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("BusinessException(코드, data) -> 코드/상태/data 그대로")
    void businessWithData() throws Exception {
        mvc.perform(auth(get("/test-support/business")).param("withData", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("USER4092"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER4092.getMessage()))
                .andExpect(jsonPath("$.data.nickname").value("dup"));
    }

    @Test
    @DisplayName("BusinessException(data 없음) -> data 키는 있고 null")
    void businessWithoutData() throws Exception {
        mvc.perform(auth(get("/test-support/business")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER4092"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("예상 못한 RuntimeException -> 500 COMMON500, 메시지/스택 미노출")
    void unexpectedException() throws Exception {
        mvc.perform(auth(get("/test-support/boom")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(content().string(not(containsString("SECRET-INTERNAL-DETAIL"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.bbolbbol"))));
    }

    @Test
    @DisplayName("허용하지 않는 Content-Type -> 415 COMMON415")
    void unsupportedMediaType() throws Exception {
        mvc.perform(auth(post("/test-support/json-only")).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON415"));
    }

    @Test
    @DisplayName("ResponseStatusException 409 -> 상태 유지, COMMON400 코드, 사유 미노출")
    void other4xxKeepsStatus() throws Exception {
        mvc.perform(auth(get("/test-support/status/409")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(content().string(not(containsString("reason-should-not-leak"))));
        mvc.perform(auth(get("/test-support/status/400")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("ResponseStatusException 5xx -> 500 COMMON500 (4xx 분기 대상이 아님)")
    void other5xxBecomes500() throws Exception {
        mvc.perform(auth(get("/test-support/status/503")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"));
    }
}
