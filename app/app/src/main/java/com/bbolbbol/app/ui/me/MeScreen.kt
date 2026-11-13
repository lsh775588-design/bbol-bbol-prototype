package com.bbolbbol.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.common.Avatar
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.ComingSoonCard
import com.bbolbbol.app.ui.common.EmptyState
import com.bbolbbol.app.ui.common.ErrorState
import com.bbolbbol.app.ui.common.LoadingState
import com.bbolbbol.app.ui.common.PrimaryButton
import com.bbolbbol.app.ui.common.ScreenTitle
import com.bbolbbol.app.ui.common.SectionHeader
import com.bbolbbol.app.ui.common.StatTile
import com.bbolbbol.app.ui.home.RecentRow
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.util.Formatters

@Composable
fun MeScreen(
    vm: MeViewModel,
    recentVm: RecentActivityViewModel,
    onPointHistory: () -> Unit,
    onActivityLog: () -> Unit,
    onStats: () -> Unit = {},
    accountSection: @Composable () -> Unit,
) {
    val s by vm.state.collectAsState()
    val recent by recentVm.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.onEnter()
        recentVm.onEnter()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BbolColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("마이")

        val me = s.me
        when {
            s.loading -> LoadingState()
            me == null -> ErrorState(s.error ?: "불러오지 못했어요.", vm::load)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(Formatters.initial(me.nickname), size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        me.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BbolColors.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${me.loginType} · 가입일 ${Formatters.date(me.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BbolColors.InkSoft,
                    )
                }
            }
        }

        StatsRow(s, vm::loadPoints)

        if (me != null) {
            BbolCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("닉네임 변경", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = s.nicknameInput,
                        onValueChange = vm::onNickname,
                        label = { Text("닉네임") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        isError = s.nicknameMessage != null && s.nicknameIsError,
                        supportingText = {
                            Text(s.nicknameMessage ?: "닉네임은 변경 후 30일이 지나야 다시 바꿀 수 있어요.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        "닉네임 변경",
                        onClick = vm::saveNickname,
                        enabled = !s.saving && s.nicknameInput.trim() != me.nickname,
                        loading = s.saving,
                    )
                }
            }
        }

        BbolCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("퀘스트 기록", actionLabel = "전체 보기", onAction = onActivityLog)
                val list = recent.data
                when {
                    recent.loading -> LoadingState()
                    list == null -> ErrorState(recent.error ?: "불러오지 못했어요.", recentVm::load)
                    list.isEmpty() -> EmptyState("아직 도착 인증한 장소가 없어요.")
                    else -> list.forEach { RecentRow(it) }
                }
            }
        }

        BbolCard {
            Column {
                MenuRow("활동 일지", onActivityLog)
                MenuRow("포인트 내역", onPointHistory)
            }
        }

        BbolCard {
            MenuRow("퀘스트 통계 (주·월·년 차트)", onStats)
        }

        accountSection() // 온보딩, 로그아웃, 회원 탈퇴 (ui/auth/AuthScreens.kt)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatsRow(s: MeUiState, onRetry: () -> Unit) {
    val p = s.points
    when {
        s.pointsLoading -> LoadingState()
        p == null -> ErrorState(s.pointsError ?: "불러오지 못했어요.", onRetry)
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("총 포인트", "${p.totalPoint}P", Modifier.weight(1f), Icons.Filled.Star)
            StatTile("오늘 포인트", "${p.todayPoint}P", Modifier.weight(1f), Icons.Filled.Today)
            StatTile("인증 횟수", "${p.completionCount}회", Modifier.weight(1f), Icons.Filled.CheckCircle)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BbolColors.InkSoft, modifier = Modifier.size(22.dp))
    }
}
