package com.bbolbbol.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetStep { EMAIL, CODE, PASSWORD }

data class ResetPasswordState(
    val newPassword: String = "",
    val passwordError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
    /** 재설정 성공. 화면이 로그인으로 돌아가며 성공 메시지를 보여준다. */
    val done: Boolean = false,
)

data class PasswordResetFlowState(
    val step: ResetStep = ResetStep.EMAIL,
    val code: CodeStepState = CodeStepState(),
    val reset: ResetPasswordState = ResetPasswordState(),
)

class PasswordResetViewModel(private val repo: AuthExtraRepository) : ViewModel() {
    val codeFlow = EmailCodeController(repo, EmailPurpose.PASSWORD_RESET, viewModelScope)
    private val _reset = MutableStateFlow(ResetPasswordState())

    val state: StateFlow<PasswordResetFlowState> = combine(codeFlow.state, _reset) { c, r ->
        val step = when {
            c.verified -> ResetStep.PASSWORD
            c.sent -> ResetStep.CODE
            else -> ResetStep.EMAIL
        }
        PasswordResetFlowState(step, c, r)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PasswordResetFlowState())

    fun onPassword(v: String) = _reset.update { it.copy(newPassword = v, passwordError = null, formError = null) }

    fun submit() {
        val token = codeFlow.state.value.verificationToken ?: return
        val r = _reset.value
        if (r.loading || r.done) return
        if (!Validators.isValidPassword(r.newPassword)) {
            _reset.update { it.copy(passwordError = Validators.PASSWORD_MESSAGE) }
            return
        }
        _reset.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            when (val res = repo.resetPassword(token, r.newPassword)) {
                is ApiResult.Success -> _reset.update { it.copy(loading = false, done = true) }
                is ApiResult.Failure -> {
                    if (res.code == "EMAIL4003") {
                        _reset.update { it.copy(loading = false) }
                        codeFlow.invalidateToken(ErrorMessages.message(res))
                    } else {
                        val fields = ErrorMessages.fieldErrors(res)
                        _reset.update {
                            it.copy(
                                loading = false,
                                passwordError = fields["newPassword"],
                                formError = if (fields.isEmpty()) ErrorMessages.message(res) else null,
                            )
                        }
                    }
                }
                ApiResult.NetworkError ->
                    _reset.update { it.copy(loading = false, formError = ErrorMessages.message(res)) }
            }
        }
    }
}
