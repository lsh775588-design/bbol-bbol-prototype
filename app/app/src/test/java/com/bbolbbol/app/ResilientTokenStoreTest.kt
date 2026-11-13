package com.bbolbbol.app

import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.InMemoryTokenStore
import com.bbolbbol.app.data.local.ResilientTokenStore
import com.bbolbbol.app.data.local.TokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class ResilientTokenStoreTest {
    /** 처음 열 때만 실패하는 저장소 공장(키스토어 손상을 흉내). */
    private class BrokenOnce {
        var opens = 0
        var wipes = 0
        val healthy = InMemoryTokenStore()
        fun open(): TokenStore {
            opens++
            if (wipes == 0) throw GeneralSecurityException("keystore broken")
            return healthy
        }
        fun wipe() { wipes++ }
    }

    @Test
    fun startupFailureWipesAndRecreatesEmptyStoreAsLoggedOut() {
        val f = BrokenOnce()
        val store = ResilientTokenStore(f::open, f::wipe)
        assertEquals(1, f.wipes)
        assertEquals(2, f.opens)
        assertTrue(store.recovered)
        assertNull(store.accessToken)
        assertFalse(AuthSession(store).loggedIn.value) // 로그아웃 상태, 크래시 없음
        // 복구 뒤에는 정상 저장소처럼 동작한다.
        store.accessToken = "a"
        store.refreshToken = "r"
        assertEquals("a", f.healthy.accessToken)
        store.clear()
        assertNull(f.healthy.refreshToken)
    }

    @Test
    fun healthyStoreIsNotWiped() {
        val mem = InMemoryTokenStore().apply { accessToken = "a" }
        var wipes = 0
        val store = ResilientTokenStore({ mem }, { wipes++ })
        assertEquals("a", store.accessToken)
        assertEquals(0, wipes)
        assertFalse(store.recovered)
    }

    @Test
    fun runtimeReadFailureRecoversAndReturnsNull() {
        var broken = true
        var wipes = 0
        val flaky = object : TokenStore {
            override var accessToken: String?
                get() = if (broken) throw GeneralSecurityException("bad tag") else "x"
                set(_) {}
            override var refreshToken: String? = null
            override fun clear() {}
        }
        val fresh = InMemoryTokenStore()
        var opened = 0
        val store = ResilientTokenStore({ if (opened++ == 0) flaky else fresh }, { wipes++; broken = false })
        assertNull(store.accessToken) // 읽다 실패 -> 복구 -> null
        assertEquals(1, wipes)
        assertTrue(store.recovered)
        store.accessToken = "n"
        assertEquals("n", fresh.accessToken)
    }

    @Test
    fun fallsBackToMemoryWhenRecreationAlsoFails() {
        val store = ResilientTokenStore({ throw IllegalStateException("no keystore") }, {})
        assertNull(store.accessToken)
        store.accessToken = "a"
        assertEquals("a", store.accessToken) // 메모리 저장소로 동작(앱은 계속 쓸 수 있다)
    }
}
