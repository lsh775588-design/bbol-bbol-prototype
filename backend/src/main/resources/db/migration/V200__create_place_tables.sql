-- place 도메인: place_categories, places. H2(MySQL 모드)와 MySQL 8 모두에서 실행된다.
CREATE TABLE place_categories (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    reward_point INT          NOT NULL,
    CONSTRAINT pk_place_categories PRIMARY KEY (id),
    CONSTRAINT uk_place_categories_code UNIQUE (code)
);

CREATE TABLE places (
    id              BIGINT AUTO_INCREMENT NOT NULL,
    category_id     BIGINT        NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    address         VARCHAR(255)  NOT NULL,
    road_address    VARCHAR(255)  NULL,
    phone           VARCHAR(30)   NULL,
    lat             DOUBLE        NOT NULL,
    lng             DOUBLE        NOT NULL,
    radius_m        INT           NOT NULL DEFAULT 100,
    notify_radius_m INT           NOT NULL DEFAULT 200,
    reward_point    INT           NULL,
    kakao_place_id  VARCHAR(30)   NOT NULL,
    kakao_place_url VARCHAR(255)  NOT NULL,
    hot_type        VARCHAR(20)   NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT uk_places_kakao_place_id UNIQUE (kakao_place_id),
    CONSTRAINT fk_places_category FOREIGN KEY (category_id) REFERENCES place_categories (id)
);

-- 주변 검색(위경도 바운딩 박스) 조회용
CREATE INDEX idx_places_lat_lng ON places (lat, lng);
CREATE INDEX idx_places_category ON places (category_id);
