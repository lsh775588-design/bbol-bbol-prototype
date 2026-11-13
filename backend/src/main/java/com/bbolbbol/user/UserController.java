package com.bbolbbol.user;

import com.bbolbbol.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(MeResponse.from(userService.getActiveUser(userId)));
    }

    @PatchMapping("/me")
    public ApiResponse<NicknameChangeResponse> changeNickname(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody NicknameChangeRequest request) {
        User u = userService.changeNickname(userId, request.nickname());
        return new ApiResponse<>(true, ApiResponse.CODE_OK, "닉네임이 변경되었습니다.",
                new NicknameChangeResponse(u.getNickname(), u.nextNicknameChangeableAt()));
    }
}
