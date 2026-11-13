package com.bbolbbol.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes

/** 둥근 흰 카드 + 부드러운 그림자. */
@Composable
fun BbolCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.White,
    content: @Composable () -> Unit,
) {
    val base = Modifier.fillMaxWidth().then(modifier)
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = base, shape = BbolShapes.Card, color = containerColor,
            shadowElevation = 3.dp, content = content,
        )
    } else {
        Surface(modifier = base, shape = BbolShapes.Card, color = containerColor, shadowElevation = 3.dp, content = content)
    }
}

/** 알약 모양 필터 칩. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = BbolShapes.Pill,
        color = if (selected) BbolColors.Violet else Color.White,
        contentColor = if (selected) Color.White else BbolColors.Ink,
        border = if (selected) null else BorderStroke(1.dp, BbolColors.Outline),
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

enum class StatusKind { DONE, IN_PROGRESS, NOT_STARTED }

/** 상태 칩: 초록 "완료"/"진행 중", 회색 "시작 전". */
@Composable
fun StatusChip(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        StatusKind.DONE, StatusKind.IN_PROGRESS -> BbolColors.SuccessBg to BbolColors.Success
        StatusKind.NOT_STARTED -> BbolColors.NeutralBg to BbolColors.Neutral
    }
    Box(modifier.clip(BbolShapes.Pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** 거리/포인트처럼 작은 정보 칩. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(BbolShapes.Pill).background(BbolColors.Lavender).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = BbolColors.Violet, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = BbolColors.VioletDeep, style = MaterialTheme.typography.labelMedium)
    }
}

/** 사진 자리에 쓰는 카테고리 아이콘 타일. */
@Composable
fun CategoryTile(code: String?, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        modifier.size(size).clip(BbolShapes.Tile).background(BbolColors.Lavender),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(code), null, tint = BbolColors.Violet, modifier = Modifier.size(size * 0.5f))
    }
}

/** 숫자 + 라벨 통계 타일. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = BbolShapes.Tile, color = Color.White, shadowElevation = 2.dp) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = BbolColors.Violet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(4.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, color = BbolColors.VioletDeep, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft, maxLines = 1)
        }
    }
}

/** 섹션 제목 + 오른쪽 "전체 보기". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            Text(
                "$actionLabel >",
                color = BbolColors.Violet,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(BbolShapes.Pill).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** 화면 맨 위 큰 제목(탭 화면용). */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        trailing()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BbolColors.Violet)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onRetry) { Text("다시 시도") }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(PawIcon, null, tint = BbolColors.LavenderStrong, modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, color = BbolColors.InkSoft, textAlign = TextAlign.Center)
    }
}

/** 서버 데이터가 없어 아직 못 만드는 영역. 가짜 숫자 대신 이걸 보여준다. */
@Composable
fun ComingSoonCard(title: String, message: String, modifier: Modifier = Modifier) {
    BbolCard(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(BbolColors.Lavender), contentAlignment = Alignment.Center) {
                Icon(PawIcon, null, tint = BbolColors.LavenderStrong, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodySmall, color = BbolColors.InkSoft)
            }
            StatusChip("준비 중", StatusKind.NOT_STARTED)
        }
    }
}

/** 주 버튼(보라 알약). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = BbolShapes.Pill,
        colors = ButtonDefaults.buttonColors(containerColor = BbolColors.Violet),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Text(text)
    }
}

/** 둥근 아바타(닉네임 첫 글자). */
@Composable
fun Avatar(initial: String, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(BbolColors.LavenderStrong),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = BbolColors.VioletDeep, style = MaterialTheme.typography.headlineMedium)
    }
}
