-- completion 도메인: 사진 key는 난수이고 한 완료 기록에 한 장이다. 같은 key가 두 기록에 붙는 일을 DB가 막는다.
-- NULL(사진 없음)은 여러 행이 가질 수 있다(H2, PostgreSQL 모두 유니크 인덱스에서 NULL 중복 허용).
CREATE UNIQUE INDEX uk_completions_photo_key ON completions (photo_key);
