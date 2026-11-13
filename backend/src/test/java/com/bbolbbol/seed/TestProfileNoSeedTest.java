package com.bbolbbol.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** test 프로파일은 seed-local(V900+)을 읽지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class TestProfileNoSeedTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void test_프로파일에는_시드_마이그레이션이_적용되지_않는다() {
        Integer seeded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE CAST(\"version\" AS INT) >= 900", Integer.class);
        assertThat(seeded).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE kakao_place_id LIKE 'SAMPLE-%'",
                Integer.class)).isZero();
    }
}
