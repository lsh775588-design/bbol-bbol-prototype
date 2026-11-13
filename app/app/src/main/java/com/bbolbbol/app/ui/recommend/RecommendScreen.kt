package com.bbolbbol.app.ui.recommend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.CategoryTile
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.InfoChip
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.util.Formatters

@Composable
fun RecommendScreen(vm: RecommendViewModel, onBack: () -> Unit, onPick: (Long) -> Unit) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().background(BbolColors.Background).systemBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTitle("뽈뽈 추천", Modifier.padding(top = 20.dp, bottom = 12.dp)) {
            TextButton(onClick = onBack) { Text("뒤로", color = BbolColors.Violet) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                s.loading -> LoadingState()
                s.error != null -> ErrorState(s.error!!, vm::load)
                s.isEmpty -> EmptyState("추천할 장소가 아직 없어요.")
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.places, key = { it.placeId }) { p ->
                        BbolCard(onClick = { onPick(p.placeId) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                CategoryTile(p.category, size = 56.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            p.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (p.isHot) {
                                            Spacer(Modifier.width(6.dp))
                                            StatusChip("HOT", StatusKind.IN_PROGRESS)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        InfoChip(Formatters.categoryName(p.category))
                                        InfoChip(Formatters.distance(p.distance))
                                        InfoChip("${p.expectedPoint}P")
                                    }
                                    if (p.reason.isNotBlank()) {
                                        Text(p.reason, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
