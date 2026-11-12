package com.bbolbbol.common.response;

import com.bbolbbol.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static final String CODE_OK = "COMMON200";
    public static final String CODE_CREATED = "COMMON201";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, CODE_OK, "요청이 성공했습니다.", data);
    }

    /** body의 code만 COMMON201이다. HTTP 상태는 200이므로 컨트롤러에서는 {@link #createdResponse}를 쓴다. */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, CODE_CREATED, "생성되었습니다.", data);
    }

    /**
     * HTTP 201 + body COMMON201. 회원가입, 도착 인증, 사진 업로드 등 명세가 201인 엔드포인트는
     * 반드시 이 메서드로 응답한다(created()만 반환하면 HTTP 상태가 200이 된다).
     */
    public static <T> ResponseEntity<ApiResponse<T>> createdResponse(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(created(data));
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }
}
