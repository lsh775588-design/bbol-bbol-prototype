package com.bbolbbol.server.domain.place.exception

import com.bbolbbol.server.global.error.ErrorCode
import org.springframework.http.HttpStatus

enum class PlaceErrorCode(
	override val status: HttpStatus,
	override val code: String,
	override val message: String,
) : ErrorCode {

	PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE404", "존재하지 않는 장소"),
}
