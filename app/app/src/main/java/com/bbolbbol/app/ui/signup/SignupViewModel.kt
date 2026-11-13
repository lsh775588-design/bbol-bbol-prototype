package com.bbolbbol.app.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val nickname: String = "",
    /** 개발용 인증 토큰. debug 빌드 화면에서만 입력받는다. */
    val devToken: String = "",
    val emailError: String? = null,
    val emailOk: Boolean = false,
    val passwordError: String? = null,
    val nicknameError: String? = null,
    val nicknameOk: Boolean = false,
    val tokenError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
)

class SignupViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, emailError = null, emailOk = false, formError = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, passwordError = null, formError = null) }
    fun onNickname(v: String) =
        _state.update { it.copy(nickname = v, nicknameError = null, nicknameOk = false, formError = null) }
    fun onDevToken(v: String) = _state.update { it.copy(devToken = v, tokenError = null, formError = null) }

    /** 이메일 입력칸에서 포커스가 빠질 때. */
    fun checkEmail() {
        val email = _state.value.email.trim()
        if (email.isEmpty()) return
        if (!Validators.isValidEmail(email)) {
            _state.update { it.copy(emailError = Validators.EMAIL_MESSAGE) }
            return
        }
        viewModelScope.launch {
            when (val r = repo.emailAvailability(email)) {
                is ApiResult.Success -> _state.update {
                    if (it.email.trim() != email) it
                    else if (r.data.available) it.copy(emailOk = true, emailError = null)
                    else it.copy(emailOk = false, emailError = r.data.reason ?: "사용할 수 없는 이메일이에요.")
                }
                else -> _state.update { it.copy(emailError = ErrorMessages.message(r)) }
            }
        }
    }

    /** 닉네임 입력칸에서 포커스가 빠질 때. */
    fun checkNickname() {
        val nick = _state.value.nickname.trim()
        if (nick.isEmpty()) return
        if (!Validators.isValidNickname(nick)) {
            _state.update { it.copy(nicknameError = Validators.NICKNAME_MESSAGE) }
            return
        }
        viewModelScope.launch {
            when (val r = repo.nicknameAvailability(nick)) {
                is ApiResult.Success -> _state.update {
                    if (it.nickname.trim() != nick) it
                    else if (r.data.available) it.copy(nicknameOk = true, nicknameError = null)
                    else it.copy(nicknameOk = false, nicknameError = r.data.reason ?: "사용할 수 없는 닉네임이에요.")
                }
                else -> _state.update { it.copy(nicknameError = ErrorMessages.message(r)) }
            }
        }
    }

    /**
     * 가입 요청. 백엔드 SignupRequest에는 email이 없어서 이메일은 인증 토큰에 들어 있어야 한다.
     * 로컬 개발 서버의 토큰 형식은 "{고정토큰}:{이메일}"이라, 개발용 입력에 ':'이 없으면 이메일을 붙여 준다.
     */
    fun submit() {
        val s = _state.value
        if (s.loading) return
        val passwordError = if (Validators.isValidPassword(s.password)) null else Validators.PASSWORD_MESSAGE
        val nicknameError = if (Validators.isValidNickname(s.nickname.trim())) null else Validators.NICKNAME_MESSAGE
        val emailError = if (Validators.isValidEmail(s.email)) null else Validators.EMAIL_MESSAGE
        val tokenError = if (s.devToken.isBlank()) "이메일 인증이 필요해요. (인증 흐름은 아직 구현되지 않았어요)" else null
        if (listOf(passwordError, nicknameError, emailError, tokenError).any { it != null }) {
            _state.update {
                it.copy(
                    passwordError = passwordError,
                    nicknameError = nicknameError,
                    emailError = emailError,
                    tokenError = tokenError,
                )
            }
            return
        }
        val token = buildToken(s.devToken, s.email)
        _state.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            val r = repo.signup(token, s.password, s.nickname)
            if (r is ApiResult.Success) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { st ->
                var next = st.copy(loading = false)
                if (r is ApiResult.Failure) {
                    val fields = ErrorMessages.fieldErrors(r)
                    next = next.copy(
                        passwordError = fields["password"],
                        nicknameError = fields["nickname"],
                        tokenError = fields["verificationToken"],
                    )
                    when (r.code) {
                        "USER4001", "USER4092" -> next = next.copy(nicknameError = ErrorMessages.message(r))
                        "USER409" -> next = next.copy(emailError = ErrorMessages.message(r))
                        "EMAIL4003" -> next = next.copy(tokenError = ErrorMessages.message(r))
                    }
                    if (r.code == "COMMON400" && fields.isEmpty()) next = next.copy(formError = ErrorMessages.message(r))
                    if (r.code !in FIELD_CODES && r.code != "COMMON400") next = next.copy(formError = ErrorMessages.message(r))
                } else {
                    next = next.copy(formError = ErrorMessages.message(r))
                }
                next
            }
        }
    }

    companion object {
        private val FIELD_CODES = setOf("USER4001", "USER4092", "USER409", "EMAIL4003")

        fun buildToken(devToken: String, email: String): String {
            val t = devToken.trim()
            return if (t.contains(':')) t else "$t:${email.trim()}"
        }
    }
}
