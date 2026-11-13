package com.bbolbbol.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    /** null이면 아직 모르거나 불러오지 못했다. */
    val onboardingCompleted: Boolean? = null,
    val busy: Boolean = false,
    val showWithdrawDialog: Boolean = false,
    val error: String? = null,
)

/** 마이 화면의 로그아웃, 회원 탈퇴, 온보딩 상태. 성공하면 AuthSession이 비워져 내비게이션이 로그인으로 보낸다. */
class AccountViewModel(
    private val extra: AuthExtraRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()


    fun refreshOnboarding() {
        viewModelScope.launch {
            val r = users.me()
            if (r is ApiResult.Success) _state.update { it.copy(onboardingCompleted = r.data.onboardingCompleted) }
        }
    }

    /** 서버 호출이 실패해도 토큰은 지워진다. */
    fun logout() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            extra.logout()
            _state.update { it.copy(busy = false) }
        }
    }

    fun askWithdraw() = _state.update { it.copy(showWithdrawDialog = true, error = null) }
    fun dismissWithdraw() = _state.update { it.copy(showWithdrawDialog = false) }

    fun confirmWithdraw() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, showWithdrawDialog = false, error = null) }
        viewModelScope.launch {
            val r = extra.withdraw()
            _state.update {
                it.copy(busy = false, error = if (r is ApiResult.Success) null else ErrorMessages.message(r))
            }
        }
    }
}
