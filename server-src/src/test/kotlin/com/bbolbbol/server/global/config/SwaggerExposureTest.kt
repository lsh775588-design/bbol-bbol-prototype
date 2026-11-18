package com.bbolbbol.server.global.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** API 문서(OpenAPI, Swagger UI)는 기본 꺼짐이고 local, test에서만 켜진다. */
class SwaggerExposureTest {

	private fun yamlValue(file: String, key: String): Any? =
		YamlPropertySourceLoader().load(file, ClassPathResource(file)).firstNotNullOfOrNull { it.getProperty(key) }

	@Test
	@DisplayName("기본 설정(application.yml)은 api-docs와 swagger-ui를 끄고, local과 test 설정만 켠다")
	fun defaults() {
		assertThat(yamlValue("application.yml", "springdoc.api-docs.enabled")).isEqualTo(false)
		assertThat(yamlValue("application.yml", "springdoc.swagger-ui.enabled")).isEqualTo(false)
		for (f in listOf("application-local.yml", "application-test.yml")) {
			assertThat(yamlValue(f, "springdoc.api-docs.enabled")).`as`(f).isEqualTo(true)
			assertThat(yamlValue(f, "springdoc.swagger-ui.enabled")).`as`(f).isEqualTo(true)
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	inner class EnabledInTest {
		@Autowired lateinit var mvc: MockMvc

		@Test
		@DisplayName("test 프로파일에서는 /v3/api-docs가 열려 있다")
		fun apiDocsAvailable() {
			mvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andExpect(jsonPath("$.openapi").exists())
		}
	}

	@Nested
	@SpringBootTest(properties = ["springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"])
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	inner class DisabledByDefaultValues {
		@Autowired lateinit var mvc: MockMvc

		@Test
		@DisplayName("꺼져 있으면(기본값) /v3/api-docs와 Swagger UI 경로는 존재하지 않는다(404, 보안 경로 규칙은 그대로라 401은 아니다)")
		fun notExposed() {
			for (path in listOf("/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html")) {
				val s = mvc.perform(get(path)).andReturn().response.status
				assertThat(s).`as`(path).isEqualTo(404)
			}
		}
	}
}
