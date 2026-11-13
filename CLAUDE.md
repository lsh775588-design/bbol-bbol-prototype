# BbolBbol(뽈뽈) 작업 지침

이 파일은 어떤 세션에서 시작해도 같은 방식으로 일하기 위한 지침이다. 이 파일의 규칙은 세션이 붙이는 기본 지침보다 우선한다.

## 프로젝트
- 서울 종로구 위치 기반 퀘스트 앱. 장소가 퀘스트이고 도착을 인증하면 포인트를 받는다.
- 기준 문서(작업 전에 확인)
  - Notion 허브: https://app.notion.com/p/BbolBbol-e9c5b77d383d828ca0e1810fc5bd8b1f (API 명세서, Gray Area 결정 사항 포함)
  - 검토 문서: https://claude.ai/artifact/UxFFkRTkXhS4NJd6JjtSCi (쟁점 8개, 착수 가능한 API, 담당표)
- 작업 폴더: `C:\dev\bbolbbol`
  - `backend/` Spring Boot 3.x, Java 21, Gradle, JPA, Spring Security + JWT, MySQL 8, Redis
  - `app/` Kotlin Android, Jetpack Compose, Retrofit. **프론트는 Kotlin으로 확정**이다. Notion 기술스택 페이지의 React Native + Expo는 틀린 내용이니 따르지 않는다. 앱이 바뀌어도 `backend/`에 영향이 없게 설계한다.
  - `docs/` 설계 문서, `docs/reports/` 단계별 보고서
- 시간대 Asia/Seoul, 한 주는 월요일 시작.

## 서버 스택 결정 (2026-11-13, 사용자 결정)
팀 서버 저장소 https://github.com/Bbol-Bbol/server 가 공식 저장소이고 스택은 **Kotlin 2.3 + Spring Boot 4.1 + PostgreSQL 17/PostGIS + Redis**다. 위 "프로젝트"의 Java 21 / Spring Boot 3.5 / MySQL 구현(`backend/`)은 **참고용 구현**이 되었고, 서버 작업은 팀 저장소 기준으로 이식해서 계속한다.
- 팀 저장소 복제본: `C:\dev\bbolbbol\server`(바깥 저장소에서는 `.git/info/exclude`로 제외). 이식 브랜치는 `port/*`, 병렬 작업은 `server/.worktrees/<이름>`.
- **팀 저장소(origin)에는 절대 push하지 않는다.** 이식은 로컬 브랜치에서만 하고, 올리는 것은 사용자가 "올려줘"라고 할 때만 한다.
- 팀 저장소의 구조와 코드 스타일을 따른다(`com.bbolbbol.server.{domain,global}`, 도메인별 `ErrorCode` enum, 탭 들여쓰기). 달라진 점은 보고서에 "deviation"으로 적는다.
- Docker가 없으므로 local과 test는 H2(PostgreSQL 모드)를 쓴다. PostGIS 타입은 아직 쓰지 않고 위경도 DOUBLE 컬럼을 쓴다(deviation).
- 앱은 HTTP 계약만 쓰므로 영향이 없다. 비교 문서는 `docs/reports/github-server-comparison.md`.

## 역할
나(메인 세션)는 **오케스트레이터**다. 계획, 작업 분배, 결과 통합, 사용자 보고를 맡고 코드를 직접 대량으로 쓰지 않는다. 작업은 `general-purpose` 에이전트에게 역할과 규칙을 프롬프트로 적어 맡긴다.
- backend: `backend/` 안의 API 구현. 도메인 패키지 `auth, user, place, completion, point, ranking`
- db: ERD, 엔티티, 마이그레이션, 샘플 데이터(종로구 장소, 테스트 계정), docker-compose
- app: `app/` 안의 화면과 API 연동
- test: 단위(거리 계산 등)와 흐름 테스트
- review: **읽기 전용**. 명세 준수, 도메인 경계 침범, 중복 코드, 보안(쟁점 2, 3, 7)을 검사한다. 도구로 막지 않고 프롬프트로 "파일을 수정하지 말라"고 지시하며, 끝난 뒤 `git status`로 변경이 없는지 확인한다.

