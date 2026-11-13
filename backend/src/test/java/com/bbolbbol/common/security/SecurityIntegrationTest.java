package com.bbolbbol.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@org.springframework.context.annotation.Import(com.bbolbbol.support.AllUsersActiveConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String PROTECTED = "/test-support/whoami";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Value("${jwt.secret}") String secret;

    private SecretKeySpec key(String s) {
        return new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private ResultActions withHeader(String header) throws Exception {
        return mvc.perform(get(PROTECTED).header("Authorization", header));
    }

    private void expect401(ResultActions r) throws Exception {
        r.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("유효 토큰 -> 200, principal은 userId(Long)")
    void validToken() throws Exception {
        withHeader("Bearer " + jwt.createAccessToken(42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));
        withHeader("Bearer " + jwt.createAccessToken(42L))
                .andExpect(content().string(containsString("\"data\":42")));
        mvc.perform(get("/test-support/principal-type")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(42L)))
                .andExpect(jsonPath("$.data").value("java.lang.Long"));
    }

    @Test
    @DisplayName("Bearer 뒤 공백이 더 있어도 trim 되어 통과")
    void extraSpaceAfterBearer() throws Exception {
        withHeader("Bearer  " + jwt.createAccessToken(3L)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료된 토큰 -> 401 AUTH401")
    void expired() throws Exception {
        String t = Jwts.builder().subject("1").claim("typ", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(key(secret), Jwts.SIG.HS256).compact();
        expect401(withHeader("Bearer " + t));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰 -> 401")
    void wrongKey() throws Exception {
        String t = Jwts.builder().subject("1").claim("typ", "access").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key("another-FAKE-key-0123456789-abcdefghijklmnop"), Jwts.SIG.HS256).compact();
        expect401(withHeader("Bearer " + t));
    }

    @Test
    @DisplayName("alg=none 토큰 -> 401")
    void algNone() throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String h = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String p = enc.encodeToString(("{\"sub\":\"1\",\"typ\":\"access\",\"exp\":"
                + (System.currentTimeMillis() / 1000 + 600) + "}").getBytes(StandardCharsets.UTF_8));
        expect401(withHeader("Bearer " + h + "." + p + "."));
    }

    @Test
    @DisplayName("typ 없는 토큰, typ=refresh 토큰 -> 401")
    void wrongTokenType() throws Exception {
        String noTyp = Jwts.builder().subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(secret), Jwts.SIG.HS256).compact();
        String refresh = Jwts.builder().subject("1").claim("typ", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(secret), Jwts.SIG.HS256).compact();
        expect401(withHeader("Bearer " + noTyp));
        expect401(withHeader("Bearer " + refresh));
        withHeader("Bearer " + jwt.createAccessToken(1L)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("숫자가 아닌 subject -> 401")
    void nonNumericSubject() throws Exception {
        String t = Jwts.builder().subject("not-a-number").claim("typ", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(secret), Jwts.SIG.HS256).compact();
        expect401(withHeader("Bearer " + t));
    }

    @Test
    @DisplayName("Bearer 접두어 오류/토큰 없음/빈 토큰/Authorization 헤더 없음 -> 401")
    void badHeaders() throws Exception {
        String t = jwt.createAccessToken(1L);
        expect401(withHeader("Token " + t));
        expect401(withHeader("bearer " + t));
        expect401(withHeader("Basic " + t));
        expect401(withHeader(t));
        expect401(withHeader("Bearer"));
        expect401(withHeader("Bearer "));
        expect401(withHeader(""));
        expect401(mvc.perform(get(PROTECTED)));
    }

    @Test
    @DisplayName("401 응답은 application/json UTF-8, 사유(만료/서명 등) 미노출")
    void unauthorizedBodyShape() throws Exception {
        for (String header : new String[] {null, "Bearer garbage"}) {
            var req = get(PROTECTED);
            if (header != null) {
                req.header("Authorization", header);
            }
            mvc.perform(req)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(jsonPath("$.code").value("AUTH401"))
                    .andExpect(jsonPath("$.message").value(ErrorCode401.MESSAGE))
                    .andExpect(content().string(not(containsString("expired"))))
                    .andExpect(content().string(not(containsString("signature"))))
                    .andExpect(content().string(not(containsString("garbage"))));
        }
    }

    @Test
    @DisplayName("공개 경로는 토큰 없이 401이 아니다")
    void publicPaths() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        for (String path : new String[] {"/auth/anything", "/swagger-ui/index.html", "/v3/api-docs",
                "/health/deep"}) {
            int s = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(s).as(path).isNotEqualTo(401).isNotEqualTo(403);
        }
    }

    @Test
    @DisplayName("POST /auth/logout: 토큰 없으면 401, 유효 토큰이면 401이 아니다")
    void logout() throws Exception {
        expect401(mvc.perform(post("/auth/logout")));
        int s = mvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(1L)))
                .andReturn().getResponse().getStatus();
        assertThat(s).isNotEqualTo(401);
    }

    @Test
    @DisplayName("그 밖의 경로는 토큰 없이 모두 401 (HTTP 메서드 무관)")
    void everythingElseProtected() throws Exception {
        for (String path : new String[] {"/places", "/users/me", "/", "/actuator/env", "/authx",
                "/healthz", PROTECTED}) {
            expect401(mvc.perform(get(path)));
        }
        expect401(mvc.perform(post("/places").contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    @Test
    @DisplayName("GET /auth/logout은 공개 경로 규칙을 따른다(POST만 인증 필요)")
    void getLogoutIsNotProtected() throws Exception {
        int s = mvc.perform(get("/auth/logout")).andReturn().getResponse().getStatus();
        assertThat(s).isNotEqualTo(401);
    }

    /** 응답 메시지 상수(ErrorCode.AUTH401 메시지)를 테스트에서 한 곳으로 모은다. */
    static final class ErrorCode401 {
        static final String MESSAGE = com.bbolbbol.common.error.ErrorCode.AUTH401.getMessage();
    }
}
