package com.bbolbbol.common.response;

import com.bbolbbol.common.error.ErrorCode;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static final String CODE_OK = "COMMON200";
    public static final String CODE_CREATED = "COMMON201";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, CODE_OK, "요청이 성공했습니다.", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, CODE_CREATED, "생성되었습니다.", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }
}
