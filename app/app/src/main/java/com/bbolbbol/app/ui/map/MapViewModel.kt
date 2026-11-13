package com.bbolbbol.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CategoryItem
import com.bbolbbol.app.data.api.CompletionRequest
import com.bbolbbol.app.data.api.CompletionResponse
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.api.PlaceSummary
import com.bbolbbol.app.data.repository.CompletionRepository
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
    /** 추천에서 고른 장소처럼 지도 카메라만 옮길 때 쓴다(검색 기준 위치와 별개). */
    val focusLat: Double? = null,
    val focusLng: Double? = null,
    val arriving: Boolean = false,
    val arrivalOutcome: ArrivalOutcome? = null,
) {
    val isBrowse get() = mode == LocationMode.BROWSE

    /** 지금 열린 장소를 오늘 이미 인증했는가(상세 응답에 이미 들어 있는 값). */
    val detailCompletedToday get() = detail?.isCompleted == true

    /** 도착 인증을 막는 이유. 인증할 수 있으면 null. */
    fun arrivalBlockedReason(hasLocationPermission: Boolean): String? = when {
        isBrowse -> "둘러보기 모드에서는 도착 인증을 할 수 없어요."
        !hasLocationPermission -> "위치 권한을 허용하면 도착 인증을 할 수 있어요."
        else -> null
    }
}

/** 도착 인증 요청에 쓰는 현재 위치. accuracy는 미터. */
data class ArrivalFix(val lat: Double, val lng: Double, val accuracy: Double, val isMock: Boolean)

sealed interface ArrivalOutcome {
    data class Success(val result: CompletionResponse) : ArrivalOutcome
    data class Error(val message: String, val code: String? = null) : ArrivalOutcome
}

/** 둘러보기 모드의 초기 줌(종로구 중심 한 동네 크기). */
const val BROWSE_ZOOM = 15.0

const val JONGNO_LAT = 37.5729
const val JONGNO_LNG = 126.9794

class MapViewModel(
    private val repo: PlaceRepository,
    private val completions: CompletionRepository,
) : ViewModel() {
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
        _state.update {
            it.copy(mode = LocationMode.MY_LOCATION, lat = lat, lng = lng, focusLat = null, focusLng = null)
        }
        loadPlaces()
    }

    /** 권한 거부 또는 위치를 못 얻은 경우. 종로구 중심으로 둘러본다. */
    fun enterBrowseMode() {
        _state.update {
            it.copy(mode = LocationMode.BROWSE, lat = JONGNO_LAT, lng = JONGNO_LNG, focusLat = null, focusLng = null)
        }
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

    /** [centerOnPlace]가 true면 지도 카메라도 그 장소로 옮긴다(추천 카드에서 열 때). */
    fun openDetail(placeId: Long, centerOnPlace: Boolean = false) {
        _state.update { it.copy(detailLoading = true, detail = null, detailError = null) }
        viewModelScope.launch {
            when (val r = repo.place(placeId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        detailLoading = false,
                        detail = r.data,
                        focusLat = if (centerOnPlace) r.data.lat else it.focusLat,
                        focusLng = if (centerOnPlace) r.data.lng else it.focusLng,
                    )
                }
                else -> _state.update { it.copy(detailLoading = false, detailError = ErrorMessages.message(r)) }
            }
        }
    }

    /** 도착 인증. 둘러보기 모드이거나 이미 요청 중이면 서버를 부르지 않는다. */
    fun arrive(placeId: Long, fix: ArrivalFix) {
        val s = _state.value
        if (s.isBrowse || s.arriving) return
        _state.update { it.copy(arriving = true, arrivalOutcome = null) }
        viewModelScope.launch {
            when (val r = completions.complete(placeId, CompletionRequest(fix.lat, fix.lng, fix.accuracy, fix.isMock))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(arriving = false, arrivalOutcome = ArrivalOutcome.Success(r.data)) }
                    refreshAfterArrival(placeId)
                }
                else -> _state.update {
                    it.copy(arriving = false, arrivalOutcome = ArrivalOutcome.Error(ErrorMessages.message(r), (r as? ApiResult.Failure)?.code))
                }
            }
        }
    }

    fun onArrivalLocationFailed() = _state.update {
        it.copy(arrivalOutcome = ArrivalOutcome.Error("현재 위치를 가져오지 못했어요. GPS가 켜져 있는지 확인해 주세요."))
    }

    fun dismissArrivalOutcome() = _state.update { it.copy(arrivalOutcome = null) }

    /** 방문/완료 표시가 바뀌도록 목록과 상세를 조용히 다시 읽는다. */
    private suspend fun refreshAfterArrival(placeId: Long) {
        loadPlaces()
        val r = repo.place(placeId)
        if (r is ApiResult.Success) _state.update { if (it.detail?.placeId == placeId) it.copy(detail = r.data) else it }
    }

    fun closeDetail() = _state.update { it.copy(detail = null, detailError = null, detailLoading = false) }

    companion object {
        const val NEAR_RADIUS = 1000
        const val BROWSE_RADIUS = 3000
    }
}
