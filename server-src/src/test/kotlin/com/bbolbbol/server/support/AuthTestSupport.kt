package com.bbolbbol.server.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey

/** 만료, 위조 같은 비정상 JWT를 만들어 주는 테스트 도우미. 키는 모두 테스트용 가짜 값 */
object AuthTestSupport {

	fun key(secret: String): SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

	fun token(
		secret: String,
		subject: String = "1",
		type: String? = "ACCESS",
		expiresInMillis: Long = 60_000,
	): String {
		val now = System.currentTimeMillis()
		val builder = Jwts.builder()
			.subject(subject)
			.issuedAt(Date(now - 120_000))
			.expiration(Date(now + expiresInMillis))
		if (type != null) builder.claim("type", type)
		return builder.signWith(key(secret)).compact()
	}

	fun algNoneToken(): String {
		val enc = Base64.getUrlEncoder().withoutPadding()
		val header = enc.encodeToString("""{"alg":"none"}""".toByteArray())
		val payload = enc.encodeToString(
			"""{"sub":"1","type":"ACCESS","exp":${System.currentTimeMillis() / 1000 + 600}}""".toByteArray(),
		)
		return "$header.$payload."
	}
}
