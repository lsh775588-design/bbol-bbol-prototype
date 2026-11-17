# 로컬 데모 실행 (local 프로파일)

Docker, PostgreSQL, Redis, 카카오 키 없이 H2(메모리, PostgreSQL 호환 모드)로 서버를 띄우고 샘플 데이터를 확인한다. 서버를 끄면 데이터는 사라지고 다시 켜면 시드가 새로 들어간다.

## 서버 시작
```
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
# 포트를 바꾸려면
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --args='--server.port=8099'
```
- 기본 포트 8080. 에뮬레이터 앱은 `http://10.0.2.2:8080`(바꿨다면 해당 포트)으로 접속한다.
- 시드는 `src/main/resources/db/seed-local/`(V900+)이며 `local` 프로파일에서만 Flyway가 읽는다. test와 실제 DB에는 들어가지 않는다.
- local은 H2 DB 이름이 `bbolbbol-local`, test는 `bbolbbol-test`로 달라서 시드가 테스트로 새지 않는다.

## 데모 계정
공통 비밀번호(개발용 가짜 값): `demo1234!`

| 이메일 | 상태 |
| --- | --- |
| demo1@example.com | ACTIVE, 닉네임 `뽈뽈이`, 온보딩 완료 (id 1, local 고정 userId와 같음) |
| demo2@example.com | ACTIVE, 닉네임 없음, 온보딩 전 |
| demo3@example.com | WITHDRAWN. 로그인하면 401 `AUTH4011` |
| demo4@example.com | ACTIVE, 닉네임 `종로탐험가`, 온보딩 완료 |

로그인: `POST /auth/login` `{"email":"demo1@example.com","password":"demo1234!"}`

## 회원가입 확인용 개발 토큰
signup의 `verificationToken`에 `<고정값>:<가입할 이메일>`을 넣는다. 고정값은 `application-local.yml`의 `bbolbbol.local.verification-token`(개발용 가짜 값 `DEV-ONLY-FAKE-verification-token`)이다.
예: `DEV-ONLY-FAKE-verification-token:new1@example.com`

## 샘플 장소
종로구 장소 33곳(궁, 북촌, 인사동, 시장, 박물관, 카페 등), 그중 12곳이 뽈뽈 추천(CURATED). `kakao_place_id`는 `SAMPLE-NNN`, URL은 example.com 가짜 값이다. **좌표와 주소는 대략값이라 팀 검토가 필요하다**(인증 반경 판정에 쓰기 전에 확인).

## 에뮬레이터 위치
경복궁 근처로 맞추려면(경도, 위도 순서):
```
adb emu geo fix 126.9780 37.5796
```

## 서버 종료
bootRun을 띄운 터미널에서 Ctrl+C. 백그라운드로 띄웠다면 해당 프로세스만 종료한다. `netstat -ano | grep :8080`으로 PID를 보고 그 PID만 `taskkill //PID <pid> //F` 한다. 다른 java 프로세스를 일괄 종료하지 않는다.

## 이메일 인증, 카카오 (local 프로파일 개발용)
- 메일은 보내지 않고 서버 로그에 남는다(`LoggingEmailSender`). 코드는 `[LOCAL MAIL] to=... body=...` 줄에서 읽는다.
  `POST /auth/email-verifications` -> 로그의 6자리 코드 -> `POST /auth/email-verifications/confirm` -> 받은 `verificationToken`으로 `/auth/signup` 또는 `/auth/password-reset`.
  위 고정 개발 토큰(`<고정값>:<이메일>`)도 계속 가입에 쓸 수 있다.
- 카카오는 가짜(`FakeKakaoClient`)다. `POST /auth/kakao`의 `kakaoAccessToken`에 `DEV-KAKAO|<kakaoId>|<이메일, 비우면 이메일 없음>|<카카오 닉네임>` 형식을 넣는다.
  예: `DEV-KAKAO|1001|kakao1@example.com|뽈뽈 탐험가!`. `DEV-KAKAO-EXPIRED`는 KAKAO401, `DEV-KAKAO-DOWN`은 KAKAO502.
- 주의: local에서는 유효한 토큰이 없으면 고정 사용자(id 1, demo1)로 인증된다. 토큰 없이(또는 잘못된 토큰으로) `DELETE /users/me`를 부르면 demo1이 탈퇴 처리된다.
- 로그인은 같은 IP에서 1분에 10번 실패하면 429 `AUTH429`다. 재발급은 옛 refresh token을 20초 동안 한 번 더 받아 준다.
