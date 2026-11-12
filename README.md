# BbolBbol (뽈뽈)

서울 종로구 위치 기반 퀘스트 앱. 장소가 퀘스트이고, 도착을 인증하면 포인트를 받는다.

## 구성
- `backend/` Spring Boot 3.x, Java 21, Gradle, JPA, Spring Security + JWT, MySQL 8, Redis
- `app/` Kotlin Android, Jetpack Compose, Retrofit
- `docs/` 설계 문서(ERD 등)

## 규칙
- 시간대 Asia/Seoul, 한 주는 월요일 시작
- 비밀값은 환경변수로만 사용 (`.env.example` 참고, `.env`는 커밋하지 않음)
- 로컬 개발용 호스트 포트: MySQL 3307, Redis 6380
