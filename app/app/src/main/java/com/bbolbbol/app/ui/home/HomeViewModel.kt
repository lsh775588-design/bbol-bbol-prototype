package com.bbolbbol.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointSummary
import com.bbolbbol.app.data.api.RecommendedPlace
import com.bbolbbol.app.data.api.PlaceDetail
import com.bbolbbol.app.data.local.QuestBook
import com.bbolbbol.app.ui.map.distanceMeters
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PlaceRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.data.repository.UserRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 홈의 한 구역(닉네임, 포인트, 추천, 최근 활동)이 따로 로딩/실패/성공을 갖는다. 한 구역이 실패해도 나머지는 보여준다. */
data class Section<out T>(val loading: Boolean = true, val data: T? = null, val error: String? = null)

/** 홈의 "진행 중인 퀘스트" 카드 하나. distance는 현재 위치를 모르면 null. */
data class OngoingQuest(
    val placeId: Long,
    val name: String,
    val category: String,
    val rewardPoint: Int,
    val distance: Long?,
)

data class HomeUiState(
    /** 오늘 받아 둔(아직 인증/포기하지 않은) 퀘스트. 장소 정보를 못 읽은 것은 빠진다. */
    val ongoing: List<OngoingQuest> = emptyList(),
    /** 위치 정보(권한)가 있어서 distance를 계산할 수 있는지. */
    val ongoingHasLocation: Boolean = false,
    /** 처음 읽기가 끝나기 전에는 빈 상태 안내를 보이지 않는다. */
    val ongoingLoading: Boolean = true,
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
    private val quests: QuestBook? = null,
    private val userId: suspend () -> Long? = { null },
) : ViewModel() {
    private val placeCache = HashMap<Long, PlaceDetail>()
    private var questJob: kotlinx.coroutines.Job? = null
    private var hasLocation = false
    private var myLat = DEFAULT_LAT
    private var myLng = DEFAULT_LNG

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
        loadOngoing(!browse, useLat, useLng)
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
        loadOngoing(!browse, useLat, useLng)
        loadNickname()
        loadPoints()
        loadRecommendations()
        loadRecent()
    }

    /** 받아 둔 퀘스트를 QuestBook에서 읽고 장소 정보를 붙인다. 지도에서 받기/포기/인증한 뒤 홈에 돌아오면 호출된다. */
    fun loadOngoing(located: Boolean = hasLocation, lat: Double = myLat, lng: Double = myLng) {
        hasLocation = located; myLat = lat; myLng = lng
        val q = quests ?: return
        questJob?.cancel()
        questJob = viewModelScope.launch {
            val uid = userId() ?: return@launch
            val ids = q.acceptedIds(uid).sorted()
            val list = ArrayList<OngoingQuest>()
            // 아직 모르는 장소는 한꺼번에 병렬로 읽는다.
            val fetched = kotlinx.coroutines.coroutineScope {
                ids.filter { it !in placeCache }.map { id -> async { id to places.place(id) } }.awaitAll()
            }
            for ((id, r) in fetched) if (r is ApiResult.Success) placeCache[id] = r.data
            for (id in ids) {
                val d = placeCache[id] ?: continue
                if (d == null) continue
                if (d.isCompleted) { q.remove(uid, id); continue }
                val dist = if (located) distanceMeters(lat, lng, d.lat, d.lng) else null
                list += OngoingQuest(id, d.name, d.category, d.rewardPoint, dist)
            }
            // 가까운 순(거리를 모르면 받은 순서 그대로)
            _state.update { it.copy(ongoing = if (located) list.sortedBy { c -> c.distance } else list, ongoingHasLocation = located, ongoingLoading = false) }
        }
    }

    /** 홈에서 포기. 기록을 지우고 목록을 바로 갱신한다. */
    fun abandon(placeId: Long) {
        val q = quests ?: return
        viewModelScope.launch {
            val uid = userId() ?: return@launch
            q.remove(uid, placeId)
            _state.update { st -> st.copy(ongoing = st.ongoing.filterNot { it.placeId == placeId }) }
        }
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
