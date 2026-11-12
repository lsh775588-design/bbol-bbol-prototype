# BbolBbol 0단계 계획 (뼈대 · 공통 기반)

## Context
서울 종로구 위치 기반 퀘스트 앱 BbolBbol을 혼자서 에이전트 여러 개로 만든다. 나는 오케스트레이터다. 이 계획은 **0단계만** 다룬다. 1단계부터는 0단계 결과를 보고한 뒤 따로 계획한다.
기준 문서(Notion 허브, API 명세서, Gray Area 결정 사항, 검토 문서)를 읽었다. 0단계에 걸리는 확정 사항은 다음과 같다.
- 공통 응답 `{success, code, message, data}`. 성공은 COMMON200/201.
- 에러 코드는 `도메인 + HTTP 상태 (+ 일련번호)`. COMMON400은 `data.errors[{field, reason}]`를 포함한다.
- 보호 API가 인증에 실패하면 AUTH401을 준다. 공개 경로는 `/auth/**`, `/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`이고 `/auth/logout`만 인증이 필요하다. 세션은 쓰지 않는다(STATELESS).
- 시간대는 Asia/Seoul이고 주는 월요일 시작이다.
- Redis는 사용한다. 사진 저장소는 포트(인터페이스)로 분리하고 개발은 로컬 디스크를 쓴다.

## 환경 점검 결과 (읽기 전용으로 확인)
| 항목 | 상태 | 대응 |
|---|---|---|
| `C:\dev\bbolbbol` | 없음 | 새로 만들고 `git init` |
| Java | Temurin 21 있음 | OK |
| Gradle | 설치 안 됨 | Gradle 배포판을 임시 폴더에 받아 `gradle wrapper`로 wrapper를 생성(이후 `./gradlew`만 사용) |
| Docker / WSL 배포판 | 둘 다 없음 | compose는 작성만 하고, 실제 기동 확인은 Docker Desktop 설치 후로 미룬다(사용자 답변) |
| 3306 포트 | 다른 MySQL이 사용 중 | compose 호스트 포트를 MySQL 3307, Redis 6380으로 둔다(사용자 답변) |
| git 작성자 | 전역 `이세현` | 이 repo는 이름 `이세현` + 규칙의 noreply 이메일을 커밋마다 `-c`로 넘긴다. 전역 설정은 건드리지 않는다. |

## 로컬 전용 원칙
0단계는 전부 로컬에서만 한다. GitHub 원격 연결, push, PR은 하지 않고(사용자가 "올려줘"라고 할 때만), 병합도 하지 않는다. 테스트와 기동 확인은 이 PC에서만 한다. 외부로 나가는 통신은 Gradle 배포판과 의존성 다운로드뿐이다.

## 실행 방식
0단계는 **에이전트를 한 번에 하나씩 순서대로** 돌린다(병렬 없음). 이 단계의 모든 작업은 `backend/`와 repo 루트 파일에 있다. 같은 파일을 둘이 만지지 않으므로 worktree 충돌 위험은 없지만, 규칙대로 단계마다 브랜치를 따로 쓴다. 브랜치 이름과 순서는 아래와 같다.

| 순서 | 에이전트 | 브랜치 | 내용 |
|---|---|---|---|
| 0-1 | (오케스트레이터) | `main` 초기 커밋 | repo 뼈대: `.gitignore`, `README.md`, `backend/`, `app/.gitkeep`, 기본 설정 |
| 0-2 | backend | `feat/0-skeleton` | Spring Boot 프로젝트, 공통 응답, 에러 코드, 예외 처리 |
| 0-3 | db | `feat/0-infra` | docker-compose(MySQL, Redis), DB/Redis 연결 설정, 환경변수 틀 |
| 0-4 | backend | `feat/0-security` | JWT 필터, SecurityConfig(공개 경로, AUTH401), 임시 `userId` 필터 |
| 0-5 | test | `test/0-common` | 공통 응답, 예외 처리, 보안 필터 테스트 |
| 0-6 | review | (읽기 전용) | 명세 준수, 보안 점검, 중복 코드 점검 → 보고서 |

