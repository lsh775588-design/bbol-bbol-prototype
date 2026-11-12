package com.bbolbbol.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Access token(HS256) 생성과 검증. subject = userId.
 * 토큰 값과 비밀키는 절대 로그에 남기지 않는다.
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String ALG = "HS256";

    private final SecretKey key;
    private final long accessExpirationMs;

    public JwtProvider(@Value("${jwt.secret:}") String secret,
                       @Value("${jwt.access-expiration:3600000}") long accessExpirationMs,
                       Environment env) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            if (env.acceptsProfiles(Profiles.of("local", "test"))) {
                // DEV ONLY: local/test에서 키가 없으면 기동마다 바뀌는 임시 키를 쓴다.
                log.warn("jwt.secret이 없거나 짧아 임시 개발용 키를 생성합니다(local/test 전용).");
                bytes = new byte[MIN_SECRET_BYTES];
                new java.security.SecureRandom().nextBytes(bytes);
            } else {
                throw new IllegalStateException(
                        "jwt.secret(JWT_SECRET)은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다.");
            }
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.accessExpirationMs = accessExpirationMs;
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 유효하면 userId, 만료/형식 오류/서명 불일치/알고리즘 불일치(none 포함)면 empty. 사유는 debug 로그에만 남긴다. */
    public Optional<Long> parseUserId(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            if (!ALG.equals(jws.getHeader().getAlgorithm())) {
                log.debug("JWT 거부: 허용되지 않은 알고리즘");
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(jws.getPayload().getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 거부: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
