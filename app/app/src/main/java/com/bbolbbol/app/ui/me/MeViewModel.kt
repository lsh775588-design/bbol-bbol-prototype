package com.bbolbbol.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.MeResponse
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val loading: Boolean = true,
    val me: MeResponse? = null,
    val error: String? = null,
    val nicknameInput: String = "",
    val saving: Boolean = false,
    val nicknameMessage: String? = null,
    val nicknameIsError: Boolean = false,
)

class MeViewModel(
    private val users: UserRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = users.me()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, me = r.data, nicknameInput = r.data.nickname)
                }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }

    fun onNickname(v: String) = _state.update { it.copy(nicknameInput = v, nicknameMessage = null) }

    fun saveNickname() {
        val s = _state.value
        val nick = s.nicknameInput.trim()
        if (s.saving || nick == s.me?.nickname) return
        if (!Validators.isValidNickname(nick)) {
            _state.update { it.copy(nicknameMessage = Validators.NICKNAME_MESSAGE, nicknameIsError = true) }
            return
        }
        _state.update { it.copy(saving = true, nicknameMessage = null) }
        viewModelScope.launch {
            when (val r = users.changeNickname(nick)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        saving = false,
                        me = it.me?.copy(nickname = r.data.nickname),
                        nicknameInput = r.data.nickname,
                        nicknameMessage = "닉네임을 변경했어요.",
                        nicknameIsError = false,
                    )
                }
                else -> _state.update {
                    it.copy(saving = false, nicknameMessage = ErrorMessages.message(r), nicknameIsError = true)
                }
            }
        }
    }

    fun logout() = auth.logout()
}
