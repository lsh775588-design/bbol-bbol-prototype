package com.bbolbbol.server.global.db

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/** 실제 컨텍스트를 H2(PostgreSQL 모드)로 띄워서 Flyway 베이스라인이 적용되고 Hibernate validate가 통과하는지 확인 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayBaselineTest {

	@Autowired
	lateinit var jdbcTemplate: JdbcTemplate

	@Test
	fun `V0 베이스라인 마이그레이션이 성공적으로 적용된다`() {
		val count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '0' AND success = TRUE",
			Int::class.java,
		)
		assertEquals(1, count)
	}
}
