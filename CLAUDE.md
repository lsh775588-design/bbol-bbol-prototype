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
- 병렬 작업은 한 번에 최대 2개. 각 에이전트는 별도 git worktree와 브랜치에서 작업하고, 같은 파일을 둘이 건드리지 않게 한다.
- 테이블 하나에 소유자 한 명. 다른 도메인의 repository를 직접 쓰지 말고 서비스 메서드를 호출한다.
- 미결정 쟁점(검토 문서의 쟁점 2~8)이 걸린 API는 구현하지 말고 목록으로 보고한다. 단 가입의 verificationToken 검증은 인터페이스로 분리해 먼저 구현해도 된다.
- 명세와 다르게 만들어야 할 때는 임의로 바꾸지 말고 사용자에게 묻는다. 명세의 불규칙한 에러 코드 번호도 그대로 옮긴다.
- 비밀값(JWT 키, Gmail, 카카오 키, DB 비밀번호)은 코드와 문서와 로그에 쓰지 않고 환경변수로만 쓴다. 값을 출력하지 말고 필요하면 파일 이름과 줄 번호만 말한다.
- 에뮬레이터나 실기기 확인은 사용자가 한다. 사용자가 "테스트 끝났다"고 하면 그다음 작업으로 넘어간다.
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
- 0-1 repo 뼈대 완료(main). 0-2 뼈대, 공통 응답, 에러 코드 완료(`feat/0-skeleton`).
- 0-3 인프라(`feat/0-infra`), 0-4 보안 기반, 0-5 테스트, 0-6 review가 남았다.
- 결정 대기: COMMON500 유지 여부, 성공 응답 기본 message 문구, `.gitattributes` 추가 여부.
