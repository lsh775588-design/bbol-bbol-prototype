package com.bbolbbol.auth;

import com.bbolbbol.user.NicknamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignupRequest(
        @NotBlank(message = "verificationToken은 필수입니다.")
        String verificationToken,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.FORMAT_MESSAGE)
        String nickname) {}
