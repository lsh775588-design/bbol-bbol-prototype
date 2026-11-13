package com.bbolbbol.app.ui.recommend

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.util.Formatters

@Composable
fun RecommendScreen(vm: RecommendViewModel, onBack: () -> Unit, onPick: (Long) -> Unit) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("뽈뽈 추천", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("지도") }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                s.loading -> CircularProgressIndicator()
                s.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::load) { Text("다시 시도") }
                }
                s.isEmpty -> Text("추천할 장소가 아직 없어요.", color = MaterialTheme.colorScheme.secondary)
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.places, key = { it.placeId }) { p ->
                        Card(Modifier.fillMaxWidth().clickable { onPick(p.placeId) }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${p.expectedPoint}P", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    "${p.category} · ${Formatters.distance(p.distance)}" + if (p.isHot) " · HOT" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                if (p.reason.isNotBlank()) Text(p.reason, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
