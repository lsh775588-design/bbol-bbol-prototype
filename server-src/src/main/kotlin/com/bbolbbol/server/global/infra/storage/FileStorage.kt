package com.bbolbbol.server.global.infra.storage

import org.springframework.web.multipart.MultipartFile

/**
 * 파일 저장소 (개발은 로컬 디스크, 배포는 S3)
 *
 * 서비스 코드는 이 인터페이스에만 의존해야 함
 * 그러면 S3로 바꿀 때 S3FileStorage만 추가하고 storage.type=s3로 바꾸면 끝남
 */
interface FileStorage {

	/**
	 * @param directory 저장할 폴더 이름 (ex. "completions")
	 * @return 저장된 파일의 key. DB에는 이 값을 저장하면 됨 (ex. "completions/3f2a...c1.jpg")
	 */
	fun upload(file: MultipartFile, directory: String): String

	/** 회원 탈퇴 시 사진 삭제 등에 사용 */
	fun delete(key: String)

	/**
	 * (사진 업로드 구현 때 추가) key로 클라이언트에 내려줄 주소를 만든다.
	 * 로컬은 `{storage.public-base-url}/files/{key}`, S3는 CDN/presigned 주소가 된다.
	 */
	fun url(key: String): String
}
