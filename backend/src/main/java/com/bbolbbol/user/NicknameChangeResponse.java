package com.bbolbbol.user;

import java.time.LocalDateTime;

public record NicknameChangeResponse(String nickname, LocalDateTime nextChangeableAt) {}