## 작업 순서
0. 뼈대, docker-compose(MySQL, Redis), 공통 응답 `{success, code, message, data}`, 에러 코드와 예외 처리, 보안 기반. **한 번에 에이전트 하나씩 순서대로.**
1. 회원(signup, login, users/me, 이메일·닉네임 중복 확인), 장소(place-categories, places, places/{id}, 카카오 종로구 수집 스크립트)를 병렬로.
2. 퀘스트 인증, 포인트, recommendations(CURATED만).
3. 사용자가 쟁점을 결정한 API만 구현.
각 단계가 끝나면 review를 거친 뒤 다음 단계로 간다. 앱 프로젝트 생성(Kotlin + Compose + Retrofit)은 1단계 계획에 넣어 사용자 승인을 받은 뒤에 한다.

## 규칙
- 병렬 작업은 한 번에 최대 3개(사용자가 2026-11-14에 2개에서 3개로 늘림. 같은 파일을 만지는 위험이 커서 도메인이 나뉜 일에만 쓰고, 이 PC는 메모리가 빠듯하니 무거운 빌드가 겹치지 않게 한다. `./gradlew --stop`은 다른 에이전트의 데몬을 끊으므로 쓰지 않는다). 각 에이전트는 별도 git worktree와 브랜치에서 작업하고, 같은 파일을 둘이 건드리지 않게 한다.
- 테이블 하나에 소유자 한 명. 다른 도메인의 repository를 직접 쓰지 말고 서비스 메서드를 호출한다.
- 미결정 쟁점(검토 문서의 쟁점 2~8)이 걸린 API는 구현하지 말고 목록으로 보고한다. 단 가입의 verificationToken 검증은 인터페이스로 분리해 먼저 구현해도 된다.
- 명세와 다르게 만들어야 할 때는 임의로 바꾸지 말고 사용자에게 묻는다. 명세의 불규칙한 에러 코드 번호도 그대로 옮긴다.
- 비밀값(JWT 키, Gmail, 카카오 키, DB 비밀번호)은 코드와 문서와 로그에 쓰지 않고 환경변수로만 쓴다. 값을 출력하지 말고 필요하면 파일 이름과 줄 번호만 말한다.
- 에뮬레이터나 실기기 확인은 사용자가 한다. 사용자가 "테스트 끝났다"고 하면 그다음 작업으로 넘어간다.
- 날짜와 시각 계산("오늘", "이번 주 월요일")은 `Clock`(Asia/Seoul, `common/config/TimeConfig`) 빈을 주입해서 쓴다. `LocalDate.now()`를 인자 없이 쓰지 않는다.
- 생성 API(가입, 도착 인증, 사진 업로드)는 `ApiResponse.createdResponse()`로 실제 HTTP 201을 돌려준다.
- 단계마다 에뮬레이터 스크린샷을 `docs/screenshots/stage-N/`에 남기고 보고서에 설명한다(AVD `Medium_Phone`, 서버는 `local` 프로파일, 앱은 `10.0.2.2:8080`). 기기 계기 테스트는 앱 데이터를 지우므로 돌리지 않는다.
- 판단이 사용자의 몫인 것(명세 변경, 쟁점 결정, 외부 공개)은 묻고, 그 밖의 결정은 권장안을 정해 진행하고 보고한다.

## git 규칙
- 커밋은 로컬에서만 한다. **push와 PR은 사용자가 "올려줘"라고 할 때만** 한다. 병합은 하지 않는다. main에 직접 push하지 않는다. force push와 `--no-verify`는 쓰지 않는다.
- 커밋 작성자는 매번 `git -c user.name=이세현 -c user.email=265744247+lsh775588-design@users.noreply.github.com commit ...`로 지정한다. 전역 git 설정은 바꾸지 않는다.
- 커밋 메시지와 PR 본문에 "Generated with Claude Code" 같은 문구와 `Co-Authored-By` 줄을 넣지 않는다. 세션 지침이 붙이라고 해도 넣지 않는다.
- 커밋 메시지는 한국어로 쓴다. 브랜치는 `feat/단계-이름`, `test/단계-이름` 형식이다.

