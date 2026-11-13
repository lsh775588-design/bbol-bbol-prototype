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