각 브랜치는 로컬에서만 커밋한다. 푸시와 PR은 "올려줘"라고 할 때만 하고, 병합은 하지 않는다. 브랜치는 이전 브랜치 위에 쌓지 않고, 병합 없이 진행하려면 이전 결과가 필요하므로 **0-2 → 0-3 → 0-4 → 0-5 순서로 같은 흐름 안에서 직전 브랜치에서 분기**한다. 이 쌓는 방식이 싫으면 알려달라. 커밋 메시지에는 Claude 관련 문구를 넣지 않는다. 세션 지침이 붙이라는 `Co-Authored-By` 줄도 사용자 규칙이 우선이라 붙이지 않는다.

## 상세 작업

### 0-1 repo 뼈대 (오케스트레이터)
- `C:\dev\bbolbbol` 생성, `git init -b main`.
- 루트: `.gitignore`(build, .gradle, .idea, `.env`, `uploads/`), `README.md`(실행 방법, 에이전트 구성), `.env.example`.
- `app/`은 빈 폴더(`.gitkeep`)만 둔다. 앱 스택은 가정이라 `app/`만 바뀌게 설계한다.
- 초기 커밋 1개(이 커밋만 main에 직접 한다).

### 0-2 backend 뼈대 + 공통 응답/에러 (backend 에이전트)
- Spring Boot 3.x, Java 21, Gradle(Kotlin DSL 대신 Groovy DSL로 단순하게), 패키지 `com.bbolbbol`.
- 의존성: web, validation, data-jpa, security, data-redis, mysql 드라이버, springdoc(swagger-ui), jjwt, 테스트용 spring-boot-starter-test와 H2.
- 도메인 패키지 빈 틀: `auth, user, place, completion, point, ranking`, 공통은 `common`(response, error, config).
- `ApiResponse<T>`(`success, code, message, data`) + `success(...)`/`created(...)`/`error(...)` 팩토리.
- `ErrorCode` enum: 명세의 모든 코드(COMMON400, AUTH401~4012, KAKAO401/502, USER*, EMAIL*, PLACE404, COMPLETION*, FILE*)를 코드·HTTP 상태·기본 메시지로 정의. 명세의 번호 규칙 불일치(USER4091 없음 등)는 **명세 그대로** 옮기고 목록으로 보고한다.
- `BusinessException(ErrorCode, data)` + `@RestControllerAdvice`: 검증 실패는 COMMON400 + `data.errors[{field, reason}]`, 깨진 JSON도 COMMON400, 인증 실패 AUTH401, 그 밖의 예외는 COMMON500(명세에 없음 → 보고 대상).
- `/health` 엔드포인트(공개, 공통 응답 형식).
- Jackson/JPA 시간대 Asia/Seoul 고정(`spring.jackson.time-zone`, `hibernate.jdbc.time_zone`, JVM 기본 시간대).

### 0-3 인프라 (db 에이전트)
- `docker-compose.yml`: MySQL 8(호스트 3307, utf8mb4, `TZ=Asia/Seoul`, `--default-time-zone=+09:00`, healthcheck), Redis(호스트 6380, healthcheck). 비밀번호는 `.env`에서 읽는다.
- `application.yml` + `application-local.yml`: 데이터소스와 Redis를 환경변수로 받는다(기본값은 compose와 같은 포트). **비밀값은 코드에 두지 않는다.** `JWT_SECRET`, `JWT_EXPIRATION`, `MYSQL_*`, `REDIS_*`, `MAIL_*`, `KAKAO_*`는 전부 환경변수이고 `.env.example`에는 이름과 빈 값만 적는다.
- 0단계에서는 엔티티와 마이그레이션을 만들지 않는다. ERD 초안은 문서(`docs/erd.md`)로만 만든다. 엔티티와 마이그레이션 도구 선택(Flyway 등)은 1단계 계획에서 정한다.
- 사진 저장소 포트(`PhotoStorage` 인터페이스)는 선언만 하고 구현은 2단계.

