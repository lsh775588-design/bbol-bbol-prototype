# 팀 서버 저장소(Bbol-Bbol/server)와 로컬 구현 비교

- 일자: 2026-11-13
- 대상: https://github.com/Bbol-Bbol/server (main, 커밋 2개, 2026-11-10, 작성자 soyeon). 읽기 전용으로 별도 폴더에 받아서 코드를 읽었다. **빌드와 테스트는 실행하지 않았다**(받은 코드는 실행하지 않는 원칙). 아래 내용은 코드를 읽어서 판단한 것이다.
- 비교 대상: `C:\dev\bbolbbol`(로컬 통합 브랜치 `feat/1-integration`)

## 한눈에 보기
팀 저장소는 **뼈대만** 있다(공통 응답, 예외 처리, JWT 보안, 메일과 파일 저장소 인터페이스, 설정). 도메인 기능은 `/health`뿐이다. 설계 방향은 로컬 구현과 거의 같다(명세 기반 공통 응답, 도메인별 계층, 인터페이스로 분리한 외부 연동, `Clock` 주입). 그런데 **기술 스택이 다르다.** 이 점이 가장 큰 쟁점이다.

## 기술 스택 차이 (가장 중요)
| 항목 | 팀 저장소 | 로컬 구현 |
|---|---|---|
| 언어 | Kotlin 2.3.21 | Java 21 |
| 프레임워크 | Spring Boot 4.1.2 | Spring Boot 3.5.16 |
| DB | PostgreSQL 17 + PostGIS 3.5 (`hibernate-spatial`) | MySQL 8 (local은 H2) |
| 위치 검색 | PostGIS 사용 예정(`Point` 타입) | 위경도 범위 + haversine 직접 계산 |
| 스키마 관리 | `ddl-auto: update`(Flyway 권장이라고 주석에 적음) | Flyway + `validate` |
| 빌드 | Gradle Kotlin DSL | Gradle Groovy DSL |
| API 문서 경로 | `/swagger-ui.html` | `/swagger-ui/index.html` |

지시문에는 "Spring Boot 3.x, Java 21, MySQL 8"이라고 되어 있었고(사용자가 승인), 팀 저장소의 기술스택 README는 Kotlin과 PostgreSQL이다. Notion 기술스택 페이지에는 "Spring Boot"뿐이라 둘 중 어느 쪽이 팀의 결정인지 문서로는 확인되지 않는다.

## 구조 비교
| 항목 | 팀 저장소 | 로컬 구현 |
|---|---|---|
| 패키지 | `com.bbolbbol.server.domain.<도메인>/{controller,service,repository,entity,dto,exception}` + `global/{common,config,error,security,infra}` | `com.bbolbbol.{auth,user,place,completion,point,ranking}` + `common/{config,error,response,security,storage}` |
| 에러 코드 | `ErrorCode` 인터페이스 + 도메인별 enum(`CommonErrorCode`, `FileErrorCode`) | 단일 `ErrorCode` enum(명세 28개 + 추가 4개) |
| 추가한 코드 | AUTH403, COMMON404/405/415/500, 그 밖의 4xx는 `COMMON{상태}`로 동적 생성 | COMMON404/405/415/500, 그 밖의 4xx는 COMMON400 |
| 공통 응답 | `ok()`, `created()`, `fail()`. message "요청 성공", "생성 성공" | `success()`, `created()`, `createdResponse()`, `error()`. message "요청이 성공했습니다." |
| 시간 | `ClockConfig`(Asia/Seoul Clock 빈) | `TimeConfig`(같은 방식) + JVM 기본 시간대 고정 |
| 외부 연동 | `EmailSender`(+ Gmail SMTP 구현), `FileStorage`(+ 로컬 디스크 구현), `MultipartFile` 기반 | `PhotoStorage` 인터페이스만 선언, 메일 없음 |
| compose | PostgreSQL+PostGIS(`amd64` 이미지), Redis(비밀번호 없음), 포트는 127.0.0.1에만 바인딩 | MySQL 8.4, Redis(비밀번호 필수), 포트는 127.0.0.1에만 바인딩 |
| 환경 변수 | `.env`를 앱이 직접 읽음(`optional:file:.env`) | 환경변수만 사용 |

