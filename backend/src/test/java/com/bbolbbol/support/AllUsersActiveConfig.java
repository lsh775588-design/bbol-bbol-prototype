package com.bbolbbol.support;

import com.bbolbbol.common.security.UserStatusChecker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * common 보안 테스트용: DB에 없는 userId의 토큰도 통과시킨다.
 * user 도메인의 UserStatusChecker(DB 조회)를 대체하며, user/auth 통합 테스트에서는 쓰지 않는다.
 */
@TestConfiguration
public class AllUsersActiveConfig {

    @Bean
    @Primary
    UserStatusChecker allUsersActive() {
        return userId -> true;
    }
}
