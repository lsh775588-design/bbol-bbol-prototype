package com.bbolbbol.server.domain.place.service

import com.bbolbbol.server.global.error.BusinessException
import com.bbolbbol.server.global.error.CommonErrorCode
import com.bbolbbol.server.global.error.GlobalExceptionHandler.FieldError
import com.bbolbbol.server.global.error.GlobalExceptionHandler.ValidationErrors

/** GET /places/recommendations 쿼리 파라미터를 검증한 조건. 검증 실패는 COMMON400 + data.errors. */
data class RecommendationCondition(val lat: Double, val lng: Double, val size: Int) {
	companion object {
		const val DEFAULT_SIZE = 5
		const val MAX_SIZE = 10

		fun validated(lat: Double?, lng: Double?, size: Int?): RecommendationCondition {
			val errors = mutableListOf<FieldError>()
			PlaceSearchCondition.validateLatLng(lat, lng, errors)
			val s = size ?: DEFAULT_SIZE
			if (s < 1 || s > MAX_SIZE) {
				errors += FieldError("size", "size는 1 이상 $MAX_SIZE 이하여야 합니다.")
			}
			if (errors.isNotEmpty()) {
				throw BusinessException(CommonErrorCode.INVALID_INPUT, ValidationErrors(errors))
			}
			return RecommendationCondition(lat!!, lng!!, s)
		}
	}
}
