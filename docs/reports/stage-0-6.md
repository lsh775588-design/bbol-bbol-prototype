# 0-6단계 보고서: review와 수정

- 일자: 2026-11-12 ~ 13
- 담당: review 에이전트(읽기 전용), backend 에이전트(수정), 오케스트레이터가 검증
- 브랜치: `feat/0-fixes` (`test/0-common`에서 분기, 로컬 전용, 에이전트 커밋 6개)

## review 결과: 조건부 통과
명세의 에러 코드 28개와 응답 형식, 공개 경로와 인증 규칙, access token 1시간, STATELESS, Asia/Seoul 설정이 모두 명세와 일치했다. JWT 처리(알고리즘 고정, 키 길이 검증, 토큰과 키 비로깅)와 예외 응답(스택과 메시지 비노출)도 문제가 없었다. 도메인 경계와 테이블 소유자 구분은 CLAUDE.md 규칙과 맞고, `common`은 어떤 도메인도 가져오지 않는다. git 이력에 실제 비밀값은 없다.
- review 에이전트는 파일을 바꾸지 않았다(`git status` 깨끗, 오케스트레이터가 확인).
- 오케스트레이터가 지적 사항 중 4건(Redis 비밀번호 없음, 토큰 용도 클레임 없음, 조건부 빈 방식, JVM 시간대)을 코드에서 직접 사실임을 확인했다.

## 1단계 전에 고친 것 (모두 로컬 커밋)
| 항목 | 내용 | 커밋 |
|---|---|---|
| A | MySQL과 Redis 호스트 포트를 `127.0.0.1`에만 바인딩. Redis에 `--requirepass`(필수 환경변수)와 인증하는 healthcheck 추가. `mysql:8.4`로 고정 | 381956e |
| B | `UserStatusChecker`의 `@ConditionalOnMissingBean` 제거. 구현 빈이 있으면 그것을, 없으면 모두 활성으로 처리. 구현 빈이 쓰이는지 테스트 추가 | ed727ba |
| C | access token에 `typ=access` 클레임을 넣고 파싱 때 필수로 검증. 없거나 다른 값(refresh 등)은 401 | 995efed |
| D | `Clock`(Asia/Seoul) 빈과 `TimeConfig` 추가. JVM 기본 시간대를 컨텍스트 기동 시 설정. 건너뛰었던 시간대 테스트 활성화 | 4b3387b |
| E | `ApiResponse.createdResponse()`로 실제 HTTP 201과 COMMON201을 함께 반환. 가입, 도착 인증, 사진 업로드는 이것을 써야 함 | 42c89e9 |
| 정리 | ErrorCodeTest 표시 문구를 28개로 수정, 쓰지 않는 `/test-support/time` 삭제 | 541ae07 |

## 테스트 결과
`./gradlew clean test`: 전체 68개 통과, 건너뜀 0, 실패 0. 에이전트가 실행했고 오케스트레이터가 다시 실행해 같은 수를 확인했다. 0-5에서 건너뛰던 시간대 테스트도 통과한다. 에이전트가 `local` 프로파일로 기동해 `/health` 200을 확인했다.

## 나중에 고칠 항목 (review 지적 사항 중 남은 것)
- 배포 전: local 설정(고정 JWT 키, 고정 userId)이 `src/main`에 있어 운영 jar에 포함됨, H2 `runtimeOnly` 분리, Swagger 운영 공개 여부(명세상 공개 경로), `jwt.access-expiration` 양수 검증, 키 생성 가이드.
- 1단계 계획에 포함: Flyway(마이그레이션 도구). 현재 `mysql` 프로파일은 `ddl-auto: validate`인데 마이그레이션 도구가 없어 엔티티를 만들면 MySQL 기동이 실패한다.
- 2단계: 업로드 용량 초과(`MaxUploadSizeExceededException`)를 FILE413으로 매핑. 로그에 개인정보가 남지 않는지 확인.
- 필터에서 `UserStatusChecker`가 예외를 던질 때 공통 응답이 아니라 컨테이너 기본 응답이 나감(낮음).
- `/auth/**`가 통째로 공개라서 새 엔드포인트를 추가할 때마다 공개 여부를 검토해야 함. `/swagger-ui.html` 경로 처리는 미검증.
- 중복 테스트 정리(`SecuritySmokeTest`와 겹침), 메시지 문구가 명세 설명보다 짧은 점.

## 명세와 다른 점 (가정으로 진행)
- 실제 불일치는 없다. 명세에 없는 것을 추가했다: COMMON404, 405, 415, 500과 "그 밖의 4xx 프레임워크 예외는 COMMON400" 폴백, 성공 응답의 기본 message 문구.

## 미검증
- `docker-compose.yml` 변경(A) 전체. Docker가 없어 `docker compose config`와 기동을 못 했다. Docker 설치 후 command 안의 `${REDIS_PASSWORD:?}` 치환, healthcheck의 `$$` 이스케이프, `mysql:8.4` 옵션 호환을 확인해야 한다.
- 시간대 테스트는 이 PC의 JVM 기본 시간대가 이미 KST라서 `TimeConfig`의 설정 없이도 통과할 수 있다. 시간대가 다른 환경(CI 등)에서 효과가 달라질 수 있다.
- 프로덕션 프로파일에서 짧은 키로 기동 실패하는 것은 단위 테스트로만 확인했다.

## 내가 결정해야 할 것
- Redis 비밀번호가 `redis-server` 실행 인자에 노출된다(`docker inspect`, `ps`). 로컬 개발용이라 현행 유지를 권장한다. 더 엄격히 하려면 셸로 감싸 환경변수로 넘기는 방식이 있다.
- compose를 쓸 때는 `.env`에서 compose와 앱이 같은 `REDIS_PASSWORD`를 쓰게 해야 한다.
- 새 `Clock` 빈 때문에 이후 코드는 `LocalDate.now()` 대신 `Clock` 주입을 써야 한다. 이 규칙을 CLAUDE.md에 추가했다(가정).

## 정리되지 않은 것
- 수정 에이전트가 확인용으로 띄운 서버(`local` 프로파일, 8080, PID 26596)가 종료되지 않았다. 에이전트의 `Stop-Process`가 두 번 거부돼서 우회하지 않고 멈췄다. 오케스트레이터는 이 프로세스가 `C:\dev\bbolbbol\backend\build`의 이 프로젝트 빌드임을 확인했고, 종료는 사용자가 하기로 했다.
