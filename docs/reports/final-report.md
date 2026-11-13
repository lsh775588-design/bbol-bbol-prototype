# 뽈뽈(BbolBbol) 최종 보고서

- 작성일: 2026-11-14
- 범위: 0단계(뼈대)부터 2단계(도착 인증, 포인트, 추천), 서버 이식, 앱, UI 개편까지. 3단계는 구현할 API가 없다(아래 "구현하지 않은 것" 참고).
- **GitHub에는 아무것도 올리지 않았다.** push, PR, 병합 모두 하지 않았다. 모든 결과는 로컬 브랜치에 있다.

## 1. 한눈에 보기
| 영역 | 상태 | 위치 | 테스트 |
|---|---|---|---|
| 서버(팀 저장소 스택, Kotlin) | API 14개 구현(명세 28개 중), 나머지 14개는 쟁점 대기 | `C:\dev\bbolbbol\server` (`port/integration`) | **226개 통과** |
| 앱(Kotlin Android) | 로그인, 가입, 홈, 지도, 도착 인증, 포인트, 활동 일지, 추천, 마이, 랭킹(준비 중) | `C:\dev\bbolbbol\app` (`feat/2-integration`) | **57개 통과**, APK 생성 |
| 서버 참고 구현(Java, MySQL) | 사용자 6개, 장소 3개 API. 이식의 기준이 된 구현 | `C:\dev\bbolbbol\backend` (`feat/1-integration`) | 165개 통과 |
| 카카오 장소 수집 스크립트 | 작성 완료, **실호출 미검증**(키 없음) | `server/scripts/` | `node --test` 9개 통과 |

