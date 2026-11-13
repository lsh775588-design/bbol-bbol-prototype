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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.repository.RankingPeriod
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.PillChip
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes
import com.bbolbbol.app.util.Formatters

/** 랭킹 탭. 사용자/장소/연속 참여, 기간(오늘/이번 주/이번 달), 포디움(1~3위)과 4위부터의 목록, 내 순위. */
@Composable
fun RankingScreen(vm: RankingViewModel) {
    val s by vm.state.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("랭킹")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RankingTab.entries.forEach { PillChip(it.label, s.tab == it, { vm.selectTab(it) }) }
        }
        if (s.tab.hasPeriod) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RankingPeriod.entries.forEach { PillChip(it.label(), s.period == it, { vm.selectPeriod(it) }) }
            }
        } else {
            Text("현재 이어지고 있는 연속 참여 일수예요.", style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
        }

        val content = s.content
        when {
            s.loading -> LoadingState()
            content == null -> ErrorState(s.error ?: "불러오지 못했어요.", vm::load)
            s.isEmpty -> EmptyState("아직 순위가 없어요. 첫 도착 인증으로 1위에 도전해 보세요!")
            else -> {
                val me = content.me
                if (me != null) MyRankCard(me) else NoMyRank(s.tab)
                Podium(content.rows.take(3))
                val rest = content.rows.drop(3)
                if (rest.isNotEmpty()) {
                    BbolCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            rest.forEach { RankListRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoMyRank(tab: RankingTab) {
    val text = when (tab) {
        RankingTab.USERS -> "이 기간에 받은 포인트가 없어서 내 순위가 없어요."
        RankingTab.STREAKS -> "지금 이어지는 연속 참여가 없어서 내 순위가 없어요."
        RankingTab.PLACES -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
}

@Composable
private fun MyRankCard(me: RankRow) {
    BbolCard(containerColor = BbolColors.Lavender) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("내 순위", style = MaterialTheme.typography.labelLarge, color = BbolColors.VioletDeep)
            Spacer(Modifier.width(12.dp))
            Text("${me.rank}위", style = MaterialTheme.typography.titleLarge, color = BbolColors.VioletDeep)
            Spacer(Modifier.width(12.dp))
            Text(me.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(horizontalAlignment = Alignment.End) {
                Text(me.primary, style = MaterialTheme.typography.titleMedium, color = BbolColors.Violet)
                me.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft) }
            }
        }
    }
}

/** 1~3위. 가운데가 1위인 배치(2-1-3). 공동 순위면 서버가 준 순위 숫자와 메달 색을 그대로 쓴다. */
@Composable
private fun Podium(top: List<RankRow>) {
    if (top.isEmpty()) return
    val slots = listOf(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.forEachIndexed { slot, row ->
            val m = Modifier.weight(1f)
            if (row == null) Spacer(m) else PodiumSlot(row, slot == 1, m)
        }
    }
}

@Composable
private fun PodiumSlot(row: RankRow, first: Boolean, modifier: Modifier) {
    val (medal, bg) = when (row.rank) {
        1 -> BbolColors.Gold to BbolColors.LavenderStrong
        2 -> BbolColors.Silver to BbolColors.Lavender
        else -> BbolColors.Bronze to BbolColors.PawPinkBg
    }
    val barHeight: Dp = if (first) 150.dp else 112.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Initial(Formatters.initial(row.name), if (first) 64.dp else 52.dp, bg)
        Spacer(Modifier.height(6.dp))
        Text(
            row.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (row.isMe) BbolColors.Violet else BbolColors.Ink,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(barHeight).clip(BbolShapes.Card).background(bg.copy(alpha = 0.7f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.EmojiEvents, null, tint = medal, modifier = Modifier.size(28.dp))
                Text("${row.rank}", style = MaterialTheme.typography.titleLarge, color = BbolColors.VioletDeep)
                Spacer(Modifier.height(4.dp))
                Text(row.primary, style = MaterialTheme.typography.labelLarge, color = BbolColors.Ink)
                row.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun RankListRow(row: RankRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${row.rank}", color = BbolColors.InkSoft, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        Initial(Formatters.initial(row.name), 40.dp, BbolColors.Lavender)
        Spacer(Modifier.width(12.dp))
        Text(
            row.name,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = if (row.isMe) BbolColors.Violet else BbolColors.Ink,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(row.primary, style = MaterialTheme.typography.labelLarge, color = BbolColors.Violet)
            row.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft) }
        }
    }
}

/** 서버가 아바타를 주지 않으므로 이름 첫 글자를 원 안에 그린다. */
@Composable
private fun Initial(text: String, size: Dp, bg: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(text, color = BbolColors.VioletDeep, style = MaterialTheme.typography.titleMedium)
    }
}
