# 팀 서버 README 준수 점검 보고서

- 작성일: 2026-11-17
- 대상: 우리 서버 코드(`port/integration`, 커밋 `b425ee6`, 이 저장소의 `server-src/`와 같은 내용)
- 기준: 팀 서버 저장소(https://github.com/Bbol-Bbol/server) `main`의 `README.md` (커밋 `ed7915b`)
- 방법: README의 항목(기술 스택, 실행 방법, 환경 변수, 패키지 구조)을 우리 코드와 대조했다. 코드와 설정 파일을 읽어서 확인했고, **서버를 README 순서대로 직접 띄워 재현하지는 않았다(미검증).**
- 이 문서는 점검 결과만 담는다. 코드는 바꾸지 않았다.

## 요약
| 구분 | 건수 |
|---|---|
| README와 어긋나는 점 | 4건 (실행 순서 1, Swagger 1, 기술 스택 1, 패키지 구조 1) |
| README에 없지만 팀에 알려야 할 변경 | 4건 |
| 지킨 항목 | 8건 |

가장 중요한 것은 **1번**이다. README 순서대로 따라 하면 서버가 기동되지 않을 가능성이 높고, 팀원이 가장 먼저 겪는 문제다.

## 1. README와 어긋나는 점

### 1-1. README 순서대로 실행하면 기동되지 않을 가능성이 높다 (높음)
- README의 순서: `.env.example`을 `.env`로 복사 → `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 입력 → `docker compose up -d` → `./gradlew bootRun`
- 우리는 `storage.type`의 기본값을 없앴고(`application.yml`: `type: ${STORAGE_TYPE:}`), 값이 비어 있으면 기동을 막는 `StorageTypeGuard`를 추가했다. 운영에서 설정을 빠뜨렸을 때 사진이 컨테이너 디스크에 조용히 쌓이는 것을 막으려는 보안 점검 결과였다.
- 그런데 `.env.example`에 `STORAGE_TYPE`이 없고 README와 `docs/`에도 언급이 없다(검색 결과 없음). `local`, `test` 프로파일은 yml에서 `local`로 정하므로 괜찮지만, **프로파일 없이 README대로 실행하면** `storage.type이 설정되지 않았습니다` 오류가 날 것으로 보인다.
- 영향: 팀원이 처음 서버를 실행할 때 막힌다. IntelliJ에서 `ServerApplication.kt`를 실행하는 방법도 같다.

### 1-2. README의 Swagger 주소가 기본 실행에서는 열리지 않는다 (중간)
- README 5번: `http://localhost:8080/swagger-ui.html`
- 우리는 API 문서를 기본 꺼짐으로 바꾸고(`springdoc.api-docs.enabled=false`, `swagger-ui.enabled=false`) `local`과 `test` 프로파일에서만 켰다. 운영에 엔드포인트 목록이 공개되지 않게 하려는 보안 점검 결과였다.
- 영향: 기본 프로파일로 실행하면 README의 Swagger 주소가 404다.

### 1-3. 기술 스택 표와 다르다 (중간)
| README | 우리 구현 | 비고 |
|---|---|---|
| PostgreSQL 17 + **PostGIS 3.5** (위치 데이터) | PostGIS 타입을 쓰지 않고 위경도 **DOUBLE 컬럼**을 쓴다 | `Place.kt`에 "PostGIS 도입 전까지 평범한 DOUBLE 컬럼"이라는 주석이 있다. 거리는 앱 코드(`GeoDistance`)에서 계산한다. 이미 알려진 deviation이다 |
| Redis 7: refresh token, 이메일 인증 코드, **랭킹 캐시** | refresh token, 이메일 인증 코드, 로그인 실패 횟수는 Redis에 저장한다. **랭킹 캐시는 없다** | 랭킹은 요청마다 기간 집계를 읽어 메모리에서 순위를 매긴다. 사용자가 늘면 캐시나 Redis 정렬 집합이 필요하다 |

### 1-4. 패키지 구조 원칙과 다르다 (낮음)
- README: "외부 서비스와 연동하는 부분(**파일 저장소, 메일, 카카오**)만 `global/infra`에 인터페이스로 분리한다."
- 파일 저장소(`global/infra/storage`)와 메일(`global/infra/mail`)은 지켰다.
- **카카오 클라이언트는 어겼다.** `KakaoClient`, `RestKakaoClient`, `FakeKakaoClient`가 `domain/auth/kakao/`에 있다.
- 부가: README의 DTO 예시(`SignupRequest`, `UserResponse`를 파일 하나씩)와 달리 우리는 도메인별 묶음 파일(`AuthDtos.kt`, `PlaceDtos.kt` 등)을 쓴다. README에 "(예시)"라고 적혀 있어 경미하다.
- 부가: 도메인 경계상 `ranking` 서비스가 `completion.repository`의 `PlaceRankingRow`(조회용 프로젝션 클래스)를 가져다 쓴다. 저장소 자체를 직접 쓰는 것은 아니다.

## 2. README에 없지만 팀에 알려야 할 변경
팀 저장소에 올릴 때(이미 보고서 `final-report.md` 7절에도 있다) 팀원 로컬 환경에 영향이 있다.
| 변경 | 영향 |
|---|---|
| Flyway 도입(`spring-boot-starter-flyway`), `ddl-auto` 기본값 `update` → `validate` | 이미 `update`로 만들어진 팀원 로컬 DB는 **초기화하거나 baseline 설정**이 필요하다. 엔티티를 추가하면 같은 번호 구역의 마이그레이션도 넣어야 기동한다 |
| `build.gradle.kts`에 H2 추가(`runtimeOnly`) | local 프로파일과 테스트가 Docker 없이 돈다. **배포 산출물에도 H2가 들어가므로** 배포 전에 분리해야 한다 |
| 로그에서 `SqlExceptionHelper`를 끔 | 제약 위반 메시지의 개인정보(이메일 등)가 로그에 찍히지 않게 한다. 예외는 `GlobalExceptionHandler`가 값을 지운 형태로 남긴다 |
| 계약 테스트용 `-Dcontract.update=true` 옵션과 `docs/` 폴더(`contract-testing.md`, `local-demo.md`) | 새 문서 폴더가 생긴다 |

## 3. 지킨 항목
| 항목 | 확인 결과 |
|---|---|
| 언어, 프레임워크, 빌드 | Kotlin 2.3, Spring Boot 4.1, JDK 21, Gradle Kotlin DSL 동일 |
| 서버 주소와 확인 경로 | 기본 포트 8080, `/health` 유지 |
| 환경 변수 | `.env` 자동 읽기(`spring.config.import`) 유지, `.gitignore`에 `.env` 포함, `docker-compose.yml`은 팀 저장소와 **변경 없음** |
| 도메인 구조 | 도메인별 `controller/service/repository/entity/dto/exception` 계층 유지(도메인에 맞게 일부 계층 생략: `ranking`은 repository와 entity 없음, `point`는 exception 없음) |
| 공통 코드 | `global/{common,config,error,security,infra}` 구조, 공통 응답 `ApiResponse`, 도메인별 `ErrorCode` enum |
| 외부 연동 분리 | 파일 저장소(`FileStorage` → `LocalFileStorage`), 메일(`EmailSender` → `SmtpEmailSender`)은 `global/infra`에 인터페이스와 구현 분리 |
| 코드 스타일 | Kotlin 메인 파일 113개가 모두 **탭 들여쓰기**, 스페이스 들여쓰기 0개 |
| 테스트 | `./gradlew test` 빌드 성공. 전체 504개 중 483개 통과, 21개 opt-in 건너뜀, 실패 0개 |

## 4. 제안 (코드는 아직 바꾸지 않았다)
| 번호 | 대상 | 방법 | 규모 | 비고 |
|---|---|---|---|---|
| 1 | 1-1 실행 순서 | `.env.example`에 `STORAGE_TYPE=local`을 추가하고, README 환경 변수 절에 "로컬 실행은 `STORAGE_TYPE=local`"을 설명한다 | 작음 | 팀원의 첫 실행 실패를 막는다. **우선 처리 권장** |
| 2 | 1-2 Swagger | README에 "Swagger UI는 `SPRING_PROFILES_ACTIVE=local`에서만 열린다"를 적는다. 또는 기본 켜짐으로 되돌린다 | 작음 | 되돌리면 운영에 API 목록이 공개되므로 문서화를 권장한다 |
| 3 | 1-3 기술 스택 | 팀 README 표를 현실에 맞게 고치거나(PostGIS는 "도입 예정", 랭킹 캐시는 "미구현"), 팀에 공유해 결정한다 | 작음 | PostGIS를 쓸지는 팀 결정이다 |
| 4 | 1-4 카카오 위치 | 카카오 클라이언트 3개를 `global/infra/kakao/`로 옮기고 import와 테스트를 고친다. deviation 기록은 지운다 | 중간 | README 원칙을 따르는 이동이다. 테스트로 검증한다 |

## 5. 결정이 필요한 것
1. 위 1~3번을 코드와 문서에 반영할지. (1번은 팀에 올리기 전에 반드시 필요하다고 본다.)
2. 4번(카카오 클라이언트 이동)을 할지, deviation으로 남겨 둘지.
3. PostGIS를 도입할지. 도입하면 `Place`의 위경도 컬럼, 거리 계산, 마이그레이션, H2 호환(테스트)이 모두 바뀐다.
4. 랭킹 캐시를 Redis로 만들지. 지금은 프로토타입 규모라 미구현이다.

## 6. 미검증
- README의 실행 순서를 실제로 따라 해 보지 않았다. 1-1, 1-2는 설정과 코드를 읽고 판단한 것이다. 실제 PostgreSQL을 띄운 환경에서 프로파일 없이 `bootRun` 했을 때의 오류 메시지와 시점은 확인하지 못했다.
- 팀 저장소의 `main`이 이 점검 이후 바뀌었는지는 확인하지 않았다(점검 시점의 `main`은 `ed7915b`, 커밋 2개).
