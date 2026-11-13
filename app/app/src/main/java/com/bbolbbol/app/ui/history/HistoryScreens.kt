package com.bbolbbol.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointHistoryItem
import com.bbolbbol.app.ui.common.CursorPagingViewModel
import com.bbolbbol.app.util.Formatters

@Composable
fun PointHistoryScreen(vm: PointHistoryViewModel, onBack: () -> Unit) {
    PagedListScreen("포인트 내역", "아직 받은 포인트가 없어요.", vm, onBack) { h: PointHistoryItem ->
        ListRow(
            title = h.placeName,
            subtitle = "${Formatters.pointType(h.type)} · ${Formatters.dateTime(h.createdAt)}",
            trailing = "+${h.point}P",
        )
    }
}

@Composable
fun ActivityLogScreen(vm: ActivityLogViewModel, onBack: () -> Unit) {
    PagedListScreen("활동 일지", "아직 도착 인증한 장소가 없어요.", vm, onBack) { c: CompletionLogItem ->
        ListRow(
            title = c.placeName,
            subtitle = "${c.category} · ${Formatters.dateTime(c.completedAt)}",
            trailing = "+${c.earnedPoint}P",
        )
    }
}

@Composable
private fun <T> PagedListScreen(
    title: String,
    emptyText: String,
    vm: CursorPagingViewModel<T>,
    onBack: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("뒤로") }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                s.loading -> CircularProgressIndicator()
                s.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::refresh) { Text("다시 시도") }
                }
                s.isEmpty -> Text(emptyText, color = MaterialTheme.colorScheme.secondary)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(s.items) { item ->
                        row(item)
                        HorizontalDivider()
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            s.moreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            when {
                                s.loadingMore -> CircularProgressIndicator()
                                s.hasNext -> OutlinedButton(onClick = vm::loadMore) { Text("더 보기") }
                                else -> Text(
                                    "마지막이에요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Text(trailing, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
