package com.bbolbbol.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtProviderTest {

    static final String FAKE_SECRET = "unit-test-FAKE-secret-key-0123456789-abcdefghij-ABCDEFGHIJ";
    static final String OTHER_SECRET = "unit-test-OTHER-fake-key-0123456789-abcdefghij";

    private static JwtProvider provider(String secret, long expMs, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new JwtProvider(secret, expMs, env);
    }

    private static SecretKey key(String s) {
        return new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private final JwtProvider p = provider(FAKE_SECRET, 3_600_000L, "test");

    @Test
    @DisplayName("발급한 토큰은 같은 userId로 파싱된다")
    void roundTrip() {
        assertThat(p.parseUserId(p.createAccessToken(7L))).contains(7L);
        assertThat(p.parseUserId(p.createAccessToken(Long.MAX_VALUE))).contains(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("변조된 토큰(payload/서명)은 거부")
    void tampered() {
        String t = p.createAccessToken(1L);
        String[] parts = t.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"2\",\"typ\":\"access\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(p.parseUserId(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
        assertThat(p.parseUserId(parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A")).isEmpty();
        assertThat(p.parseUserId(t + "x")).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 거부")
    void expired() {
        String t = Jwts.builder().subject("1").claim("typ", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(key(FAKE_SECRET), Jwts.SIG.HS256).compact();
        assertThat(p.parseUserId(t)).isEmpty();
    }

    @Test
    @DisplayName("access-expiration이 음수면 발급 즉시 만료된 토큰")
    void negativeExpiry() {
        JwtProvider neg = provider(FAKE_SECRET, -60_000L, "test");
        assertThat(neg.parseUserId(neg.createAccessToken(1L))).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부")
    void wrongKey() {
        String t = Jwts.builder().subject("1").claim("typ", "access").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(OTHER_SECRET), Jwts.SIG.HS256).compact();
        assertThat(p.parseUserId(t)).isEmpty();
    }

    @Test
    @DisplayName("같은 키라도 HS256이 아닌 알고리즘(HS384)은 거부")
    void otherAlgorithm() {
        String t = Jwts.builder().subject("1").claim("typ", "access").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(FAKE_SECRET), Jwts.SIG.HS384).compact();
        assertThat(p.parseUserId(t)).isEmpty();
    }

    @Test
    @DisplayName("alg=none 토큰은 거부")
    void algNone() {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"1\",\"typ\":\"access\",\"exp\":"
                + (System.currentTimeMillis() / 1000 + 600) + "}").getBytes(StandardCharsets.UTF_8));
        assertThat(p.parseUserId(header + "." + payload + ".")).isEmpty();
        assertThat(p.parseUserId(header + "." + payload)).isEmpty();
    }

    @Test
    @DisplayName("typ 클레임이 없거나 access가 아닌 토큰은 거부")
    void typeClaimRequired() {
        java.util.function.Function<String, String> build = typ -> {
            var b = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000));
            if (typ != null) {
                b.claim("typ", typ);
            }
            return b.signWith(key(FAKE_SECRET), Jwts.SIG.HS256).compact();
        };
        assertThat(p.parseUserId(build.apply(null))).isEmpty();
        assertThat(p.parseUserId(build.apply("refresh"))).isEmpty();
        assertThat(p.parseUserId(build.apply("ACCESS"))).isEmpty();
        assertThat(p.parseUserId(build.apply("access"))).contains(1L);
    }

    @Test
    @DisplayName("빈 문자열/쓰레기/숫자가 아닌 subject/subject 없음은 empty")
    void garbage() {
        assertThat(p.parseUserId("")).isEmpty();
        assertThat(p.parseUserId("garbage")).isEmpty();
        assertThat(p.parseUserId("a.b.c")).isEmpty();
        String nonNumeric = Jwts.builder().subject("abc").claim("typ", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(FAKE_SECRET), Jwts.SIG.HS256).compact();
        assertThat(p.parseUserId(nonNumeric)).isEmpty();
        String noSubject = Jwts.builder().claim("typ", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(FAKE_SECRET), Jwts.SIG.HS256).compact();
        assertThat(p.parseUserId(noSubject)).isEmpty();
    }

    @Test
    @DisplayName("짧은/빈 비밀키는 local/test가 아니면 기동 실패, 메시지에 비밀값 없음")
    void shortSecretFailsOutsideLocalTest() {
        String shortSecret = "short-FAKE-secret";
        assertThatThrownBy(() -> provider(shortSecret, 1000L, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortSecret);
        assertThatThrownBy(() -> provider("", 1000L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider(null, 1000L, "mysql"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("local/test 프로파일에서는 키가 없어도 임시 키로 기동하고 토큰이 동작")
    void shortSecretAllowedInLocalAndTest() {
        for (String profile : new String[] {"local", "test"}) {
            JwtProvider dev = provider("", 3_600_000L, profile);
            assertThat(dev.parseUserId(dev.createAccessToken(5L))).contains(5L);
        }
    }

    @Test
    @DisplayName("임시 개발 키는 인스턴스마다 달라 서로의 토큰을 거부")
    void devKeysDiffer() {
        JwtProvider a = provider("", 3_600_000L, "test");
        JwtProvider b = provider("", 3_600_000L, "test");
        assertThat(b.parseUserId(a.createAccessToken(1L))).isEmpty();
    }
}
