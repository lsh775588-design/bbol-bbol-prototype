package com.bbolbbol.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointHistoryItem
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.CategoryTile
import com.bbolbbol.app.ui.common.CursorPagingViewModel
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.util.Formatters

@Composable
fun PointHistoryScreen(vm: PointHistoryViewModel, onBack: () -> Unit) {
    PagedListScreen("포인트 내역", "아직 받은 포인트가 없어요.", vm, onBack) { h: PointHistoryItem ->
        ListRow(
            category = h.category,
            title = h.placeName,
            subtitle = "${Formatters.pointType(h.type)} · ${Formatters.dateTime(h.createdAt)}",
            points = h.point,
        )
    }
}

@Composable
fun ActivityLogScreen(vm: ActivityLogViewModel, onBack: () -> Unit) {
    PagedListScreen("활동 일지", "아직 도착 인증한 장소가 없어요.", vm, onBack) { c: CompletionLogItem ->
        ListRow(
            category = c.category,
            title = c.placeName,
            subtitle = "${Formatters.categoryName(c.category)} · ${Formatters.dateTime(c.completedAt)}",
            points = c.earnedPoint,
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
    Column(Modifier.fillMaxSize().background(BbolColors.Background).systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTitle(title, Modifier.padding(top = 20.dp, bottom = 12.dp)) {
            TextButton(onClick = onBack) { Text("뒤로", color = BbolColors.Violet) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                s.loading -> LoadingState()
                s.error != null -> ErrorState(s.error!!, vm::refresh)
                s.isEmpty -> EmptyState(emptyText)
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.items) { item -> row(item) }
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            s.moreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            when {
                                s.loadingMore -> CircularProgressIndicator(color = BbolColors.Violet)
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
private fun ListRow(category: String, title: String, subtitle: String, points: Int) {
    BbolCard {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusChip("완료", StatusKind.DONE)
                Text("+${points}P", style = MaterialTheme.typography.labelMedium, color = BbolColors.Violet)
            }
        }
    }
}
