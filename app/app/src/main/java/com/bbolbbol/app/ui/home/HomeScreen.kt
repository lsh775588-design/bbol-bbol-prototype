package com.bbolbbol.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.data.api.PointSummary
import com.bbolbbol.app.data.api.RecommendedPlace
import com.bbolbbol.app.ui.common.Avatar
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.CategoryTile
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.InfoChip
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.PawIcon
import com.bbolbbol.app.ui.common.SectionHeader
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.common.categoryIcon
import com.bbolbbol.app.ui.common.fetchLocation
import com.bbolbbol.app.ui.common.hasLocationPermission
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes
import com.bbolbbol.app.util.Formatters

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenPlace: (Long) -> Unit,
    onOpenRecommend: (lat: Double, lng: Double) -> Unit,
    onOpenActivityLog: () -> Unit,
    onGoMap: () -> Unit = {},
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    // 위치 권한이 이미 있을 때만 현재 위치로 추천을 받는다. 권한 요청은 지도 탭에서 한다.
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            fetchLocation(context) { loc -> vm.start(loc?.latitude, loc?.longitude) }
        } else {
            vm.start(null, null)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("홈", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Greeting(s.nickname, Modifier.padding(horizontal = 20.dp), onRetry = vm::loadNickname)
        PointsCard(s.points, Modifier.padding(horizontal = 20.dp), onRetry = vm::loadPoints)

        OngoingQuests(
            s.ongoing, s.ongoingHasLocation, s.ongoingLoading, Modifier.padding(horizontal = 20.dp),
            onOpenPlace = onOpenPlace, onAbandon = vm::abandon, onGoMap = onGoMap,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(
                "뽈뽈 추천",
                Modifier.padding(horizontal = 20.dp),
                actionLabel = "전체 보기",
                onAction = { onOpenRecommend(s.lat, s.lng) },
            )
            if (s.browse && !s.recommendations.loading) {
                Text(
                    "위치를 사용할 수 없어 종로구 중심으로 추천해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BbolColors.InkSoft,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            RecommendRow(s.recommendations, onOpenPlace, vm::loadRecommendations)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionHeader("최근 활동", actionLabel = "전체 보기", onAction = onOpenActivityLog)
            RecentList(s.recent, vm::loadRecent)
        }
    }
}

@Composable
private fun Greeting(nickname: Section<String>, modifier: Modifier, onRetry: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Avatar(Formatters.initial(nickname.data), size = 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("안녕하세요,", color = BbolColors.InkSoft, style = MaterialTheme.typography.bodyMedium)
            val name = nickname.data
            when {
                nickname.loading -> Text("...", style = MaterialTheme.typography.titleLarge)
                name != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(PawIcon, null, tint = BbolColors.PawPink, modifier = Modifier.size(20.dp))
                }
                else -> ErrorState(nickname.error ?: "불러오지 못했어요.", onRetry)
            }
        }
    }
}

