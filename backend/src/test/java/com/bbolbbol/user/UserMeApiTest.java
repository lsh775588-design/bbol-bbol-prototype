package com.bbolbbol.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbolbbol.common.security.JwtProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserMeApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @Autowired UserService userService;

    Long userId;
    String auth;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_interests");
        jdbc.update("DELETE FROM users");
        User u = userService.createEmailUser("me@example.com", "$2a$10$fakehashfakehashfakehashfakehashfakehashfakehashfakeh", "내닉네임");
        userId = u.getId();
        auth = "Bearer " + jwt.createAccessToken(userId);
    }

    private ResultActions patchNickname(String body, String authorization) throws Exception {
        var req = patch("/users/me").contentType(MediaType.APPLICATION_JSON).content(body);
        if (authorization != null) req = req.header("Authorization", authorization);
        return mvc.perform(req);
    }

    @Test
    @DisplayName("GET /users/me: 프로필 필드가 명세대로")
    void me() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("내닉네임"))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.createdAt").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("토큰 없음/잘못된 토큰 -> 401 AUTH401 (GET, PATCH)")
    void authRequired() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
        mvc.perform(get("/users/me").header("Authorization", "Bearer bad.token.value"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        patchNickname("{\"nickname\":\"바꿀닉\"}", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
    }

    @Test
    @DisplayName("탈퇴한 사용자의 유효한 토큰 -> 401 AUTH401")
    void withdrawnUserTokenRejected() throws Exception {
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId);
        mvc.perform(get("/users/me").header("Authorization", auth))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
        patchNickname("{\"nickname\":\"바꿀닉\"}", auth).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 유효한 토큰 -> 401 AUTH401")
    void nonexistentUserTokenRejected() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + jwt.createAccessToken(987654L)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401"));
    }

    @Test
    @DisplayName("PATCH 성공: 닉네임 변경, nextChangeableAt = 지금 + 30일")
    void changeNickname() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        patchNickname("{\"nickname\":\"종로탐험가\"}", auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.message").value("닉네임이 변경되었습니다."))
                .andExpect(jsonPath("$.data.nickname").value("종로탐험가"))
                .andExpect(jsonPath("$.data.nextChangeableAt").isString());
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("종로탐험가");
        LocalDateTime changedAt = jdbc.queryForObject(
                "SELECT nickname_changed_at FROM users WHERE id = ?", LocalDateTime.class, userId);
        assertThat(changedAt).isAfterOrEqualTo(before.minusSeconds(2));
        mvc.perform(get("/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.data.nickname").value("종로탐험가"));
    }

    @Test
    @DisplayName("USER4002: 변경 직후 다시 변경하면 400 + data.nextChangeableAt")
    void changeTwiceWithin30Days() throws Exception {
        patchNickname("{\"nickname\":\"첫번째\"}", auth).andExpect(status().isOk());
        patchNickname("{\"nickname\":\"두번째\"}", auth)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER4002"))
                .andExpect(jsonPath("$.data.nextChangeableAt").isString());
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("첫번째");
    }

    @Test
    @DisplayName("30일이 지나면 다시 변경 가능, 가입 때 닉네임(null)은 제한 없음")
    void changeAfter30Days() throws Exception {
        patchNickname("{\"nickname\":\"첫번째\"}", auth).andExpect(status().isOk());
        jdbc.update("UPDATE users SET nickname_changed_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(31), userId);
        patchNickname("{\"nickname\":\"두번째\"}", auth).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("두번째"));
    }

    @Test
    @DisplayName("USER4001: 금칙어, USER4092: 중복(대소문자 무시)/현재 닉네임과 동일")
    void forbiddenAndDuplicate() throws Exception {
        userService.createEmailUser("other@example.com", "$2a$10$x", "Taken1");
        patchNickname("{\"nickname\":\"관리자123\"}", auth).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER4001"));
        patchNickname("{\"nickname\":\"taken1\"}", auth).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER4092"));
        patchNickname("{\"nickname\":\"내닉네임\"}", auth).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER4092"));
        assertThat(jdbc.queryForObject("SELECT nickname_changed_at FROM users WHERE id = ?",
                LocalDateTime.class, userId)).isNull();
    }

    @Test
    @DisplayName("PATCH COMMON400: 형식 오류, 필드 누락, 깨진 본문")
    void validation() throws Exception {
        patchNickname("{\"nickname\":\"가\"}", auth).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.data.errors[0].field").value("nickname"));
        patchNickname("{\"nickname\":\"뽈 뽈\"}", auth).andExpect(status().isBadRequest());
        patchNickname("{\"nickname\":\"열한글자닉네임입니다요\"}", auth).andExpect(status().isBadRequest());
        patchNickname("{}", auth).andExpect(status().isBadRequest());
        patchNickname("{\"nickname\":\"\"}", auth).andExpect(status().isBadRequest());
        patchNickname("not json", auth).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("UserService 공개 메서드: 표시 닉네임, 탈퇴/없음은 탈퇴한 사용자")
    void displayNicknames() {
        assertThat(userService.getDisplayNickname(userId)).isEqualTo("내닉네임");
        assertThat(userService.isActive(userId)).isTrue();
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', nickname = NULL WHERE id = ?", userId);
        assertThat(userService.isActive(userId)).isFalse();
        assertThat(userService.getDisplayNickname(userId)).isEqualTo(UserService.WITHDRAWN_NICKNAME);
        assertThat(userService.getDisplayNickname(424242L)).isEqualTo(UserService.WITHDRAWN_NICKNAME);
        assertThat(userService.getDisplayNicknames(java.util.List.of(userId, 424242L)))
                .containsEntry(userId, UserService.WITHDRAWN_NICKNAME)
                .containsEntry(424242L, UserService.WITHDRAWN_NICKNAME);
    }
}
