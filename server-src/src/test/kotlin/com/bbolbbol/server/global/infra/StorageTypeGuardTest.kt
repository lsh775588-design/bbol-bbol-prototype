package com.bbolbbol.server.global.infra

import com.bbolbbol.server.global.infra.storage.LocalFileStorage
import com.bbolbbol.server.global.infra.storage.StorageTypeGuard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource

class StorageTypeGuardTest {

	private fun rootCause(t: Throwable): Throwable = generateSequence(t) { it.cause }.last()

	@Test
	@DisplayName("storage.type이 없으면 기동이 실패하고 STORAGE_TYPE을 안내한다(로컬 디스크로 조용히 대체하지 않는다)")
	fun missingFailsStartup() {
		ApplicationContextRunner().withBean(StorageTypeGuard::class.java, "").run { ctx ->
			assertThat(ctx).hasFailed()
			assertThat(rootCause(ctx.startupFailure!!).message).contains("storage.type이 설정되지 않았습니다").contains("STORAGE_TYPE")
		}
	}

	@Test
	@DisplayName("프로퍼티가 아예 없어도 같은 이유로 기동이 실패한다")
	fun unsetPropertyFailsStartup() {
		ApplicationContextRunner().withUserConfiguration(StorageTypeGuard::class.java).run { ctx ->
			assertThat(ctx).hasFailed()
			assertThat(rootCause(ctx.startupFailure!!).message).contains("storage.type이 설정되지 않았습니다")
		}
	}

	@Test
	@DisplayName("지원하지 않는 값(s3 등)도 기동이 실패한다")
	fun unknownFailsStartup() {
		ApplicationContextRunner().withBean(StorageTypeGuard::class.java, "s3").run { ctx ->
			assertThat(ctx).hasFailed()
			assertThat(rootCause(ctx.startupFailure!!).message).contains("지원하지 않는 storage.type")
		}
	}

	@Test
	@DisplayName("storage.type=local이면 기동되고 로컬 저장소 빈이 만들어진다. 값이 없으면 로컬 저장소 빈은 조건에서 빠진다(matchIfMissing 제거)")
	fun localOk() {
		ApplicationContextRunner()
			.withUserConfiguration(StorageTypeGuard::class.java, LocalFileStorage::class.java)
			.withPropertyValues("storage.type=local", "storage.local.base-dir=build/test-uploads")
			.run { ctx ->
				assertThat(ctx).hasNotFailed()
				assertThat(ctx).hasSingleBean(LocalFileStorage::class.java)
			}
		ApplicationContextRunner()
			.withUserConfiguration(LocalFileStorage::class.java)
			.withPropertyValues("storage.local.base-dir=build/test-uploads")
			.run { ctx -> assertThat(ctx).doesNotHaveBean(LocalFileStorage::class.java) }
	}

	@Test
	@DisplayName("기본 설정에는 storage.type 기본값이 없고, local과 test 설정이 local로 명시한다")
	fun yamlDefaults() {
		fun value(file: String): Any? =
			YamlPropertySourceLoader().load(file, ClassPathResource(file)).firstNotNullOfOrNull { it.getProperty("storage.type") }
		assertThat(value("application.yml").toString()).isEqualTo("\${STORAGE_TYPE:}")
		assertThat(value("application-local.yml")).isEqualTo("local")
		assertThat(value("application-test.yml")).isEqualTo("local")
	}
}