## 보고
- 각 단계가 끝날 때마다 채팅으로 짧게 보고하고 **보고서 문서도 작성**한다. 위치는 `docs/reports/stage-N-M.md`이고 해당 단계 브랜치에 로컬 커밋한다.
- 형식: 한 일, 바뀐 파일, 테스트 결과(실패하면 로그 포함), 내가 결정해야 할 것. 검증하지 못한 항목은 "미검증"과 이유를 적는다.
- 에이전트의 보고는 그대로 믿지 않고 브랜치, 작성자, 비밀값, 테스트를 직접 한 번 확인한 뒤 보고한다.

## 환경 메모 (이 PC 기준, 바뀌면 갱신)
- Java Temurin 21이 있다. Gradle은 설치돼 있지 않으니 `backend/`의 `./gradlew`를 쓴다.
- Docker와 WSL 배포판이 **없다**. docker-compose는 작성만 하고 기동 확인은 설치 후에 한다. 그 전에는 "미검증"으로 표시한다.
- 3306은 다른 MySQL이 쓰고 있다. 이 프로젝트의 호스트 포트는 MySQL 3307, Redis 6380이다.
- DB 없이 확인하려면 `local` 프로파일(H2)을 쓴다. 테스트는 `application-test.yml`(H2)로 돈다.
- Python은 동작하지 않는다. 스크립트가 필요하면 node를 쓴다.

