package com.bbolbbol.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh token 저장소 포트. 사용자당 1개이며 save는 이전 값을 덮어쓴다(새 기기 로그인 시 이전 기기 로그아웃).
 * 저장하는 값은 토큰 원문이 아니라 해시다.
 */
public interface RefreshTokenStore {

    void save(Long userId, String tokenHash, Duration ttl);

    Optional<String> find(Long userId);

    void delete(Long userId);
}
