package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.kakao.KakaoClient
import com.bbolbbol.server.domain.user.event.UserWithdrawnEvent
import com.bbolbbol.server.domain.user.service.UserWithdrawalService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

/**
 * 회원 탈퇴 전체 흐름. 순서와 결정:
 * 1) DB에서 탈퇴 처리(개인정보 즉시 삭제, 트랜잭션 하나). 여기서 실패하면 아무것도 바뀌지 않는다.
 * 2) refresh token 삭제(모든 기기 로그아웃). 이미 발급한 access token은 UserStatusChecker가 AUTH401로 막는다.
 * 3) 카카오 사용자는 연결 끊기. 실패해도 탈퇴는 이미 끝났으므로 로그만 남기고 계속한다(결정: 되돌릴 수 없고 재시도 수단이 없다).
 * 4) UserWithdrawnEvent 발행(사진 삭제 등 다른 도메인의 정리). 리스너의 예외도 삼켜서 응답에 영향을 주지 않는다.
 * 2~4는 DB 커밋 뒤에 실행한다. 서버가 2와 4 사이에 죽으면 정리가 누락될 수 있다(아웃박스 같은 재시도 장치는 만들지 않았다).
 */
@Service
class WithdrawalService(
	private val userWithdrawalService: UserWithdrawalService,
	private val refreshTokenService: RefreshTokenService,
	private val kakaoClient: KakaoClient,
	private val events: ApplicationEventPublisher,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun withdraw(userId: Long) {
		val result = userWithdrawalService.withdraw(userId)
		refreshTokenService.delete(userId)
		result.kakaoId?.let { kakaoId ->
			try {
				kakaoClient.unlink(kakaoId)
			} catch (e: Exception) {
				log.warn("카카오 연결 끊기 실패(탈퇴는 계속): userId={}, {}", userId, e.javaClass.simpleName)
			}
		}
		try {
			events.publishEvent(UserWithdrawnEvent(userId))
		} catch (e: Exception) {
			log.error("탈퇴 후속 처리 실패: userId={}", userId, e)
		}
	}
}
