package com.bbolbbol.server.global.seed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** local 프로파일의 개발용 시드(V900+)를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalSeedTest {

	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var mvc: MockMvc

	private fun count(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

	private fun login(email: String, pw: String): ResultActions =
		mvc.perform(
			post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""{"email":"$email","password":"$pw"}"""),
		)

	@Test
	@DisplayName("샘플 장소가 로드되고 기본 정합성을 만족한다")
	fun places() {
		val total = count("SELECT COUNT(*) FROM places")
		assertThat(total).isGreaterThanOrEqualTo(20)
		assertThat(count("SELECT COUNT(*) FROM places WHERE hot_type = 'CURATED'")).isGreaterThanOrEqualTo(10)
		assertThat(count("SELECT COUNT(*) FROM places p JOIN place_categories c ON c.id = p.category_id")).isEqualTo(total)
		assertThat(count("SELECT COUNT(*) FROM place_categories")).isEqualTo(8)
		assertThat(
			count(
				"SELECT COUNT(*) FROM places WHERE lat NOT BETWEEN 37.56 AND 37.62" +
					" OR lng NOT BETWEEN 126.94 AND 127.02 OR radius_m > notify_radius_m" +
					" OR radius_m <= 0 OR kakao_place_id NOT LIKE 'SAMPLE-%'",
			),
		).isZero()
		assertThat(count("SELECT COUNT(*) FROM places WHERE name = '경복궁'")).isEqualTo(1)
		assertThat(count("SELECT COUNT(*) FROM places WHERE reward_point = 150")).isGreaterThanOrEqualTo(2)
	}

	@Test
	@DisplayName("데모 계정이 문서의 비밀번호로 로그인된다")
	fun demoLogin() {
		for (email in listOf("demo1@example.com", "demo2@example.com", "demo4@example.com")) {
			login(email, DEMO_PW)
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty)
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
		}
	}

	@Test
	@DisplayName("탈퇴 계정은 AUTH4011이고 계정 상태가 시드와 일치한다")
	fun withdrawn() {
		login("demo3@example.com", DEMO_PW)
			.andExpect(status().isUnauthorized)
			.andExpect(jsonPath("$.code").value("AUTH4011"))
		val demo1 = jdbc.queryForMap(
			"SELECT id, status, nickname, onboarding_completed FROM users WHERE email = 'demo1@example.com'",
		)
		assertThat((demo1["id"] as Number).toLong()).isEqualTo(1L)
		assertThat(demo1["status"]).isEqualTo("ACTIVE")
		assertThat(demo1["nickname"]).isNotNull
		assertThat(demo1["onboarding_completed"]).isEqualTo(true)
		assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = 'demo3@example.com'", String::class.java))
			.isEqualTo("WITHDRAWN")
		assertThat(count("SELECT COUNT(*) FROM users WHERE nickname IS NULL")).isEqualTo(1)
	}

	@Test
	@DisplayName("도착 인증 시드: demo1은 4건 1050점, demo4는 3건 750점이고 내역 합계와 일치하며 경복궁은 demo1이 방문한 적 없다")
	fun completionsSeed() {
		fun sum(email: String) = count(
			"SELECT COALESCE(SUM(h.point), 0) FROM point_histories h JOIN users u ON u.id = h.user_id WHERE u.email = '$email'",
		)
		fun earned(email: String) = count(
			"SELECT COALESCE(SUM(c.earned_point), 0) FROM completions c JOIN users u ON u.id = c.user_id WHERE u.email = '$email'",
		)
		assertThat(count("SELECT COUNT(*) FROM completions")).isEqualTo(7)
		assertThat(count("SELECT COUNT(*) FROM point_histories")).isEqualTo(7)
		assertThat(sum("demo1@example.com")).isEqualTo(1050).isEqualTo(earned("demo1@example.com"))
		assertThat(sum("demo4@example.com")).isEqualTo(750).isEqualTo(earned("demo4@example.com"))
		assertThat(
			count(
				"SELECT COUNT(*) FROM completions c JOIN places p ON p.id = c.place_id JOIN users u ON u.id = c.user_id" +
					" WHERE u.email = 'demo1@example.com' AND p.name = '경복궁'",
			),
		).isZero()
		assertThat(count("SELECT COUNT(*) FROM completions WHERE completed_at >= CURRENT_TIMESTAMP")).isZero()
	}

	companion object {
		const val DEMO_PW = "demo1234!"
	}
}
