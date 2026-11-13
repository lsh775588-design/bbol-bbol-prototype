package com.bbolbbol.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * refresh token 발급. 형식은 "{userId}.{256비트 난수(base64url)}"이다. 난수 부분이 추측 불가능하며,
 * userId는 재발급 때 저장소 키(refresh:{userId})를 찾기 위한 용도다. 저장소에는 SHA-256 해시만 둔다.
 * 재발급(rotation)과 로그아웃은 쟁점 3 대기라 여기서는 발급과 해시 비교만 제공한다.
 */
@Service
public class RefreshTokenService {

    public static final Duration TTL = Duration.ofDays(14);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenStore store;

    public RefreshTokenService(RefreshTokenStore store) {
        this.store = store;
    }

    /** 새 refresh token을 만들어 저장(기존 것 덮어쓰기)하고 원문을 돌려준다. 원문은 로그에 남기지 않는다. */
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.save(userId, hash(token), TTL);
        return token;
    }

    /** 저장된 해시와 일치하는지 확인한다(상수 시간 비교). 재발급 구현(쟁점 3)에서 사용한다. */
    public boolean matches(Long userId, String token) {
        return token != null && store.find(userId)
                .map(h -> MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8),
                        hash(token).getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
