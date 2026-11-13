package com.bbolbbol.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 카카오 access token을 가져오는 방법. 진짜 SDK(키 필요)는 나중에 이 인터페이스의 구현으로 끼운다.
 * 서버는 이 토큰으로 카카오에 프로필을 물어본다.
 */
interface KakaoTokenProvider {
    val mode: Mode

    enum class Mode { SDK, DEV_MANUAL, UNAVAILABLE }

    /** [manualInput]은 DEV_MANUAL에서 사용자가 입력한 값. 못 가져오면 null. */
    suspend fun getToken(manualInput: String?): String?
}

/** release 기본값: 아직 카카오 SDK가 없다. 버튼은 "준비 중"으로 비활성화된다. */
class UnavailableKakaoTokenProvider : KakaoTokenProvider {
    override val mode = KakaoTokenProvider.Mode.UNAVAILABLE
    override suspend fun getToken(manualInput: String?): String? = null
}

data class KakaoLoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val showDevDialog: Boolean = false,
)

class KakaoLoginViewModel(
    private val repo: AuthExtraRepository,
    private val provider: KakaoTokenProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(KakaoLoginUiState())
    val state: StateFlow<KakaoLoginUiState> = _state.asStateFlow()

    val enabled get() = provider.mode != KakaoTokenProvider.Mode.UNAVAILABLE

    /** 버튼 클릭. 개발 제공자는 입력 다이얼로그를 먼저 띄운다. */
    fun onButton() {
        when (provider.mode) {
            KakaoTokenProvider.Mode.UNAVAILABLE -> Unit
            KakaoTokenProvider.Mode.DEV_MANUAL -> _state.update { it.copy(showDevDialog = true, error = null) }
            KakaoTokenProvider.Mode.SDK -> login(null)
        }
    }

    fun dismissDialog() = _state.update { it.copy(showDevDialog = false) }

    /** 성공하면 토큰이 저장되고(AuthSession) isNewUser면 내비게이션이 온보딩으로 보낸다. */
    fun login(manualInput: String?) {
        if (_state.value.loading || !enabled) return
        _state.update { it.copy(loading = true, error = null, showDevDialog = false) }
        viewModelScope.launch {
            val token = provider.getToken(manualInput)
            if (token == null) {
                _state.update { it.copy(loading = false, error = "카카오 토큰을 가져오지 못했어요.") }
                return@launch
            }
            val r = repo.kakaoLogin(token)
            _state.update {
                it.copy(loading = false, error = if (r is ApiResult.Success) null else ErrorMessages.message(r))
            }
        }
    }
}
