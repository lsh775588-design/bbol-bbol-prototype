package com.bbolbbol.auth;

import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 운영/mysql 프로파일용. 키 refresh:{userId}, TTL은 refresh token 만료와 같다. local, test에서는 인메모리 구현을 쓴다. */
@Component
@Profile("!local & !test")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(Long userId, String tokenHash, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + userId, tokenHash, ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + userId));
    }

    @Override
    public void delete(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
