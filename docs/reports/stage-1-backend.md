# 1단계 보고서(백엔드): Flyway, 사용자, 장소, 통합

- 일자: 2026-11-13
- 담당: db 에이전트(Flyway), backend 에이전트 2개(사용자, 장소, 병렬), 오케스트레이터가 검증과 통합
- 브랜치: `feat/1-base`(Flyway) → `feat/1-user`, `feat/1-place`(각각 별도 worktree) → `feat/1-integration`(로컬 통합, main에는 병합하지 않음)

## 한 일

### 1-0 Flyway (db)
- `flyway-core`, `flyway-mysql`을 추가했다. 모든 프로파일에서 켜져 있고 `ddl-auto`는 `validate`다(Flyway가 스키마를 소유한다).
- 병렬 작업 시 번호가 겹치지 않게 구역을 나눴다: V0 베이스라인, user V100대, place V200대, completion V300대, point V400대, ranking V500대, 공용·시드 V900+. 규칙은 `backend/src/main/resources/db/migration/README.md`에 있다.
- `local` 프로파일만 `db/seed-local`도 읽는다. 시드가 test 프로파일이나 운영에 들어가지 않게 하려는 것이다.

### 사용자 도메인 (`feat/1-user`, V100, V101)
- API 6개: `POST /auth/signup`, `POST /auth/login`, `GET /users/me`, `PATCH /users/me`, `GET /auth/email-availability`, `GET /auth/nickname-availability`.
- 비밀번호는 BCrypt, 가입은 실제 HTTP 201, 로그인 실패는 이메일 없음, 비밀번호 틀림, 탈퇴, 카카오 전용 계정이 모두 같은 AUTH4011 응답이다(없는 이메일에도 더미 비교를 해 시간 차이를 막음).
- 닉네임 규칙: 금칙어 필터(USER4001), 중복(USER4092), 30일 변경 제한(USER4002 + `nextChangeableAt`), 가입 때 정한 닉네임은 횟수에 넣지 않는다.
- 쟁점 2(이메일 인증 토큰)는 인터페이스 `VerificationTokenVerifier`로 분리했다. 기본 구현은 항상 EMAIL4003으로 거부하고, `local` 프로파일에서만 개발용 고정 토큰을 받는 구현이 있다.
- refresh token: 인터페이스 `RefreshTokenStore`(Redis 구현과 local·test용 메모리 구현). 형식은 `{userId}.{256비트 난수}`이고 저장소에는 SHA-256 해시만 둔다. 재발급과 로그아웃(쟁점 3)은 만들지 않았다.
- `UserStatusChecker` 구현: 활성 사용자만 통과하고 탈퇴·없는 사용자의 유효 토큰은 401이다. `UserService`는 다른 도메인이 쓸 메서드(`isActive`, `getDisplayNickname(s)`)만 공개하고 `UserRepository`는 패키지 밖에서 못 쓴다.

### 장소 도메인 (`feat/1-place`, V200, V201)
- API 3개: `GET /place-categories`, `GET /places`(주변 검색), `GET /places/{id}`.
- 위경도 범위로 DB에서 후보를 한 번에 조회한 뒤 haversine으로 정확한 반경을 거른다(N+1 없음). 장소 점수가 카테고리 점수를 덮는다(쟁점 7 가정).
- 다른 도메인에 의존하는 필드(방문 여부, 완료 여부, 인기 장소)는 한 곳(`PlaceholderPlaceUserStateProvider`)의 임시값이다. 2단계에서 채운다.
- 카테고리 8개를 V201로 넣었다(명세가 고정한 기준 데이터).
- 카카오 종로구 수집 스크립트(`backend/scripts/collect-places.mjs`): 지도를 격자로 나눠 질의하고, 카카오 id로 중복을 제거하고, 종로구만 거르고, JSON과 검토용 SQL을 만든다. 키는 환경변수 `KAKAO_REST_API_KEY`만 쓰고 없으면 안내 후 종료한다.

### 통합 (`feat/1-integration`)
- 사용자와 장소 브랜치를 충돌 없이 합쳤다. 마이그레이션 번호도 겹치지 않았다.
- 합친 뒤 장소 통합 테스트 14개가 실패했다. 장소 테스트가 DB에 없는 사용자 번호로 토큰을 만들어서, 이제 구현된 사용자 상태 확인에서 401이 났다. 따로 개발할 때는 보이지 않다가 합쳤을 때 처음 드러났다. 사용자 에이전트가 만든 테스트용 설정을 장소 테스트에 적용해 고쳤다(테스트 파일 1개, 3줄).

## 바뀐 파일(요약)
- user: `auth/*`(컨트롤러, 서비스, 토큰 검증, refresh 저장소), `user/*`(엔티티, 서비스, 닉네임 규칙, 금칙어), `V100`, `V101`, `forbidden-words.txt`
- place: `place/{domain,repository,service,controller,dto}`, `V200`, `V201`, `backend/scripts/*`
- 공유 파일 수정: `application-local.yml`에 개발용 가짜 인증 토큰 설정 1줄(사용자). 기존 common 테스트는 사용자 도메인 구현 후 실패하던 22개를 테스트용 설정으로 보정했다.

