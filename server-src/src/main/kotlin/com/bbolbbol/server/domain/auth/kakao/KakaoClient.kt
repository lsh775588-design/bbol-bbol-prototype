package com.bbolbbol.server.domain.auth.kakao

/** 카카오가 확인해 준 사용자 정보. email은 동의하지 않았거나 인증되지 않은 이메일이면 null이다. */
data class KakaoProfile(val kakaoId: String, val email: String?, val nickname: String?)

/**
 * 카카오 API 포트. 구현은 실패를 BusinessException(KakaoErrorCode)으로 던진다.
 * 운영은 RestKakaoClient, local과 test는 FakeKakaoClient(개발용 토큰)를 쓴다.
 */
interface KakaoClient {

	/** 카카오 access token으로 사용자를 확인한다. 만료, 무효면 KAKAO401, 카카오 서버 문제면 KAKAO502. */
	fun fetchProfile(accessToken: String): KakaoProfile

	/**
	 * 앱과 카카오 계정의 연결을 끊는다(탈퇴). 사용자의 카카오 토큰은 서버에 없으므로 앱 어드민 키로 kakaoId를 지정해 끊는다.
	 * 실패하면 예외를 던진다(호출하는 쪽이 로그만 남기고 탈퇴는 계속한다).
	 */
	fun unlink(kakaoId: String)
}
