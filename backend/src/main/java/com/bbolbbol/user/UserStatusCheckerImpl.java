package com.bbolbbol.user;

import com.bbolbbol.common.security.UserStatusChecker;
import org.springframework.stereotype.Component;

/** ACTIVE 사용자만 통과. 탈퇴/없는 사용자의 유효 토큰은 JwtAuthenticationFilter에서 AUTH401이 된다. */
@Component
public class UserStatusCheckerImpl implements UserStatusChecker {

    private final UserService userService;

    public UserStatusCheckerImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean isActive(Long userId) {
        return userService.isActive(userId);
    }
}
