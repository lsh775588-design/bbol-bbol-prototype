package com.bbolbbol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BbolbbolApplication {

    public static void main(String[] args) {
        // JVM 기본 시간대(Asia/Seoul)는 common.config.TimeConfig가 설정한다.
        SpringApplication.run(BbolbbolApplication.class, args);
    }
}
