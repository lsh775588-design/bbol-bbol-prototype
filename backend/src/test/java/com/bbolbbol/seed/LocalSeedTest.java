package com.bbolbbol.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** local 프로파일의 개발용 시드(V900+)를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalSeedTest {

    static final String DEMO_PW = "demo1234!";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("샘플 장소가 로드되고 기본 정합성을 만족한다")
    void places() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM places", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE hot_type = 'CURATED'", Integer.class))
                .isGreaterThanOrEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM places p JOIN place_categories c ON c.id = p.category_id", Integer.class))
                .isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_categories", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM places WHERE lat NOT BETWEEN 37.56 AND 37.62"
                        + " OR lng NOT BETWEEN 126.94 AND 127.02 OR radius_m > notify_radius_m"
                        + " OR radius_m <= 0 OR kakao_place_id NOT LIKE 'SAMPLE-%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE name = '경복궁'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE reward_point = 150", Integer.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("데모 계정이 문서의 비밀번호로 로그인된다")
    void demoLogin() throws Exception {
        for (String email : List.of("demo1@example.com", "demo2@example.com", "demo4@example.com")) {
            mvc.perform(login(email, DEMO_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }
        mvc.perform(login("demo1@example.com", DEMO_PW))
                .andExpect(jsonPath("$.data.isNewUser").value(false));
        mvc.perform(login("demo2@example.com", DEMO_PW))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    @DisplayName("탈퇴 계정은 AUTH4011, 계정 상태가 시드와 일치한다")
    void withdrawn() throws Exception {
        mvc.perform(login("demo3@example.com", DEMO_PW))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH4011"));
        Map<String, Object> demo1 = jdbc.queryForMap(
                "SELECT status, nickname, onboarding_completed FROM users WHERE email = 'demo1@example.com'");
        assertThat(demo1.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(demo1.get("NICKNAME")).isNotNull();
        assertThat(demo1.get("ONBOARDING_COMPLETED")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = 'demo3@example.com'", String.class))
                .isEqualTo("WITHDRAWN");
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String email, String pw) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}");
    }
}
