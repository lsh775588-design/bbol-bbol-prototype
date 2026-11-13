-- user 도메인 소유: users. H2(MySQL 모드)와 MySQL 8 공용 DDL.
-- ERD 초안과의 차이: deleted/deleted_at 대신 status(ACTIVE|WITHDRAWN) + withdrawn_at 사용.
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT NOT NULL,
    email VARCHAR(255) NULL,
    password_hash VARCHAR(100) NULL,
    login_type VARCHAR(20) NOT NULL,
    kakao_id VARCHAR(64) NULL,
    nickname VARCHAR(30) NULL,
    nickname_changed_at DATETIME NULL,
    status VARCHAR(20) NOT NULL,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    withdrawn_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
);
