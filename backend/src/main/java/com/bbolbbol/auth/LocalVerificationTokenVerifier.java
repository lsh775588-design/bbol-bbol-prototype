package com.bbolbbol.auth;

import com.bbolbbol.common.error.BusinessException;
import com.bbolbbol.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * !!! DEV ONLY !!! local 프로파일에서만 로드된다. 이메일 인증 없이 가입을 확인하기 위한 고정 토큰.
 * 토큰 형식은 "{고정토큰}:{이메일}"이며, 고정토큰은 bbolbbol.local.verification-token(application-local.yml)에서 읽는다.
 * 다른 프로파일에는 이 프로퍼티도 이 빈도 없다. 쟁점 2 확정 후 삭제한다.
 */
@Component
@Profile("local")
public class LocalVerificationTokenVerifier implements VerificationTokenVerifier {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final String fixedToken;

    public LocalVerificationTokenVerifier(@Value("${bbolbbol.local.verification-token}") String fixedToken) {
        this.fixedToken = fixedToken;
    }

    @Override
    public String verifySignupToken(String token) {
        if (token == null || fixedToken == null || fixedToken.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL4003);
        }
        int sep = token.indexOf(':');
        if (sep <= 0) {
            throw new BusinessException(ErrorCode.EMAIL4003);
        }
        String head = token.substring(0, sep);
        String email = token.substring(sep + 1).trim();
        boolean same = MessageDigest.isEqual(head.getBytes(StandardCharsets.UTF_8),
                fixedToken.getBytes(StandardCharsets.UTF_8));
        if (!same || !SIMPLE_EMAIL.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.EMAIL4003);
        }
        return email;
    }

    @Override
    public void consume(String token) {
        // 고정 토큰은 재사용 가능(개발용).
    }
}
