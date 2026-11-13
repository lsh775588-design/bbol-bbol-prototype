package com.bbolbbol.app.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.PawIcon
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes

/** 랭킹 API가 아직 없다(미결정 쟁점). 포디움과 목록 뼈대만 흐린 색으로 그리고 가짜 사용자/숫자는 쓰지 않는다. */
@Composable
fun RankingScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("랭킹") { StatusChip("준비 중", StatusKind.NOT_STARTED) }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PodiumSlot(2, 120.dp, BbolColors.Silver, BbolColors.Lavender, Modifier.weight(1f))
            PodiumSlot(1, 150.dp, BbolColors.Gold, BbolColors.LavenderStrong, Modifier.weight(1f))
            PodiumSlot(3, 104.dp, BbolColors.Bronze, BbolColors.PawPinkBg, Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("랭킹은 준비 중이에요", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "순위가 정해지면 이곳에서 함께 뽈뽈 다니는 사람들을 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = BbolColors.InkSoft,
                textAlign = TextAlign.Center,
            )
        }

        BbolCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (4..8).forEach { rank -> SkeletonRow(rank) }
            }
        }
    }
}

@Composable
private fun PodiumSlot(rank: Int, height: Dp, medal: Color, bg: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (rank == 1) 64.dp else 52.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(PawIcon, null, tint = Color.White, modifier = Modifier.size(if (rank == 1) 30.dp else 24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Bar(width = 56.dp)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(height).clip(BbolShapes.Card).background(bg.copy(alpha = 0.7f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.EmojiEvents, null, tint = medal, modifier = Modifier.size(28.dp))
                Text("$rank", style = MaterialTheme.typography.titleLarge, color = BbolColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                Bar(width = 36.dp)
            }
        }
    }
}

@Composable
private fun SkeletonRow(rank: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", color = BbolColors.InkSoft, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(BbolColors.Lavender))
        Spacer(Modifier.width(12.dp))
        Bar(width = 110.dp, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        Bar(width = 28.dp)
    }
}

@Composable
private fun Bar(width: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(10.dp).clip(BbolShapes.Pill).background(BbolColors.NeutralBg))
}
