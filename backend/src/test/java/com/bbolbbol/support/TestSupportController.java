package com.bbolbbol.support;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import com.bbolbbol.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 테스트 전용 컨트롤러. src/test에만 존재하므로 운영 클래스패스에는 포함되지 않는다. */
@RestController
@Validated
@RequestMapping("/test-support")
public class TestSupportController {

    public record Body(@NotBlank(message = "이름은 필수입니다.") String name,
                       @Min(value = 1, message = "1 이상이어야 합니다.") int age) {}

    @GetMapping("/whoami")
    public ApiResponse<Object> whoami() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return ApiResponse.success(a.getPrincipal());
    }

    @GetMapping("/principal-type")
    public ApiResponse<String> principalType() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return ApiResponse.success(a.getPrincipal().getClass().getName());
    }

    @PostMapping("/body")
    public ApiResponse<Void> body(@Valid @RequestBody Body body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/param")
    public ApiResponse<Void> param(@RequestParam @Min(1) @Max(10) int size) {
        return ApiResponse.success(null);
    }

    @GetMapping("/business")
    public ApiResponse<Void> business(@RequestParam(defaultValue = "false") boolean withData) {
        throw new BusinessException(ErrorCode.USER4092, withData ? Map.of("nickname", "dup") : null);
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("SECRET-INTERNAL-DETAIL-xyz");
    }

    @PostMapping(value = "/json-only", consumes = "application/json")
    public ApiResponse<Void> jsonOnly(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/status/{code}")
    public ApiResponse<Void> status(@PathVariable int code) {
        throw new ResponseStatusException(HttpStatus.valueOf(code), "reason-should-not-leak");
    }

    @PostMapping("/created")
    public ResponseEntity<ApiResponse<Map<String, Object>>> created() {
        return ApiResponse.createdResponse(Map.of("id", 1));
    }
}
