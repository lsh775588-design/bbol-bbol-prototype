# 0-4단계 보고서: 보안 기반

- 일자: 2026-11-12
- 담당: backend 에이전트, 오케스트레이터가 검증
- 브랜치: `feat/0-security` (`feat/0-infra`에서 분기, 로컬 전용, 에이전트 커밋 2개)

## 한 일
- 임시 permitAll 설정을 지우고 `common/security` 패키지로 교체했다.
- `JwtProvider`: HS256, 토큰 subject는 userId다. `createAccessToken(Long)`과 `parseUserId(String)`(Optional 반환)을 제공한다. 만료, 형식 오류, 서명 불일치, alg=none, HS256이 아닌 알고리즘, 숫자가 아닌 subject는 모두 빈 값으로 처리한다. 실패 사유는 debug 로그에만 남기고 토큰과 비밀키는 로그에 쓰지 않는다. `jwt.secret`이 32바이트 미만이면 local과 test가 아닌 프로파일에서는 기동에 실패한다.
- `JwtAuthenticationFilter`: Bearer 토큰을 검증하고, 사용자가 활성 상태일 때만 인증한다. 실패하면 컨텍스트를 비워 두고 진입점이 AUTH401을 반환한다.
- `UserStatusChecker` 인터페이스와 기본 구현(항상 활성)을 만들었다. 탈퇴 사용자의 access token이 최대 1시간 유효한 문제를 user 도메인이 나중에 구현해 막는다.
- `SecurityConfig`: STATELESS, CSRF와 formLogin, httpBasic, logout 비활성. 규칙은 `POST /auth/logout` 인증 필요 → `/auth/**`, `/health/**`, `/swagger-ui/**`, `/v3/api-docs/**` 공개 → 나머지 인증 필요 순서다. 실패 시 AUTH401 공통 응답(JSON, UTF-8)을 준다.
- `LocalFixedUserFilter`: `local` 프로파일에서만 로드되는 개발 전용 필터다. `bbolbbol.local.fixed-user-id`가 설정돼 있고 유효 토큰이 없을 때 고정 userId로 인증한다. 다른 도메인 작업이 인증 도메인보다 먼저 진행될 수 있게 하는 용도다.
- 버그 수정(별도 커밋): 스프링의 404, 405, 415와 그 밖의 4xx 예외가 500(COMMON500)으로 바뀌던 문제를 고쳤다. 원래 HTTP 상태를 유지한다. 이 버그는 0-2에서 만든 예외 처리에 있었고, 0-4 에이전트가 curl로 확인하다 발견했다.

## 바뀐 파일 (모두 `backend/` 아래)
- 삭제: `common/config/SecurityConfig.java`(임시 설정)
- 추가: `common/security/{JwtProvider, JwtAuthenticationFilter, LocalFixedUserFilter, SecurityConfig, UserStatusChecker}.java`
- 수정: `common/error/ErrorCode.java`, `common/error/GlobalExceptionHandler.java`, `application-local.yml`, `application-test.yml`
- 테스트 추가: `common/security/SecuritySmokeTest.java`, `common/error/FrameworkErrorTest.java`

## 테스트 결과
| 항목 | 결과 |
|---|---|
| `./gradlew clean test` | 통과. 총 9개(기존 2, 보안 스모크 4, 프레임워크 오류 3). 에이전트가 실행했고 오케스트레이터가 다시 실행했다. |
| curl, 고정 userId 비활성 | `/health` 200, 토큰 없이 `/places` 401 AUTH401, 잘못된 Bearer 401, alg=none 토큰 401, 토큰 없이 `POST /auth/logout` 401 (에이전트 실행 결과) |
| curl, 수정 후 | 인증된 사용자가 없는 경로 호출 시 404 COMMON404, `POST /health` 405 COMMON405 (에이전트 실행 결과) |
| 8080 포트 | 두 번 모두 비어 있음을 확인했다. |

## 오케스트레이터 검증
- 커밋 작성자는 이세현이고 Claude 관련 문구는 없다. `git status`는 깨끗하다.
- `SecurityConfig` 소스를 직접 읽었다. 규칙 순서, STATELESS, AUTH401 응답이 계획과 같다.
- 설정 파일의 비밀값은 환경변수 자리표시자이거나 이름에 FAKE가 들어간 개발용 값이다(값은 출력하지 않았다).

## 명세와 다른 점 (가정으로 진행)
- COMMON404, COMMON405, COMMON415를 추가했다. 명세에는 COMMON500처럼 이 코드들도 없다. 프론트가 HTTP 상태로 분기하니 영향은 작다. 팀 확인이 필요하다.
- 그 밖의 4xx 프레임워크 예외는 COMMON400으로 응답한다.
- 403은 명세에 코드가 없어 스프링 기본 동작(공통 응답 아님)을 유지했다. 현재 권한 규칙이 없어 403은 발생하지 않는다.

## 내가 결정해야 할 것
1. COMMON404, 405, 415, 500 유지 여부.
2. 403 일반 코드가 필요한지(권한 규칙이 생길 때).
3. `UserStatusChecker` 기본 빈은 `@ConditionalOnMissingBean`이다. user 도메인이 구현 빈을 추가할 때 중복되지 않는지 그때 확인해야 한다.
4. `local`에서 고정 사용자 필터가 켜져 있으면 잘못된 토큰도 고정 사용자로 인증된다. local에서 AUTH401을 보려면 `BBOLBBOL_LOCAL_FIXED_USER_ID=`로 비워야 한다.

## 미검증
- 유효 토큰으로 보호 경로를 통과하는 흐름은 bootRun으로 확인하지 못했다(토큰 발급 API가 아직 없다). 0-5 테스트에서 `createAccessToken`으로 검증한다.
- 만료 토큰 거부는 실행 확인을 하지 않았다. 0-5에서 확인한다.
- local과 test가 아닌 프로파일에서 짧은 secret일 때 기동 실패는 코드로만 구현했다. 실행해 보지 않았다.
- 415 경로와 "그 밖의 4xx → COMMON400" 분기는 테스트하지 않았다. `consumes`가 있는 엔드포인트가 생긴 뒤 추가한다.
