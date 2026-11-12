package com.bbolbbol.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayBaselineTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 베이스라인_마이그레이션이_성공적으로_적용된다() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '0' AND \"success\" = TRUE",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
