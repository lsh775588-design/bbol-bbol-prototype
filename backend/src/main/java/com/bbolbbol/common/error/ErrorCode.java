package com.bbolbbol.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    COMMON400("COMMON400", HttpStatus.BAD_REQUEST, "필드 누락, 형식 또는 범위 오류"),
    AUTH401("AUTH401", HttpStatus.UNAUTHORIZED, "access token이 없거나 유효하지 않음"),
    AUTH4011("AUTH4011", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않음"),
    AUTH4012("AUTH4012", HttpStatus.UNAUTHORIZED, "refresh token이 만료, 위조되었거나 이미 사용됨"),
    KAKAO401("KAKAO401", HttpStatus.UNAUTHORIZED, "카카오 토큰이 만료되었거나 유효하지 않음"),
    KAKAO502("KAKAO502", HttpStatus.BAD_GATEWAY, "카카오 서버 응답 실패"),
    USER404("USER404", HttpStatus.NOT_FOUND, "탈퇴했거나 존재하지 않는 사용자"),
    USER409("USER409", HttpStatus.CONFLICT, "이미 가입된 이메일"),
    USER4001("USER4001", HttpStatus.BAD_REQUEST, "닉네임에 금칙어 포함"),
    USER4002("USER4002", HttpStatus.BAD_REQUEST, "닉네임 변경 후 30일이 지나지 않음"),
    USER4092("USER4092", HttpStatus.CONFLICT, "이미 사용 중인 닉네임"),
    USER4093("USER4093", HttpStatus.CONFLICT, "카카오 이메일이 이미 이메일 가입 계정으로 등록됨"),
    EMAIL4001("EMAIL4001", HttpStatus.BAD_REQUEST, "인증 코드 불일치"),
    EMAIL4002("EMAIL4002", HttpStatus.BAD_REQUEST, "인증 코드 만료 또는 5번 틀려서 무효됨"),
    EMAIL4003("EMAIL4003", HttpStatus.BAD_REQUEST, "verificationToken이 없거나 만료됨, 또는 다른 용도의 토큰"),
    EMAIL429("EMAIL429", HttpStatus.TOO_MANY_REQUESTS, "인증 코드 재발송 제한"),
    EMAIL502("EMAIL502", HttpStatus.BAD_GATEWAY, "메일 발송 실패"),
    PLACE404("PLACE404", HttpStatus.NOT_FOUND, "존재하지 않는 장소"),
    COMPLETION4001("COMPLETION4001", HttpStatus.BAD_REQUEST, "인증 반경 밖"),
    COMPLETION4002("COMPLETION4002", HttpStatus.BAD_REQUEST, "GPS 정확도가 낮음 (100m 초과)"),
    COMPLETION4003("COMPLETION4003", HttpStatus.BAD_REQUEST, "모의 위치 사용"),
    COMPLETION4004("COMPLETION4004", HttpStatus.BAD_REQUEST, "사진 업로드 마감 시각이 지남"),
    COMPLETION403("COMPLETION403", HttpStatus.FORBIDDEN, "내 완료 기록이 아님"),
    COMPLETION404("COMPLETION404", HttpStatus.NOT_FOUND, "존재하지 않는 완료 기록"),
    COMPLETION409("COMPLETION409", HttpStatus.CONFLICT, "오늘 이미 이 장소를 인증함"),
    COMPLETION4091("COMPLETION4091", HttpStatus.CONFLICT, "이미 사진을 올림"),
    FILE4001("FILE4001", HttpStatus.BAD_REQUEST, "파일 누락 또는 지원하지 않는 형식"),
    FILE413("FILE413", HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량 10MB 초과"),

    // NOT IN SPEC: added minimal, needs team confirmation
    COMMON500("COMMON500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
