package com.bbolbbol.server.domain.completion.service

import com.bbolbbol.server.domain.completion.dto.CompletionLogItem
import com.bbolbbol.server.domain.completion.dto.CompletionLogPage
import com.bbolbbol.server.domain.completion.dto.PhotoUploadResponse
import com.bbolbbol.server.domain.completion.repository.ParticipationDay
import com.bbolbbol.server.domain.completion.repository.PlaceRankingRow
import com.bbolbbol.server.domain.user.service.UserQueryService
import com.bbolbbol.server.global.infra.storage.FileStorage
import org.springframework.web.multipart.MultipartFile
import com.bbolbbol.server.domain.completion.dto.CompletionResponse
import com.bbolbbol.server.domain.completion.entity.Completion
import com.bbolbbol.server.domain.completion.exception.CompletionErrorCode
import com.bbolbbol.server.domain.completion.repository.CompletionRepository
import com.bbolbbol.server.domain.place.exception.PlaceErrorCode
import com.bbolbbol.server.domain.place.service.GeoDistance
import com.bbolbbol.server.domain.place.service.PlaceQueryService
import com.bbolbbol.server.domain.point.entity.PointType
import com.bbolbbol.server.domain.point.service.PointService
import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.FileErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** completion 도메인의 공개 진입점. completions는 이 서비스(와 같은 도메인의 provider)만 읽고 쓴다. */
@Service
@Transactional(readOnly = true)
class CompletionService(
	private val completions: CompletionRepository,
	private val placeQueryService: PlaceQueryService,
	private val pointService: PointService,
	private val userQueryService: UserQueryService,
	private val fileStorage: FileStorage,
	private val clock: Clock,
) {

	/**
	 * 도착 인증. 검사 순서(명세에 없어 정한 값): 장소 없음 PLACE404 -> 오늘 이미 인증 COMPLETION409
	 * -> 모의 위치 COMPLETION4003 -> 정확도 COMPLETION4002 -> 반경 밖 COMPLETION4001.
	 * 완료 기록과 포인트 내역은 한 트랜잭션이다. 동시 요청은 하루 1회 유니크 제약([UNIQUE_CONSTRAINT]) 위반만 COMPLETION409로 바꾼다.
	 *
	 * 신뢰 경계: lat, lng, accuracy, isMockLocation은 클라이언트가 보낸 값을 그대로 믿는다. 명세의 백엔드 참고대로
	 * "완벽한 방어는 아니지만 과제 범위에서는 충분"하다고 본 것이다. 요청 빈도 제한과 불가능한 이동(짧은 시간 안의 먼 거리)
	 * 검사도 하지 않는다. 제출 좌표와 정확도는 completions(lat, lng, accuracy_m)에 남기므로 나중에 이상 징후 탐지에 쓸 수 있다.
	 */
	@Transactional
	fun complete(
		userId: Long,
		placeId: Long,
		lat: Double,
		lng: Double,
		accuracy: Double,
		isMockLocation: Boolean,
	): CompletionResponse {
		// Gray Area A4: 온보딩을 마치지 않은 사용자는 도착 인증을 할 수 없다(명세에 에러 코드 없음, COMPLETION4031은 임시).
		if (!userQueryService.isOnboardingCompleted(userId)) throw BusinessException(CompletionErrorCode.ONBOARDING_REQUIRED)
		val place = placeQueryService.findCompletionTarget(placeId)
			?: throw BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)

		val now: LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
		val today: LocalDate = CompletionPolicy.completionDate(now)
		if (completions.existsByUserIdAndPlaceIdAndCompletionDate(userId, placeId, today)) {
			throw BusinessException(CompletionErrorCode.ALREADY_COMPLETED_TODAY)
		}
		if (isMockLocation) throw BusinessException(CompletionErrorCode.MOCK_LOCATION)
		if (accuracy > CompletionPolicy.MAX_ACCURACY_M) throw BusinessException(CompletionErrorCode.LOW_ACCURACY)
		val distance = GeoDistance.haversineMeters(lat, lng, place.lat, place.lng)
		if (distance > place.radiusM) throw BusinessException(CompletionErrorCode.OUT_OF_RADIUS)

		val firstVisit = !completions.existsByUserIdAndPlaceId(userId, placeId)
		val earned = CompletionPolicy.earnedPoint(place.rewardPoint, firstVisit)
		val completion = try {
			completions.saveAndFlush(
				Completion(
					userId = userId,
					placeId = placeId,
					completionDate = today,
					completedAt = now,
					firstVisit = firstVisit,
					earnedPoint = earned,
					lat = lat,
					lng = lng,
					accuracyM = accuracy,
				),
			)
			} catch (e: DataIntegrityViolationException) {
			// 같은 날 같은 장소의 유니크 위반(연타, 동시 요청)만 409다. 다른 무결성 위반은 숨기지 않고 500으로 올린다.
			if (!isDuplicateCompletion(e)) {
				// 개인정보(좌표, 값이 담긴 메시지)는 남기지 않는다: 예외 종류와 고정 문구만.
				log.error("도착 인증 저장 중 예상하지 못한 무결성 위반: {}", rootCauseOf(e).javaClass.name)
				throw e
			}
			throw BusinessException(CompletionErrorCode.ALREADY_COMPLETED_TODAY)
		}
		pointService.record(userId, completion.id!!, placeId, PointType.QUEST_COMPLETE, earned, now)

		return CompletionResponse(
			completionId = completion.id!!,
			placeId = placeId,
			placeName = place.name,
			isFirstVisit = firstVisit,
			earnedPoint = earned,
			totalPoint = pointService.totalPoint(userId),
			completedAt = now,
			photoBonusPoint = CompletionPolicy.photoBonusPoint(firstVisit),
			photoDeadline = CompletionPolicy.photoDeadline(now, firstVisit),
		)
	}

	fun countByUser(userId: Long): Long = completions.countByUserId(userId)

	/**
	 * 인증 사진 업로드. 검사 순서(명세에 없어 정한 값): COMPLETION404 -> COMPLETION403 -> COMPLETION4091(이미 올림)
	 * -> COMPLETION4004(마감) -> FILE413/FILE4001(내용 검증). 파트 누락과 서블릿 한도 초과는 컨트롤러 앞단(GlobalExceptionHandler)이 먼저 처리한다.
	 *
	 * - 마감은 첫 방문 인증 시각 + 1시간(정각까지 허용). 재방문은 마감이 없고 보너스 0점이다(명세 "백엔드 참고").
	 * - 파일은 디코딩해서 실제 이미지인지 확인하고 메타데이터(EXIF)를 버린 JPEG로 다시 저장한다([PhotoSanitizer]).
	 *   key는 FileStorage가 만드는 UUID 난수라 완료 id나 사용자 id로 추측할 수 없다.
	 * - 동시 업로드는 `photo_key IS NULL` 조건부 UPDATE로 한 건만 통과시키고, 진 쪽은 저장한 파일을 지우고 COMPLETION4091이다.
	 *   보너스(PHOTO_BONUS)는 같은 트랜잭션에서 기록하며 (completion_id, type) 유니크가 두 번째 방어선이다.
	 * - 보너스의 earned_at은 업로드 시각이다(통계/랭킹의 적립 시각 기준과 일치).
	 */
	@Transactional
	fun uploadPhoto(userId: Long, completionId: Long, file: MultipartFile): PhotoUploadResponse {
		val completion = completions.findById(completionId).orElse(null)
			?: throw BusinessException(CompletionErrorCode.COMPLETION_NOT_FOUND)
		if (completion.userId != userId) throw BusinessException(CompletionErrorCode.NOT_MY_COMPLETION)
		if (completion.photoKey != null) throw BusinessException(CompletionErrorCode.PHOTO_ALREADY_UPLOADED)
		val now: LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
		if (CompletionPolicy.photoDeadlinePassed(completion.completedAt, completion.firstVisit, now)) {
			throw BusinessException(CompletionErrorCode.PHOTO_DEADLINE_PASSED)
		}
		if (file.size > PhotoSanitizer.MAX_BYTES) throw BusinessException(FileErrorCode.FILE_TOO_LARGE)
		val clean = PhotoSanitizer.sanitize(file.bytes, file.contentType)

		val key = fileStorage.upload(BytesMultipartFile(clean, "photo.jpg", "image/jpeg"), PHOTO_DIRECTORY)
		val bonus = CompletionPolicy.photoBonusPoint(completion.firstVisit)
		try {
			if (completions.attachPhoto(completionId, key, now) == 0) {
				throw BusinessException(CompletionErrorCode.PHOTO_ALREADY_UPLOADED)
			}
			if (bonus > 0) {
				pointService.record(userId, completionId, completion.placeId, PointType.PHOTO_BONUS, bonus, now)
			}
		} catch (e: Throwable) {
			// 저장한 파일이 고아가 되지 않게 지운다(커밋 이후 실패까지는 못 막는다: 호출자 KDoc/보고서의 주의점).
			runCatching { fileStorage.delete(key) }
			if (e is DataIntegrityViolationException) throw BusinessException(CompletionErrorCode.PHOTO_ALREADY_UPLOADED)
			throw e
		}
		return PhotoUploadResponse(
			completionId = completionId,
			photoUrl = fileStorage.url(key),
			bonusPoint = bonus,
			totalPoint = pointService.totalPoint(userId),
		)
	}

	/** 인기 장소 랭킹용: 기간 [from, to)의 장소별 인증 횟수와 서로 다른 사용자 수. 탈퇴 사용자의 기록도 센다(Gray Area D4). */
	fun placeRanking(from: LocalDateTime, to: LocalDateTime, placeIds: Collection<Long>?): List<PlaceRankingRow> =
		completions.rankPlaces(from, to, placeIds == null, placeIds?.ifEmpty { listOf(-1L) } ?: listOf(-1L))

	/** 연속 참여 계산용: (사용자, 참여 날짜) 전체. 재방문도 참여로 센다. */
	fun participationDays(): List<ParticipationDay> = completions.participationDays()

	/**
	 * 활동 일지(방문 기준, 최신순 커서). 기간은 completed_at의 한국 시간 날짜로 자르고 to는 그날 끝까지 포함한다.
	 * 검증 실패(날짜 순서, size, cursor, 없는 category)는 COMMON400.
	 */
	fun log(userId: Long, from: LocalDate?, to: LocalDate?, category: String?, cursor: Long?, size: Int?): CompletionLogPage {
		val s = size ?: DEFAULT_SIZE
		val errors = mutableListOf<FieldError>()
		if (s < 1 || s > MAX_SIZE) errors += FieldError("size", "size는 1 이상 $MAX_SIZE 이하여야 합니다.")
		if (cursor != null && cursor < 1) errors += FieldError("cursor", "cursor는 1 이상이어야 합니다.")
		if (from != null && to != null && from.isAfter(to)) errors += FieldError("from", "from은 to보다 늦을 수 없습니다.")
		if (category != null && !placeQueryService.isKnownCategory(category)) {
			errors += FieldError("category", "존재하지 않는 카테고리 코드입니다: $category")
		}
		if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))

		val fromAt = (from ?: MIN_DATE).atStartOfDay()
		val toAt = (to ?: MAX_DATE).plusDays(1).atStartOfDay()
		val placeIds = if (category == null) listOf(-1L) else placeQueryService.placeIdsByCategory(category).ifEmpty { listOf(-1L) }
		val rows = completions.findPage(
			userId, cursor ?: Long.MAX_VALUE, fromAt, toAt, category == null, placeIds, Pageable.ofSize(s + 1),
		)
		val hasNext = rows.size > s
		val pageRows = rows.take(s)
		val places = placeQueryService.briefs(pageRows.map { it.placeId })
		// earnedPoint는 이 방문으로 받은 합계(기본 점수 + 사진 보너스). point_histories가 원천이다.
		val earned = pointService.pointByCompletions(pageRows.map { it.id!! })
		val items = pageRows.map {
			val place = places[it.placeId]
			CompletionLogItem(
				completionId = it.id!!,
				placeId = it.placeId,
				placeName = place?.name ?: PointService.UNKNOWN_PLACE,
				category = place?.categoryCode ?: "",
				address = place?.address ?: "",
				earnedPoint = earned[it.id!!] ?: it.earnedPoint,
				photoUrl = it.photoKey?.let(fileStorage::url),
				completedAt = it.completedAt,
			)
		}
		return CompletionLogPage(items, if (hasNext) items.last().completionId else null, hasNext)
	}

	companion object {
		private val log = LoggerFactory.getLogger(CompletionService::class.java)

		/** completions의 하루 1회 유니크 제약 이름(V300). H2와 PostgreSQL 모두 예외 메시지나 constraintName에 담긴다. */
		const val UNIQUE_CONSTRAINT = "uk_completions_user_place_date"

		private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }

		private fun rootCauseOf(e: Throwable): Throwable = causes(e).last()

		/** 예외 원인 사슬에 하루 1회 유니크 제약 이름이 있으면 true. */
		internal fun isDuplicateCompletion(e: Throwable): Boolean = causes(e).any {
			(it is ConstraintViolationException && it.constraintName.equals(UNIQUE_CONSTRAINT, ignoreCase = true)) ||
				it.message?.contains(UNIQUE_CONSTRAINT, ignoreCase = true) == true
		}


		const val PHOTO_DIRECTORY = "completions"
		const val DEFAULT_SIZE = 20
		const val MAX_SIZE = 50
		val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
		val MAX_DATE: LocalDate = LocalDate.of(2999, 12, 31)
	}
}
