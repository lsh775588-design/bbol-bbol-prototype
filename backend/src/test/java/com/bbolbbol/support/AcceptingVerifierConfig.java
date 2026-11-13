package com.bbolbbol.support;

import com.bbolbbol.auth.VerificationTokenVerifier;
import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트용: "ok:{email}" 토큰을 허용하고 consume으로 폐기한다. 그 밖의 토큰은 EMAIL4003. */
@TestConfiguration
public class AcceptingVerifierConfig {

    public static class FakeVerifier implements VerificationTokenVerifier {
        public final Set<String> consumed = new HashSet<>();

        @Override
        public String verifySignupToken(String token) {
            if (token == null || !token.startsWith("ok:") || consumed.contains(token)) {
                throw new BusinessException(ErrorCode.EMAIL4003);
            }
            return token.substring(3);
        }

        @Override
        public void consume(String token) {
            consumed.add(token);
        }
    }

    @Bean
    @Primary
    FakeVerifier fakeVerificationTokenVerifier() {
        return new FakeVerifier();
    }
}
