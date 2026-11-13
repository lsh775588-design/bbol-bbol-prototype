package com.bbolbbol.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RefreshTokenStoreTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    @DisplayName("인메모리: 저장/조회/삭제, 사용자당 1개(덮어쓰기), TTL 지나면 만료")
    void inMemory() {
        MutableClock clock = new MutableClock();
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore(clock);
        store.save(1L, "h1", Duration.ofDays(14));
        assertThat(store.find(1L)).contains("h1");
        store.save(1L, "h2", Duration.ofDays(14));
        assertThat(store.find(1L)).contains("h2");
        assertThat(store.find(2L)).isEmpty();
        clock.now = clock.now.plus(Duration.ofDays(14)).minusSeconds(1);
        assertThat(store.find(1L)).isPresent();
        clock.now = clock.now.plusSeconds(1);
        assertThat(store.find(1L)).isEmpty();
        store.save(3L, "h3", Duration.ofDays(14));
        store.delete(3L);
        assertThat(store.find(3L)).isEmpty();
    }

    @Test
    @DisplayName("발급: 난수(추측 불가, 매번 다름), 형식 userId.random, 저장소에는 SHA-256 해시만, 14일 TTL")
    void issue() {
        MutableClock clock = new MutableClock();
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore(clock);
        RefreshTokenService service = new RefreshTokenService(store);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(service.issue(7L));
        }
        assertThat(seen).hasSize(50);
        String token = service.issue(7L);
        assertThat(token).matches("7\\.[A-Za-z0-9_-]{43}");
        String stored = store.find(7L).orElseThrow();
        assertThat(stored).isNotEqualTo(token).doesNotContain(token).matches("[0-9a-f]{64}");
        assertThat(stored).isEqualTo(RefreshTokenService.hash(token));
        assertThat(service.matches(7L, token)).isTrue();
        assertThat(service.matches(7L, token + "x")).isFalse();
        assertThat(service.matches(8L, token)).isFalse();
        assertThat(service.matches(7L, null)).isFalse();
        // 새로 발급하면 이전 토큰은 무효(사용자당 1개)
        String second = service.issue(7L);
        assertThat(service.matches(7L, token)).isFalse();
        assertThat(service.matches(7L, second)).isTrue();
        clock.now = clock.now.plus(RefreshTokenService.TTL).plusSeconds(1);
        assertThat(service.matches(7L, second)).isFalse();
        assertThat(RefreshTokenService.TTL).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("Redis 구현: 키 refresh:{userId}, TTL 전달, 조회, 삭제")
    @SuppressWarnings("unchecked")
    void redis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("refresh:5")).thenReturn("hash5");
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis);

        store.save(5L, "hash5", Duration.ofDays(14));
        verify(ops).set("refresh:5", "hash5", Duration.ofDays(14));
        assertThat(store.find(5L)).contains("hash5");
        assertThat(store.find(6L)).isEmpty();
        store.delete(5L);
        verify(redis).delete(eq("refresh:5"));
    }

    @Test
    @DisplayName("프로파일 선택: Redis는 local/test 제외 전부, 인메모리는 local/test만")
    void profileExpressions() {
        Profiles redisProfile = Profiles.of("!local & !test");
        Profiles memoryProfile = Profiles.of("local | test");
        for (String p : new String[] {"mysql", "prod", ""}) {
            StandardEnvironment env = new StandardEnvironment();
            if (!p.isEmpty()) env.setActiveProfiles(p);
            assertThat(env.acceptsProfiles(redisProfile)).isTrue();
            assertThat(env.acceptsProfiles(memoryProfile)).isFalse();
        }
        for (String p : new String[] {"local", "test"}) {
            StandardEnvironment env = new StandardEnvironment();
            env.setActiveProfiles(p);
            assertThat(env.acceptsProfiles(redisProfile)).isFalse();
            assertThat(env.acceptsProfiles(memoryProfile)).isTrue();
        }
        // 어노테이션이 위 표현식을 쓰는지 고정
        assertThat(RedisRefreshTokenStore.class.getAnnotation(org.springframework.context.annotation.Profile.class)
                .value()).containsExactly("!local & !test");
        assertThat(InMemoryRefreshTokenStore.class.getAnnotation(org.springframework.context.annotation.Profile.class)
                .value()).containsExactly("local | test");
    }

    @Test
    @DisplayName("비밀번호: BCrypt 해시는 원문과 다르고 솔트로 매번 다르며 matches로 검증된다")
    void passwordHashing() {
        BCryptPasswordEncoder enc = new AuthConfig().passwordEncoder() instanceof BCryptPasswordEncoder b
                ? b : null;
        assertThat(enc).isNotNull();
        String h1 = enc.encode("bbol1234!");
        String h2 = enc.encode("bbol1234!");
        assertThat(h1).startsWith("$2").isNotEqualTo("bbol1234!").isNotEqualTo(h2);
        assertThat(enc.matches("bbol1234!", h1)).isTrue();
        assertThat(enc.matches("bbol1234?", h1)).isFalse();
    }

    @Test
    @DisplayName("비밀번호 규칙: 8~20자, 영문+숫자+특수문자 각 1개 이상")
    void passwordPolicy() {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(PasswordPolicy.REGEX);
        for (String ok : new String[] {"bbol1234!", "a1!aaaaa", "Zz9~Zz9~Zz9~Zz9~Zz9~"}) {
            assertThat(p.matcher(ok).matches()).as(ok).isTrue();
        }
        for (String bad : new String[] {"bbol12!", "bbolbbol!!", "12345678!", "bbol12345", "bbol 1234!",
                "bbol1234!bbol1234!bbb", "bbol1234!한글"}) {
            assertThat(p.matcher(bad).matches()).as(bad).isFalse();
        }
    }
}
