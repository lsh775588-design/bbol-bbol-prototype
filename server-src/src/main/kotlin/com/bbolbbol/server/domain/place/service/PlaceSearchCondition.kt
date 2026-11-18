package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors

/** GET /places 쿼리 파라미터를 검증해 만든 검색 조건. 검증 실패는 COMMON400 + data.errors. */
data class PlaceSearchCondition(
	val lat: Double,
	val lng: Double,
	val radius: Int,
	val categories: Set<String>,
	val excludeCompletedToday: Boolean,
	val size: Int,
) {
	companion object {
		const val DEFAULT_RADIUS = 1000
		const val MAX_RADIUS = 5000
		const val DEFAULT_SIZE = 100
		const val MAX_SIZE = 200

		/** knownCategoryCodes: 존재하는 카테고리 코드(없는 category 검증용) */
		fun validated(
			lat: Double?,
			lng: Double?,
			radius: Int?,
			category: String?,
			excludeCompletedToday: Boolean?,
			size: Int?,
			knownCategoryCodes: Set<String>,
		): PlaceSearchCondition {
			val errors = mutableListOf<FieldError>()
			validateLatLng(lat, lng, errors)
			val r = radius ?: DEFAULT_RADIUS
			if (r < 1 || r > MAX_RADIUS) {
				errors += FieldError("radius", "반경은 1 이상 $MAX_RADIUS 이하여야 합니다.")
			}
			val s = size ?: DEFAULT_SIZE
			if (s < 1 || s > MAX_SIZE) {
				errors += FieldError("size", "size는 1 이상 $MAX_SIZE 이하여야 합니다.")
			}
			val codes = LinkedHashSet<String>()
			if (category != null) {
				for (raw in category.split(",")) {
					val code = raw.trim()
					if (code.isEmpty() || code !in knownCategoryCodes) {
						errors += FieldError("category", "존재하지 않는 카테고리 코드입니다: $code")
						break
					}
					codes += code
				}
			}
			if (errors.isNotEmpty()) {
				throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))
			}
			return PlaceSearchCondition(lat!!, lng!!, r, codes, excludeCompletedToday == true, s)
		}

		/** lat/lng 필수 + 범위 검증(GET /places, GET /places/recommendations 공용). */
		internal fun validateLatLng(lat: Double?, lng: Double?, errors: MutableList<FieldError>) {
			if (lat == null) {
				errors += FieldError("lat", "필수 파라미터가 누락되었습니다.")
			} else if (!lat.isFinite() || lat < -90 || lat > 90) {
				errors += FieldError("lat", "위도는 -90 이상 90 이하여야 합니다.")
			}
			if (lng == null) {
				errors += FieldError("lng", "필수 파라미터가 누락되었습니다.")
			} else if (!lng.isFinite() || lng < -180 || lng > 180) {
				errors += FieldError("lng", "경도는 -180 이상 180 이하여야 합니다.")
			}
		}
	}
}
