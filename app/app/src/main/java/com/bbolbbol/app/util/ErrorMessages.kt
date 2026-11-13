package com.bbolbbol.app.util

import com.bbolbbol.app.data.api.ApiResult

/** 백엔드 에러 코드를 사용자에게 보여줄 한국어 문구로 바꾸는 유일한 곳. */
object ErrorMessages {
    const val NETWORK = "서버에 연결할 수 없어요. 네트워크 상태를 확인해 주세요."
    const val UNKNOWN = "알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
    private const val FILE_TOO_LARGE = "사진 용량이 너무 커요(최대 10MB). 다른 사진을 골라 주세요."
    private const val SERVER = "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요."

    private val byCode = mapOf(
        "AUTH401" to "로그인이 만료되었어요. 다시 로그인해 주세요.",
        "AUTH4011" to "이메일 또는 비밀번호가 일치하지 않아요.",
        "AUTH4012" to "로그인이 만료되었어요. 다시 로그인해 주세요.",
        "AUTH429" to "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.",
        "EMAIL4001" to "인증 코드가 일치하지 않아요.",
        "EMAIL4002" to "인증 코드가 만료되었거나 5번 틀려서 사용할 수 없어요. 코드를 다시 받아 주세요.",
        "EMAIL429" to "인증 코드는 1분에 한 번, 하루 5번까지만 보낼 수 있어요. 잠시 후 다시 시도해 주세요.",
        "EMAIL502" to "인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.",
        "KAKAO401" to "카카오 로그인이 만료되었거나 올바르지 않아요. 다시 시도해 주세요.",
        "KAKAO502" to "카카오 서버에서 응답이 없어요. 잠시 후 다시 시도해 주세요.",
        "USER4093" to "이 카카오 계정의 이메일은 이미 이메일로 가입되어 있어요. 이메일로 로그인해 주세요.",
        "COMPLETION4031" to "도착 인증을 하려면 먼저 추가 정보(온보딩)를 입력해야 해요.",
        "USER404" to "존재하지 않거나 탈퇴한 계정이에요.",
        "USER409" to "이미 가입된 이메일이에요.",
        "USER4001" to "사용할 수 없는 단어가 닉네임에 포함되어 있어요.",
        "USER4002" to "닉네임은 변경 후 30일이 지나야 다시 바꿀 수 있어요.",
        "USER4092" to "이미 사용 중인 닉네임이에요.",
        "EMAIL4003" to "이메일 인증 정보가 없거나 만료되었어요.",
        "PLACE404" to "존재하지 않는 장소예요.",
        "COMPLETION4001" to "인증 반경 밖이에요. 장소에 더 가까이 가서 다시 시도해 주세요.",
        "COMPLETION4002" to "GPS 정확도가 낮아요. 실외로 이동해서 다시 시도해 주세요.",
        "COMPLETION4003" to "모의 위치는 사용할 수 없어요.",
        "COMPLETION409" to "오늘 이미 인증했어요. 내일 다시 도전해 보세요.",
        "COMPLETION403" to "내 인증 기록이 아니라서 사진을 올릴 수 없어요.",
        "COMPLETION404" to "인증 기록을 찾을 수 없어요.",
        "COMPLETION4004" to "사진 보너스 마감 시간(인증 후 1시간)이 지났어요.",
        "COMPLETION4091" to "이미 사진을 올렸어요. 보너스는 한 번만 받을 수 있어요.",
        "FILE4001" to "사진 파일이 없거나 지원하지 않는 형식이에요. JPEG 또는 PNG 사진을 골라 주세요.",
        "FILE413" to FILE_TOO_LARGE,
        "COMMON400" to "입력값을 다시 확인해 주세요.",
        "COMMON404" to "요청한 경로를 찾을 수 없어요.",
        "COMMON500" to SERVER,
    )

    fun forCode(code: String): String? = byCode[code]

    /** COMMON400이면 첫 필드 오류의 이유(서버가 준 한국어)를 우선 보여준다. */
    fun message(result: ApiResult<*>): String = when (result) {
        is ApiResult.Success -> ""
        ApiResult.NetworkError -> NETWORK
        is ApiResult.Failure -> {
            val first = result.fieldErrors.firstOrNull()?.reason
            if (result.code == "COMMON400" && !first.isNullOrBlank()) {
                first
            } else if (result.code == "EMAIL4001" && result.remainingAttempts != null) {
                "${byCode["EMAIL4001"]} 남은 시도 ${result.remainingAttempts}번"
            } else {
                byCode[result.code] ?: if (result.httpStatus == 413) FILE_TOO_LARGE else if (result.httpStatus >= 500) SERVER else UNKNOWN
            }
        }
    }

    /** 필드별 오류(회원가입 폼). */
    fun fieldErrors(result: ApiResult.Failure): Map<String, String> =
        result.fieldErrors.filter { it.field.isNotBlank() }.associate { it.field to it.reason }

    fun isUnauthorized(result: ApiResult<*>) = result is ApiResult.Failure && result.code == "AUTH401"
}
