package com.bbolbbol.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** access/refresh 토큰 저장소. 토큰 값을 로그나 toString에 노출하지 않는다. */
interface TokenStore {
    var accessToken: String?
    var refreshToken: String?
    fun clear()
}

class InMemoryTokenStore : TokenStore {
    override var accessToken: String? = null
    override var refreshToken: String? = null
    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}

/** EncryptedSharedPreferences(Android Keystore 마스터 키) 기반 저장소. */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences = create(context.applicationContext)

    override var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putOrRemove(KEY_ACCESS, value).apply()

    override var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putOrRemove(KEY_REFRESH, value).apply()

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)

    private companion object {
        const val FILE = "bbol_secure_tokens"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"

        fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context, FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

/** 로그인 상태의 단일 출처. 토큰 저장/삭제와 "로그인됨" 상태 흐름을 묶는다. */
class AuthSession(private val store: TokenStore) {
    private val _loggedIn = MutableStateFlow(!store.accessToken.isNullOrEmpty())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    val accessToken: String? get() = store.accessToken

    fun saveTokens(access: String, refresh: String) {
        store.accessToken = access
        store.refreshToken = refresh
        _loggedIn.value = true
    }

    /** 로그아웃 또는 AUTH401. 재발급 API가 없으므로 토큰을 지우고 로그인으로 보낸다. */
    fun clear() {
        store.clear()
        _loggedIn.value = false
    }
}
