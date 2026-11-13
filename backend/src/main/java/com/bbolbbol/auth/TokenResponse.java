package com.bbolbbol.auth;

public record TokenResponse(Long userId, String accessToken, String refreshToken, boolean isNewUser) {}
