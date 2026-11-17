package com.bbolbbol.server.global.seed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/** test 프로파일은 seed-local(V900+)을 읽지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class TestProfileNoSeedTest {

	@Autowired lateinit var jdbc: JdbcTemplate

	@Test
	fun `test 프로파일에는 시드 마이그레이션이 적용되지 않는다`() {
		val seeded = jdbc.queryForObject(
			"SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL AND CAST(\"version\" AS INT) >= 900",
			Int::class.java,
		)
		assertThat(seeded).isZero()
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE kakao_place_id LIKE 'SAMPLE-%'", Int::class.java)).isZero()
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE 'demo%@example.com'", Int::class.java)).isZero()
	}
}
