package com.bbolbbol.auth;

import com.bbolbbol.common.response.ApiResponse;
import com.bbolbbol.user.AvailabilityResult;
import com.bbolbbol.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        TokenResponse data = authService.signup(request);
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(true, ApiResponse.CODE_CREATED, "회원가입에 성공했습니다.", data));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return new ApiResponse<>(true, ApiResponse.CODE_OK, "로그인에 성공했습니다.", authService.login(request));
    }

    @GetMapping("/email-availability")
    public ApiResponse<AvailabilityResult> emailAvailability(
            @RequestParam @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {
        return ApiResponse.success(userService.checkEmailAvailability(email));
    }

    @GetMapping("/nickname-availability")
    public ApiResponse<AvailabilityResult> nicknameAvailability(
            @RequestParam @NotBlank(message = "닉네임은 필수입니다.") String nickname) {
        return ApiResponse.success(userService.checkNicknameAvailability(nickname));
    }
}
