package com.bbolbbol.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class VerificationTokenVerifierTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class NonLocalProfile {

        @Autowired ApplicationContext ctx;
        @Autowired Environment env;
        @Autowired VerificationTokenVerifier verifier;
        @Autowired MockMvc mvc;

        @Test
        @DisplayName("test(비 local) 컨텍스트에는 Local 구현 빈도 고정 토큰 프로퍼티도 없다")
        void noLocalBeanAndNoProperty() {
            assertThat(ctx.getBeansOfType(LocalVerificationTokenVerifier.class)).isEmpty();
            assertThat(env.getProperty("bbolbbol.local.verification-token")).isNull();
            assertThat(verifier).isInstanceOf(RejectingVerificationTokenVerifier.class);
        }

        @Test
        @DisplayName("기본 구현은 어떤 토큰이든 EMAIL4003으로 거부(개발용 토큰 형식 포함)")
        void rejectsEverything() {
            for (String t : new String[] {"anything", "DEV-ONLY-FAKE-verification-token:a@example.com", ""}) {
                assertThatThrownBy(() -> verifier.verifySignupToken(t))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL4003);
            }
        }

        @Test
        @DisplayName("signup API도 EMAIL4003(400)으로 거부하고 사용자를 만들지 않는다")
        void signupRejected() throws Exception {
            mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                            "{\"verificationToken\":\"DEV-ONLY-FAKE-verification-token:z@example.com\","
                                    + "\"password\":\"bbol1234!\",\"nickname\":\"거부될닉\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL4003"));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    class LocalProfile {

        @Autowired ApplicationContext ctx;
        @Autowired VerificationTokenVerifier verifier;
        @Autowired Environment env;

        @Test
        @DisplayName("local에서는 Local 구현이 쓰이고 고정 토큰 + 이메일만 통과한다")
        void localAcceptsFixedToken() {
            assertThat(verifier).isInstanceOf(LocalVerificationTokenVerifier.class);
            assertThat(ctx.getBeansOfType(RejectingVerificationTokenVerifier.class)).isEmpty();
            String fixed = env.getProperty("bbolbbol.local.verification-token");
            assertThat(fixed).startsWith("DEV-ONLY-FAKE");
            assertThat(verifier.verifySignupToken(fixed + ":Dev@Example.com")).isEqualTo("Dev@Example.com");
            for (String bad : new String[] {"wrong:dev@example.com", fixed, fixed + ":", fixed + ":notemail"}) {
                assertThatThrownBy(() -> verifier.verifySignupToken(bad))
                        .isInstanceOf(BusinessException.class);
            }
        }
    }
}
