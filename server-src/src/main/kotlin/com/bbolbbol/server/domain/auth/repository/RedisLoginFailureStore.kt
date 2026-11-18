package com.bbolbbol.server.domain.auth.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** 운영 프로파일용. 키 login-fail:{key}, 첫 실패 때 TTL을 건다. 실제 Redis에서 실행해 보지 못했다(미검증). */
@Component
@Profile("!local & !test")
class RedisLoginFailureStore(
	private val redis: StringRedisTemplate,
) : LoginFailureStore {

	override fun count(key: String): Long = redis.opsForValue().get(PREFIX + key)?.toLongOrNull() ?: 0

	override fun increment(key: String, window: Duration): Long {
		val redisKey = PREFIX + key
		val count = redis.opsForValue().increment(redisKey) ?: 1L
		// INCR과 EXPIRE 사이에 서버가 죽으면 TTL 없는 키가 남을 수 있다. 첫 실패가 아니어도 TTL이 없으면 다시 건다.
		if (count == 1L || redis.getExpire(redisKey) < 0) redis.expire(redisKey, window)
		return count
	}

	override fun reset(key: String) {
		redis.delete(PREFIX + key)
	}

	companion object {
		const val PREFIX = "login-fail:"
	}
}
