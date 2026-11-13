package com.bbolbbol.app.ui.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbolbbol.app.data.api.CompletionLogItem
import com.bbolbbol.app.ui.common.BbolCard
import com.bbolbbol.app.ui.common.CategoryTile
import com.bbolbbol.app.ui.common.StatusChip
import com.bbolbbol.app.ui.common.StatusKind
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.util.Formatters

/** 활동 일지 한 줄: 사진이 있으면 사진, 없으면 카테고리 타일과 "사진 추가" 버튼. */
@Composable
fun ActivityLogRow(c: CompletionLogItem, status: UploadStatus?, onAddPhoto: () -> Unit) {
    val done = status as? UploadStatus.Done
    val photoUrl = c.photoUrl ?: done?.photoUrl
    val points = c.earnedPoint + (done?.bonusPoint ?: 0)
    BbolCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photoUrl != null) ServerPhoto(photoUrl, size = 48.dp) else CategoryTile(c.category, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.placeName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Formatters.categoryName(c.category)} · ${Formatters.dateTime(c.completedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BbolColors.InkSoft,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip("완료", StatusKind.DONE)
                    Text("+${points}P", style = MaterialTheme.typography.labelMedium, color = BbolColors.Violet)
                }
            }
            // 사진이 없는 기록만. 마감/이미 올림 같은 판단은 서버가 하고 실패하면 문구로 보여준다.
            if (c.photoUrl == null) {
                PhotoUploadControl(status, label = "사진 추가", onPick = onAddPhoto, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
