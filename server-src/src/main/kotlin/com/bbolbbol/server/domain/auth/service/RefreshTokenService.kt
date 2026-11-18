package com.bbolbbol.server.domain.auth.service

import com.bbolbbol.server.domain.auth.repository.RefreshTokenStore
import com.bbolbbol.server.global.security.jwt.JwtProperties
import com.bbolbbol.server.global.security.jwt.JwtProvider
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * refresh token 발급. 토큰은 JwtProvider가 만든 JWT(type=REFRESH)이고, 저장소에는 SHA-256 해시만 둔다.
 * 만료(TTL)는 jwt.refresh-token-expiration(기본 14일)과 같다.
 * 재발급(rotate)은 새 토큰을 만들어 저장소가 원자적으로 교체하고, 밀려난 토큰은 [REISSUE_GRACE] 동안 한 번 더 받아 준다.
 */
@Service
class RefreshTokenService(
	private val store: RefreshTokenStore,
	private val jwtProvider: JwtProvider,
	jwtProperties: JwtProperties,
) {

	private val ttl = jwtProperties.refreshTokenExpiration

	/** 새 refresh token을 만들어 해시를 저장(기존 것 덮어쓰기)하고 원문을 돌려준다. 원문은 로그에 남기지 않는다. */
	fun issue(userId: Long): String {
		val token = jwtProvider.createRefreshToken(userId)
		store.save(userId, hash(token), ttl)
		return token
	}

	/**
	 * 제시한 refresh token이 현재 토큰이거나 유예 안의 옛 토큰이면 새 토큰으로 교체해서 돌려준다. 아니면 null.
	 * JWT 서명, 만료, type 검증은 호출하는 쪽(userId를 구하는 단계)에서 끝나 있어야 한다.
	 */
	fun rotate(userId: Long, presentedToken: String): String? {
		val newToken = jwtProvider.createRefreshToken(userId)
		return if (store.rotate(userId, hash(presentedToken), hash(newToken), ttl, REISSUE_GRACE)) newToken else null
	}

	/** 저장된 해시와 일치하는지 확인한다(상수 시간 비교). 재발급 구현(쟁점 3)에서 사용한다. */
	fun matches(userId: Long, token: String?): Boolean {
		if (token == null) return false
		val stored = store.find(userId) ?: return false
		return MessageDigest.isEqual(
			stored.toByteArray(StandardCharsets.UTF_8),
			hash(token).toByteArray(StandardCharsets.UTF_8),
		)
	}

	/** 로그아웃 등에서 쓸 저장된 refresh token 삭제. */
	fun delete(userId: Long) = store.delete(userId)

	companion object {
		/** 재발급 직후 옛 refresh token을 계속 받아 주는 시간(동시 요청과 응답 유실 대비). */
		val REISSUE_GRACE: Duration = Duration.ofSeconds(20)

		fun hash(token: String): String =
			HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)))
	}
}