@Composable
private fun PointsCard(points: Section<PointSummary>, modifier: Modifier, onRetry: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(BbolShapes.Card)
            .background(Brush.linearGradient(listOf(BbolColors.Violet, BbolColors.VioletDeep))),
    ) {
        Icon(
            PawIcon, null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(72.dp),
        )
        Column(Modifier.padding(20.dp)) {
            Text("내 포인트", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            val p = points.data
            when {
                points.loading -> Text("...", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                p == null -> Column {
                    Text(points.error ?: "불러오지 못했어요.", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "다시 시도",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(BbolShapes.Pill).background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                else -> {
                    Text("${p.totalPoint}P", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("오늘", "${p.todayPoint}P", Modifier.weight(1f))
                        MiniStat("인증 횟수", "${p.completionCount}회", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(BbolShapes.Tile).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RecommendRow(sec: Section<List<RecommendedPlace>>, onOpenPlace: (Long) -> Unit, onRetry: () -> Unit) {
    val list = sec.data
    when {
        sec.loading -> LoadingState()
        list == null -> ErrorState(sec.error ?: "불러오지 못했어요.", onRetry)
        list.isEmpty() -> EmptyState("추천할 장소가 아직 없어요.")
        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.placeId }) { RecommendCard(it) { onOpenPlace(it.placeId) } }
        }
    }
}

@Composable
private fun RecommendCard(p: RecommendedPlace, onClick: () -> Unit) {
    BbolCard(Modifier.width(220.dp), onClick = onClick) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(96.dp).background(BbolColors.Lavender),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(p.category), null, tint = BbolColors.Violet, modifier = Modifier.size(44.dp))
                if (p.isHot) {
                    Text(
                        "HOT",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(BbolShapes.Pill)
                            .background(BbolColors.PawPink).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(Formatters.distance(p.distance))
                    InfoChip("${p.expectedPoint}P")
                }
                if (p.reason.isNotBlank()) {
                    Text(
                        p.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = BbolColors.InkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentList(sec: Section<List<CompletionLogItem>>, onRetry: () -> Unit) {
    val list = sec.data
    BbolCard {
        when {
            sec.loading -> LoadingState()
            list == null -> ErrorState(sec.error ?: "불러오지 못했어요.", onRetry)
            list.isEmpty() -> EmptyState("아직 도착 인증한 장소가 없어요.")
            else -> Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                list.forEach { RecentRow(it) }
            }
        }
    }
}

/** 최근 활동 한 줄. 내 정보 화면의 퀘스트 기록에서도 쓴다. */
@Composable
fun RecentRow(c: CompletionLogItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryTile(c.category, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.placeName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatters.categoryName(c.category)} · ${Formatters.date(c.completedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = BbolColors.InkSoft,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusChip("완료", StatusKind.DONE)
            Text("+${c.earnedPoint}P", style = MaterialTheme.typography.labelMedium, color = BbolColors.Violet)
        }
    }
}

/** 프로토타입의 "Ongoing Quests" 카드: 큰 개수, 장소 줄 목록. 비면 안내와 지도 버튼. */
@Composable
private fun OngoingQuests(
    list: List<OngoingQuest>,
    hasLocation: Boolean,
    loading: Boolean,
    modifier: Modifier,
    onOpenPlace: (Long) -> Unit,
    onAbandon: (Long) -> Unit,
    onGoMap: () -> Unit,
) {
    BbolCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("진행 중인 퀘스트", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (loading && list.isEmpty()) "-" else "${list.size}", style = MaterialTheme.typography.headlineMedium, color = BbolColors.Violet)
                        Text("  개 진행 중", style = MaterialTheme.typography.bodyMedium, color = BbolColors.InkSoft, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Icon(PawIcon, null, tint = BbolColors.PawPink, modifier = Modifier.size(36.dp))
            }
            if (list.isEmpty() && loading) {
                LoadingState()
            } else if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "아직 받은 퀘스트가 없어요.\n지도에서 가고 싶은 장소의 퀘스트를 받아 보세요!",
                        color = BbolColors.InkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        "지도에서 퀘스트 찾기",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(BbolShapes.Pill).background(BbolColors.Violet)
                            .clickable(onClick = onGoMap).padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            } else {
                list.forEach { OngoingRow(it, hasLocation, onOpenPlace, onAbandon) }
            }
        }
    }
}

@Composable
private fun OngoingRow(q: OngoingQuest, hasLocation: Boolean, onOpenPlace: (Long) -> Unit, onAbandon: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(BbolShapes.Tile).clickable { onOpenPlace(q.placeId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(q.category, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(q.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    Formatters.categoryName(q.category),
                    if (q.distance != null) Formatters.distance(q.distance) else if (!hasLocation) "위치 권한 필요" else null,
                    "${q.rewardPoint}P",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = BbolColors.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusChip("진행 중", StatusKind.IN_PROGRESS)
            Text(
                "포기",
                style = MaterialTheme.typography.labelMedium,
                color = BbolColors.InkSoft,
                modifier = Modifier.clip(BbolShapes.Pill).clickable { onAbandon(q.placeId) }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
