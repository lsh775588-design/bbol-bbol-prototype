# 서버 이식 보고서: 팀 저장소(Kotlin, Spring Boot 4.1) 기준

- 일자: 2026-11-13 ~ 14
- 결정: 사용자가 "팀 저장소 기준으로 이식"을 선택(2026-11-13). 팀 저장소는 https://github.com/Bbol-Bbol/server 이며 복제본이 `C:\dev\bbolbbol\server`에 있다(바깥 저장소에서는 제외).
- **팀 저장소(origin)에는 올리지 않았다.** 모든 작업은 로컬 브랜치에서만 했다.
- 담당: backend/db 에이전트 4개(P0 기반, user, place, seed-port), 오케스트레이터가 검증과 통합
- 브랜치: `port/base`(P0) → `port/user`, `port/place`, `port/seed`(각각 별도 worktree) → `port/integration`(로컬 통합)

## 한 일

### P0 공통 기반 (`port/base`)
- 팀 저장소를 손대지 않은 상태에서 이 PC(JDK 21)로 빌드하니 그대로 통과했다(기존 테스트 21개, DB와 Redis 불필요).
- Docker 없이 돌 수 있게 H2(PostgreSQL 모드)를 `local`과 `test` 프로파일에 추가했다. 기본 설정은 PostgreSQL 환경변수 방식 그대로다.
- Flyway(`spring-boot-starter-flyway`)를 추가하고 `ddl-auto` 기본값을 `validate`로 바꿨다. 마이그레이션 번호 구역 규칙(V100번대 user, V200번대 place, V300 completion, V400 point, V500 ranking, V900+ 시드)을 README로 남겼다.
- 제 Java 구현에서 가져온 공통 기능: 실제 HTTP 201을 돌려주는 `ApiResponse.createdResponse`, 탈퇴 사용자 차단 훅 `UserStatusChecker`, `local` 전용 개발 필터 `LocalFixedUserFilter`.
- 팀 코드의 JWT 설계(access와 refresh 모두 JWT, `type` 클레임, `jti`), AUTH403 처리, 공개 경로는 그대로 두었다.
- 공통 테스트를 Kotlin으로 옮겼다(응답 형식, 에러 처리, 보안 통합, Flyway, Clock 등). 전체 65개.

### 사용자와 인증 (`port/user`)
- 가입(HTTP 201), 로그인, 내 정보 조회와 수정, 이메일과 닉네임 중복 확인을 이식했다. 닉네임 규칙(금칙어, 중복, 30일 제한), 이메일 인증 토큰 검증 인터페이스(기본은 항상 거부, `local`에서만 개발용 토큰), 탈퇴 사용자 차단 구현도 같다.
- **refresh token은 팀 저장소 방식(JWT)**을 쓴다. 명세 예시와 맞는다. 저장소에는 SHA-256 해시만 두고 사용자당 1개다(Redis 구현 + local·test용 메모리 구현).
- 앱이 쓰는 응답 JSON 키와 에러 코드는 Java 구현과 같게 유지했고, 키를 정확히 검사하는 테스트를 추가했다.

### 장소 (`port/place`)
- 장소 카테고리, 장소 목록(위경도 범위 + haversine), 장소 상세를 이식했다. 응답 필드와 JSON 키(`isHot`, `isCompleted` 등)는 Java와 같고 키를 검사하는 테스트를 추가했다.
- 카카오 종로구 수집 스크립트를 `scripts/`로 복사했다(실호출은 키가 없어 미검증).

### 샘플 데이터 (`port/seed`, local 전용)
- 종로구 장소 33곳(뽈뽈 추천 13곳)과 데모 계정 4개를 `db/seed-local`에 두었다. `local` 환경에서만 로드되고 테스트와 운영에는 들어가지 않는다. `docs/local-demo.md`에 로컬 실행 방법을 적었다.

