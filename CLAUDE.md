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
- local과 test는 H2(PostgreSQL 모드)를 쓰고, 실제 PostgreSQL 17/Redis는 `BBOL_IT_PG`/`BBOL_IT_REDIS` opt-in 테스트로 검증했다. PostGIS 타입은 쓰지 않고 위경도 DOUBLE 컬럼을 쓴다(deviation).
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
- Docker Desktop은 설치돼 있다(CLI 전체 경로 `C:/Users/f/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe`, PATH에는 없음). 사용자가 엔진을 켜야 한다. compose 기동은 pg-verify에서 실검증했다.
- 3306은 다른 MySQL이 쓰고 있다. 이 프로젝트의 호스트 포트는 MySQL 3307, Redis 6380이다.
- DB 없이 확인하려면 `local` 프로파일(H2)을 쓴다. 테스트는 `application-test.yml`(H2)로 돈다.
- Python은 동작하지 않는다. 스크립트가 필요하면 node를 쓴다.

## 현재 진행 상황 (새 세션은 여기부터 읽는다)

### 한눈에 (2026-11-16 기준)
- **프로토타입 구현과 검증 완료.** 명세 API 28개(+/health) 구현, Kotlin 앱(Compose) 전 화면 구현, 에뮬레이터 E2E와 재검증 통과.
- **작업 브랜치: `feat/2-integration`** (앱 + 문서 통합 브랜치, main에는 병합하지 않음). 서버는 `C:\dev\bbolbbol\server`(팀 저장소 복제본, 브랜치 `port/integration`)에 있다. Java `backend/`는 참고용이다.
- 테스트: 서버 H2 483개 통과(전체 504, opt-in 21 건너뜀), 실제 PostgreSQL 467개, Redis 통합 17개 / 앱 단위 debug 183개, release 182개 통과.
- **GitHub와 팀 저장소 origin에는 올리지 않는다.** 로컬 전용. 사용자가 "올려줘"라고 할 때만 push/PR, 병합은 하지 않는다.
- 최종 정리 문서: `docs/reports/final-report.md`(구현 기능, 기술, Notion과 달라진 점, 사용자 결정 A1~A8, 주의점). 사용자 결정 문서는 Claude 문서 "뽈뽈 사용자 결정·준비 사항"(https://claude.ai/artifact/5dTCTDe98WT8QhG1gE6wH1).

### 3단계 이후에 한 일 (최신순)
- **홈 "진행 중인 퀘스트"**(`feat/10-home-ongoing`, 병합됨): 받은 퀘스트를 카드로 표시, 포기, 빈 상태. 보고서 `docs/reports/stage-5-home.md`, 스크린샷 `docs/screenshots/stage-5/`.
- **지도 개선과 "퀘스트 받기" 단계**(`feat/9-map-quest`, 병합됨): 흐리게 처리한 배경 지도, 카테고리 핀, 내 위치 점, 핀 탭 → 퀘스트 받기 → 도착 인증. 받은 퀘스트는 앱 안(`QuestBook`, SharedPreferences, 사용자·장소·KST 날짜별, 자정 만료)에만 저장하고 서버는 모른다. 이는 Notion 명세와 다르다(`docs/reports/stage-3-map.md`). 스크린샷 `docs/screenshots/stage-4/`.
- **에뮬레이터 재검증 2회**(`stage-3-recheck.md`, `stage-3-recheck2.md`)와 앱 버그 수정(`stage-3-fix.md`): 가입·카카오 직후 온보딩 이동(NavHost 시작 화면 고정), 홈·마이 재진입 갱신 등.
- **에뮬레이터 E2E**(`stage-3-e2e.md`, 스크린샷 `docs/screenshots/stage-3/`), PostgreSQL/Redis 실검증, 보안 점검과 수정.
- 0~2단계 결과는 `docs/reports/stage-0-*.md`, `stage-1-*.md`, `stage-2.md`, `port-1-server.md`에 있다.

### 남은 일 (선택)
- 서버에 퀘스트 받기 저장(수락·목록·포기 API와 테이블) — 지금은 기기 안에만 저장돼 재설치·다른 기기에서는 사라진다. 사용자가 원하면 진행.
- 낮은 등급 앱 개선: 마이에서 홈 복귀 직후 잠깐 0P로 보임, 통계 타일 높이, 랭킹 단상 쏠림, 둘러보기 중심부 핀 겹침, 내 위치 연속 추적 없음.
- 지도 품질을 더 올리려면 카카오맵 SDK(사용자가 카카오 개발자 앱 등록·네이티브 키 발급 필요).
- 미검증: 실제 카카오 SDK 로그인, 실제 SMTP(Gmail) 발송, R8(release 난독화), 실기기와 Android 8~10, 네트워크 끊김 화면, 반경 밖 도착 인증 화면.
- 결정 대기(현재 동작 유지): COMMON404/405/415/500 유지 여부, 성공 응답 기본 message 문구, H2 `runtimeOnly` 분리(배포 전), 팀 저장소에 올릴 시점.

### 데모 실행 방법 (이 PC)
1. 서버: `C:\dev\bbolbbol\server`에서 `SPRING_PROFILES_ACTIVE=local BBOLBBOL_LOCAL_FIXED_USER_ID= ./gradlew bootRun --args='--server.port=8120'`(백그라운드). 서버를 켜 둔 동안 그 폴더의 `./gradlew --stop`을 쓰지 않는다(서버가 같이 꺼진다). 재시작하면 데이터와 로그인이 초기화된다. 이메일 인증 코드는 서버 로그의 `[LOCAL MAIL]` 줄에 찍힌다.
2. 에뮬레이터: AVD `Medium_Phone`(`emulator -avd Medium_Phone`, adb `emulator-5554`). 위치는 `adb emu geo fix 126.9780 37.5796`(경복궁).
3. 앱 빌드와 설치: `cd app && ./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8120/` 후 `adb install -r app/build/outputs/apk/debug/app-debug.apk`. `app/local.properties`에 `sdk.dir=C:/Users/f/AppData/Local/Android/Sdk`(슬래시) 필요.
4. 데모 계정: `demo1@example.com`(뽈뽈이, 온보딩 완료), `demo4@example.com`(종로탐험가), `demo2@example.com`(온보딩 전), 공통 비밀번호 `demo1234!`. 자세한 내용은 `server/docs/local-demo.md`.
- 메모리가 빠듯하다: Gradle은 한 번에 하나, 에이전트가 모두 쉬는 때만 데몬을 끈다.

### 새 세션 시작 절차
1. `git branch --show-current`, `git log --oneline -10`, `git status`로 위치와 진행 중인 작업을 확인한다(`docs/UI 프로토타입` 파일 변경은 사용자 것이니 건드리지 않는다).
2. `docs/reports/final-report.md`와 이 절을 읽는다. 8120 포트 서버와 에뮬레이터가 켜져 있는지 확인한다(`curl localhost:8120/health`, `adb devices`).
3. 커밋할 때는 `git add -A`를 쓰지 말고 파일을 지정한다. 에이전트에는 `name`을 붙이고, 동시에 3개까지만 돌린다.
