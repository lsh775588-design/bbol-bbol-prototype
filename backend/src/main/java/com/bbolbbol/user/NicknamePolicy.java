package com.bbolbbol.user;

import java.util.regex.Pattern;

/** 닉네임 형식 규칙: 2~10자, 한글(완성형)/영문/숫자. */
public final class NicknamePolicy {

    public static final String REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    public static final String FORMAT_MESSAGE = "닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있습니다.";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private NicknamePolicy() {}

    public static boolean isValidFormat(String nickname) {
        return nickname != null && PATTERN.matcher(nickname).matches();
    }
}
