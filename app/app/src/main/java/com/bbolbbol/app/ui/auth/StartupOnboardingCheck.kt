package com.bbolbbol.app.ui.auth

import com.bbolbbol.app.data.api.ApiResult
import com.bbolbbol.app.data.local.AuthSession
import com.bbolbbol.app.data.repository.UserRepository

/**
 * 토큰이 저장된 채 앱이 시작됐을 때 온보딩을 마쳤는지 GET /users/me의 `onboardingCompleted`로 확인한다.
 * 마치지 않았으면 [AuthSession.onboardingRequired]를 켜고 true를 돌려준다(호출한 쪽이 온보딩 화면으로 보낸다).
 * 확인에 실패하면(네트워크 오류, 서버 오류, AUTH401 등) 아무것도 바꾸지 않고 false: 앱 진입을 막지 않는다.
 */
suspend fun checkOnboardingOnStart(users: UserRepository, session: AuthSession): Boolean {
    if (!session.loggedIn.value) return false
    val r = try {
        users.me()
    } catch (e: Exception) {
        return false
    }
    if (r !is ApiResult.Success || r.data.onboardingCompleted) return false
    session.markOnboarding(true, session.suggestedNickname)
    return true
}
