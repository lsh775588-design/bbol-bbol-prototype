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
    @Volatile override var accessToken: String? = null
    @Volatile override var refreshToken: String? = null
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

    companion object {
        private const val FILE = "bbol_secure_tokens"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"

        /**
         * 앱이 쓰는 저장소. Keystore 키가 깨져도(백업 복원, OS 업데이트) 앱이 죽지 않고
         * 저장 파일과 마스터 키를 지운 뒤 빈 저장소(= 로그아웃 상태)로 다시 만든다.
         */
        fun createResilient(context: Context): TokenStore =
            ResilientTokenStore(open = { EncryptedTokenStore(context) }, wipe = { wipe(context) })

        /** 깨진 저장 파일과 Keystore 마스터 키 항목을 지운다. 실패는 무시한다(다시 만들 때 드러난다). */
        fun wipe(context: Context) {
            runCatching { context.applicationContext.deleteSharedPreferences(FILE) }
            runCatching {
                java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }

        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context, FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

/**
 * 저장소를 열거나 읽고 쓰다가 예외(키 손상 등)가 나면 [wipe] 후 다시 열고, 그래도 안 되면 메모리 저장소로 물러난다.
 * 실패한 읽기는 null(로그아웃)이다. 예외 메시지에는 토큰이 없지만 어디에도 기록하지 않는다.
 */
class ResilientTokenStore(
    private val open: () -> TokenStore,
    private val wipe: () -> Unit,
) : TokenStore {
    @Volatile private var delegate: TokenStore = openOrWipe()

    /** 복구가 한 번이라도 일어났는가(진단/테스트용). */
    @Volatile var recovered: Boolean = false
        private set

    private fun openOrWipe(): TokenStore = try {
        open()
    } catch (e: Exception) {
        recovered = true
        try {
            wipe()
            open()
        } catch (e2: Exception) {
            InMemoryTokenStore()
        }
    }

    private inline fun <T> guard(onFail: T, op: (TokenStore) -> T): T = try {
        op(delegate)
    } catch (e: Exception) {
        recovered = true
        runCatching { wipe() }
        delegate = try { open() } catch (e2: Exception) { InMemoryTokenStore() }
        onFail
    }

    override var accessToken: String?
        get() = guard(null) { it.accessToken }
        set(value) = guard(Unit) { it.accessToken = value }

    override var refreshToken: String?
        get() = guard(null) { it.refreshToken }
        set(value) = guard(Unit) { it.refreshToken = value }

    override fun clear() = guard(Unit) { it.clear() }
}

/** 로그인 상태의 단일 출처. 토큰 저장/삭제와 "로그인됨" 상태 흐름을 묶는다. */
class AuthSession(private val store: TokenStore) {
    private val _loggedIn = MutableStateFlow(!store.accessToken.isNullOrEmpty())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    val accessToken: String? get() = store.accessToken
    val refreshToken: String? get() = store.refreshToken

    private val _onboardingRequired = MutableStateFlow(false)

    /** 방금 로그인/가입한 사용자가 온보딩을 마치지 않았는가. 내비게이션이 온보딩 화면으로 보낸다. */
    val onboardingRequired: StateFlow<Boolean> = _onboardingRequired.asStateFlow()

    /** 카카오 첫 로그인에서 서버가 준 닉네임 제안. 온보딩 화면의 닉네임 칸을 채운다. */
    @Volatile var suggestedNickname: String? = null
        private set

    /** saveTokens보다 먼저 부른다(로그인 상태가 바뀌기 전에 갈 곳이 정해져 있어야 한다). */
    fun markOnboarding(required: Boolean, suggestedNickname: String? = null) {
        this.suggestedNickname = suggestedNickname
        _onboardingRequired.value = required
    }

    private val _sessionExpired = MutableStateFlow(false)

    /** 세션 만료(재발급 실패, AUTH401)로 로그인 화면에 돌아왔는가. 로그인 화면이 안내 문구를 보여준다. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun saveTokens(access: String, refresh: String) {
        store.accessToken = access
        store.refreshToken = refresh
        _sessionExpired.value = false
        _loggedIn.value = true
    }

    /** 서버가 세션을 거절해서(재발급 실패, AUTH401) 로그아웃된 경우. 로그인 화면에 이유를 알린다. */
    fun expire() {
        val wasLoggedIn = _loggedIn.value
        clear()
        if (wasLoggedIn) _sessionExpired.value = true
    }

    /** 로그아웃 또는 AUTH401. 재발급 API가 없으므로 토큰을 지우고 로그인으로 보낸다. */
    fun clear() {
        store.clear()
        _onboardingRequired.value = false
        suggestedNickname = null
        _sessionExpired.value = false
        _loggedIn.value = false
    }
}
