package com.bbolbbol.server.domain.user.event

/**
 * 회원 탈퇴가 DB에 반영된 뒤(트랜잭션 커밋 후) 발행되는 이벤트.
 * 다른 도메인(사진 삭제 등)이 user 코드를 고치지 않고 정리 작업을 붙일 수 있게 한다.
 * 리스너에서 난 예외는 탈퇴 응답에 영향을 주지 않도록 리스너가 직접 잡아서 로그로 남겨야 한다.
 */
data class UserWithdrawnEvent(val userId: Long)
