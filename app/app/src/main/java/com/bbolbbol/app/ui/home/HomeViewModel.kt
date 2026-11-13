package com.bbolbbol.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointSummary
import com.bbolbbol.app.data.api.RecommendedPlace
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 홈의 한 구역(닉네임, 포인트, 추천, 최근 활동)이 따로 로딩/실패/성공을 갖는다. 한 구역이 실패해도 나머지는 보여준다. */
data class Section<out T>(val loading: Boolean = true, val data: T? = null, val error: String? = null)

data class HomeUiState(
    val nickname: Section<String> = Section(),
    val points: Section<PointSummary> = Section(),
    val recommendations: Section<List<RecommendedPlace>> = Section(),
    val recent: Section<List<CompletionLogItem>> = Section(),
    /** 추천에 쓴 위치가 현재 위치가 아니라 종로구 중심(둘러보기)인지. */
    val browse: Boolean = true,
    val lat: Double = DEFAULT_LAT,
    val lng: Double = DEFAULT_LNG,
)

const val DEFAULT_LAT = 37.5729
const val DEFAULT_LNG = 126.9794
const val RECENT_COUNT = 3

class HomeViewModel(
    private val users: UserRepository,
    private val pointRepo: PointRepository,
    private val places: PlaceRepository,
    private val completions: CompletionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var started = false

    /**
     * 화면이 (다시) 보일 때마다 호출한다. 처음에는 전체를 불러오고, 탭에 다시 들어올 때는 화면을 비우지 않고
     * 조용히 갱신한다(도착 인증 포인트, 온보딩에서 정한 닉네임 반영). 위치를 못 얻었으면 둘러보기(종로구 중심).
     */
    fun start(lat: Double?, lng: Double?) {
        if (!started) {
            started = true
            load(lat, lng)
        } else {
            refresh(lat, lng)
        }
    }

    /** 로딩 표시 없이 모든 구역을 다시 읽는다. 실패하면 이전 값을 그대로 둔다. */
    fun refresh(lat: Double? = null, lng: Double? = null) {
        val browse = lat == null || lng == null
        val useLat = lat ?: DEFAULT_LAT
        val useLng = lng ?: DEFAULT_LNG
        _state.update { it.copy(browse = browse, lat = useLat, lng = useLng) }
        viewModelScope.launch {
            val me = users.me()
            if (me is ApiResult.Success) _state.update { it.copy(nickname = Section(false, me.data.nickname)) }
        }
        viewModelScope.launch {
            val r = pointRepo.summary()
            if (r is ApiResult.Success) _state.update { it.copy(points = Section(false, r.data)) }
        }
        viewModelScope.launch {
            val r = places.recommendations(useLat, useLng)
            if (r is ApiResult.Success) _state.update { it.copy(recommendations = Section(false, r.data)) }
        }
        viewModelScope.launch {
            val r = completions.log(null, RECENT_COUNT)
            if (r is ApiResult.Success) _state.update { it.copy(recent = Section(false, r.data.completions.take(RECENT_COUNT))) }
        }
    }

    /** 당겨서 새로고침/다시 시도. lat/lng가 null이면 종로구 중심. */
    fun load(lat: Double? = null, lng: Double? = null) {
        val browse = lat == null || lng == null
        val useLat = lat ?: DEFAULT_LAT
        val useLng = lng ?: DEFAULT_LNG
        _state.value = HomeUiState(browse = browse, lat = useLat, lng = useLng)
        loadNickname()
        loadPoints()
        loadRecommendations()
        loadRecent()
    }

    fun loadNickname() {
        _state.update { it.copy(nickname = Section()) }
        viewModelScope.launch {
            val s = when (val r = users.me()) {
                is ApiResult.Success -> Section(false, r.data.nickname)
                else -> Section(false, null, ErrorMessages.message(r))
            }
            _state.update { it.copy(nickname = s) }
        }
    }

    fun loadPoints() {
        _state.update { it.copy(points = Section()) }
        viewModelScope.launch {
            val s = when (val r = pointRepo.summary()) {
                is ApiResult.Success -> Section(false, r.data)
                else -> Section(false, null, ErrorMessages.message(r))
            }
            _state.update { it.copy(points = s) }
        }
    }

    fun loadRecommendations() {
        val st = _state.value
        _state.update { it.copy(recommendations = Section()) }
        viewModelScope.launch {
            val s = when (val r = places.recommendations(st.lat, st.lng)) {
                is ApiResult.Success -> Section(false, r.data)
                else -> Section(false, null, ErrorMessages.message(r))
            }
            _state.update { it.copy(recommendations = s) }
        }
    }

    fun loadRecent() {
        _state.update { it.copy(recent = Section()) }
        viewModelScope.launch {
            val s = when (val r = completions.log(null, RECENT_COUNT)) {
                is ApiResult.Success -> Section(false, r.data.completions.take(RECENT_COUNT))
                else -> Section(false, null, ErrorMessages.message(r))
            }
            _state.update { it.copy(recent = s) }
        }
    }
}
