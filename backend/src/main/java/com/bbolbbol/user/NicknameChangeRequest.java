package com.bbolbbol.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NicknameChangeRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.FORMAT_MESSAGE)
        String nickname) {}
