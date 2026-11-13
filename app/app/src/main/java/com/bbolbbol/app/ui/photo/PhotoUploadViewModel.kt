package com.bbolbbol.app.ui.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.repository.PhotoRepository
import com.bbolbbol.app.util.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 같은 기록으로 다시 시도해도 결과가 같은 서버 코드. */
val FINAL_CODES = setOf("COMPLETION403", "COMPLETION404", "COMPLETION4004", "COMPLETION4091")

/** 완료 기록 하나의 업로드 진행 상태. */
sealed interface UploadStatus {
    data object Preparing : UploadStatus
    data object Uploading : UploadStatus
    data class Done(val photoUrl: String, val bonusPoint: Int, val totalPoint: Long) : UploadStatus
    /** [retryable]이 false면(마감 지남, 이미 올림, 남의 기록 등) 같은 기록에 다시 올려도 소용없다. */
    data class Failed(val message: String, val retryable: Boolean = true) : UploadStatus

    val busy get() = this is Preparing || this is Uploading
}

const val UNREADABLE_MESSAGE = "이 사진을 읽을 수 없어요. 다른 사진을 골라 주세요."
const val TOO_LARGE_MESSAGE = "사진이 너무 커서 줄일 수 없어요. 다른 사진을 골라 주세요."

/** 도착 인증 팝업과 활동 일지가 함께 쓴다(완료 기록 id별 상태). */
class PhotoUploadViewModel(
    private val repo: PhotoRepository,
    private val prepare: PhotoPrepareService,
) : ViewModel() {
    private val _statuses = MutableStateFlow<Map<Long, UploadStatus>>(emptyMap())
    val statuses: StateFlow<Map<Long, UploadStatus>> = _statuses.asStateFlow()

    /** [uri]는 Photo Picker가 돌려준 content Uri 문자열. 같은 기록을 이미 올리는 중이면 무시한다. */
    fun upload(completionId: Long, uri: String) {
        if (_statuses.value[completionId]?.busy == true) return
        if (_statuses.value[completionId] is UploadStatus.Done) return
        set(completionId, UploadStatus.Preparing)
        viewModelScope.launch {
            when (val p = prepare.prepare(uri)) {
                PrepareResult.Unreadable -> set(completionId, UploadStatus.Failed(UNREADABLE_MESSAGE))
                PrepareResult.TooLarge -> set(completionId, UploadStatus.Failed(TOO_LARGE_MESSAGE))
                is PrepareResult.Ready -> {
                    set(completionId, UploadStatus.Uploading)
                    when (val r = repo.upload(completionId, p.jpeg)) {
                        is ApiResult.Success -> set(
                            completionId,
                            UploadStatus.Done(r.data.photoUrl, r.data.bonusPoint, r.data.totalPoint),
                        )
                        else -> set(completionId, UploadStatus.Failed(ErrorMessages.message(r), retryable = !isFinal(r)))
                    }
                }
            }
        }
    }

    private fun isFinal(r: ApiResult<*>) =
        r is ApiResult.Failure && r.code in FINAL_CODES

    fun clear(completionId: Long) = _statuses.update { it - completionId }

    private fun set(id: Long, s: UploadStatus) = _statuses.update { it + (id to s) }
}

/** 첫 방문 보너스를 아직 받을 수 있는지(표시용 추정). 마감은 서버가 최종 판단한다. */
object PhotoPolicy {
    /** [now]는 Asia/Seoul 기준 현재 시각. 서버 시각 문자열을 못 읽으면 마감 전으로 본다(서버가 거절한다). */
    fun bonusOpen(bonusPoint: Int, deadlineIso: String?, now: java.time.LocalDateTime): Boolean {
        if (bonusPoint <= 0 || deadlineIso == null) return false
        val deadline = try {
            java.time.LocalDateTime.parse(deadlineIso)
        } catch (e: java.time.format.DateTimeParseException) {
            return true
        }
        return !now.isAfter(deadline)
    }
}
