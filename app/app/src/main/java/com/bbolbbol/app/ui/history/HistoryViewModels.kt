package com.bbolbbol.app.ui.history

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointHistoryItem
import com.bbolbbol.app.data.api.map
import com.bbolbbol.app.data.repository.CompletionRepository
import com.bbolbbol.app.data.repository.PointRepository
import com.bbolbbol.app.ui.common.CursorPage
import com.bbolbbol.app.ui.common.CursorPagingViewModel

class PointHistoryViewModel(private val points: PointRepository) : CursorPagingViewModel<PointHistoryItem>() {
    init {
        refresh()
    }

    override suspend fun fetch(cursor: Long?): ApiResult<CursorPage<PointHistoryItem>> =
        points.histories(cursor).map { CursorPage(it.histories, it.hasNext, it.nextCursor) }
}

class ActivityLogViewModel(private val completions: CompletionRepository) : CursorPagingViewModel<CompletionLogItem>() {
    init {
        refresh()
    }

    override suspend fun fetch(cursor: Long?): ApiResult<CursorPage<CompletionLogItem>> =
        completions.log(cursor).map { CursorPage(it.completions, it.hasNext, it.nextCursor) }
}
