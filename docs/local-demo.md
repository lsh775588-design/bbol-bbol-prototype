# 로컬 데모 실행 (local 프로파일)

카카오 키, MySQL, Redis 없이 H2(메모리)로 서버를 띄우고 샘플 데이터를 확인한다. 서버를 끄면 데이터는 사라지고 다시 켜면 시드가 새로 들어간다.

## 서버 시작
```
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
- 기본 포트 8080. 에뮬레이터 앱은 `http://10.0.2.2:8080`으로 접속한다.
- 시드는 `backend/src/main/resources/db/seed-local/`(V900+)이며 local 프로파일에서만 로드된다. test, MySQL, 운영에는 들어가지 않는다.

## 데모 계정
공통 비밀번호(개발용 가짜 값): `demo1234!`

| 이메일 | 상태 |
| --- | --- |
| demo1@example.com | ACTIVE, 닉네임 `뽈뽈이`, 온보딩 완료 (id 1, local 고정 userId와 같음) |
| demo2@example.com | ACTIVE, 닉네임 없음, 온보딩 전(isNewUser=true) |
| demo3@example.com | WITHDRAWN. 로그인하면 401 `AUTH4011` |
| demo4@example.com | ACTIVE, 닉네임 `종로탐험가`, 온보딩 완료 |

## 회원가입 확인용 개발 토큰
signup의 `verificationToken`에 `<고정값>:<가입할 이메일>`을 넣는다. 고정값은 `application-local.yml`의 `bbolbbol.local.verification-token`(개발용 가짜 값 `DEV-ONLY-FAKE-verification-token`)이다.
예: `DEV-ONLY-FAKE-verification-token:new1@example.com`

## 샘플 장소
종로구 장소 약 33곳(궁, 북촌, 인사동, 시장, 박물관, 카페 등), 그중 12곳이 뽈뽈 추천(CURATED). `kakao_place_id`는 `SAMPLE-NNN`이다. 좌표는 대략값이라 팀 검토가 필요하다.

## 에뮬레이터 위치
경복궁 근처로 맞추려면:
```
adb emu geo fix 126.9780 37.5796
```
(경도, 위도 순서)

## 서버 종료
bootRun을 띄운 터미널에서 Ctrl+C. 백그라운드로 띄웠다면 해당 프로세스만 종료한다. 포트 확인은 `netstat -ano | grep :8080`으로 PID를 보고 그 PID만 `taskkill //PID <pid> //F` 한다. 다른 java 프로세스를 일괄 종료하지 않는다.
