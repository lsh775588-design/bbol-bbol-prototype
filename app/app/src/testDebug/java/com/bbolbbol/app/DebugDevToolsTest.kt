package com.bbolbbol.app

import com.bbolbbol.app.ui.auth.DebugDevTools
import com.bbolbbol.app.ui.auth.DevKakaoTokenProvider
import com.bbolbbol.app.ui.auth.KakaoTokenProvider
import com.bbolbbol.app.ui.auth.buildDevVerificationToken
import com.bbolbbol.app.ui.auth.createDevTools
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** debug 변형에서만 컴파일/실행된다(src/testDebug). */
class DebugDevToolsTest {
    @Test
    fun debugBuildProvidesDevTools() {
        val tools = createDevTools()
        assertTrue(tools is DebugDevTools)
        assertTrue(tools.kakaoProvider is DevKakaoTokenProvider)
        assertEquals(KakaoTokenProvider.Mode.DEV_MANUAL, tools.kakaoProvider.mode)
        assertNotNull(tools.codeHint)
    }

    @Test
    fun devKakaoProviderReturnsTrimmedInputOrNull() = runBlocking {
        val p = DevKakaoTokenProvider()
        assertEquals("DEV-KAKAO|1|a@b.com|n", p.getToken("  DEV-KAKAO|1|a@b.com|n "))
        assertNull(p.getToken("   "))
        assertNull(p.getToken(null))
    }

    @Test
    fun buildTokenAppendsEmailOnlyWhenMissing() {
        assertEquals("dev:a@b.com", buildDevVerificationToken(" dev ", "a@b.com"))
        assertEquals("dev:x@y.com", buildDevVerificationToken("dev:x@y.com", "a@b.com"))
    }
}
