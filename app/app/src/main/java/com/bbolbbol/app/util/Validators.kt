package com.bbolbbol.app.util

/** 백엔드 검증 규칙(PasswordPolicy, NicknamePolicy)을 그대로 옮긴 클라이언트 사전 검증. 최종 판단은 서버가 한다. */
object Validators {
    private val PASSWORD = Regex("^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{8,20}$")
    private val NICKNAME = Regex("^[가-힣a-zA-Z0-9]{2,10}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    const val PASSWORD_MESSAGE = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 해요."
    const val NICKNAME_MESSAGE = "닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있어요."
    const val EMAIL_MESSAGE = "이메일 형식이 올바르지 않아요."

    fun isValidPassword(v: String) = PASSWORD.matches(v)
    fun isValidNickname(v: String) = NICKNAME.matches(v)
    fun isValidEmail(v: String) = EMAIL.matches(v.trim())
}
