package com.bbolbbol.server.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * "오늘", "이번 주" 같은 시간 계산은 LocalDate.now() 대신 이 Clock을 주입받아 쓰면 됨
 *   LocalDate.now(clock)
 * 테스트에서 원하는 시각으로 고정할 수 있어서 "하루 1회 인증" 같은 로직을 테스트하기 쉬워짐
 */
@Configuration
class ClockConfig {

	@Bean
	fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