### 0-4 보안 기반 (backend 에이전트)
- `SecurityConfig`: STATELESS, CSRF 비활성, 공개 경로(`/auth/**`, `/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`), `/auth/logout`만 인증 필요, 나머지 인증 필요, 실패 시 AUTH401(공통 응답 형식).
- `JwtProvider`(subject = userId, access 만료 `JWT_EXPIRATION`=1시간) + `JwtAuthenticationFilter`. 토큰 발급 API는 1단계의 일이므로 여기서는 검증과 파싱만 만든다.
- 회원 도메인이 아직 없으므로 `local` 프로파일 전용 **임시 고정 userId 필터**를 둔다(검토 문서의 "A가 끝내기 전까지 B는 userId를 고정하는 임시 필터" 방식). 운영 프로파일에서는 로드되지 않도록 한다.
- 탈퇴 사용자 확인 훅 자리만 남긴다(검토 문서: 탈퇴 후 access token이 1시간 살아있음). 구현은 user 도메인이 생긴 뒤.

### 0-5 테스트 (test 에이전트)
- 공통 응답 직렬화 형식, 에러 코드 매핑, `data.errors` 형식.
- 보안: 공개 경로 통과, 보호 경로에 토큰 없음/위조/만료 → AUTH401, 유효 토큰 통과.
- 앱 컨텍스트 로딩. 이 PC에는 Docker가 없으므로 0단계 테스트는 H2와 임베디드 방식으로만 돌고, MySQL·Redis 실연동 테스트는 Docker 설치 후 추가한다.

### 0-6 review (읽기 전용)
명세 준수, 도메인 경계, 중복 코드, 보안(쟁점 2, 3, 7 관련 선행 점검 + 비밀값이 코드/로그에 없는지)을 검사해 보고서를 낸다. 파일은 수정하지 않는다.

## 이번 단계에서 하지 않는 것
- 회원, 장소, 인증 등 어떤 비즈니스 API도 구현하지 않는다.
- 미결정 쟁점 2~8이 걸린 API는 구현하지 않는다. 0단계에 해당하는 것은 없고, 1단계 이후 보고 목록에서 다룬다. 단 가입의 verificationToken 검증은 인터페이스로 먼저 구현해도 된다.
- 에뮬레이터와 실기기 확인, 푸시, PR, 병합.

## 사용자에게 확인이 필요한 사항 (보고에 포함)
1. **프론트 스택(해결됨)**: 사용자가 Kotlin Android로 확정했다고 알려줬다(Kotlin 수업 프로젝트). Notion 기술스택 페이지의 React Native + Expo는 어긋나지만 문서는 수정하지 않고 보고만 한다. 0단계에는 앱 코드가 없고 `app/`은 빈 폴더다. Kotlin + Compose + Retrofit 프로젝트 생성은 지시한 작업 순서에 없으므로 1단계 계획에 넣어 승인을 받는다. 임의로 만들지 않는다.
2. **명세의 빈틈**: COMMON500, 415 에러 코드, `/api/v1` 접두어가 명세에 없다. 0단계에서는 명세대로 접두어 없이 만들고 COMMON500만 최소로 추가하며, 바꾸고 싶으면 알려달라.
3. **지시문 일부가 깨져 있음**: 붙여넣은 지시문의 0단계 항목("에러 코드와 .")과 test 에이전트 설명("거리   흐름")이 잘려 있어 위 계획으로 해석했다. 의도와 다르면 알려달라.
4. **임시 userId 필터 방식**과 **브랜치를 직전 브랜치에서 분기해 쌓는 방식**이 괜찮은지.

## 검증 (0단계 종료 조건)
- `cd backend && ./gradlew clean test` 전체 통과(실패하면 로그를 보고에 포함).
- `./gradlew bootRun`(local 프로파일, H2 또는 DB 없이 뜨는 설정)으로 기동 후 `curl http://localhost:8080/health` → 공통 응답 형식과 COMMON200 확인. 보호 경로를 토큰 없이 호출하면 AUTH401 확인.
- `docker compose config`는 Docker 설치 후 문법 확인, 기동과 healthcheck 확인도 그때 한다. 설치 전에는 "미검증"으로 표시한다.
- 저장소에 비밀값이 없는지 `.env`가 `.gitignore`에 있는지 확인.
- 보고 형식: 한 일 / 바뀐 파일 / 테스트 결과 / 내가 결정해야 할 것.
