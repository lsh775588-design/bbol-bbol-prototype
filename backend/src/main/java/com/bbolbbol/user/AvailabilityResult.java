package com.bbolbbol.user;

/** 이메일/닉네임 사용 가능 여부 응답(data). reason은 사용 가능하면 null. */
public record AvailabilityResult(boolean available, String reason) {

    public static AvailabilityResult ok() {
        return new AvailabilityResult(true, null);
    }

    public static AvailabilityResult unavailable(String reason) {
        return new AvailabilityResult(false, reason);
    }
}
