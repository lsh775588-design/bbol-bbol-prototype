package com.bbolbbol.auth;

/** 비밀번호 규칙: 8~20자, 영문 + 숫자 + 특수문자 각 1개 이상(공백 없는 ASCII 문자만). */
public final class PasswordPolicy {

    public static final String REGEX =
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{8,20}$";
    public static final String MESSAGE = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.";

    private PasswordPolicy() {}
}
