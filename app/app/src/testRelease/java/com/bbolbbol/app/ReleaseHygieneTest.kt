package com.bbolbbol.app

import com.bbolbbol.app.ui.auth.KakaoTokenProvider
import com.bbolbbol.app.ui.auth.NoDevTools
import com.bbolbbol.app.ui.auth.UnavailableKakaoTokenProvider
import com.bbolbbol.app.ui.auth.createDevTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** release 변형에서만 컴파일/실행된다(src/testRelease): 개발용 클래스가 release에 없음을 증명한다. */
class ReleaseHygieneTest {
    @Test
    fun releaseBuildHasNoDevTools() {
        val tools = createDevTools()
        assertTrue(tools is NoDevTools)
        assertTrue(tools.kakaoProvider is UnavailableKakaoTokenProvider)
        assertEquals(KakaoTokenProvider.Mode.UNAVAILABLE, tools.kakaoProvider.mode)
        assertNull(tools.codeHint)
    }

    @Test
    fun devClassesAreNotCompiledIntoRelease() {
        for (name in listOf(
            "com.bbolbbol.app.ui.auth.DevKakaoTokenProvider",
            "com.bbolbbol.app.ui.auth.DebugDevTools",
            "com.bbolbbol.app.ui.auth.DebugDevToolsKt",
        )) {
            try {
                Class.forName(name)
                fail("$name 이(가) release에 포함되어 있다")
            } catch (expected: ClassNotFoundException) {
                // 정상: debug 소스셋에만 있다.
            }
        }
    }
}