## 테스트 결과(제가 직접 실행)
| 항목 | 결과 |
|---|---|
| 팀 저장소 원본 | 21개 통과(에이전트가 실행) |
| P0 기반 | 65개 통과 |
| 사용자 이식 단독 | 130개 통과(에이전트 보고) |
| 장소 이식 단독 | 101개 통과(에이전트 보고) |
| 사용자 + 장소 통합 | 166개 통과, 실패 0, 건너뜀 0 |
| 샘플 데이터까지 통합(`port/integration`) | **170개 통과**, 실패 0, 건너뜀 0 |
| 카카오 스크립트 `node --test` | 9개 통과 |

병합 때 충돌은 한 곳이었다. 두 에이전트가 같은 이름의 테스트 지원 파일(`AllUsersActiveConfig.kt`)을 따로 만들었고, 코드는 같고 주석만 달라서 한쪽을 택해 해결했다.

## 이전 Java 구현과 달라진 점
- 응답 기본 message가 팀 값("요청 성공", "생성 성공")이다. 가입과 로그인, 닉네임 변경은 명세와 Java처럼 별도 문구를 쓴다. 앱은 문구가 아니라 코드를 보므로 영향이 없다.
- refresh token이 불투명 문자열에서 JWT로 바뀌었다(명세 예시와 일치).
- 에러 코드 이름은 팀 방식(도메인별 enum)으로 바뀌었다. 코드 문자열(USER4092 등)과 한국어 메시지, HTTP 상태는 그대로다.
- 그 밖의 4xx 프레임워크 예외는 `COMMON<상태>`(예: COMMON409) 형식이다. Java는 COMMON400이었다.
- `Bearer` 뒤에 공백이 두 개면 팀 코드는 401, Java는 통과시켰다.
- 시간대는 `Clock`과 Hibernate만 Asia/Seoul로 맞춰져 있고 JVM 기본 시간대와 Jackson 설정은 아직 없다.

## 팀 저장소와 달라진 점(deviation)
- `ddl-auto` 기본값이 `update`에서 `validate`로 바뀌었다(Flyway가 스키마를 관리).
- H2가 배포 산출물에도 들어간다(`runtimeOnly`). 배포 전에 분리가 필요하다.
- PostGIS 타입을 쓰지 않고 위경도 DOUBLE 컬럼과 인덱스를 쓴다. `hibernate-spatial` 의존성은 남겨 두었다.
- 요청 DTO를 nullable로 선언하고 `@field:NotBlank`를 붙였다. 필드 누락 응답이 Java 계약(`errors[].field` 이름)과 같게 하려는 것이다.
- `docs/` 폴더를 새로 만들었다(팀 저장소에는 없었음).
- 팀원 로컬 DB가 이미 `ddl-auto=update`로 만들어져 있으면 Flyway가 비어 있지 않은 스키마를 만나므로 DB 초기화나 baseline 설정이 필요하다. 팀에 알려야 한다.

## 결정이 필요한 것
1. H2를 배포 산출물에서 분리하는 시점과 방식.
2. `created_at`의 시간대. 팀의 기본 방식은 서버 JVM 시간대로 저장하므로 운영 서버가 UTC면 UTC로 저장된다. `Clock` 기준(Asia/Seoul)으로 통일할지.
3. 잘못된 퍼센트 인코딩 쿼리가 COMMON500이 되는 것을 400으로 바꿀지(팀 공통 핸들러에 없는 처리).
4. 그 밖의 4xx의 코드 형식(`COMMON<상태>` vs `COMMON400`)과 `Bearer` 공백 처리 중 어느 쪽을 명세로 볼지.
5. 샘플 장소 좌표를 팀이 검토한 뒤 확정할지.
6. PostGIS 전환 시점.
7. 팀 저장소에 올릴 때 사용자에게 알릴 점: Flyway 도입과 `ddl-auto` 변경, `docs/` 신규, H2 추가, 팀원 로컬 DB 초기화 필요.

## 미검증
- PostgreSQL 17과 PostGIS 실연동. 마이그레이션은 H2(PostgreSQL 모드)에서만 실행했다.
- Redis 실연동(refresh token 저장소는 모의 객체 단위 테스트만 했다).
- 카카오 수집 스크립트 실호출(키 없음).
- 서버를 새로 띄워 앱을 연결한 화면 확인(에뮬레이터를 쓰지 않기로 해서 보류).
- 팀 저장소에 올리는 것은 하지 않았다.
