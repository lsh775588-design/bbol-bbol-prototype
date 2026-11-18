plugins {
	// JDK 21이 없는 팀원 PC에서도 Gradle이 자동으로 내려받도록 설정
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "server"
