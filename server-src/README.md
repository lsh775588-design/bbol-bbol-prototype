# 🐾 뽈뽈(BbolBbol) 서버

종로구 장소를 퀘스트로 방문하고 인증하는 앱 **뽈뽈**의 백엔드 서버입니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 / 프레임워크 | Kotlin 2.3, Spring Boot 4.1 (JDK 21) |
| DB | PostgreSQL 17 + PostGIS 3.5 (위치 데이터) |
| 캐시 | Redis 7 (refresh token, 이메일 인증 코드, 랭킹 캐시) |
| 인증 | Spring Security + JWT (jjwt) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 빌드 | Gradle (Kotlin DSL) |

## 로컬에서 실행하기

### 1. 준비물
- **JDK 21**: 없어도 Gradle이 자동으로 내려받습니다.
- **Docker**: [Docker Desktop](https://www.docker.com/products/docker-desktop/) 또는 [OrbStack](https://orbstack.dev/)

### 2. 환경 변수 파일 만들기
```bash
cp .env.example .env
```
`.env`의 필수 값을 채웁니다. (`.env`는 git에 올라가지 않습니다)
- `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`: 백엔드 팀원에게 받으세요.

### 3. DB와 Redis 띄우기
```bash
docker compose up -d
```

### 4. 서버 실행
IntelliJ에서 `ServerApplication.kt`의 ▶ 버튼을 누르거나 터미널에서 실행합니다.
```bash
./gradlew bootRun
```

### 5. 확인
- 서버 상태: http://localhost:8080/health
- Swagger UI: http://localhost:8080/swagger-ui.html

### 환경 변수
docker compose와 Spring 서버 모두 프로젝트 루트의 `.env`를 자동으로 읽습니다. 설정할 수 있는 값은 `.env.example`을 참고하세요.

### 로컬(Docker 없이) 실행
Docker, PostgreSQL, Redis 없이 H2 인메모리 DB로 서버를 띄울 수 있습니다. `.env`도 필요 없습니다.
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
- DB는 H2(PostgreSQL 호환 모드)이고 서버를 끄면 데이터가 사라집니다. 테이블은 Flyway(`db/migration`)가 만들고, 개발용 시드는 `db/seed-local`에 둡니다.
- Redis는 기동할 때 연결하지 않아서 없어도 서버는 뜹니다. Redis가 필요한 기능만 실패합니다.
- **DEV ONLY**: 토큰이 없으면 userId 1로 인증됩니다(`LocalFixedUserFilter`). `local` 프로파일에만 있습니다.
- 샘플 데이터(종로구 장소, 데모 계정)와 에뮬레이터 연동은 [docs/local-demo.md](docs/local-demo.md)를 보세요.
- 스키마는 Flyway가 관리하므로 `ddl-auto` 기본값은 `validate`입니다. 마이그레이션 규칙은 `src/main/resources/db/migration/README.md`를 보세요.

## 패키지 구조

**도메인별로 패키지를 나누고, 각 도메인 안에서 계층을 나눕니다.**
외부 서비스와 연동하는 부분(파일 저장소, 메일, 카카오)만 `global/infra`에 인터페이스로 분리해서, 구현을 바꿔도 서비스 코드는 그대로 둘 수 있게 합니다.

```
com.bbolbbol.server
├── domain/                     # 기능별 도메인
│   ├── health/                 # 서버 상태 확인 (/health)
│   └── user/                   # (예시) 새 도메인은 이 모양으로 추가
│       ├── controller/         #   UserController       요청/응답 처리
│       ├── service/            #   UserService          비즈니스 로직
│       ├── repository/         #   UserRepository       DB 접근 (Spring Data JPA)
│       ├── entity/             #   User                 JPA 엔티티
│       ├── dto/                #   SignupRequest, UserResponse
│       └── exception/          #   UserErrorCode        도메인 에러 코드
│
└── global/                     # 모든 도메인이 함께 쓰는 코드
    ├── common/                 # ApiResponse(공통 응답), BaseTimeEntity
    ├── config/                 # Security, JPA, Swagger, Clock 설정
    ├── error/                  # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── security/               # JWT 발급/검증, 인증 필터, 401/403 응답
    └── infra/                  # 외부 연동 (인터페이스 + 구현)
        ├── storage/            #   FileStorage → LocalFileStorage (배포 시 S3 추가)
        └── mail/               #   EmailSender → SmtpEmailSender
```

## 테스트
```bash
./gradlew test
```
