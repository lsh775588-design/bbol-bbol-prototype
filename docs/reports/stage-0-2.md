# 0-2단계 보고서: Spring Boot 뼈대, 공통 응답, 에러 코드

- 일자: 2026-11-12
- 담당: backend 에이전트, 오케스트레이터가 검증
- 브랜치: `feat/0-skeleton` (main에서 분기, 로컬 전용, 커밋 3개)

## 한 일
- Spring Boot 프로젝트를 만들고 Gradle wrapper를 생성해 커밋했다. 임시 폴더에 받은 Gradle로 wrapper를 한 번 만들었고, 이후에는 `./gradlew`만 썼다.
- `ApiResponse<T>`를 만들었다. `success()`(COMMON200), `created()`(COMMON201), `error()` 팩토리가 있다.
- `ErrorCode`에 명세의 27개 코드를 번호 규칙이 불규칙한 것까지 그대로 옮겼다. 명세에 없는 `COMMON500`을 1개 추가했고, 코드에 "NOT IN SPEC" 주석을 달았다.
- `BusinessException`과 `GlobalExceptionHandler`를 만들었다.
  - 검증 실패는 COMMON400이고 `data.errors=[{field, reason}]`를 담는다.
  - 깨진 JSON은 COMMON400이고 errors가 빈 리스트다.
  - 그 밖의 예외는 COMMON500이다. 스택과 메시지는 응답에 나가지 않고 로그에만 남는다.
- `GET /health`는 COMMON200과 `{"status":"UP"}`를 반환한다.
- 임시 `SecurityConfig`를 만들었다(permitAll, stateless, CSRF off). 0-4에서 교체한다.
- 도메인 패키지 `auth, user, place, completion, point, ranking`은 빈 placeholder다.
- 시간대는 `main()`에서 `TimeZone.setDefault`, `spring.jackson.time-zone`, hibernate `jdbc.time_zone` 모두 Asia/Seoul로 맞췄다.
- `application.yml`은 DB와 Redis 접속 정보를 환경변수로 읽는다. 기본값은 MySQL localhost:3307, Redis localhost:6380이고 비밀번호 기본값은 빈 값이다.

## 버전
Spring Boot 3.5.16, Gradle 8.14.3, springdoc-openapi-starter-webmvc-ui 2.8.17, jjwt 0.13.0, Java 21 toolchain

## 바뀐 파일 (모두 `backend/` 아래, 24개)
- 빌드: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- 앱: `BbolbbolApplication.java`, 도메인 패키지 6개의 `package-info.java`
- 공통: `common/response/ApiResponse.java`, `common/error/{ErrorCode,BusinessException,GlobalExceptionHandler}.java`, `common/HealthController.java`, `common/config/SecurityConfig.java`
- 설정: `src/main/resources/application.yml`, `src/test/resources/application-test.yml`
- 테스트: `BbolbbolApplicationTests.java`, `common/HealthControllerTest.java`

## 테스트 결과
| 항목 | 결과 |
|---|---|
| `./gradlew clean test` | 통과. 테스트 2개(context load, `/health` MockMvc). 에이전트가 한 번, 오케스트레이터가 다시 한 번 실행했다. |
| `./gradlew bootRun` | **실패(예상된 결과)**. localhost:3307에 MySQL이 없어서 `Communications link failure`가 나고 JPA 초기화가 실패한다. 이 PC에 Docker가 없다. |
| 실제 서버로 `curl /health` | **미검증**. bootRun이 안 떠서 MockMvc 테스트로만 확인했다. |

## 오케스트레이터 검증
- 커밋 3개의 작성자는 모두 이세현이고 Claude 관련 문구와 trailer는 없다.
- `backend/src/main`에서 비밀값 하드코딩은 없다. 비밀번호 기본값은 빈 값이다.
- `gradlew`는 저장소에 LF로 저장돼 있다(`git ls-files --eol`: `i/lf`). 에이전트가 우려한 CRLF 문제는 지금은 없다.

## 내가 결정해야 할 것
1. `COMMON500`은 명세에 없는 추가다. 유지할지, 명세에 반영할지.
2. 성공 응답의 기본 message("요청이 성공했습니다.", "생성되었습니다.")는 명세에 문구가 없어서 에이전트가 정했다. 괜찮은지.
3. `HandlerMethodValidationException`도 COMMON400으로 처리했다. 명세에는 없지만 Spring 6.1 이상의 파라미터 검증 때문이다. 괜찮은지.
4. `ddl-auto` 기본값을 `validate`로 했다. 엔티티와 스키마 관리 방식(Flyway 등)이 정해지지 않았으니 1단계 계획에서 확정한다.
5. DB 없이 `bootRun`으로 확인하고 싶으면 `local` 프로파일에 H2를 두는 방법이 있다. 이는 0-3에서 할지.
6. `.gitattributes`(LF 고정) 추가 여부. 0-1 보고서의 같은 항목과 같다.

## 명세와 다르게 보이는 점
- 에러 코드 번호 규칙이 섞여 있다. `USER409`와 `USER4092`는 있고 `USER4091`은 없다. `COMPLETION403/404/409`와 `COMPLETION4001~4004`가 함께 있다. 명세 그대로 옮겼고 임의로 바꾸지 않았다.
