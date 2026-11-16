# BbolBbol (뽈뽈)

서울 종로구 위치 기반 퀘스트 앱 프로토타입. 장소가 퀘스트이고, 퀘스트를 받아 도착을 인증하면 포인트를 받는다.

**현재 상태: 구현과 에뮬레이터 검증 완료(로컬 전용, GitHub에는 올리지 않음).** 진행 상황과 다음 할 일은 `CLAUDE.md`의 "현재 진행 상황", 결과 요약은 `docs/reports/final-report.md`에 있다.

## 구성
- `app/` Kotlin Android 앱(Jetpack Compose, Retrofit, osmdroid 지도)
- `server/` 서버(팀 저장소 스택: Kotlin, Spring Boot 4.1, JPA, Flyway, H2/PostgreSQL, Redis). 별도 git 저장소(복제본)
- `backend/` 초기 Java 서버. 참고용
- `docs/` 설계·보고서(`docs/reports/`), UI 프로토타입 이미지, 에뮬레이터 스크린샷(`docs/screenshots/`)

## 실행(요약)
1. 서버: `cd server && SPRING_PROFILES_ACTIVE=local BBOLBBOL_LOCAL_FIXED_USER_ID= ./gradlew bootRun --args='--server.port=8120'`
2. 앱: `cd app && ./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8120/` 후 에뮬레이터에 설치
3. 데모 계정 `demo1@example.com` / `demo1234!` (자세한 내용은 `server/docs/local-demo.md`, `CLAUDE.md`)

## 규칙
- 시간대 Asia/Seoul, 한 주는 월요일 시작
- 비밀값은 환경변수로만 사용
