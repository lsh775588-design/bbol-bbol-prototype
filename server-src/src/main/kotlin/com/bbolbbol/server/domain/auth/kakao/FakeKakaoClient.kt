package com.bbolbbol.server.domain.auth.kakao

import com.bbolbbol.server.domain.auth.exception.KakaoErrorCode
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * !!! DEV ONLY !!! local, test 프로파일에서만 만들어지는 가짜 카카오. 카카오 키 없이 로그인 흐름을 확인하려는 용도다.
 *
 * 개발용 토큰 형식(`|`로 구분): `DEV-KAKAO|{kakaoId}|{이메일, 비우면 이메일 동의 안 함}|{카카오 닉네임}`
 *   예) DEV-KAKAO|1001|kakao1@example.com|뽈뽈 탐험가!   (이메일 없이: DEV-KAKAO|1002||카카오유저)
 * 특수 토큰: `DEV-KAKAO-EXPIRED` -> KAKAO401, `DEV-KAKAO-DOWN` -> KAKAO502. 그 밖의 토큰은 모두 KAKAO401.
 * unlink는 호출 기록을 [unlinkedIds]에 남기고, kakaoId가 `unlink-fail`로 시작하면 실패(KAKAO502)한다(탈퇴가 계속되는지 확인용).
 */
@Component
@Profile("local | test")
class FakeKakaoClient : KakaoClient {

	val unlinkedIds: MutableList<String> = CopyOnWriteArrayList()

	override fun fetchProfile(accessToken: String): KakaoProfile {
		if (accessToken == DOWN_TOKEN) throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		val parts = accessToken.split('|', limit = 4)
		if (parts.size != 4 || parts[0] != PREFIX || parts[1].isBlank()) {
			throw BusinessException(KakaoErrorCode.INVALID_KAKAO_TOKEN)
		}
		return KakaoProfile(
			kakaoId = parts[1],
			email = parts[2].takeIf { it.isNotBlank() },
			nickname = parts[3].takeIf { it.isNotBlank() },
		)
	}

	override fun unlink(kakaoId: String) {
		if (kakaoId.startsWith("unlink-fail")) throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		unlinkedIds.add(kakaoId)
	}

	companion object {
		const val PREFIX = "DEV-KAKAO"
		const val DOWN_TOKEN = "DEV-KAKAO-DOWN"
	}
}
