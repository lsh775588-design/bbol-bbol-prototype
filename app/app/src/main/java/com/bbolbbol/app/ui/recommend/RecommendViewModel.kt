package com.bbolbbol.app.ui.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.RecommendedPlace
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendUiState(
    val loading: Boolean = true,
    val places: List<RecommendedPlace> = emptyList(),
    val error: String? = null,
) {
    val isEmpty get() = !loading && error == null && places.isEmpty()
}

/** 현재 위치(둘러보기 모드면 종로구 중심) 기준 추천. 위치는 지도 화면이 넘겨준다. */
class RecommendViewModel(
    private val repo: PlaceRepository,
    private val lat: Double,
    private val lng: Double,
) : ViewModel() {
    private val _state = MutableStateFlow(RecommendUiState())
    val state: StateFlow<RecommendUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RecommendUiState()
        viewModelScope.launch {
            when (val r = repo.recommendations(lat, lng)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, places = r.data) }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }
}