## 보안과 예외 처리 비교
| 항목 | 팀 저장소 | 로컬 구현 |
|---|---|---|
| 공개 경로 | `/auth/**`, `/health/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/health`, `/error` (`/auth/logout`만 인증) | `/auth/**`, `/health/**`, `/swagger-ui/**`, `/v3/api-docs/**` (`POST /auth/logout`만 인증) |
| 401, 403 응답 | `SecurityErrorResponder`가 AUTH401과 AUTH403을 공통 응답으로 | AUTH401만 처리, 403은 스프링 기본 |
| JWT | access와 refresh **둘 다 JWT**, `type` 클레임으로 구분, `jti`로 같은 초 발급 충돌 방지 | access JWT(`typ=access`), refresh는 **불투명 문자열** `{userId}.{난수}`(저장소에는 해시만) |
| JWT 시크릿 | 없거나 32바이트 미만이면 기동 거부(예외 없음) | 같은 규칙, 단 local과 test 프로파일은 임시 키 허용 |
| 사용자 상태 확인 | 없음 | `UserStatusChecker`(탈퇴 사용자의 토큰 차단) |
| 개발용 고정 사용자 | 없음 | local 전용 필터 |
| 업로드 예외 | 용량 초과를 FILE413, 파일 누락을 FILE4001로 처리 | 미처리(1단계 review에서 "2단계에서 처리"로 남김) |
| 검증 실패 | COMMON400 + `data.errors[{field, reason}]` | 같음 |
| 테스트 | 3개 클래스(JWT, 보안과 에러 응답) 약 20개 | 165개(백엔드), 앱 29개 |

## 팀 저장소에만 있는 것 (로컬 구현에 가져올 만한 것)
1. **refresh token이 JWT 형식이다.** 명세 예시(`eyJ...`)와 맞는다. 로컬 구현은 불투명 문자열이라 명세와 다르다(1단계 보고서에서 결정 대기로 올렸던 항목).
2. **AUTH403 처리**(`AccessDeniedHandler`). 로컬 구현은 403이 공통 응답이 아니다(0단계 review에서 지적되었던 부분).
3. **업로드 예외 매핑**(FILE413, FILE4001). 2단계에서 필요한 부분이다.
4. `EmailSender`와 `FileStorage`의 **구현체**(SMTP, 로컬 디스크). 이메일 인증(쟁점 2)과 사진 업로드(쟁점 8)에 쓰인다.
5. `/swagger-ui.html`과 `/error`를 공개 경로에 넣은 것(1단계 review에서 `/swagger-ui.html`이 401이 날 수 있다고 지적했던 부분).
6. `.gitattributes`로 줄바꿈 고정(로컬 구현에도 추가했다).

## 로컬 구현에만 있는 것
- 구현된 API: 사용자 6개(가입, 로그인, 내 정보 조회와 수정, 이메일과 닉네임 중복 확인), 장소 3개(카테고리, 목록, 상세), 카카오 수집 스크립트.
- Flyway 마이그레이션과 번호 구역 규칙, 샘플 데이터(local 전용), 데모 계정.
- `UserStatusChecker`, 개발 전용 고정 사용자 필터, 이메일 인증 토큰 검증 인터페이스.
- Kotlin Android 앱(로그인, 가입, 내 정보, 지도)과 에뮬레이터 스크린샷.
- 단위·통합 테스트 165개, 단계별 보고서, review 결과.

## 팀 저장소에서 발견한 점 (읽은 코드 기준, 미실행)
1. `ddl-auto` 기본값이 `update`다. 주석에 배포 전에 `validate`와 Flyway로 바꾼다고 적혀 있어 인지하고 있다.
2. Redis 비밀번호가 없다(로컬 127.0.0.1 바인딩이라 위험은 작다).
3. 사용자 상태 확인이 없어서 탈퇴한 사용자의 access token이 최대 1시간 통한다(명세와 쟁점 4 관련).
4. `ApiResponse.created()`가 본문 코드만 COMMON201이고 HTTP 201은 컨트롤러가 직접 맞춰야 한다(로컬 구현도 같은 구조라서 `createdResponse()`를 따로 만들었다).
5. 기술 선택 자체: Spring Boot 4.1과 Kotlin 2.3은 매우 최신이다. 라이브러리 호환성은 빌드해 봐야 알 수 있다(미검증).
6. PostGIS 이미지는 `platform: linux/amd64`다. 이 PC(Windows)에서는 문제없을 가능성이 높다. Docker가 설치되지 않아서 확인하지 못했다.

## 결정이 필요한 것 (가장 큰 것은 스택)
이 저장소가 팀의 공식 서버 저장소라면, 지금 로컬에서 만든 Java와 MySQL 구현은 스택이 달라 그대로 올릴 수 없다. 앱은 HTTP 계약만 쓰므로 영향이 없다(`app/`만 영향받게 설계한 효과). 영향을 받는 것은 서버 코드다.
- 도메인 로직, 에러 코드, 닉네임 규칙, 점수 정책, 테스트 시나리오: 그대로 옮길 수 있다(언어만 Java에서 Kotlin으로).
- 마이그레이션 SQL: PostgreSQL 방언으로 다시 검증해야 한다(공용 SQL로 썼지만 MySQL 8에서도 아직 실행해 보지 않았다).
- 장소 검색: PostGIS를 쓰면 haversine 계산과 위경도 범위 조회를 `ST_DWithin`으로 바꿀 수 있다.
- 테스트 환경: 팀 저장소는 PostgreSQL을 쓰므로 H2 대신 테스트용 DB 전략을 다시 정해야 한다(Docker가 없는 지금은 H2 PostgreSQL 모드 정도가 가능).
