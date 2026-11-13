package com.bbolbbol.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.StatsResponse
import com.bbolbbol.app.data.repository.StatsPeriod
import com.bbolbbol.app.data.repository.StatsRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val loading: Boolean = true,
    val stats: StatsResponse? = null,
    val error: String? = null,
) {
    /** 활동이 하나도 없는 기간. */
    val isEmpty get() = stats != null && stats.completionCount == 0 && stats.totalPoint == 0L
}

class StatsViewModel(
    private val repo: StatsRepository,
    val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Seoul")) },
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun selectPeriod(period: StatsPeriod) {
        if (period == _state.value.period && _state.value.stats != null) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        val period = _state.value.period
        job?.cancel()
        _state.update { it.copy(loading = true, stats = null, error = null) }
        job = viewModelScope.launch {
            when (val r = repo.stats(period)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, stats = r.data) }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }
}