## 테스트 결과
| 항목 | 결과 |
|---|---|
| `./gradlew clean test`(통합 브랜치) | 통과. 전체 161개, 실패 0, 건너뜀 0. 오케스트레이터가 실행 |
| 사용자 브랜치 단독 | 128개 통과(에이전트 보고) |
| 장소 브랜치 단독 | 102개 통과(에이전트 보고) |
| 수집 스크립트 `node --test` | 9개 통과(오케스트레이터가 재실행) |
| curl(local 프로파일, 에이전트 실행) | 가입 201, 로그인, 내 정보, 닉네임 변경, 30일 제한(USER4002), 중복 확인, 장소 카테고리 8개, 장소 없음 404, 잘못된 좌표 COMMON400 |

## 오케스트레이터 검증
- 두 브랜치 모두 작성자는 이세현이고 Claude 문구가 없다. 공유 파일 수정은 위 1줄뿐이고 다른 도메인을 가져다 쓴 곳이 없다. 설정에 실제 비밀값이 없다.
- 장소 에이전트의 검토 문서 읽기와 사용자 에이전트의 검토 문서 읽기는 600초 무응답 중단 후 재개되면서 일부만 읽었다. 둘 다 Notion 명세 페이지는 읽고 구현했다.

## 명세와 다른 점, 의문점 (가정으로 진행)
- **signup 메시지:** 명세의 "회원가입에 성공했습니다."를 쓰려고 `createdResponse()`를 쓰지 않고 직접 201을 만들었다. `createdResponse(message, data)` 오버로드를 공유 파일에 추가할지는 결정 대기.
- **refresh token 형식:** 명세 예시는 JWT 모양인데 불투명 `{userId}.{난수}`로 했다. 재발급이 userId를 알아야 해서다(쟁점 3과 함께 결정).
- **검색 반경 이름:** 명세 그대로 `radius`를 썼다. 응답의 `radius`(인증 반경)와 이름이 겹쳐서 검토 문서는 `searchRadius`로 바꾸자고 했다(명세 변경이라 보류).
- **쟁점 7:** 장소 점수가 카테고리 점수를 덮는 것으로 가정했다.
- **닉네임:** 한글은 완성형만 허용하고 자모(ㅋㅋ)는 거부한다. 금칙어는 8개짜리 임시 목록이라 "시발점" 같은 오탐이 생길 수 있다. 실제 목록이 필요하다.
- **기타:** `isNewUser`는 온보딩 API가 없는 동안 로그인에서 항상 true다. 비밀번호 특수문자는 ASCII 기호만 허용한다. 이메일은 trim과 소문자로 저장한다. 현재 닉네임과 같은 값으로 변경하면 USER4092다.
- **user_interests의 카테고리 외래 키가 없다.** place 테이블이 합쳐졌으니 새 번호(V102)로 추가할 수 있다. 도메인 간 외래 키를 둘지는 결정 대기.
- 응답 `message`가 명세 예시("요청에 성공했습니다.")와 조금 다르다(0단계부터 열린 항목).

## 구현하지 않은 API(쟁점 대기)
이메일 인증 발송과 확인, 비밀번호 재설정(쟁점 2), 토큰 재발급과 로그아웃(쟁점 3), 회원 탈퇴(쟁점 4), 카카오 로그인(쟁점 1, 5), 온보딩 조회와 저장(쟁점 5). 코드는 만들지 않았다.

## 미검증
- Redis 실연동(Docker 없음). `RedisRefreshTokenStore`는 Mockito 단위 테스트와 프로파일 조건만 확인했다.
- MySQL 8에서 V100, V101, V200, V201 실행과 Hibernate 검증. H2(MySQL 모드)에서만 확인했다. `BOOLEAN` 컬럼의 MySQL 매핑은 확인이 필요하다.
- 카카오 수집 스크립트의 실제 API 호출(키 없음). 응답 필드와 45개 상한 분할 동작은 첫 실행 때 확인해야 한다.
- 동시 가입, 동시 닉네임 변경의 경합 테스트. DB 유니크 제약 위반을 에러 코드로 바꾸는 코드만 있다.
- 장소가 있는 경우의 curl 응답(시드가 없어서 통합 테스트로만 확인). 샘플 데이터는 다음 작업에서 넣는다.

## 내가 결정해야 할 것
1. 검색 반경 이름 `radius`를 `searchRadius`로 바꿀지(명세 변경).
2. 성공 응답 `message`를 명세 예시 문구로 맞출지, `createdResponse`에 메시지 오버로드를 추가할지.
3. refresh token을 JWT 형식으로 할지(쟁점 3과 함께).
4. `user_interests`에 카테고리 외래 키를 둘지.
5. 닉네임 자모 허용 여부와 실제 금칙어 목록.
