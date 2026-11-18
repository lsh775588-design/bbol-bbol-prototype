-- user 도메인 소유: user_interests (온보딩 관심 카테고리, 최대 3개는 서비스에서 검증).
-- category_id의 FK(place_categories)는 place 도메인 마이그레이션(V2xx)이 병합된 뒤 새 번호로 추가한다.
CREATE TABLE user_interests (
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT pk_user_interests PRIMARY KEY (user_id, category_id),
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_interests_category ON user_interests (category_id);
