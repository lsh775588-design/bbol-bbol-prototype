package com.bbolbbol.app.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.PlaceRankingResponse
import com.bbolbbol.app.data.api.StreakRankingResponse
import com.bbolbbol.app.data.api.UserRankingResponse
import com.bbolbbol.app.data.api.map
import com.bbolbbol.app.data.repository.RankingPeriod
import com.bbolbbol.app.data.repository.RankingRepository
import com.bbolbbol.app.util.ErrorMessages
import com.bbolbbol.app.util.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RankingTab(val label: String) { USERS("사용자"), PLACES("장소"), STREAKS("연속 참여") }

/** 연속 참여 순위에는 기간이 없다(서버가 "현재 연속 일수"만 센다). */
val RankingTab.hasPeriod get() = this != RankingTab.STREAKS

fun RankingPeriod.label() = when (this) {
    RankingPeriod.DAY -> "오늘"
    RankingPeriod.WEEK -> "이번 주"
    RankingPeriod.MONTH -> "이번 달"
}

/** 포디움과 목록이 같이 쓰는 한 줄. 같은 순위(공동)는 서버가 준 rank 값을 그대로 쓴다. */
data class RankRow(
    val rank: Int,
    val name: String,
    val primary: String,
    val secondary: String?,
    val isMe: Boolean,
)

data class RankingContent(val rows: List<RankRow>, val me: RankRow?)

object RankingMapper {
    fun users(r: UserRankingResponse) = RankingContent(
        r.rankings.map { RankRow(it.rank, it.nickname, "${it.completionCount}회", "${it.point}P", it.isMe) },
        r.me?.let { RankRow(it.rank, it.nickname, "${it.completionCount}회", "${it.point}P", true) },
    )

    fun places(r: PlaceRankingResponse) = RankingContent(
        r.rankings.map { RankRow(it.rank, it.placeName, "${it.completionCount}회", Formatters.categoryName(it.category), false) },
        null,
    )

    fun streaks(r: StreakRankingResponse) = RankingContent(
        r.rankings.map { RankRow(it.rank, it.nickname, "${it.streakDays}일", null, it.isMe) },
        r.me?.let { RankRow(it.rank, it.nickname, "${it.streakDays}일", "최장 ${it.longestStreakDays}일", true) },
    )
}

data class RankingUiState(
    val tab: RankingTab = RankingTab.USERS,
    val period: RankingPeriod = RankingPeriod.WEEK,
    val loading: Boolean = true,
    val content: RankingContent? = null,
    val error: String? = null,
) {
    val isEmpty get() = content != null && content.rows.isEmpty()
}

class RankingViewModel(private val repo: RankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun selectTab(tab: RankingTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        load()
    }

    fun selectPeriod(period: RankingPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        val s = _state.value
        job?.cancel() // 탭/기간을 빨리 바꿀 때 늦게 온 이전 응답이 덮어쓰지 않게 한다.
        _state.update { it.copy(loading = true, content = null, error = null) }
        job = viewModelScope.launch {
            val r: ApiResult<RankingContent> = when (s.tab) {
                RankingTab.USERS -> repo.users(s.period).map(RankingMapper::users)
                RankingTab.PLACES -> repo.places(s.period).map(RankingMapper::places)
                RankingTab.STREAKS -> repo.streaks().map(RankingMapper::streaks)
            }
            when (r) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, content = r.data) }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }
}
