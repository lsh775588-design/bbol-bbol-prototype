# 0-3단계 보고서: 인프라, 설정, ERD 초안

- 일자: 2026-11-12
- 담당: db 에이전트, 오케스트레이터가 검증
- 브랜치: `feat/0-infra` (`feat/0-skeleton`에서 분기, 로컬 전용, 에이전트 커밋 2개)

## 한 일
- `docker-compose.yml`을 작성했다.
  - MySQL 8: 호스트 포트 3307, utf8mb4, `TZ=Asia/Seoul`, `--default-time-zone=+09:00`, 네임드 볼륨, healthcheck.
  - Redis 7: 호스트 포트 6380, `--appendonly yes`, 네임드 볼륨, healthcheck.
  - `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`는 `${VAR:?}`로 필수 처리했다. 비밀번호 기본값은 없다.
- `application.yml`에 `jwt.secret`, `jwt.access-expiration`, `mail.*`, `kakao.rest-api-key`, `storage.local.path`를 환경변수 기반으로 추가했다. 비밀값 기본값은 모두 빈 값이다.
- `application-local.yml`을 추가했다. H2 인메모리(MySQL 모드)를 쓰고 Redis가 없어도 기동한다. DB 없이 `bootRun`으로 확인하는 용도다.
- `application-mysql.yml`을 추가했다. `ddl-auto: validate`와 환경변수 안내만 있다.
- `.env.example`의 이름을 설정과 일치시켰다. 값은 비워 두었다.
- `common.storage.PhotoStorage` 인터페이스를 선언했다(save, delete, urlFor). 구현은 2단계다.
- `docs/erd.md`에 Mermaid ERD, 테이블별 메모, 테이블 소유 도메인, Redis 키와 TTL, OPEN 쟁점 요약을 썼다. 엔티티와 마이그레이션은 만들지 않았다.

## 바뀐 파일
- 새로 만든 파일: `docker-compose.yml`, `docs/erd.md`, `backend/src/main/resources/application-local.yml`, `backend/src/main/resources/application-mysql.yml`, `backend/src/main/java/com/bbolbbol/common/storage/PhotoStorage.java`
- 수정한 파일: `.env.example`, `backend/src/main/resources/application.yml`, `backend/build.gradle`
- `backend/build.gradle`: H2를 `testRuntimeOnly`에서 `runtimeOnly`로 바꿨다. local 프로파일이 실행할 때 H2가 필요해서다. 운영 jar에도 H2가 들어가므로 배포 전에 분리해야 한다.

## 테스트 결과
| 항목 | 결과 |
|---|---|
| `./gradlew clean test` | 통과. 테스트 2개. 에이전트가 한 번, 오케스트레이터가 다시 한 번 실행했다. |
| `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` + `curl /health` | 통과. 3.6초에 기동했고 `{"success":true,"code":"COMMON200","message":"요청이 성공했습니다.","data":{"status":"UP"}}`를 받았다(에이전트 실행 결과). 오케스트레이터는 재실행하지 않았다. |
| 앱 종료 | 에이전트가 자기가 띄운 프로세스만 `taskkill`로 종료했다. 확인 시점에 8080은 LISTEN이 아니었다(TIME_WAIT만 남음). |

## 오케스트레이터 검증
- 에이전트 커밋 2개의 작성자는 이세현이고 Claude 관련 문구와 trailer는 없다. `git status`는 깨끗하다.
- 추적 파일에 `.env`가 없고, 설정 파일에서 비밀값 하드코딩은 찾지 못했다.
- `docker-compose.yml`의 필수 변수 `${VAR:?}` 3개와 포트 매핑(3307, 6380)을 확인했다.
- `docs/erd.md`는 167줄이고 OPEN 쟁점 표시와 소유 도메인 구분이 있다. 내용을 전부 검토하지는 않았고 구조만 확인했다.
- java.exe 프로세스가 하나 남아 있다. 에이전트는 별개의 다른 java.exe(PID 88136)를 보고했는데, 확인 시점에는 PID 22600 하나뿐이었다. Gradle 데몬일 가능성이 높고, 어느 쪽도 종료하지 않았다.

## 내가 결정해야 할 것 (OPEN)
쟁점 2~8은 에이전트가 결정하지 않았고 ERD에 OPEN으로 표시했다. 구조는 검토 문서의 제안 쪽으로 가정해 그렸다.
1. verificationToken의 Redis TTL(명세에 없음).
2. 탈퇴 시 email UNIQUE 처리. 이메일을 NULL로 비울지, 제외 조건을 둘지. 즉시 재가입이 가능해야 한다.
3. 관심 카테고리를 `user_interests` 별도 테이블로 둘지. 초안은 별도 테이블이고 소유는 user다(명세의 "최대 3개" 때문에 에이전트가 추가했다).
4. 재방문 completion에도 사진 업로드를 허용할지.
5. 로그인 시도 횟수 제한을 둘지.
6. H2를 `runtimeOnly`로 바꾼 것을 배포 전에 분리할지(이번 단계에서 필요해서 바꿨다).
7. Redis 키 이름은 초안이고 구현 단계에서 확정한다.
8. 이전 단계에서 이어진 항목: `COMMON500` 유지, 성공 응답 기본 message 문구, `.gitattributes`(LF 고정) 추가 여부.

## 미검증
- `docker-compose.yml`: Docker가 없어서 `docker compose config`와 기동을 못 했다. 문법은 눈으로만 확인했다. 헬스체크의 `$$MYSQL_ROOT_PASSWORD` 이스케이프와 `${VAR:?}` 동작은 Docker 설치 후 확인해야 한다.
- `application-mysql.yml`과 실제 MySQL, Redis 연결.
- Redis를 쓰는 코드 경로(아직 없음).
- Mermaid ERD 렌더링.
