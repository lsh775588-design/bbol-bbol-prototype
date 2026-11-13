package com.bbolbbol.app.ui.auth

import androidx.compose.runtime.Composable

/**
 * 개발 서버 전용 지름길 모음. 구현(`createDevTools()`)은 빌드 변형마다 다른 소스셋에 있다:
 * debug는 src/debug(DebugDevTools), release는 src/release(아무것도 하지 않는 NoDevTools).
 * 개발용 클래스가 release에 컴파일되지 않게 하려고 main에는 이 인터페이스와 빈 구현만 둔다.
 */
interface DevTools {
    val kakaoProvider: KakaoTokenProvider

    /** 코드 입력 화면에 보여줄 개발 안내. release는 null. */
    val codeHint: String?

    /** 가입 첫 화면의 "개발용 인증 토큰" 지름길. [onToken]에 서버에 보낼 verificationToken을 넘긴다. */
    @Composable
    fun SignupShortcut(email: String, onToken: (String) -> Unit)

    /** 개발용 카카오 토큰 입력창. */
    @Composable
    fun KakaoDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit)
}

/** release 구현: 지름길 없음, 카카오 버튼은 "준비 중". */
class NoDevTools : DevTools {
    override val kakaoProvider: KakaoTokenProvider = UnavailableKakaoTokenProvider()
    override val codeHint: String? = null

    @Composable
    override fun SignupShortcut(email: String, onToken: (String) -> Unit) = Unit

    @Composable
    override fun KakaoDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) = Unit
}
