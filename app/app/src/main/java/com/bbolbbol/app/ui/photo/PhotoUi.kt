package com.bbolbbol.app.ui.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.bbolbbol.app.BbolApplication
import com.bbolbbol.app.BuildConfig
import com.bbolbbol.app.ui.theme.BbolColors
import com.bbolbbol.app.ui.theme.BbolShapes

/** 앱 컨테이너에서 업로드 ViewModel을 만든다(AppNav를 건드리지 않으려고 Application에서 가져온다). */
@Composable
fun rememberPhotoUploadViewModel(): PhotoUploadViewModel {
    val container = (LocalContext.current.applicationContext as BbolApplication).container
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PhotoUploadViewModel(container.photoRepository, container.photoPrepareService) as T
        },
    )
}

/** 시스템 Photo Picker(저장소 권한 불필요)를 여는 함수를 돌려준다. */
@Composable
fun rememberPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** 서버 사진(인증 필요)을 불러와 그린다. 못 쓰는 주소이거나 실패하면 깨진 이미지 아이콘. */
@Composable
fun ServerPhoto(photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val url = PhotoUrls.resolve(BuildConfig.API_BASE_URL, photoUrl)
    val box = modifier.size(size).clip(BbolShapes.Tile).background(BbolColors.Lavender)
    if (url == null) {
        Box(box, contentAlignment = Alignment.Center) { Icon(Icons.Filled.BrokenImage, null, tint = BbolColors.LavenderStrong) }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "인증 사진",
        contentScale = ContentScale.Crop,
        modifier = box,
        loading = { Box(Modifier.size(size), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = BbolColors.Violet) } },
        error = { Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(Icons.Filled.BrokenImage, null, tint = BbolColors.InkSoft) } },
    )
}

/** "사진 올리기" 버튼과 진행/결과 문구. [label]은 버튼 글자. */
@Composable
fun PhotoUploadControl(
    status: UploadStatus?,
    label: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (status) {
            is UploadStatus.Done -> Text(
                if (status.bonusPoint > 0) "사진을 올렸어요! 보너스 +${status.bonusPoint}P, 총 ${status.totalPoint}P"
                else "사진을 올렸어요. (재방문은 보너스가 없어요)",
                color = BbolColors.Success,
                style = MaterialTheme.typography.bodyMedium,
            )
            UploadStatus.Preparing, UploadStatus.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = BbolColors.Violet)
                Box(Modifier.width(8.dp))
                Text(if (status == UploadStatus.Preparing) "사진을 준비하는 중..." else "올리는 중...", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                if (status is UploadStatus.Failed) {
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (status !is UploadStatus.Failed || status.retryable) {
                    OutlinedButton(onClick = onPick, shape = BbolShapes.Pill) {
                        Icon(Icons.Filled.AddAPhoto, null, Modifier.size(18.dp))
                        Box(Modifier.width(6.dp))
                        Text(if (status is UploadStatus.Failed) "다시 올리기" else label)
                    }
                }
            }
        }
    }
}

/** 도착 인증 완료 팝업 안의 사진 영역. 첫 방문이면 보너스와 마감, 재방문이면 보너스 없음을 알린다. */
@Composable
fun ArrivalPhotoSection(r: com.bbolbbol.app.data.api.CompletionResponse) {
    val vm = rememberPhotoUploadViewModel()
    val statuses by vm.statuses.collectAsState()
    val pick = rememberPhotoPicker { vm.upload(r.completionId, it) }
    val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
    val open = PhotoPolicy.bonusOpen(r.photoBonusPoint, r.photoDeadline, now)
    val status = statuses[r.completionId]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (status !is UploadStatus.Done) {
            Text(
                when {
                    open -> "사진을 올리면 +${r.photoBonusPoint}P (${com.bbolbbol.app.util.Formatters.dateTime(r.photoDeadline).takeLast(5)}까지)"
                    r.photoBonusPoint > 0 -> "보너스 마감 시간이 지났을 수 있어요. 올려 보면 서버가 알려줘요."
                    else -> "사진을 남길 수 있어요. (재방문은 보너스가 없어요)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = BbolColors.InkSoft,
            )
        }
        PhotoUploadControl(status, label = "사진 올리기", onPick = pick)
    }
}
