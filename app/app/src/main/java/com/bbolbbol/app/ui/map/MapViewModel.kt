package com.bbolbbol.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CategoryItem
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.api.PlaceSummary
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Gray Area B6: 위치 권한 설명 -> 허용이면 내 위치, 거부면 둘러보기 모드(종로구 중심, 도착 인증 없음). */
enum class LocationMode { EXPLAIN, LOCATING, MY_LOCATION, BROWSE }

data class MapUiState(
    val mode: LocationMode = LocationMode.EXPLAIN,
    val lat: Double = JONGNO_LAT,
    val lng: Double = JONGNO_LNG,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: String? = null,
    val places: List<PlaceSummary> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val detailLoading: Boolean = false,
    val detail: PlaceDetail? = null,
    val detailError: String? = null,
) {
    val isBrowse get() = mode == LocationMode.BROWSE
}

const val JONGNO_LAT = 37.5729
const val JONGNO_LNG = 126.9794

class MapViewModel(private val repo: PlaceRepository) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val r = repo.categories()
            if (r is ApiResult.Success) _state.update { it.copy(categories = r.data) }
        }
    }

    fun onLocating() = _state.update { it.copy(mode = LocationMode.LOCATING) }

    fun onLocation(lat: Double, lng: Double) {
        _state.update { it.copy(mode = LocationMode.MY_LOCATION, lat = lat, lng = lng) }
        loadPlaces()
    }

    /** 권한 거부 또는 위치를 못 얻은 경우. 종로구 중심으로 둘러본다. */
    fun enterBrowseMode() {
        _state.update { it.copy(mode = LocationMode.BROWSE, lat = JONGNO_LAT, lng = JONGNO_LNG) }
        loadPlaces()
    }

    fun selectCategory(code: String?) {
        _state.update { it.copy(selectedCategory = code) }
        loadPlaces()
    }

    fun loadPlaces() {
        val s = _state.value
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            val radius = if (s.isBrowse) BROWSE_RADIUS else NEAR_RADIUS
            when (val r = repo.places(s.lat, s.lng, radius, s.selectedCategory)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, places = r.data) }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }

    fun openDetail(placeId: Long) {
        _state.update { it.copy(detailLoading = true, detail = null, detailError = null) }
        viewModelScope.launch {
            when (val r = repo.place(placeId)) {
                is ApiResult.Success -> _state.update { it.copy(detailLoading = false, detail = r.data) }
                else -> _state.update { it.copy(detailLoading = false, detailError = ErrorMessages.message(r)) }
            }
        }
    }

    fun closeDetail() = _state.update { it.copy(detail = null, detailError = null, detailLoading = false) }

    companion object {
        const val NEAR_RADIUS = 1000
        const val BROWSE_RADIUS = 3000
    }
}
