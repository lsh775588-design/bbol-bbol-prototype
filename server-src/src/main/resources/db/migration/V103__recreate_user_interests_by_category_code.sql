-- user 도메인: user_interests를 category_id(BIGINT)가 아니라 category_code(place_categories.code)로 저장한다.
-- 이유: place 도메인은 카테고리 id를 공개하지 않고(코드 검증만 PlaceQueryService.isKnownCategory로 공개),
-- user 도메인이 PlaceRepository를 직접 쓰면 도메인 경계를 넘는다. 명세도 interests를 코드 배열로 주고받는다.
-- V101 테이블은 쓰는 코드가 없었으므로(온보딩 API 미구현) 데이터 이관 없이 다시 만든다.
-- sort_order는 요청한 순서를 그대로 돌려주기 위한 값이다(0부터).
DROP TABLE user_interests;
CREATE TABLE user_interests (
    user_id BIGINT NOT NULL,
    category_code VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT pk_user_interests PRIMARY KEY (user_id, category_code),
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_interests_category ON user_interests (category_code);