**가장 중요한 결정:** 팀 서버 저장소(https://github.com/Bbol-Bbol/server)가 Kotlin, Spring Boot 4.1, PostgreSQL 기반이라, 처음 Java/MySQL로 만든 서버를 그 스택으로 옮겼다(사용자 결정). 앱은 HTTP 계약만 쓰므로 영향이 없었다.

## 2. 구현한 기능

### 서버 API (팀 저장소 스택, 14개)
| API | 하는 일 |
|---|---|
| `GET /health` | 서버 상태 확인(공개) |
| `POST /auth/signup` | 이메일 가입(HTTP 201). 닉네임 규칙(금칙어, 중복, 형식)과 이메일 인증 토큰 검증. 가입하면 access와 refresh 토큰 발급 |
| `POST /auth/login` | 로그인. 이메일 없음, 비밀번호 틀림, 탈퇴, 카카오 전용 계정이 모두 같은 응답(AUTH4011) |
| `GET /users/me` | 내 정보 |
| `PATCH /users/me` | 닉네임 변경(30일에 1번, 금칙어, 중복 검사) |
| `GET /auth/email-availability` | 이메일 중복 확인(공개) |
| `GET /auth/nickname-availability` | 닉네임 사용 가능 여부(공개) |
| `GET /place-categories` | 장소 카테고리 8개와 기본 점수 |
| `GET /places` | 내 주변 장소(반경, 카테고리 필터, 거리순). 인증 후 방문 여부와 오늘 완료 여부 반영 |
| `GET /places/{id}` | 장소 상세 |
| `GET /places/recommendations` | 뽈뽈 추천 장소(CURATED만). 안 가본 곳 우선 + 거리순 |
| `POST /places/{id}/completions` | 도착 인증(HTTP 201). 위치, 정확도, 모의 위치 검사, 하루 1회, 첫 방문 100%와 재방문 50% 포인트 |
| `GET /users/me/points` | 총 포인트, 오늘 포인트, 인증 횟수 |
| `GET /users/me/point-histories` | 포인트 적립 내역(커서 페이지네이션) |
| `GET /users/me/completions` | 활동 일지(기간과 카테고리 필터, 커서 페이지네이션) |

### 공통 기능(서버)
- 공통 응답 `{success, code, message, data}`, 명세의 에러 코드 체계, COMMON400의 필드별 오류(`data.errors`)
- 보안: JWT(access 1시간, refresh 14일), 공개 경로 설정, 토큰 없으면 AUTH401, 탈퇴 사용자의 토큰 차단
- 한국 시간(Asia/Seoul) 기준 `Clock`, 마이그레이션(Flyway), 로컬 개발용 고정 사용자 필터(local 전용)
- `local` 환경에서만 로드되는 샘플 데이터: 종로구 장소 33곳(뽈뽈 추천 13곳), 데모 계정 4개, 샘플 도착 기록

### 앱 화면
- 로그인, 회원가입(이메일과 닉네임 실시간 중복 확인), 홈(인사, 포인트 카드, 추천 카드, 최근 활동), 지도(OpenStreetMap, 위치 권한 설명, 둘러보기 모드, 카테고리 필터, 장소 상세, 도착 인증), 포인트 내역, 활동 일지, 뽈뽈 추천 목록, 마이(프로필, 통계 타일, 닉네임 변경, 로그아웃), 랭킹(준비 중 안내)
- 하단 탭 4개(홈, 지도, 랭킹, 마이), 프로토타입에 맞춘 보라색 디자인

## 3. 사용한 기술

### 서버(팀 저장소 스택)
| 구분 | 기술 | 비고 |
|---|---|---|
| 언어, 프레임워크 | Kotlin 2.3.21, Spring Boot 4.1.2, JDK 21 | 팀 저장소 기준 |
| 빌드 | Gradle 9.8.2(Kotlin DSL) | |
| DB | PostgreSQL 17 + PostGIS 3.5(목표), **로컬과 테스트는 H2(PostgreSQL 모드)** | PostGIS 타입은 아직 사용하지 않고 위경도 DOUBLE 컬럼 사용. Docker가 없어 PostgreSQL은 실행하지 못함 |
| 마이그레이션 | Flyway(`spring-boot-starter-flyway`), `ddl-auto=validate` | 번호 구역: user V100대, place V200대, completion V300, point V400, 시드 V900+ |
| ORM | Spring Data JPA, Hibernate | |
| 캐시, 토큰 저장 | Redis(refresh token 해시 저장, 로컬은 메모리 구현) | 실제 Redis는 실행하지 못함 |
| 보안 | Spring Security(STATELESS), JWT(jjwt 0.12.6), BCrypt | |
| API 문서 | springdoc-openapi 3.2.0(Swagger UI) | |
| JSON | Jackson 3(`tools.jackson`) | Kotlin의 `isX` 속성 이름 문제를 테스트로 확인 |
| 메일 | spring-boot-starter-mail(Gmail SMTP용), `EmailSender` 인터페이스 | 인증 메일 발송 API는 쟁점 대기라 미사용 |
| 테스트 | JUnit 5, MockMvc, SpringBootTest, 단위·통합 테스트 226개 | 동시 요청(8스레드) 테스트 포함 |

### 앱
| 구분 | 기술 |
|---|---|
| 언어, UI | Kotlin 2.2.20, Jetpack Compose(BOM 2025.09.00), Material3 |
| 네트워크 | Retrofit 3.0.0, OkHttp 4.12.0, kotlinx-serialization 1.9.0 |
| 내비게이션 | Navigation Compose 2.9.4 |
| 지도 | osmdroid 6.1.20(OpenStreetMap, API 키 불필요) |
| 토큰 저장 | EncryptedSharedPreferences(security-crypto) |
| 빌드 | AGP 8.13.0, Gradle 8.14.3, compileSdk와 targetSdk 36, minSdk 26 |
| 테스트 | JVM 단위 테스트 57개(MockWebServer로 실제 Retrofit과 인터셉터 실행) |

### 개발 방식
- 오케스트레이터가 계획과 검증을 맡고, 역할별 에이전트(backend, db, app, test, review)에게 작업을 나눠 맡겼다. 에이전트는 별도 git worktree와 브랜치에서 병렬로 일했고, 결과를 제가 직접 다시 실행해 확인한 뒤 로컬 통합 브랜치에서 합쳤다.
- 단계마다 읽기 전용 review를 돌렸다(0단계와 2단계 조건부 통과, 지적 사항은 모두 수정).
- 환경 제약: Docker와 WSL 배포판이 없어 PostgreSQL, PostGIS, Redis를 실제로 돌리지 못했다.

## 4. Notion과 달라진 점
| 항목 | Notion | 실제 구현 | 이유 |
|---|---|---|---|
| 프론트 스택 | TypeScript, React Native + Expo | Kotlin Android(Jetpack Compose) | 사용자 확정(Kotlin 수업 프로젝트). Notion 기술스택 페이지는 수정하지 않았다 |
| 백엔드 스택 | "Spring Boot"만 적혀 있음 | Kotlin 2.3, Spring Boot 4.1, PostgreSQL/PostGIS, Redis, Flyway | 팀 서버 저장소의 선택을 따름. 처음 만든 Java/MySQL 구현은 참고용으로 남김 |
| 에러 코드 | 28개 | 명세 28개 + 명세에 없는 AUTH403, COMMON404, 405, 415, 500과 그 밖의 4xx용 `COMMON<상태>` | 프레임워크 오류(없는 경로, 메서드, 형식)와 권한 오류를 공통 응답으로 내려주려는 최소 추가 |
| 성공 응답 message | 명세 예시 문구 | 기본은 팀 저장소 문구("요청 성공", "생성 성공"). 가입, 로그인, 닉네임 변경, 도착 인증은 명세 문구 | 앱은 문구가 아니라 코드를 사용 |
| refresh token | JWT 형식(`eyJ...`) 예시 | JWT(access와 refresh 모두), 저장소에는 해시만 | 처음 Java 구현은 불투명 문자열이었다가 팀 방식으로 바뀌어 명세와 일치 |
| 포인트 합계 | 사용자 테이블에 누적 저장 | 포인트 내역의 합으로 계산 | 합계가 어긋날 위험을 없애려는 결정. 내역이 아주 많아지면 성능 확인 필요 |
| 연속 참여 일수 | 도착 인증과 한 트랜잭션에서 갱신 | 미구현 | 연속 참여 랭킹(3단계)에서 정해야 함 |
| 도착 인증 검사 순서 | 명세에 순서 없음 | 장소 없음 → 오늘 이미 인증 → 모의 위치 → 정확도 → 반경 | 중복 요청이 위치 오류에 가려지지 않게 하려는 가정 |
| 추천 정렬 | "안 가본 카테고리 우선 + 거리순" | "안 가본 장소 우선 + 거리순" | 카테고리별 방문 조회가 없어 장소 단위로 근사. 추천 이유 문구는 서버가 정함 |
| 추천 응답 필드 | 예시에 반경, 예상 포인트, 핫 종류 없음 | "나머지는 주변 장소와 같다"는 문장을 근거로 포함 | 추천 카드에서 바로 인증으로 이어지려면 필요 |
| 검색 반경 이름 | `radius`(응답의 인증 반경 `radius`와 이름 겹침) | 명세 그대로 `radius` | 명세 변경이라 보류. 검토 문서는 `searchRadius`를 제안 |
| 사진 보너스 | 첫 방문 응답에 `photoBonusPoint`, `photoDeadline` | 응답에는 내려주지만 업로드 API 없음 | 쟁점 8(사진 URL과 개인정보) 대기 |
| 스키마 관리 | (팀 저장소는 `ddl-auto=update`) | Flyway + `validate` | 팀 코드 주석이 배포 전에 Flyway를 권장함. 팀원 로컬 DB 초기화 필요 |
| 사용자 관심 카테고리 | "최대 3개" | `user_interests` 별도 테이블(검증은 온보딩 API 구현 때) | 온보딩은 쟁점 5 대기 |
| 지도 | (프로토타입은 일러스트 지도) | OpenStreetMap | 일러스트 지도를 만들 수 없고 API 키가 필요 없는 지도로 대체 |
| 응답 JSON | `isHot`, `isCompleted` 등 | 같은 키(Kotlin과 Jackson 3 이름 문제를 테스트로 고정) | 앱 계약 보호 |

## 5. 구현하지 않은 것(쟁점 대기)
지시한 "쟁점 2~8이 결정된 API만 구현" 원칙에 따라 아래 API 14개는 만들지 않았다. 쟁점별 제안 기본값은 검토 문서(https://claude.ai/artifact/UxFFkRTkXhS4NJd6JjtSCi)에 있다.
| API | 기다리는 결정 |
|---|---|
| `POST /auth/email-verifications`, `POST /auth/email-verifications/confirm`, `POST /auth/password-reset` | 쟁점 2: 인증 토큰을 추측 불가능한 난수로 바꾸고 Redis에서 조회 |
| `POST /auth/reissue`, `POST /auth/logout` | 쟁점 3: refresh 재발급 동시 요청 대책(클라이언트 직렬화 또는 서버 유예) |
| `DELETE /users/me` | 쟁점 4: 탈퇴 닉네임 처리(NULL로 비우고 응답에서 "탈퇴한 사용자"로 치환) |
| `POST /auth/kakao`, `GET /users/me/onboarding`, `PATCH /users/me/onboarding` | 쟁점 1과 5: 카카오 값과 닉네임 없는 사용자의 API 범위 |
| `POST /completions/{id}/photo` | 쟁점 8: 사진 URL 추측 방지와 EXIF 제거 |
| `GET /users/me/stats`, `GET /rankings/users`, `GET /rankings/places`, `GET /rankings/streaks` | 쟁점 6: 통계와 랭킹의 날짜 기준(포인트 적립 시각) |
쟁점 7(카테고리와 장소 점수 우선순위)은 "장소 점수가 카테고리 점수를 덮는다"로 가정해 구현에 반영했다. 위 결정 중 제안대로 진행해도 되는 것을 알려주시면 바로 구현할 수 있다.

## 6. 미검증
- **PostgreSQL 17과 PostGIS, Redis 실연동:** Docker가 없어 한 번도 실행하지 못했다. 마이그레이션은 H2(PostgreSQL 모드)에서만 실행했고, refresh token의 Redis 저장소는 모의 객체 단위 테스트만 했다. 하루 1회 제한의 유니크 위반 판별도 PostgreSQL 형태 메시지는 가짜 예외로만 확인했다.
- **카카오 수집 스크립트 실호출:** 키가 없다. 실제 응답 형식과 45개 상한 분할 동작은 첫 실행 때 확인해야 한다.
- **앱 2단계와 UI 개편 화면의 실제 모습:** 에뮬레이터 사용을 중단하라는 요청이 있어서 컴파일과 단위 테스트까지만 확인했다. 1단계 화면(로그인, 가입, 지도, 상세, 내 정보)은 에뮬레이터에서 실제 서버와 연결해 스크린샷으로 확인했다(`docs/screenshots/stage-1`). 새 서버(팀 저장소 스택)로 다시 연결해 확인하지는 못했다.
- **위치 권한과 모의 위치 판별의 실제 값**, 안드로이드 8~10 기기에서의 위치 취득.
- **팀 저장소에 올리는 것:** 하지 않았다.

## 7. 알아둘 점(보안과 운영)
- **앱이 보내는 위치, 정확도, 모의 위치 값을 서버가 그대로 믿는다.** 악의적인 클라이언트가 정확도 0과 장소 좌표를 보내면 어디서든 인증할 수 있고, 요청 횟수 제한과 불가능한 이동 검사도 없다. 명세 백엔드 참고에 "완벽한 방어는 아님"이라고 적혀 있어 명세 위반은 아니다.
- **H2가 배포 산출물에도 들어간다**(`runtimeOnly`). 배포 전에 로컬과 테스트 전용으로 분리해야 한다. `local` 설정(고정 JWT 키, 고정 사용자)도 배포 산출물에 포함되므로, 운영에서 `local` 프로파일이 켜지지 않게 막아야 한다.
- **팀 저장소에 올릴 때 팀에 알려야 할 변경:** Flyway 도입과 `ddl-auto` 기본값 변경, 새 `docs/` 폴더, H2 추가. 팀원 로컬 DB가 이미 `update`로 만들어져 있다면 초기화나 baseline 설정이 필요하다.
- 비밀값은 코드에 없다. 개발용 가짜 값(JWT 키, 인증 토큰, 데모 계정 비밀번호)만 `local`과 `test` 설정에 있다.
- 장소 좌표와 주소는 공개 지식 기반의 대략값(샘플)이고 일부는 실제 매장이 아니라 "샘플" 표시다. 팀 검토가 필요하다.
- 샘플 계정으로 확인할 때는 `docs/local-demo.md`(서버 복제본의 `docs/`)를 본다.

## 8. 네가 결정해야 할 것
1. 서버를 팀 저장소에 올릴 시점과 방식("올려줘"라고 하기 전에는 하지 않는다). 올릴 때 위 6, 7의 변경 사항을 팀에 알린다.
2. 쟁점 2~8의 결정(구현 보류 중인 API 14개). 제안대로 진행해도 되는지.
3. 포인트 합계를 명세대로 사용자 테이블에 저장할지, 지금처럼 내역의 합으로 계산할지.
4. 연속 참여 일수를 저장하며 갱신할지, 완료 기록으로 계산할지.
5. 재방문 점수가 0점이 되는 경우(기본 점수 1점)의 처리.
6. 추천 정렬을 카테고리 단위로 바꿀지.
7. 안드로이드 8~10 기기의 오래된 위치 문제(단발 위치 요청으로 고칠지).
8. 앱 레벨과 배지 개념(프로토타입에 있으나 서버 기획이 없다), 포인트 내역 행의 "완료" 칩을 유형 칩으로 바꿀지, 홈에서 위치 권한을 요청할지.
9. 샘플 장소를 팀이 검토한 뒤 확정할지, 카카오 키로 실제 수집을 돌릴지.
10. 에뮬레이터를 다시 쓰는 시점(앱 새 화면 스크린샷과 새 서버 연결 확인).
11. 8080 포트에서 아직 돌고 있는 오래된 서버(PID 26596) 종료(`taskkill /PID 26596 /F`).

## 9. 보고서 목록(`docs/reports/`)
- `stage-0-1.md` ~ `stage-0-6.md`: 0단계(뼈대, 공통 응답, 인프라, 보안, 테스트, review와 수정)
- `stage-1-backend.md`, `stage-1-app.md`: 1단계(Flyway, 사용자, 장소, 앱, 샘플 데이터)
- `github-server-comparison.md`: 팀 서버 저장소와의 비교
- `port-1-server.md`: 팀 저장소 스택으로의 이식
- `stage-2.md`: 2단계(도착 인증, 포인트, 추천, 앱 화면)
- `final-report.md`: 이 문서
- 스크린샷: `docs/screenshots/stage-0`, `stage-1`

## 10. 다음에 하면 좋은 일
1. Docker를 설치해서 PostgreSQL과 Redis로 서버를 실제로 돌려 보기(마이그레이션과 동시성 검증).
2. 쟁점 2~8을 결정하고 남은 API 14개 구현.
3. 에뮬레이터로 새 서버에 앱을 연결해 2단계 화면과 UI 개편 확인.
4. 서버를 팀 저장소에 올리는 PR 준비(팀에 변경 사항 공유 포함).
