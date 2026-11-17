package com.bbolbbol.server.global.infra.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * storage.type이 비어 있거나 지원하지 않는 값이면 기동을 막는다.
 * 예전에는 값이 없으면 로컬 디스크 저장소가 조용히 선택됐다(matchIfMissing). 운영에서 설정을 빠뜨리면
 * 사진이 컨테이너 디스크에 쌓였다가 사라지므로, 기본값 없이 명시하게 한다. local, test 프로파일은 yml에서 local로 정한다.
 * 새 저장소(S3 등)를 추가하면 SUPPORTED에도 넣는다.
 */
@Component
class StorageTypeGuard(@Value("\${storage.type:}") type: String) {

	init {
		check(type.isNotBlank()) {
			"storage.type이 설정되지 않았습니다. 환경변수 STORAGE_TYPE을 ${SUPPORTED.joinToString("|")} 중 하나로 정하세요(local, test 프로파일은 자동으로 local)."
		}
		check(type in SUPPORTED) {
			"지원하지 않는 storage.type입니다: '$type'. 사용 가능한 값: ${SUPPORTED.joinToString(", ")}"
		}
	}

	companion object {
		val SUPPORTED = listOf("local")
	}
}
