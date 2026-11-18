pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오맵 SDK v2(Android)
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/") {
            content { includeGroupByRegex("com\\.kakao.*") }
        }
    }
}
rootProject.name = "bbolbbol-app"
include(":app")
