# DB 마이그레이션 규칙 (Flyway)

스키마는 Flyway가 소유한다. Hibernate `ddl-auto`는 `validate`이며 테이블을 만들지 않는다.
Flyway는 이 폴더의 `V<번호>__<설명>.sql`만 읽는다(README.md 같은 파일은 무시한다).

## 번호 구역 (병렬 작업 충돌 방지)
| 구역 | 소유 |
| --- | --- |
| V0 | 베이스라인(`V0__baseline.sql`) |
| V100~V199 | user (예: `V100__create_users.sql`) |
| V200~V299 | place |
| V300~V399 | completion |
| V400~V499 | point |
| V500~V599 | ranking |
| V900+ | 공용/기준 데이터(seed) |

- 개발용 시드는 `db/seed-local`에 두고 `local` 프로파일에서만 `spring.flyway.locations`로 함께 읽는다.

## 규칙
1. SQL은 H2(MySQL 모드)와 MySQL 8 양쪽에서 돌아야 한다. 벤더 전용 문법을 쓰지 말고 `DATETIME`, `BIGINT AUTO_INCREMENT` 같은 평범한 DDL을 쓴다.
2. 제약조건과 인덱스는 이름을 명시한다(`uk_users_email`, `fk_places_category`, `idx_places_lat_lng` 등).
3. 테이블 하나에 소유자 한 명. 자기 구역 번호로 자기 테이블만 만든다.
4. 이미 적용된(병합된) 마이그레이션 파일은 절대 수정하지 않는다. 바꿀 일이 있으면 새 번호로 추가한다.
5. `outOfOrder`는 false로 유지한다.