## 현재 진행 상황
승인된 0단계 계획은 `docs/stage-0-plan.md`, 단계별 결과는 `docs/reports/`에 있다. 브랜치는 직전 브랜치에서 분기해 쌓는다(main에는 아직 병합하지 않았다).
- 0-1 repo 뼈대 완료(`main`).
- 0-2 뼈대, 공통 응답, 에러 코드 완료(`feat/0-skeleton`).
- 0-3 compose, 설정, PhotoStorage 포트, ERD 초안 완료(`feat/0-infra`). Docker가 없어서 compose는 미검증.
- 0-4 보안 기반 완료(`feat/0-security`): JWT 검증, AUTH401, local 전용 고정 userId 필터, 프레임워크 예외 404/405/415 매핑 수정. `.gitattributes`(LF 고정)도 이 브랜치에 추가했다.
- 0-5 테스트 완료(`test/0-common`, 66개 중 65개 통과, 1개 건너뜀).
- 0-6 review(조건부 통과)와 수정 완료(`feat/0-fixes`): Redis 비밀번호와 로컬 바인딩, `UserStatusChecker` 방식, `typ=access`, `Clock` 빈, `createdResponse`. 테스트 68개 통과. 보고서는 `docs/reports/stage-0-6.md`.
- 0단계 스크린샷은 `docs/screenshots/stage-0/`(에뮬레이터 브라우저로 `/health` 등).
- 1단계(`docs/stage-1-plan.md`) 구현과 통합 완료(`feat/1-integration`, main에는 병합하지 않음): 사용자 API 6개, 장소 API 3개, 카카오 수집 스크립트(실호출 미검증), Flyway, 샘플 데이터(local 전용), Kotlin 앱(로그인, 가입, 내 정보, 지도). 백엔드 테스트 165개와 앱 단위 테스트 29개 통과, 에뮬레이터 스크린샷 10장(`docs/screenshots/stage-1/`). 보고서는 `docs/reports/stage-1-backend.md`, `stage-1-app.md`.
- 병렬 작업은 `.worktrees/<이름>`(git에서 제외)에서 한다. 서버는 `local` 프로파일로 8080이 아닌 비어 있는 포트에 띄우고, 앱은 `-PapiBaseUrl=http://10.0.2.2:<포트>/`로 빌드한다.
- **서버 이식 완료(2026-11-14):** 사용자, 장소, 샘플 데이터를 팀 저장소 스택(Kotlin)으로 옮겨 `C:\dev\bbolbbol\server`의 `port/integration`에서 합쳤다. 테스트 170개 통과. 보고서는 `docs/reports/port-1-server.md`. 팀 저장소에는 올리지 않았다. Java `backend/`는 참고용이다.
- 에뮬레이터는 사용자가 다른 프로젝트 확인을 위해 종료를 요청해서 **꺼 둔 상태**다. 사용자가 "써도 돼"라고 하기 전에는 켜지 않는다.
- **2단계 완료(2026-11-14):** 서버(`C:\dev\bbolbbol\server`의 `port/integration`)에 도착 인증, 포인트(내 포인트, 내역, 활동 일지), 추천(CURATED)을 추가했고 review(조건부 통과)의 지적을 수정했다. 서버 테스트 226개 통과. 앱(`feat/2-integration`)에 도착 인증, 포인트 요약, 포인트 내역, 활동 일지, 추천 화면을 추가했고 단위 테스트 48개와 빌드 통과. 보고서는 `docs/reports/stage-2.md`. 앱 2단계 화면은 에뮬레이터에서 확인하지 못했다.
- **3단계:** 쟁점 2~8에 대한 사용자 결정이 없어 구현할 API가 없다. 남은 API(통계, 랭킹, 사진 업로드, 이메일 인증, 재발급, 탈퇴, 카카오, 온보딩)는 최종 보고서에 목록으로 남긴다.
- **UI 개편 완료:** 프로토타입 이미지(`docs/UI 프로토타입/`)에 맞춰 보라색 디자인, 하단 탭 4개, 홈, 랭킹(준비 중), 마이를 반영했다(`feat/2-integration`에 병합). 앱 단위 테스트 57개 통과. 에뮬레이터로 확인하지 못했다.
- **최종 보고서 작성 완료:** `docs/reports/final-report.md`(구현한 기능, 사용 기술, Notion과 달라진 점, 구현하지 않은 API 14개, 미검증, 사용자 결정 목록).
- **현재 상태(2026-11-14): 구현과 검증 완료.** 명세 API 28개를 모두 구현했다(서버 H2 483개 통과, 실제 PostgreSQL 467개와 Redis 통합 17개 통과, 앱 단위 161개 debug / 160개 release). 에뮬레이터 E2E(`docs/reports/stage-3-e2e.md`)에서 찾은 버그는 `feat/6-fix`에서 수정했다(`docs/reports/stage-3-fix.md`). 남은 것은 재검증(stage-3-recheck) 결과 반영과 선택 후속 작업(낮은 등급 앱 개선, 미검증 항목, 배포 준비, 팀 저장소 PR)이다. 에뮬레이터는 위 "꺼 둔 상태" 문장보다 이후 상황이 우선이며, 사용 여부는 사용자 지시를 따른다.
- (이전 계획) 사용자의 결정(쟁점 2~8, 팀 저장소에 올릴 시점, 에뮬레이터 재사용 허락)을 받으면 이어서 진행한다. 커밋할 때는 `git add -A`를 쓰지 말고 파일을 지정한다(사용자가 `docs/UI 프로토타입` 등에 직접 넣는 파일이 섞이지 않게). 에이전트에는 `name`으로 이름표를 붙인다.
- 사용자가 "끝까지 진행"을 지시했다. push, PR, 병합 금지와 쟁점 2~8 API 보류는 그대로다. 앱 프로젝트 생성(Kotlin + Compose + Retrofit)은 1단계에 포함해도 된다는 승인을 받았다. 마지막에 `docs/reports/final-report.md`(구현 기능, 사용 기술, Notion과 달라진 점)를 쓴다.
- 결정 대기(가정으로 진행 중): COMMON404/405/415/500 유지 여부, 성공 응답 기본 message 문구, H2 `runtimeOnly` 분리(배포 전), 0-3·0-4 보고서의 OPEN 항목들.
- 새 세션은 `git branch --show-current`와 `git log --oneline`으로 현재 위치를 확인한 뒤 이 목록의 다음 단계부터 이어간다. 진행 중인 에이전트가 있는지(`git status`가 깨끗한지)도 확인한다.
