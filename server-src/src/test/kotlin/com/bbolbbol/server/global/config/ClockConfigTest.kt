package com.bbolbbol.server.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.ZoneId
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
class ClockConfigTest {

	@Autowired
	lateinit var clock: Clock

	@Value("\${spring.jpa.properties.hibernate.jdbc.time_zone}")
	lateinit var hibernateZone: String

	@Test
	fun `Clock 빈의 시간대는 Asia Seoul`() {
		assertEquals(ZoneId.of("Asia/Seoul"), clock.zone)
	}

	@Test
	fun `Hibernate JDBC 시간대 설정은 Asia Seoul`() {
		assertEquals("Asia/Seoul", hibernateZone)
	}
}
