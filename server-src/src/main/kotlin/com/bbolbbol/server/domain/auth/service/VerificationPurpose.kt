package com.bbolbbol.server.domain.auth.service

/** 이메일 인증 용도. 토큰에 묶여서 가입용 토큰을 비밀번호 재설정에 쓸 수 없다. */
enum class VerificationPurpose { SIGNUP, PASSWORD_RESET }
