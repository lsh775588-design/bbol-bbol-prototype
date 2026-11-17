package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.exception.AuthErrorCode
import com.bbolbbol.server.domain.auth.repository.LoginFailureStore
import com.bbolbbol.server.global.error.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * NOT IN SPEC: 명세에 없는 로그인 시도 제한(AUTH429). IP 하나가 1분 안에 maxFailures번 실패하면 그 창이 끝날 때까지 막는다.
 *
 * 주의(나중에 직접 만들 때):
 * - IP는 호출하는 쪽이 서블릿 remoteAddr만으로 넘긴다. X-Forwarded-For는 클라이언트가 마음대로 쓸 수 있어서 믿지 않는다.
 *   그 결과 리버스 프록시 뒤에서는 모든 사용자가 프록시 IP 하나로 보여 서로를 잠글 수 있다. 배포 구조가 정해지면
 *   신뢰할 프록시 목록과 함께 다시 정해야 한다.
 * - 성공하면 카운터를 지운다(요구사항). 대신 자기 계정이 있는 공격자는 중간에 성공 로그인을 끼워 카운터를 계속 리셋하면서
 *   다른 계정을 무한히 시도할 수 있다. 계정(이메일)별 실패 카운터를 따로 두는 보완이 필요하다.
 * - IPv6는 /64 단위로 묶지 않으면 한 사용자가 주소를 바꿔 가며 우회할 수 있다.
 */
@Component
class LoginRateLimiter(
	private val store: LoginFailureStore,
	@Value("\${bbolbbol.auth.login-max-failures:10}") private val maxFailures: Int,
) {

	/**
	 * 비밀번호 검사 "전에" 시도 1회를 원자적으로 선점한다. 늘어난 값이 maxFailures를 넘으면 AUTH429(맞는 비밀번호도 거절).
	 * 확인 후 기록하는 방식(check, then record)은 BCrypt가 도는 동안 동시 요청이 모두 통과해서 우회된다.
	 * 성공하면 recordSuccess가 카운터를 지우므로 결과적으로 "실패만 센다"는 기존 동작과 같다(최대 maxFailures번 실패까지 허용, 그다음 시도부터 거절).
	 */
	fun reserveAttempt(ip: String) {
		if (store.increment(ip, WINDOW) > maxFailures) throw BusinessException(AuthErrorCode.TOO_MANY_LOGIN_ATTEMPTS)
	}

	fun recordSuccess(ip: String) = store.reset(ip)

	companion object {
		val WINDOW: Duration = Duration.ofMinutes(1)
	}
}
