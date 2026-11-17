package com.bbolbbol.server.domain.auth.kakao

import com.bbolbbol.server.domain.auth.exception.KakaoErrorCode
import com.bbolbbol.server.global.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import java.time.Duration

/**
 * 실제 카카오 호출. !!! 미검증 !!! 카카오 키가 없어서 한 번도 호출해 보지 못했고, 응답 형식은 카카오 공식 문서를 보고 옮겼다.
 * - 사용자 확인: GET https://kapi.kakao.com/v2/user/me (Authorization: Bearer {카카오 access token})
 * - 연결 끊기: POST https://kapi.kakao.com/v1/user/unlink (Authorization: KakaoAK {앱 어드민 키}, target_id_type=user_id, target_id)
 * 어드민 키는 환경변수 KAKAO_ADMIN_KEY로만 받는다(코드, 로그에 쓰지 않는다). 비어 있으면 unlink는 건너뛴다.
 * 이메일은 카카오가 "유효하고 인증됨"이라고 한 경우에만 쓴다(인증 안 된 이메일을 쓰면 남의 이메일을 선점해서 그 사람의 가입을 막을 수 있다).
 */
@Component
@Profile("!local & !test")
class RestKakaoClient(
	@Value("\${KAKAO_ADMIN_KEY:}") private val adminKey: String,
	@Value("\${KAKAO_API_BASE_URL:https://kapi.kakao.com}") baseUrl: String,
) : KakaoClient {

	private val log = LoggerFactory.getLogger(javaClass)

	private val client: RestClient = RestClient.builder()
		.baseUrl(baseUrl)
		.requestFactory(
			SimpleClientHttpRequestFactory().apply {
				setConnectTimeout(Duration.ofSeconds(3))
				setReadTimeout(Duration.ofSeconds(5))
			},
		)
		.build()

	override fun fetchProfile(accessToken: String): KakaoProfile {
		val body: JsonNode = try {
			client.get()
				.uri("/v2/user/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
				.retrieve()
				.body(JsonNode::class.java)
		} catch (e: HttpClientErrorException) {
			// 400(형식 오류)과 401(만료, 무효)은 토큰 문제로 본다. 429 등 나머지는 카카오 쪽 문제다.
			if (e.statusCode.value() == 400 || e.statusCode.value() == 401) throw BusinessException(KakaoErrorCode.INVALID_KAKAO_TOKEN)
			log.warn("카카오 사용자 조회 실패: HTTP {}", e.statusCode.value())
			throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		} catch (e: RestClientException) {
			log.warn("카카오 사용자 조회 실패: {}", e.javaClass.simpleName)
			throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		} ?: throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)

		val id = body.get("id")?.takeIf { it.isNumber || it.isString }?.asString()
			?: throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		val account = body.get("kakao_account")
		val usableEmail = account?.takeIf {
			it.get("is_email_valid")?.asBoolean(false) == true && it.get("is_email_verified")?.asBoolean(false) == true
		}?.get("email")?.asString()?.takeIf { it.isNotBlank() }
		val nickname = account?.get("profile")?.get("nickname")?.asString()
			?: body.get("properties")?.get("nickname")?.asString()
		return KakaoProfile(id, usableEmail, nickname)
	}

	override fun unlink(kakaoId: String) {
		if (adminKey.isBlank()) {
			log.warn("KAKAO_ADMIN_KEY가 없어 카카오 연결 끊기를 건너뜁니다.")
			return
		}
		val form = LinkedMultiValueMap<String, String>().apply {
			add("target_id_type", "user_id")
			add("target_id", kakaoId)
		}
		try {
			client.post()
				.uri("/v1/user/unlink")
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK $adminKey")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity()
		} catch (e: RestClientException) {
			throw BusinessException(KakaoErrorCode.KAKAO_SERVER_ERROR)
		}
	}
}
