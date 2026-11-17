package com.bbolbbol.server.domain.completion.service

import com.bbolbbol.server.domain.completion.repository.CompletionRepository
import com.bbolbbol.server.domain.user.service.UserQueryService
import com.bbolbbol.server.global.infra.storage.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * 회원 탈퇴 후 completion 도메인의 개인정보 정리(결정 D4: 사진 삭제, 개인정보 삭제, 기록과 포인트는 통계용으로 유지).
 *
 * 지우는 것: 업로드한 사진 파일과 completions.photo_key/photo_uploaded_at, 제출 좌표(lat, lng, accuracy_m -> NULL).
 * 남기는 것: completions 행(장소 통계, 연속 기록 계산용), point_histories.
 *
 * 멱등이고 다시 실행해도 안전하다. 순서가 재시도 가능성을 보장한다:
 * 1) 사진 파일을 먼저 지운다(이미 없으면 성공으로 본다). 2) 파일 삭제에 성공한 기록의 photo_key만 NULL로 만든다.
 * 삭제에 실패한 key는 DB에 그대로 남아서 다음 실행([cleanupWithdrawnUsers] 또는 같은 이벤트 재전달)이 다시 시도한다.
 * 3) 좌표는 파일과 무관하게 지운다. 각 단계는 자기 트랜잭션이라 한 단계의 실패가 다른 단계를 되돌리지 않는다.
 * 로그에는 개인정보(좌표, 파일 key)를 남기지 않고 사용자 id, 건수, 예외 종류만 남긴다.
 *
 * 안전장치: 탈퇴한 사용자가 아니면 아무것도 하지 않는다(실수로 활성 사용자의 데이터를 지우지 않게).
 */
@Service
class CompletionDataCleanupService(
	private val completions: CompletionRepository,
	private val fileStorage: FileStorage,
	private val userQueryService: UserQueryService,
	private val tx: TransactionTemplate,
) {

	data class Result(
		val skipped: Boolean = false,
		val photosDeleted: Int = 0,
		val photosFailed: Int = 0,
		val coordinatesCleared: Int = 0,
	) {
		/** 모두 정리됐으면 true(건너뛴 것은 정리된 것이 아니다). */
		val complete: Boolean get() = !skipped && photosFailed == 0
	}

	data class Summary(val checked: Int, val cleaned: Int, val incomplete: Int)

	fun cleanupUser(userId: Long): Result {
		if (!userQueryService.isWithdrawn(userId)) {
			log.warn("탈퇴하지 않은 사용자의 정리 요청을 무시한다: userId={}", userId)
			return Result(skipped = true)
		}
		val refs = completions.findPhotoRefs(userId)
		val done = ArrayList<Long>(refs.size)
		var failed = 0
		for (ref in refs) {
			try {
				fileStorage.delete(ref.photoKey) // 이미 없으면 그냥 지나간다
				done += ref.id
			} catch (e: Exception) {
				failed++
				log.error("탈퇴 사용자의 사진 파일 삭제 실패(다음 정리 때 다시 시도): userId={}, {}", userId, e.javaClass.simpleName)
			}
		}
		if (done.isNotEmpty()) tx.executeWithoutResult { completions.clearPhotos(userId, done) }
		val cleared = tx.execute { completions.anonymizeCoordinates(userId) } ?: 0
		log.info("탈퇴 사용자 completion 개인정보 정리: userId={}, 사진 삭제={}, 사진 실패={}, 좌표 삭제 기록={}", userId, done.size, failed, cleared)
		return Result(photosDeleted = done.size, photosFailed = failed, coordinatesCleared = cleared)
	}

	/**
	 * 재시도 점검(예약 실행은 하지 않는다): 탈퇴한 사용자 중 지울 개인정보가 남은 사용자만 골라 [cleanupUser]를 다시 실행한다.
	 * 이벤트 처리 중 서버가 죽었거나 파일 삭제가 실패한 경우를 복구한다. 호출 방법:
	 * - 수동: 관리용 코드나 테스트에서 이 메서드를 직접 호출.
	 * - 주기 실행이 필요하면 `@EnableScheduling`을 켜고 `@Scheduled(cron = "0 30 4 * * *")` 메서드에서 호출한다(기본으로는 꺼 둔다).
	 * 여러 인스턴스에서 동시에 돌려도 멱등이라 결과는 같다(락은 없다).
	 */
	fun cleanupWithdrawnUsers(): Summary {
		val pending = userQueryService.withdrawnUserIds().chunked(CHUNK).flatMap { completions.userIdsWithPersonalData(it) }
		var cleaned = 0
		var incomplete = 0
		for (userId in pending) {
			val r = try {
				cleanupUser(userId)
			} catch (e: Exception) {
				log.error("탈퇴 사용자 정리 중 오류: userId={}, {}", userId, e.javaClass.simpleName)
				null
			}
			if (r != null && r.complete) cleaned++ else incomplete++
		}
		log.info("탈퇴 사용자 정리 점검: 대상={}, 완료={}, 미완료={}", pending.size, cleaned, incomplete)
		return Summary(pending.size, cleaned, incomplete)
	}

	private companion object {
		val log = LoggerFactory.getLogger(CompletionDataCleanupService::class.java)
		const val CHUNK = 500
	}
}
