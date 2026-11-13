package com.bbolbbol.app.ui.auth

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.EmailPurpose
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CodeStepState(
    val email: String = "",
    val emailError: String? = null,
    val sending: Boolean = false,
    /** 코드를 보낸 적이 있고 아직 이메일을 바꾸지 않았다. */
    val sent: Boolean = false,
    val code: String = "",
    val confirming: Boolean = false,
    /** 다음 재발송까지 남은 초. 화면이 1초마다 [EmailCodeController.tick]을 부른다. */
    val resendIn: Int = 0,
    /** 코드 유효 시간(초). 0이 되면 만료로 본다. */
    val expiresIn: Int = 0,
    /** EMAIL4001로 받은 남은 시도 횟수. */
    val remainingAttempts: Int? = null,
    /** EMAIL4002: 코드가 무효가 되어 다시 받아야 한다. */
    val codeInvalid: Boolean = false,
    val error: String? = null,
    /** 인증 성공 후 받은 토큰. 값을 로그나 화면에 내지 않는다. */
    val verificationToken: String? = null,
) {
    val verified get() = verificationToken != null
    val canResend get() = sent && resendIn <= 0 && !sending && !verified
    val canConfirm get() = sent && code.length == CODE_LENGTH && !confirming && !codeInvalid && !verified
}

const val CODE_LENGTH = 6
private const val FALLBACK_COOLDOWN = 60

/** 이메일 입력 -> 코드 발송 -> 코드 확인을 한 덩어리로 다루는 상태 기계. 가입과 비밀번호 재설정이 같이 쓴다. */
class EmailCodeController(
    private val repo: AuthExtraRepository,
    private val purpose: EmailPurpose,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CodeStepState())
    val state: StateFlow<CodeStepState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, emailError = null, error = null) }

    fun onCode(v: String) =
        _state.update { it.copy(code = v.filter(Char::isDigit).take(CODE_LENGTH), error = null) }

    /** 이메일을 고치려고 처음 단계로 돌아간다. 코드와 토큰은 버린다(서버의 쿨다운은 이메일 기준으로 계속된다). */
    fun changeEmail() = _state.update { CodeStepState(email = it.email) }

    /** 1초마다 호출. */
    fun tick() = _state.update {
        if (it.resendIn <= 0 && it.expiresIn <= 0) it
        else it.copy(resendIn = (it.resendIn - 1).coerceAtLeast(0), expiresIn = (it.expiresIn - 1).coerceAtLeast(0))
    }

    fun send() {
        val s = _state.value
        if (s.sending || (s.sent && s.resendIn > 0)) return
        if (!Validators.isValidEmail(s.email)) {
            _state.update { it.copy(emailError = Validators.EMAIL_MESSAGE) }
            return
        }
        _state.update { it.copy(sending = true, error = null, emailError = null) }
        val email = s.email
        scope.launch {
            when (val r = repo.sendCode(email, purpose)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        sending = false, sent = true, code = "", remainingAttempts = null, codeInvalid = false,
                        resendIn = r.data.resendAvailableIn.toInt(), expiresIn = r.data.expiresIn.toInt(),
                    )
                }
                is ApiResult.Failure -> _state.update {
                    val msg = ErrorMessages.message(r)
                    when (r.code) {
                        "USER409" -> it.copy(sending = false, emailError = msg)
                        // 서버는 남은 대기 시간을 주지 않는다. 1분 제한이라 가정하고 버튼을 잠시 막는다.
                        "EMAIL429" -> it.copy(sending = false, error = msg, resendIn = maxOf(it.resendIn, FALLBACK_COOLDOWN))
                        else -> it.copy(sending = false, error = msg)
                    }
                }
                ApiResult.NetworkError -> _state.update { it.copy(sending = false, error = ErrorMessages.message(r)) }
            }
        }
    }

    fun confirm() {
        val s = _state.value
        if (!s.canConfirm) return
        _state.update { it.copy(confirming = true, error = null) }
        scope.launch {
            when (val r = repo.confirmCode(s.email, purpose, s.code)) {
                is ApiResult.Success -> _state.update {
                    it.copy(confirming = false, verificationToken = r.data.verificationToken, remainingAttempts = null)
                }
                is ApiResult.Failure -> _state.update {
                    when (r.code) {
                        "EMAIL4001" -> it.copy(
                            confirming = false, code = "", remainingAttempts = r.remainingAttempts,
                            error = ErrorMessages.message(r),
                        )
                        // 5번 틀렸거나 만료. 서버가 코드를 지웠으니 맞는 코드도 거절한다 -> 다시 발송해야 한다.
                        "EMAIL4002" -> it.copy(
                            confirming = false, code = "", codeInvalid = true, remainingAttempts = null,
                            error = ErrorMessages.message(r),
                        )
                        else -> it.copy(confirming = false, error = ErrorMessages.message(r))
                    }
                }
                ApiResult.NetworkError -> _state.update { it.copy(confirming = false, error = ErrorMessages.message(r)) }
            }
        }
    }

    /** 개발용: 서버의 고정 개발 토큰으로 인증 단계를 건너뛴다. */
    fun skipWithToken(token: String) = _state.update { it.copy(verificationToken = token.trim(), sent = true) }

    /** 토큰이 만료/무효(EMAIL4003)가 되었을 때. 이메일은 남기고 처음부터 다시 받게 한다. */
    fun invalidateToken(message: String) = _state.update { CodeStepState(email = it.email, error = message) }
}
