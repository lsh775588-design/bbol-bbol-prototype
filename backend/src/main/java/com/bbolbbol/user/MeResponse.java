package com.bbolbbol.user;

import java.time.LocalDateTime;

public record MeResponse(Long userId, String email, String nickname, String loginType,
                         boolean onboardingCompleted, LocalDateTime createdAt) {

    static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getEmail(), u.getNickname(), u.getLoginType().name(),
                u.isOnboardingCompleted(), u.getCreatedAt());
    }
}
