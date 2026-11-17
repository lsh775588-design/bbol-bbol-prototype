package com.bbolbbol.server.domain.auth.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * 운영 프로파일용. 키 refresh:{userId}(현재 토큰 해시, TTL은 refresh token 만료와 같다),
 * refresh-recent:{userId}(유예 중인 옛 토큰 해시 ZSET, 점수 = 유예 만료 epoch millis).
 * rotate는 Lua 스크립트 한 번으로 판단과 교체를 원자적으로 한다. 실제 Redis에서 실행해 보지 못했다(미검증).
 * 유예 만료 시각은 이 서버의 Clock으로 계산해서 넘기므로 서버 사이 시계 차이만큼 유예가 늘거나 줄 수 있다.
 * local, test에서는 인메모리 구현을 쓴다.
 */
@Component
@Profile("!local & !test")
class RedisRefreshTokenStore(
	private val redis: StringRedisTemplate,
	private val clock: Clock,
) : RefreshTokenStore {

	override fun save(userId: Long, tokenHash: String, ttl: Duration) {
		redis.delete(RECENT_PREFIX + userId)
		redis.opsForValue().set(KEY_PREFIX + userId, tokenHash, ttl)
	}

	override fun find(userId: Long): String? = redis.opsForValue().get(KEY_PREFIX + userId)

	override fun delete(userId: Long) {
		redis.delete(listOf(KEY_PREFIX + userId, RECENT_PREFIX + userId))
	}

	override fun rotate(userId: Long, presentedHash: String, newHash: String, ttl: Duration, grace: Duration): Boolean {
		val now = clock.millis()
		val result = redis.execute(
			ROTATE_SCRIPT,
			listOf(KEY_PREFIX + userId, RECENT_PREFIX + userId),
			presentedHash,
			newHash,
			ttl.toMillis().toString(),
			(now + grace.toMillis()).toString(),
			now.toString(),
			MAX_RECENT.toString(),
		)
		return result == 1L
	}

	companion object {
		const val KEY_PREFIX = "refresh:"
		const val RECENT_PREFIX = "refresh-recent:"
		private const val MAX_RECENT = 50

		/** KEYS[1]=현재, KEYS[2]=유예 ZSET / ARGV: 제시한 해시, 새 해시, ttl ms, 유예 만료 ms, 지금 ms, 유예 최대 개수 */
		private val ROTATE_SCRIPT = DefaultRedisScript(
			"""
			local cur = redis.call('GET', KEYS[1])
			if not cur then return 0 end
			redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[5])
			local ok = (cur == ARGV[1])
			if not ok then
			  local score = redis.call('ZSCORE', KEYS[2], ARGV[1])
			  ok = (score ~= false and tonumber(score) > tonumber(ARGV[5]))
			end
			if not ok then return 0 end
			redis.call('ZADD', KEYS[2], ARGV[4], cur)
			redis.call('ZREMRANGEBYRANK', KEYS[2], 0, -(tonumber(ARGV[6]) + 1))
			redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
			redis.call('PEXPIRE', KEYS[2], ARGV[3])
			return 1
			""".trimIndent(),
			Long::class.java,
		)
	}
}
