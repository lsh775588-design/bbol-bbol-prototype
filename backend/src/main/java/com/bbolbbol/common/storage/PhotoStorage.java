package com.bbolbbol.common.storage;

import java.io.InputStream;

/**
 * 사진 저장소 포트. 개발은 로컬 디스크, 배포는 S3 구현체를 쓴다(구현은 2단계).
 * 도메인 코드는 이 인터페이스에만 의존한다.
 */
public interface PhotoStorage {

    /** key 위치에 저장한다. key는 추측 불가능한 값을 쓴다. 저장된 key를 반환한다. */
    String save(String key, InputStream in, long size, String contentType);

    /** key의 파일을 삭제한다. 없으면 무시한다. */
    void delete(String key);

    /** key로 접근할 수 있는 URL을 반환한다. */
    String urlFor(String key);
}
