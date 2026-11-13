package com.bbolbbol.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CategoryItem
import com.bbolbbol.app.data.api.OnboardingRequest
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.repository.AuthExtraRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_INTERESTS = 3

data class OnboardingUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val categories: List<CategoryItem> = emptyList(),
    /** 카카오 가입자의 첫 온보딩에서만 닉네임을 받는다. */
    val showNickname: Boolean = false,
    val nickname: String = "",
    val ageGroup: String? = null,
    val gender: String? = null,
    val interests: List<String> = emptyList(),
    /** 이미 한 번 완료한 사용자가 마이에서 다시 연 경우. 이때는 연령대/성별이 필수가 아니다. */
    val alreadyCompleted: Boolean = false,
    val nicknameError: String? = null,
    val ageGroupError: String? = null,
    val genderError: String? = null,
    val interestsError: String? = null,
    val formError: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
)

class OnboardingViewModel(
    private val extra: AuthExtraRepository,
    private val users: UserRepository,
    private val places: PlaceRepository,
    private val session: AuthSession,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val info = extra.onboarding()
            if (info !is ApiResult.Success) return@launch fail(ErrorMessages.message(info))
            val me = users.me()
            if (me !is ApiResult.Success) return@launch fail(ErrorMessages.message(me))
            val cats = places.categories()
            if (cats !is ApiResult.Success) return@launch fail(ErrorMessages.message(cats))
            val kakaoFirst = me.data.loginType == "KAKAO" && !info.data.onboardingCompleted
            _state.update {
                it.copy(
                    loading = false,
                    categories = cats.data,
                    showNickname = kakaoFirst,
                    nickname = if (kakaoFirst) session.suggestedNickname.orEmpty() else "",
                    ageGroup = info.data.ageGroup,
                    gender = info.data.gender,
                    interests = info.data.interests.take(MAX_INTERESTS),
                    alreadyCompleted = info.data.onboardingCompleted,
                )
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, loadError = message) }

    fun onNickname(v: String) = _state.update { it.copy(nickname = v, nicknameError = null, formError = null) }
    fun onAgeGroup(v: String) = _state.update { it.copy(ageGroup = v, ageGroupError = null, formError = null) }
    fun onGender(v: String) = _state.update { it.copy(gender = v, genderError = null, formError = null) }

    /** 관심 카테고리 선택/해제. 4번째를 고르면 선택하지 않고 안내만 한다. */
    fun toggleInterest(code: String) = _state.update {
        when {
            code in it.interests -> it.copy(interests = it.interests - code, interestsError = null)
            it.interests.size >= MAX_INTERESTS ->
                it.copy(interestsError = "관심 카테고리는 최대 ${MAX_INTERESTS}개까지 고를 수 있어요.")
            else -> it.copy(interests = it.interests + code, interestsError = null)
        }
    }

    fun submit() {
        val s = _state.value
        if (s.saving || s.loading) return
        val nick = s.nickname.trim()
        val nicknameError = if (s.showNickname && !Validators.isValidNickname(nick)) Validators.NICKNAME_MESSAGE else null
        val ageError = if (!s.alreadyCompleted && s.ageGroup == null) "연령대를 골라 주세요." else null
        val genderError = if (!s.alreadyCompleted && s.gender == null) "성별을 골라 주세요." else null
        if (nicknameError != null || ageError != null || genderError != null) {
            _state.update { it.copy(nicknameError = nicknameError, ageGroupError = ageError, genderError = genderError) }
            return
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val request = OnboardingRequest(
                nickname = nick.takeIf { s.showNickname },
                ageGroup = s.ageGroup,
                gender = s.gender,
                interests = s.interests,
            )
            when (val r = extra.saveOnboarding(request)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, done = true) }
                is ApiResult.Failure -> {
                    val fields = ErrorMessages.fieldErrors(r)
                    _state.update {
                        val msg = ErrorMessages.message(r)
                        val nickByCode = if (r.code == "USER4001" || r.code == "USER4092") msg else null
                        it.copy(
                            saving = false,
                            nicknameError = fields["nickname"] ?: nickByCode,
                            ageGroupError = fields["ageGroup"],
                            genderError = fields["gender"],
                            interestsError = fields["interests"],
                            formError = if (fields.isEmpty() && nickByCode == null) msg else null,
                        )
                    }
                }
                ApiResult.NetworkError -> _state.update { it.copy(saving = false, formError = ErrorMessages.message(r)) }
            }
        }
    }
}
