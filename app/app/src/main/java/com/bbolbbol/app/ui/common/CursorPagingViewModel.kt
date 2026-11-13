package com.bbolbbol.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 서버 커서 페이지 한 장. */
data class CursorPage<T>(val items: List<T>, val hasNext: Boolean, val nextCursor: Long?)

data class PagingState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** 첫 페이지 실패 메시지(목록이 비어 있을 때 보여준다). */
    val error: String? = null,
    /** "더 보기" 실패 메시지(기존 목록은 유지한다). */
    val moreError: String? = null,
    val hasNext: Boolean = false,
    val nextCursor: Long? = null,
) {
    val isEmpty get() = !loading && error == null && items.isEmpty()
}

/** 포인트 내역과 활동 일지가 공유하는 커서 페이징. */
abstract class CursorPagingViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow(PagingState<T>())
    val state: StateFlow<PagingState<T>> = _state.asStateFlow()

    protected abstract suspend fun fetch(cursor: Long?): ApiResult<CursorPage<T>>

    fun refresh() {
        _state.value = PagingState()
        viewModelScope.launch {
            when (val r = fetch(null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, items = r.data.items, hasNext = r.data.hasNext, nextCursor = r.data.nextCursor)
                }
                else -> _state.update { it.copy(loading = false, error = ErrorMessages.message(r)) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasNext || s.nextCursor == null) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        viewModelScope.launch {
            when (val r = fetch(s.nextCursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + r.data.items,
                        hasNext = r.data.hasNext,
                        nextCursor = r.data.nextCursor,
                    )
                }
                else -> _state.update { it.copy(loadingMore = false, moreError = ErrorMessages.message(r)) }
            }
        }
    }
}
