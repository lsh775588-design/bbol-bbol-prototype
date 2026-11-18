package com.bbolbbol.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import java.util.TimeZone

// UserDetailsServiceAutoConfiguration 제외: JWT로 인증하므로 Spring 기본 로그인 계정(임시 비밀번호)이 필요 없음
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class ServerApplication

fun main(args: Array<String>) {
	// 하루의 기준은 한국 시간 0시. Spring이 뜨기 전에 설정해야 모든 빈에 적용됨
	TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
	runApplication<ServerApplication>(*args)
}
