package com.bbolbbol.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.data.repository.AuthRepository
import com.bbolbbol.app.ui.signup.SignupViewModel
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignupStep { EMAIL, CODE, PROFILE }

data class SignupProfileState(
    val password: String = "",
    val nickname: String = "",
    val passwordError: String? = null,
    val nicknameError: String? = null,
    val nicknameOk: Boolean = false,
    val formError: String? = null,
    val loading: Boolean = false,
)

data class SignupFlowState(
    val step: SignupStep = SignupStep.EMAIL,
    val code: CodeStepState = CodeStepState(),
    val profile: SignupProfileState = SignupProfileState(),
) {
    val email get() = code.email
}

/** 이메일 -> 코드 -> 비밀번호/닉네임 가입. 성공하면 토큰이 저장되고(AuthSession) 내비게이션이 온보딩으로 보낸다. */
class SignupFlowViewModel(
    private val auth: AuthRepository,
    extra: AuthExtraRepository,
) : ViewModel() {
    val codeFlow = EmailCodeController(extra, EmailPurpose.SIGNUP, viewModelScope)
    private val _profile = MutableStateFlow(SignupProfileState())

    val state: StateFlow<SignupFlowState> = combine(codeFlow.state, _profile) { c, p ->
        val step = when {
            c.verified -> SignupStep.PROFILE
            c.sent -> SignupStep.CODE
            else -> SignupStep.EMAIL
        }
        SignupFlowState(step, c, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SignupFlowState())

    fun onPassword(v: String) = _profile.update { it.copy(password = v, passwordError = null, formError = null) }

    fun onNickname(v: String) =
        _profile.update { it.copy(nickname = v, nicknameError = null, nicknameOk = false, formError = null) }

    fun checkNickname() {
        val nick = _profile.value.nickname.trim()
        if (nick.isEmpty()) return
        if (!Validators.isValidNickname(nick)) {
            _profile.update { it.copy(nicknameError = Validators.NICKNAME_MESSAGE) }
            return
        }
        viewModelScope.launch {
            when (val r = auth.nicknameAvailability(nick)) {
                is ApiResult.Success -> _profile.update {
                    if (it.nickname.trim() != nick) it
                    else if (r.data.available) it.copy(nicknameOk = true, nicknameError = null)
                    else it.copy(nicknameOk = false, nicknameError = r.data.reason ?: "사용할 수 없는 닉네임이에요.")
                }
                else -> _profile.update { it.copy(nicknameError = ErrorMessages.message(r)) }
            }
        }
    }

    /** debug 빌드 전용 지름길. 화면이 BuildConfig.DEBUG일 때만 부른다. 이메일이 없으면 토큰의 ':' 뒤 이메일을 쓴다. */
    fun useDevToken(devToken: String) {
        val email = codeFlow.state.value.email
        if (devToken.isBlank()) return
        if (!Validators.isValidEmail(email)) {
            codeFlow.send() // 이메일 형식 오류를 화면에 보여준다(서버 호출은 하지 않는다)
            return
        }
        codeFlow.skipWithToken(SignupViewModel.buildToken(devToken, email))
    }

    fun submit() {
        val c = codeFlow.state.value
        val p = _profile.value
        val token = c.verificationToken ?: return
        if (p.loading) return
        val passwordError = if (Validators.isValidPassword(p.password)) null else Validators.PASSWORD_MESSAGE
        val nicknameError = if (Validators.isValidNickname(p.nickname.trim())) null else Validators.NICKNAME_MESSAGE
        if (passwordError != null || nicknameError != null) {
            _profile.update { it.copy(passwordError = passwordError, nicknameError = nicknameError) }
            return
        }
        _profile.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            val r = auth.signup(token, p.password, p.nickname)
            if (r is ApiResult.Success) {
                _profile.update { it.copy(loading = false) }
                return@launch
            }
            if (r is ApiResult.Failure && r.code == "EMAIL4003") {
                // 인증 토큰이 만료됐다. 이메일부터 다시 받는다.
                _profile.update { it.copy(loading = false) }
                codeFlow.invalidateToken(ErrorMessages.message(r))
                return@launch
            }
            if (r is ApiResult.Failure && r.code == "USER409") {
                _profile.update { it.copy(loading = false) }
                codeFlow.invalidateToken(ErrorMessages.message(r))
                return@launch
            }
            _profile.update { st ->
                var next = st.copy(loading = false)
                if (r is ApiResult.Failure) {
                    val fields = ErrorMessages.fieldErrors(r)
                    next = next.copy(passwordError = fields["password"], nicknameError = fields["nickname"])
                    when (r.code) {
                        "USER4001", "USER4092" -> next = next.copy(nicknameError = ErrorMessages.message(r))
                        else -> if (fields.isEmpty()) next = next.copy(formError = ErrorMessages.message(r))
                    }
                } else {
                    next = next.copy(formError = ErrorMessages.message(r))
                }
                next
            }
        }
    }
}
