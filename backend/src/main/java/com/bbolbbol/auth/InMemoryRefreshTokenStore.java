package com.bbolbbol.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** local, test 프로파일용 인메모리 구현(Redis 없이 동작). TTL은 Clock 기준으로 만료를 판단한다. */
@Component
@Profile("local | test")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private record Entry(String hash, Instant expiresAt) {}

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(Long userId, String tokenHash, Duration ttl) {
        store.put(userId, new Entry(tokenHash, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<String> find(Long userId) {
        Entry e = store.get(userId);
        if (e == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(e.expiresAt())) {
            store.remove(userId, e);
            return Optional.empty();
        }
        return Optional.of(e.hash());
    }

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }
}
