package com.bbolbbol.common.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 기준 시간대(Asia/Seoul) 설정.
 * 모든 "오늘", "이번 주(월요일 시작)" 계산은 이 Clock을 주입받아 쓴다.
 * 인자 없는 LocalDate.now(), LocalDateTime.now() 등은 쓰지 않는다(테스트 고정과 시간대 일관성을 위해).
 */
@Configuration
public class TimeConfig {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    static {
        // main()을 거치지 않는 진입점(SpringBootTest, IDE 실행)에서도 컨텍스트가 뜨면 적용되도록 클래스 로딩 시 설정한다.
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_ZONE));
    }

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
