# 0-5단계 보고서: 공통 기반 테스트

- 일자: 2026-11-12
- 담당: test 에이전트, 오케스트레이터가 검증
- 브랜치: `test/0-common` (`feat/0-security`에서 분기, 로컬 전용, 에이전트 커밋 3개)

## 한 일
`backend/src/test/**`에만 테스트를 추가했다. 프로덕션 코드와 `build.gradle`은 바뀌지 않았다(오케스트레이터가 `feat/0-security`와 비교해 확인).
- 응답 형식: `ApiResponse`의 JSON 구조, COMMON200과 COMMON201, `data`가 null이어도 키가 유지되는 동작.
- 에러 코드: 명세 27개와 추가 4개(COMMON404, 405, 415, 500)의 코드 문자열, HTTP 상태, 중복 없음, 메시지 비어 있지 않음. 불규칙한 번호(USER4092, COMPLETION403 등)는 그대로 보존한다고 단언했고 USER4091이 없다는 것도 고정했다.
- 예외 처리: 검증 실패, 파라미터 누락과 타입 불일치, 깨진 JSON, BusinessException, 예상 못한 예외(응답에 메시지와 스택이 없음), 415, ResponseStatusException.
- 보안: 유효 토큰, 만료, 다른 키, alg=none, HS384, 숫자가 아닌 subject, 접두어 오류, 빈 토큰, 헤더 없음, 공개 경로, `POST /auth/logout`, 401 응답 형식, 비활성 사용자.
- `JwtProvider`: 왕복, 변조, 만료, 짧은 키로 기동 실패(prod와 mysql 프로파일, 메시지에 비밀값이 없음).
- `LocalFixedUserFilter`: local 프로파일(H2)에서 고정 userId 인증, 유효 토큰 우선, 값이 비면 필터 빈이 없고 401.
- 시간대: Jackson이 Asia/Seoul로 직렬화하는지.
- 테스트 전용 컨트롤러 `/test-support/**`는 `src/test`에만 있어서 운영 클래스패스에 들어가지 않는다.

## 추가한 파일 (`backend/src/test/java/com/bbolbbol/` 아래)
`support/TestSupportController.java`, `common/response/ApiResponseTest.java`, `common/error/{ErrorCodeTest, GlobalExceptionHandlerTest}.java`, `common/security/{JwtProviderTest, SecurityIntegrationTest, UserStatusCheckerTest, LocalFixedUserFilterTest}.java`, `common/TimezoneTest.java`

## 테스트 결과
`./gradlew clean test`는 통과했다. 전체 66개 중 65개 통과, 1개 건너뜀, 실패 0개. 에이전트가 실행했고 오케스트레이터가 다시 실행해 같은 수를 확인했다.

| 클래스 | 전체 | 건너뜀 |
|---|---|---|
| BbolbbolApplicationTests | 1 | 0 |
| HealthControllerTest | 1 | 0 |
| TimezoneTest | 6 | 1 |
| ErrorCodeTest | 5 | 0 |
| FrameworkErrorTest | 3 | 0 |
| GlobalExceptionHandlerTest | 12 | 0 |
| ApiResponseTest | 5 | 0 |
| JwtProviderTest | 11 | 0 |
| LocalFixedUserFilterTest | 3 | 0 |
| SecurityIntegrationTest | 12 | 0 |
| SecuritySmokeTest | 4 | 0 |
| UserStatusCheckerTest | 2 | 0 |

중간에 실패가 한 번 있었다. `JwtProviderTest`에서 HS384용 테스트 키가 짧아 `WeakKeyException`이 났고, 테스트 키를 늘려 고쳤다. 테스트 쪽 문제였다.

## 발견한 문제
1. **JVM 기본 시간대 설정이 `BbolbbolApplication.main()`에만 있다.** 테스트나 다른 진입점으로 컨텍스트를 띄우면 적용되지 않아 실행 방식에 따라 시간대가 달라질 수 있다. 이를 확인하는 테스트(`TimezoneTest.jvmDefaultTimeZoneAfterContextStart`)를 `@Disabled`로 두었다. 프로덕션 수정이 필요하며 0-6 review 뒤 고친다.
2. `ApiResponse`는 `data`가 null이어도 `"data":null`을 내보낸다. 명세의 의도와 맞는지 확인이 필요하고 테스트는 현재 동작을 고정했다.
3. 그 밖의 4xx 프레임워크 예외(예: 409)는 상태는 유지하지만 코드가 모두 COMMON400이다. 명세에 해당 코드가 없어서 가정한 동작이다.

## 미검증
- MySQL과 Redis 실연동(범위 밖, Docker 없음).
- 시간대: 실제 HTTP 응답 경로와 Hibernate 저장 시 변환은 확인하지 못했다.
- `TestSupportController`의 `/test-support/time` 엔드포인트는 어떤 테스트도 호출하지 않는다.
