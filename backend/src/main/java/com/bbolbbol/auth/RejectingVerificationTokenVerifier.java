package com.bbolbbol.auth;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local을 제외한 모든 프로파일의 기본 구현. 쟁점 2가 결정되기 전이라 가짜로 통과시키지 않고 항상 EMAIL4003으로 거부한다.
 * 쟁점 2가 결정되면 이 클래스를 실제 구현(Redis 조회 등)으로 교체한다.
 */
@Component
@Profile("!local")
public class RejectingVerificationTokenVerifier implements VerificationTokenVerifier {

    @Override
    public String verifySignupToken(String token) {
        throw new BusinessException(ErrorCode.EMAIL4003);
    }

    @Override
    public void consume(String token) {
        // 검증이 항상 실패하므로 폐기할 토큰이 없다.
    }
}
