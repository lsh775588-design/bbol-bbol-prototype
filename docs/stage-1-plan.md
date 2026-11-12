# 1단계 계획 (회원 + 장소 + 앱 뼈대)

사용자 지시: "끝까지 계속 진행". 앱 프로젝트 생성 포함 승인됨. push/PR/병합 금지, 쟁점 2~8 API 보류, 명세 임의 변경 금지는 그대로.

## 범위
- 구현: POST /auth/signup, POST /auth/login, GET /users/me, PATCH /users/me, GET /auth/email-availability, GET /auth/nickname-availability, GET /place-categories, GET /places, GET /places/{id}, 카카오 종로구 장소 수집 스크립트.
- 보류(쟁점 대기): email-verifications(2), email-verifications/confirm(2), password-reset(2), reissue(3), logout(3), DELETE /users/me(4), kakao(1,5), onboarding(5). signup의 verificationToken 검증은 인터페이스(`VerificationTokenVerifier`)로 분리하고 기본 구현은 명세의 형태만 검증하지 않는다(쟁점 2 확정 전이므로 가짜 통과 구현 금지: 기본 구현은 항상 EMAIL4003 거부, 테스트용 구현만 통과).
  - 주의: 위 기본 구현이면 local에서 signup을 끝까지 확인할 수 없다. local 프로파일에서만 `LocalVerificationTokenVerifier`(개발 전용, 고정 토큰 허용)를 두는 방안을 에이전트가 제안하되 운영 프로파일에 로드되지 않게 한다.
- 앱: Kotlin + Compose + Retrofit 프로젝트 생성, 로그인/회원가입/내 정보/지도(종로구 장소 목록) 화면, 서버 주소 10.0.2.2:8080, 개발용 HTTP 허용.

## 선행(순차, 병렬 전)
1. DB: Flyway 추가(build.gradle, application*.yml), 마이그레이션 번호 구역 분리(user: V1xx, place: V2xx). 소유자가 자기 테이블 마이그레이션을 만든다.
2. 테이블 소유: users, user_interests = user. places, place_categories = place.

## 병렬 라운드 (최대 2개, 각각 별도 git worktree와 브랜치)
- 라운드 1
  - backend-user (`feat/1-user`): auth + user 패키지. 엔티티, 마이그레이션(V1xx), signup/login, users/me GET/PATCH, 이메일/닉네임 중복 확인, BCrypt, JWT 발급(access 1시간), `UserStatusChecker` 구현(탈퇴 상태), 금칙어 필터(목록은 설정 파일), 닉네임 30일 변경 제한. 응답은 `ApiResponse.createdResponse`로 201.
  - backend-place (`feat/1-place`): place 패키지. 엔티티, 마이그레이션(V2xx), place-categories, places(주변 검색, 완료 여부는 completion 도메인 전이므로 필드만 두고 false), places/{id}, 카카오 수집 스크립트(`scripts/` 또는 gradle task, 키는 환경변수 KAKAO_REST_API_KEY, 키가 없으면 실행 불가를 명확히 안내).
- 라운드 2
  - app (`feat/1-app`): 앱 프로젝트 생성과 화면. backend 라운드 1 병합본(통합 브랜치) 위에서 연동.
  - db (`feat/1-seed`): 샘플 데이터(종로구 장소 시드, 테스트 계정)를 local 프로파일 전용 데이터로. 카카오 키 없이도 쓰도록 소량의 수기 시드(경복궁 등) 포함.
- 통합: 라운드 사이에 `feat/1-integration`에서 두 브랜치를 병합(로컬 전용)하고 테스트를 돌린다. main에는 병합하지 않는다.

## 종료 조건
- 모든 테스트 통과, review 에이전트 통과(또는 조건부 항목 처리), 에뮬레이터 스크린샷 `docs/screenshots/stage-1/`, 보고서 `docs/reports/stage-1-*.md`.
