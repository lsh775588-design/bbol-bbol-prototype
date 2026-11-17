package com.bbolbbol.server.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * 오른쪽 위 Authorize 버튼에 access token을 넣으면 보호된 API도 호출해 볼 수 있음
 */
@Configuration
class SwaggerConfig {

	@Bean
	fun openApi(): OpenAPI =
		OpenAPI()
			.info(Info().title("뽈뽈 API").version("v1"))
			.components(
				Components().addSecuritySchemes(
					BEARER_AUTH,
					SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT"),
				),
			)
			.addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

	companion object {
		private const val BEARER_AUTH = "bearerAuth"
	}
}
