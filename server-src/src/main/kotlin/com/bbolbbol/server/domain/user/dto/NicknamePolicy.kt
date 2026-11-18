package com.bbolbbol.server.domain.user.dto

/** 닉네임 형식 규칙: 2~10자, 한글(완성형)/영문/숫자. */
object NicknamePolicy {
	const val REGEX = "^[가-힣a-zA-Z0-9]{2,10}$"
	const val FORMAT_MESSAGE = "닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있습니다."
	private val PATTERN = Regex(REGEX)

	fun isValidFormat(nickname: String?): Boolean = nickname != null && PATTERN.matches(nickname)
}
