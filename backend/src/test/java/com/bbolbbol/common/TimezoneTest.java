package com.bbolbbol.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimezoneTest {

    @Autowired ObjectMapper om;
    @Autowired Clock clock;
    @Value("${spring.jackson.time-zone}") String jacksonZone;
    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone}") String hibernateZone;

    @Test
    @DisplayName("설정: Jackson/Hibernate 시간대는 Asia/Seoul")
    void configuredZones() {
        assertThat(jacksonZone).isEqualTo("Asia/Seoul");
        assertThat(hibernateZone).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("ObjectMapper 시간대는 Asia/Seoul")
    void objectMapperZone() {
        assertThat(om.getSerializationConfig().getTimeZone().getID()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("ZonedDateTime(UTC 자정)은 응답에서 ISO 문자열이고 Seoul 기준 09:00으로 변환된다")
    void zonedDateTimeSerialization() throws Exception {
        String json = om.writeValueAsString(
                java.time.ZonedDateTime.parse("2026-01-01T00:00:00Z"));
        // WRITE_DATES_AS_TIMESTAMPS 기본값(false)이면 문자열, true면 숫자. 실제 동작을 확인하고 시각을 비교한다.
        java.time.Instant instant = om.readValue(json, java.time.ZonedDateTime.class).toInstant();
        assertThat(instant).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(json).doesNotContain("[");
        assertThat(json).contains("2026-01-01T09:00");
    }

    @Test
    @DisplayName("컨텍스트 기동 후 JVM 기본 시간대는 Asia/Seoul")
    void jvmDefaultTimeZoneAfterContextStart() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("Clock 빈의 시간대는 Asia/Seoul")
    void clockBeanZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
