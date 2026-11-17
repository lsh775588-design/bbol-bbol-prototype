import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 서버 주소: -PapiBaseUrl=http://10.0.2.2:8081/ 로 덮어쓸 수 있다(끝에 / 필수).
val apiBaseUrl: String? = providers.gradleProperty("apiBaseUrl").orNull

// 카카오 네이티브 앱 키: app/local.properties 의 KAKAO_NATIVE_APP_KEY (gitignore 대상). 없으면 빈 문자열로 두고 지도 화면에서 안내한다.
val kakaoNativeAppKey: String = rootProject.file("local.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } }.getProperty("KAKAO_NATIVE_APP_KEY") }
    ?.trim()?.removeSurrounding("\"")
    .orEmpty()
// BuildConfig 문자열 리터럴에 안전하게 들어가도록 \ 와 " 를 이스케이프한다.
val kakaoKeyLiteral: String = kakaoNativeAppKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.bbolbbol.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bbolbbol.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // 카카오맵 SDK 네이티브 라이브러리는 arm만 제공한다(x86 없음). x86_64 라이브러리가 섞이면 x86_64 에뮬레이터가
        // x86_64로 실행하려다 지도 라이브러리를 못 찾으므로 arm만 넣는다(에뮬레이터는 ARM 변환 이미지 필요).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl ?: "http://10.0.2.2:8080/"}\"")
            buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKeyLiteral\"")
        }
        release {
            isMinifyEnabled = false
            // 운영 주소는 미정. 반드시 https. 배포 전에 -PapiBaseUrl 로 지정한다.
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl ?: "https://api.bbolbbol.invalid/"}\"")
            buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKeyLiteral\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kakao.map)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
