package com.bbolbbol.app

import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.local.InMemoryTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionTest {
    @Test
    fun startsLoggedOutAndLogsInAndOut() {
        val store = InMemoryTokenStore()
        val s = AuthSession(store)
        assertFalse(s.loggedIn.value)
        s.saveTokens("a", "r")
        assertTrue(s.loggedIn.value)
        assertEquals("a", s.accessToken)
        assertEquals("r", store.refreshToken)
        s.clear()
        assertFalse(s.loggedIn.value)
        assertNull(store.accessToken)
        assertNull(store.refreshToken)
    }

    @Test
    fun restoresLoggedInStateFromStore() {
        val store = InMemoryTokenStore().apply { accessToken = "a" }
        assertTrue(AuthSession(store).loggedIn.value)
    }
}
