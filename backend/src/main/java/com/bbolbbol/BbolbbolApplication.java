package com.bbolbbol;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BbolbbolApplication {

    public static void main(String[] args) {
        // 서비스 기준 시간대는 항상 Asia/Seoul (주 시작은 월요일)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(BbolbbolApplication.class, args);
    }
}
