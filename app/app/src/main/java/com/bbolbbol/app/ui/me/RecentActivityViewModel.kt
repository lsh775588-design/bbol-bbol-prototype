package com.bbolbbol.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.ui.home.RECENT_COUNT
import com.bbolbbol.app.ui.home.Section
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 마이 화면의 "퀘스트 기록" 미리보기(최근 3건). */
class RecentActivityViewModel(private val completions: CompletionRepository) : ViewModel() {
    private val _state = MutableStateFlow(Section<List<CompletionLogItem>>())
    val state: StateFlow<Section<List<CompletionLogItem>>> = _state.asStateFlow()

    init {
        load()
    }

    private var entered = false

    /** 화면이 보일 때마다 호출. 다시 들어올 때는 목록을 비우지 않고 조용히 갱신한다. */
    fun onEnter() {
        if (!entered) { entered = true; return }
        viewModelScope.launch {
            val r = completions.log(null, RECENT_COUNT)
            if (r is ApiResult.Success) _state.value = Section(false, r.data.completions.take(RECENT_COUNT))
        }
    }

    fun load() {
        _state.value = Section()
        viewModelScope.launch {
            _state.value = when (val r = completions.log(null, RECENT_COUNT)) {
                is ApiResult.Success -> Section(false, r.data.completions.take(RECENT_COUNT))
                else -> Section(false, null, ErrorMessages.message(r))
            }
        }
    }
}
