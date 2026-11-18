# 뽈뽈(BbolBbol) API 명세서 (구현 기준)

이 문서는 **현재 구현된 서버 코드**(`server-src/src/main`의 컨트롤러, DTO, ErrorCode enum, 보안 설정)를 읽고 정리한 명세서다. 코드에 있는 것만 적었고, Notion 명세와 다른 점은 4절에 모았다. 코드와 이 문서가 어긋나면 코드가 맞다.

- 컨트롤러 기준 API 개수: 명세 API 28개 + `/health` + `/files/{directory}/{name}`
- 기준 코드: `server-src/src/main/kotlin/com/bbolbbol/server/`
- 근거 문서: `docs/reports/final-report.md`, `docs/reports/stage-1-*.md`, `docs/reports/stage-2.md`

---

## 1. 개요

### 1.1 기본 정보

| 항목 | 값 |
|---|---|
| 기본 주소(로컬) | `http://localhost:8120` (에뮬레이터에서는 `http://10.0.2.2:8120`) |
| 형식 | 요청과 응답 모두 JSON(UTF-8). 사진 업로드만 `multipart/form-data` |
| 시간대 | Asia/Seoul. 날짜 경계(오늘, 이번 주 월요일 시작, 이번 달)는 모두 한국 시간 기준 |
| 날짜 형식 | 쿼리의 날짜는 `yyyy-MM-dd`. 응답의 `LocalDateTime` 값은 시간대 표시 없는 한국 시간 ISO-8601(예: `2026-11-18T10:30:00`) |
| 주의 | 필드 `isHot`, `isCompleted`, `isFirstVisit`, `isMe`, `isNewUser`, `isMockLocation`은 JSON 키 이름이 코드에서 고정돼 있다 |

### 1.2 공통 응답

성공과 실패 모두 같은 모양이다.

```json
{
  "success": true,
  "code": "COMMON200",
  "message": "요청 성공",
  "data": { }
}
```

| 필드 | 설명 |
|---|---|
| `success` | 성공이면 `true`, 에러면 `false` |
| `code` | 성공은 `COMMON200`(조회와 일반 요청), `COMMON201`(생성). 에러는 3절의 코드 |
| `message` | 사람이 읽는 문구. API마다 성공 문구가 다르다(예: 로그인 "로그인에 성공했습니다.") |
| `data` | 응답 본문. 값이 없으면 `null`. 에러에서도 일부 코드는 `data`에 추가 정보를 담는다(`remainingAttempts`, `nextChangeableAt`, `errors`) |

`message` 문구는 안정적인 계약이 아니다. 앱은 `code`로 분기해야 한다.

### 1.3 인증 방식

- 헤더: `Authorization: Bearer {accessToken}`
- access token: JWT, 기본 유효 1시간(`JWT_EXPIRATION`). refresh token: JWT, 기본 유효 14일(`JWT_REFRESH_EXPIRATION`). 토큰의 subject가 userId이고 `type` 클레임으로 access와 refresh를 구분한다.
- refresh token은 **회전(rotation)** 한다. `POST /auth/reissue`를 부를 때마다 새 refresh token이 나오고, 서버는 사용자당 하나만 저장한다(해시만 저장). 다른 기기에서 로그인하면 이전 기기의 refresh token은 무효가 된다. 직전 refresh token은 약 20초 동안 한 번 더 받아 준다.
- 토큰이 없거나, 만료됐거나, 위조됐거나, **탈퇴한 사용자의 토큰이면** 인증이 필요한 모든 경로에서 `AUTH401`(HTTP 401)이다.
- 로그인했지만 권한이 없는 경우는 `AUTH403`(HTTP 403)이다. 현재 권한 구분은 없어 실제로 나오는 API는 없다.
- `local` 프로파일에서만 `bbolbbol.local.fixed-user-id`가 설정되면 토큰이 없어도 고정 사용자로 인증하는 개발용 필터가 켜진다(데모 실행에서는 값을 비운다). 배포 동작이 아니다.

### 1.4 인증이 필요 없는 경로

`SecurityConfig`의 `PUBLIC_PATHS` 기준이다. 그 밖의 모든 경로(없는 경로 포함)는 인증이 필요해서 토큰이 없으면 404가 아니라 `AUTH401`이 나온다.

| 경로 | 비고 |
|---|---|
| `/auth/**` | 단, **`/auth/logout`만 인증 필요** |
| `/health/**` | |
| `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` | local, test 프로파일에서만 켜진다 |
| `/actuator/health`, `/error` | |

### 1.5 검증 실패 응답 (`COMMON400`)

입력 검증에 실패하면 HTTP 400, `code`는 `COMMON400`이고 `data.errors`에 실패한 필드가 담긴다.

```json
{
  "success": false,
  "code": "COMMON400",
  "message": "입력값을 다시 확인해 주세요.",
  "data": {
    "errors": [
      { "field": "nickname", "reason": "닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있습니다." }
    ]
  }
}
```

`COMMON400`이 되는 경우: 본문 검증 실패, 쿼리 파라미터 누락, 쿼리와 경로 변수의 타입 변환 실패(`field`는 파라미터 이름, `reason`은 "형식이 올바르지 않습니다."), JSON을 읽을 수 없음(`field`가 `body`). 이후 표에서는 이 코드를 "검증 실패"로 줄여 쓴다.

### 1.6 생성 API는 실제 HTTP 201

다음 세 API는 `COMMON201`과 함께 **실제 HTTP 상태 201**을 돌려준다. 나머지 성공은 모두 200이다.

- `POST /auth/signup`
- `POST /places/{placeId}/completions`
- `POST /completions/{completionId}/photo`

### 1.7 페이지네이션(커서)

활동 일지와 포인트 내역은 최신순 커서 방식이다. 응답의 `nextCursor`(마지막 항목의 id, 다음이 없으면 `null`)와 `hasNext`를 쓰고, 다음 요청에 `cursor={nextCursor}`를 붙인다. `cursor`는 1 이상이다.

---

## 2. API 목록

표기: 인증 "필요"는 Bearer access token이 필요하다는 뜻이다. 에러 표에서 `AUTH401`(인증 실패)은 인증이 필요한 모든 API에 공통이라 각 표에는 적지 않고, 검증 실패(`COMMON400`)는 요청 값이 있는 API에 공통이다. 공통 에러(`COMMON404`, `COMMON405`, `COMMON415`, `COMMON500`)는 3.1절에 있다.

| 영역 | API |
|---|---|
| 회원 (2.1) | signup, login, kakao, 이메일 인증 코드 발송, 이메일 인증 코드 확인, password-reset, reissue, logout, 이메일 중복 확인, 닉네임 중복 확인, GET/PATCH/DELETE `/users/me`, GET/PATCH `/users/me/onboarding` (15개) |
| 지도·장소 (2.2) | `/place-categories`, `/places`, `/places/recommendations`, `/places/{placeId}` (4개) |
| 퀘스트 인증 (2.3) | 도착 인증, 사진 업로드, 활동 일지 (3개) |
| 보상 (2.4) | `/users/me/points`, `/users/me/point-histories` (2개) |
| 통계 (2.5) | `/users/me/stats` (1개) |
| 랭킹 (2.6) | `/rankings/users`, `/rankings/places`, `/rankings/streaks` (3개) |
| 기타 (2.7) | `/health`, `/files/{directory}/{name}` |

합계: 15 + 4 + 3 + 2 + 1 + 3 = 28개(명세 API) + 2개(기타).

### 2.1 회원

#### POST /auth/signup — 이메일 회원가입

- 인증: 불필요
- 요청 본문(JSON)

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `verificationToken` | string | 필수 | `purpose=SIGNUP`으로 발급받은 토큰. 이메일은 토큰에 묶여 있어 따로 보내지 않는다 |
| `password` | string | 필수 | 8~20자, 영문+숫자+특수문자 각 1개 이상, 공백 없는 ASCII |
| `nickname` | string | 필수 | 2~10자, 한글(완성형)/영문/숫자 |

- 성공: **HTTP 201**, `COMMON201`, message "회원가입에 성공했습니다."

| data 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | number | |
| `accessToken` | string | |
| `refreshToken` | string | |
| `isNewUser` | boolean | 가입 응답은 항상 `true` |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 검증 실패(비밀번호, 닉네임 형식 등) |
| EMAIL4003 | 400 | verificationToken이 없거나 만료됨, 또는 다른 용도의 토큰 |
| USER4001 | 400 | 닉네임에 금칙어 포함 |
| USER409 | 409 | 이미 가입된 이메일 |
| USER4092 | 409 | 이미 사용 중인 닉네임(대소문자 구분 없이 비교) |

가입이 성공하면 토큰이 소비돼 다시 쓸 수 없다. 가입에 실패하면(닉네임 중복 등) 토큰은 남아 있어 다시 시도할 수 있다.

#### POST /auth/login — 이메일 로그인

- 인증: 불필요
- 요청 본문: `email`(string, 필수), `password`(string, 필수)
- 성공: 200, message "로그인에 성공했습니다.". data는 signup과 같은 모양이고 `isNewUser`는 **온보딩을 아직 마치지 않았으면 `true`**

```json
{
  "success": true, "code": "COMMON200", "message": "로그인에 성공했습니다.",
  "data": { "userId": 1, "accessToken": "eyJ...", "refreshToken": "eyJ...", "isNewUser": false }
}
```

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 이메일 또는 비밀번호 누락 |
| AUTH4011 | 401 | 이메일 또는 비밀번호가 일치하지 않음(없는 이메일, 카카오 가입자, 탈퇴한 사용자도 같은 응답) |
| AUTH429 | 429 | 로그인 시도가 너무 많음(IP당 1분에 10번을 넘으면 맞는 비밀번호도 거절) |

#### POST /auth/kakao — 카카오 로그인(없으면 자동 가입)

- 인증: 불필요
- 요청 본문: `kakaoAccessToken`(string, 필수). 앱이 카카오 SDK로 받은 카카오 access token
- 성공: 200, message "로그인에 성공했습니다."

| data 필드 | 타입 | 설명 |
|---|---|---|
| `userId`, `accessToken`, `refreshToken` | | signup과 같음 |
| `isNewUser` | boolean | 온보딩을 마치지 않았으면 `true` |
| `suggestedNickname` | string 또는 null | 닉네임이 아직 없는 사용자에게 카카오 닉네임을 규칙에 맞게 다듬은 값. 다듬을 수 없거나 금칙어/중복이면 `null`. 키는 `null`이어도 내려간다 |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | `kakaoAccessToken` 누락 |
| KAKAO401 | 401 | 카카오 토큰이 만료되었거나 유효하지 않음 |
| USER4093 | 409 | 카카오 이메일이 이미 이메일 가입 계정으로 등록됨. 이메일로 로그인해야 함 |
| KAKAO502 | 502 | 카카오 서버 응답 실패 |

새 가입자는 닉네임이 없고 온보딩 전 상태다. 이메일 동의가 없으면 이메일 없이 가입한다.

#### POST /auth/email-verifications — 이메일 인증 코드 발송

- 인증: 불필요
- 요청 본문

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `email` | string | 필수 | 이메일 형식 |
| `purpose` | string | 필수 | `SIGNUP` 또는 `PASSWORD_RESET` |

- 성공: 200, message "인증 코드를 보냈습니다."

| data 필드 | 설명 |
|---|---|
| `expiresIn` | 코드 유효 시간(초), 600(10분) |
| `resendAvailableIn` | 재발송까지 기다릴 시간(초), 60 |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 이메일 형식 오류, purpose 값 오류 |
| USER409 | 409 | (`SIGNUP`만) 이미 가입된 이메일(카카오 가입 포함) |
| EMAIL429 | 429 | 재발송 제한: 같은 이메일에 1분에 1번, 하루 5번 |
| EMAIL502 | 502 | 메일 발송 실패(쿨다운과 일일 횟수는 되돌려진다) |

`PASSWORD_RESET`은 가입 여부와 상관없이 항상 같은 모양으로 성공한다(가입 여부를 응답으로 알 수 없게). 가입되지 않았거나 카카오로만 가입한 이메일로는 맞출 수 없는 코드가 저장된다.

#### POST /auth/email-verifications/confirm — 이메일 인증 코드 확인

- 인증: 불필요
- 요청 본문: `email`(필수, 이메일 형식), `purpose`(필수, `SIGNUP|PASSWORD_RESET`), `code`(필수, 숫자 6자리)
- 성공: 200, message "이메일 인증이 완료되었습니다."

| data 필드 | 설명 |
|---|---|
| `verificationToken` | 32바이트 난수(base64url, 패딩 없음). 이메일과 purpose에 묶여 있고 서버에는 해시만 저장된다 |
| `expiresIn` | 토큰 유효 시간(초), 1800(30분) |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 검증 실패(코드가 숫자 6자리가 아님 등) |
| EMAIL4001 | 400 | 인증 코드가 일치하지 않음. `data.remainingAttempts`에 남은 시도 횟수 |
| EMAIL4002 | 400 | 인증 코드가 없거나 만료됨, **5번째 시도가 틀려서** 코드가 무효가 됨(다시 발송해야 함), 5번 넘게 시도함 |
| EMAIL429 | 429 | 같은 IP의 확인 시도가 10분에 30번을 넘음 |

시도 횟수는 맞는 시도도 센다(5번째 시도까지는 맞으면 성공). 코드 확인에 성공하면 코드는 삭제된다.

#### POST /auth/password-reset — 비밀번호 재설정

- 인증: 불필요
- 요청 본문: `verificationToken`(필수, `purpose=PASSWORD_RESET`으로 받은 토큰), `newPassword`(필수, 비밀번호 규칙은 signup과 같음)
- 성공: 200, message "비밀번호가 변경되었습니다.", data는 `null`
- 부작용: 토큰은 한 번만 쓸 수 있다. 성공하면 해당 사용자의 refresh token이 지워져 다른 기기가 로그아웃된다(이미 발급된 access token은 만료까지 유효).

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 검증 실패(새 비밀번호 규칙 등) |
| EMAIL4003 | 400 | 토큰이 없거나 만료됨, 다른 용도의 토큰, 또는 토큰의 이메일이 이메일 가입자가 아님 |

#### POST /auth/reissue — 토큰 재발급

- 인증: 불필요(refresh token을 본문으로 보낸다)
- 요청 본문: `refreshToken`(string, 필수)
- 성공: 200, message "토큰이 재발급되었습니다."

| data 필드 | 설명 |
|---|---|
| `accessToken` | 새 access token |
| `refreshToken` | 새 refresh token(이전 것은 즉시 무효. 단 약 20초 유예) |

`userId`와 `isNewUser`는 내려가지 않는다.

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | `refreshToken` 누락 |
| AUTH4012 | 401 | refresh token이 없음, 만료, 위조, 유예(20초)가 지난 재사용, 다른 기기 로그인으로 교체됨, 탈퇴한 사용자 |

#### POST /auth/logout — 로그아웃

- 인증: **필요**(`/auth/**` 중 유일)
- 요청: 본문 없음
- 성공: 200, message "로그아웃되었습니다.", data `null`. 서버가 저장한 refresh token을 지운다(이미 발급된 access token은 만료까지 유효).
- 에러: AUTH401만

#### GET /auth/email-availability — 이메일 중복 확인

- 인증: 불필요
- 쿼리: `email`(필수, 이메일 형식)
- 성공: 200, `COMMON200`

| data 필드 | 설명 |
|---|---|
| `available` | 사용 가능 여부 |
| `reason` | 사용 가능하면 `null`. 아니면 `DUPLICATED_EMAIL`(이메일 가입 계정) 또는 `REGISTERED_WITH_KAKAO`(카카오 가입 계정) |

- 에러: COMMON400(누락, 형식 오류)

#### GET /auth/nickname-availability — 닉네임 중복 확인

- 인증: 불필요
- 쿼리: `nickname`(필수)
- 성공: 200. 형식이 틀려도 400이 아니라 `available: false`로 응답한다.

| data 필드 | 설명 |
|---|---|
| `available` | 사용 가능 여부 |
| `reason` | 사용 가능하면 `null`. 아니면 `INVALID_FORMAT`, `FORBIDDEN_WORD`, `DUPLICATED` |

- 에러: COMMON400(`nickname` 누락 또는 빈 값)

#### GET /users/me — 내 정보

- 인증: 필요
- 성공: 200, `COMMON200`

| data 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | number | |
| `email` | string 또는 null | 카카오 가입자가 이메일 동의를 안 했으면 `null` |
| `nickname` | string 또는 null | 온보딩 전 카카오 가입자는 `null` |
| `loginType` | string | `EMAIL` 또는 `KAKAO` |
| `onboardingCompleted` | boolean | |
| `createdAt` | datetime | 초 단위 |

- 에러: USER404(실제로는 필터가 먼저 AUTH401로 막는다. 3.2절 참고)

#### PATCH /users/me — 닉네임 변경

- 인증: 필요
- 요청 본문: `nickname`(필수, 2~10자 한글/영문/숫자)
- 성공: 200, message "닉네임이 변경되었습니다."

| data 필드 | 설명 |
|---|---|
| `nickname` | 변경된 닉네임 |
| `nextChangeableAt` | 다음에 바꿀 수 있는 시각(방금 변경 시각 + 30일) |

- 에러(검사 순서대로): COMMON400(형식) -> USER4001(금칙어) -> USER4002(마지막 변경 후 30일이 안 지남, `data.nextChangeableAt`) -> USER4092(중복. **현재 닉네임과 같은 값도 중복으로 본다**)

온보딩에서 처음 정하는 닉네임은 30일 제한에 포함되지 않는다.

#### DELETE /users/me — 회원 탈퇴

- 인증: 필요
- 성공: 200, message "회원 탈퇴가 완료되었습니다.", data `null`
- 동작: 개인정보(이메일, 닉네임 등)를 즉시 지우고, 관심 카테고리를 삭제하고, 모든 기기를 로그아웃하고(refresh token 삭제), 카카오 가입자는 카카오 연결을 끊는다. 인증 기록과 포인트는 통계용으로 남고, 인증 사진과 위치는 탈퇴 정리 리스너가 지운다. 탈퇴한 사용자는 랭킹에서 "탈퇴한 사용자"로 표시되거나 빠진다.
- 에러: USER404(실제로는 필터가 먼저 AUTH401로 막는다. 3.2절 참고)

#### GET /users/me/onboarding — 온보딩 정보 조회

- 인증: 필요
- 성공: 200, message "요청에 성공했습니다."

| data 필드 | 설명 |
|---|---|
| `ageGroup` | `TEENS`, `TWENTIES`, `THIRTIES`, `FORTIES`, `FIFTIES_PLUS`, `UNDISCLOSED`. 온보딩 전이면 `null` |
| `gender` | `MALE`, `FEMALE`, `UNDISCLOSED`. 온보딩 전이면 `null` |
| `interests` | 관심 카테고리 코드 배열(선택한 순서). 없으면 빈 배열 |
| `onboardingCompleted` | boolean |

#### PATCH /users/me/onboarding — 온보딩 저장

- 인증: 필요
- 요청 본문: 모든 필드가 선택이다(없는 필드는 바꾸지 않는다)

| 필드 | 타입 | 제약 |
|---|---|---|
| `nickname` | string | **카카오 가입자의 첫 온보딩에서만** 받는다(필수, 2~10자). 그 밖의 경우 값이 와도 무시한다 |
| `ageGroup` | string | `TEENS|TWENTIES|THIRTIES|FORTIES|FIFTIES_PLUS|UNDISCLOSED` |
| `gender` | string | `MALE|FEMALE|UNDISCLOSED` |
| `interests` | string 배열 | 최대 3개(중복은 하나로), 존재하는 카테고리 코드만. 오면 목록을 통째로 교체한다(빈 배열이면 전부 삭제) |

- 첫 온보딩(`onboardingCompleted=false`)에서는 `ageGroup`과 `gender`가 필수이고(이미 저장된 값이 있으면 생략 가능), 저장이 끝나면 `onboardingCompleted`가 `true`가 된다.
- 성공: 200, message "온보딩 정보가 저장되었습니다."

| data 필드 | 설명 |
|---|---|
| `nickname`, `ageGroup`, `gender`, `interests`, `onboardingCompleted` | 조회 응답에 `nickname`이 더해진 모양 |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 값 오류, 첫 온보딩의 ageGroup/gender/닉네임 누락, interests 4개 이상, 없는 카테고리 코드 |
| USER4001 | 400 | 닉네임에 금칙어 포함 |
| USER4092 | 409 | 이미 사용 중인 닉네임 |

### 2.2 지도·장소

모든 장소 API는 인증이 필요하다(`/place-categories` 포함). 거리 단위는 미터이고 `distance`는 반올림한 정수다.

#### GET /place-categories — 카테고리 목록

- 성공: 200

| data 필드 | 설명 |
|---|---|
| `categories[]` | `{ code, name, rewardPoint }` (카테고리 기본 점수). 등록 순서 |

#### GET /places — 주변 장소 조회

- 쿼리

| 파라미터 | 필수 | 기본 | 제약 |
|---|---|---|---|
| `lat` | 필수 | | -90 이상 90 이하 |
| `lng` | 필수 | | -180 이상 180 이하 |
| `radius` | 선택 | 1000 | 1 이상 5000 이하(미터) |
| `category` | 선택 | 전체 | 카테고리 코드. 쉼표로 여러 개(`a,b`). 없는 코드면 검증 실패 |
| `excludeCompletedToday` | 선택 | false | true면 오늘 이미 인증한 장소를 뺀다 |
| `size` | 선택 | 100 | 1 이상 200 이하 |

- 정렬: 거리 가까운 순(같으면 id순). 활성 장소만.
- 성공: 200

| data.places[] 필드 | 설명 |
|---|---|
| `placeId`, `name`, `category`(코드), `lat`, `lng` | |
| `distance` | 요청 좌표로부터 거리(m) |
| `radius` | 도착 인증 반경(m) |
| `notifyRadius` | 알림 반경(m). 지오펜싱 알림은 구현하지 않았고 값만 내려준다 |
| `rewardPoint` | 이 장소의 기본 점수(장소 값이 있으면 카테고리 값을 덮는다) |
| `expectedPoint` | 지금 인증하면 받을 점수. 첫 방문이면 `rewardPoint`, 재방문이면 절반(내림) |
| `isHot` | `hotType`이 있으면 `true` |
| `hotType` | `CURATED` 또는 `null`(`POPULAR`는 나오지 않는다) |
| `visitedBefore` | 이전에 이 장소를 인증한 적이 있는지 |
| `isCompleted` | 오늘 이미 인증했는지 |

```json
{ "success": true, "code": "COMMON200", "message": "요청 성공",
  "data": { "places": [ {
    "placeId": 12, "name": "경복궁", "category": "HISTORY", "lat": 37.5796, "lng": 126.977,
    "distance": 180, "radius": 100, "notifyRadius": 300, "rewardPoint": 100, "expectedPoint": 100,
    "isHot": true, "hotType": "CURATED", "visitedBefore": false, "isCompleted": false } ] } }
```

- 에러: COMMON400(필수 파라미터 누락, 범위 초과, 없는 category, 타입 변환 실패)

#### GET /places/recommendations — 뽈뽈 추천 장소

- 쿼리: `lat`(필수), `lng`(필수), `size`(선택, 기본 5, 1 이상 10 이하). 반경 제한은 없다.
- 후보: `hot_type`이 CURATED인 활성 장소만.
- 정렬: 오늘 이미 인증한 장소는 뒤로, 그다음 **안 가본 장소 먼저**, 거리 가까운 순, id순.
- 성공: 200. `data.places[]`의 필드는 `GET /places`의 항목과 같고 **`reason`**(string)이 더해진다.

| `reason` 값(서버가 정함) | 조건 |
|---|---|
| "뽈뽈이 고른 추천 장소, 아직 가보지 않았어요" | 가본 적 없음 |
| "뽈뽈이 고른 추천 장소예요" | 가본 적 있음 |
| "오늘 이미 다녀온 뽈뽈 추천 장소예요" | 오늘 인증함 |

- 에러: COMMON400

#### GET /places/{placeId} — 장소 상세

- 경로: `placeId`(number)
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `placeId`, `name`, `category`(코드), `categoryName` | |
| `address`, `roadAddress`(null 가능), `phone`(null 가능) | |
| `lat`, `lng`, `radius`, `notifyRadius` | |
| `kakaoPlaceUrl` | 카카오 장소 페이지 주소 |
| `rewardPoint`, `expectedPoint` | `GET /places`와 같은 의미 |
| `photoBonusAvailable` | 사진 보너스(+50)를 받을 수 있는지. 첫 방문이고 오늘 아직 인증하지 않았을 때만 `true` |
| `isHot`, `hotType` | `CURATED` 또는 `null` |
| `completedCount` | 모든 사용자의 이 장소 누적 인증 수 |
| `visitedBefore`, `isCompleted` | |
| `myLastCompletedAt` | 내가 마지막으로 인증한 시각, 없으면 `null` |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | `placeId`가 숫자가 아님 |
| PLACE404 | 404 | 존재하지 않는 장소(비활성/폐업 장소 포함) |

### 2.3 퀘스트 인증

#### POST /places/{placeId}/completions — 도착 인증

- 인증: 필요
- 요청 본문

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `lat` | number | 필수 | -90 이상 90 이하 |
| `lng` | number | 필수 | -180 이상 180 이하 |
| `accuracy` | number | 필수 | 0 이상(m) |
| `isMockLocation` | boolean | 필수 | 모의 위치 여부(클라이언트가 보낸 값을 그대로 믿는다) |

- 검사 순서: 검증 -> **온보딩 완료 여부** -> **장소 없음** -> **오늘 이미 인증** -> **모의 위치** -> **정확도(100m 초과 거절)** -> **반경(장소의 `radius` 초과 거절)**. 먼저 걸린 에러가 나온다.
- 점수: 첫 방문은 `rewardPoint` 전액, 재방문은 절반(내림). 같은 장소는 하루(한국 시간 기준) 1회만 인증된다.
- 성공: **HTTP 201**, `COMMON201`, message "퀘스트를 완료했습니다."

| data 필드 | 설명 |
|---|---|
| `completionId`, `placeId`, `placeName` | |
| `isFirstVisit` | 이 장소의 첫 인증인지 |
| `earnedPoint` | 이번에 받은 점수 |
| `totalPoint` | 누적 포인트(이번 점수 포함) |
| `completedAt` | 인증 시각 |
| `photoBonusPoint` | 사진을 올리면 받을 보너스. 첫 방문 50, 재방문 0 |
| `photoDeadline` | 사진 업로드 마감(인증 시각 + 1시간). 재방문이면 `null` |

```json
{ "success": true, "code": "COMMON201", "message": "퀘스트를 완료했습니다.",
  "data": { "completionId": 31, "placeId": 12, "placeName": "경복궁", "isFirstVisit": true,
    "earnedPoint": 100, "totalPoint": 350, "completedAt": "2026-11-18T10:30:00",
    "photoBonusPoint": 50, "photoDeadline": "2026-11-18T11:30:00" } }
```

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| COMMON400 | 400 | 검증 실패 |
| COMPLETION4031 | 403 | 온보딩을 완료해야 퀘스트를 인증할 수 있음 |
| PLACE404 | 404 | 존재하지 않는 장소(비활성 포함) |
| COMPLETION409 | 409 | 오늘 이미 이 장소를 인증함(동시 요청 포함) |
| COMPLETION4003 | 400 | 모의 위치는 사용할 수 없음 |
| COMPLETION4002 | 400 | GPS 정확도가 낮음(`accuracy` > 100) |
| COMPLETION4001 | 400 | 장소의 인증 반경 밖 |

#### POST /completions/{completionId}/photo — 인증 사진 업로드

- 인증: 필요
- 요청: `multipart/form-data`, 파트 이름 **`photo`**(파일 하나)
- 파일 규칙: JPEG 또는 PNG만(선언한 Content-Type과 실제 디코딩 결과가 모두 맞아야 함), 최대 10MB, 최대 1600만 화소. 서버는 EXIF 회전을 픽셀에 반영한 뒤 메타데이터(GPS 등)를 모두 버리고 **긴 변 1920px 이하의 JPEG로 다시 인코딩**해 저장한다. 저장 키는 UUID 난수다.
- 검사 순서: 인증 기록 없음 -> 내 기록 아님 -> 이미 올림 -> 마감 -> 파일 크기 -> 파일 내용.
- 마감: 첫 방문 인증이면 인증 시각 + 1시간(정각까지 허용). 재방문은 마감이 없다.
- 보너스: 첫 방문이면 +50, 재방문이면 사진은 올릴 수 있지만 0점. 한 인증 기록에 사진은 한 번만 올릴 수 있다.
- 성공: **HTTP 201**, `COMMON201`, message "사진이 등록되었습니다."

| data 필드 | 설명 |
|---|---|
| `completionId` | |
| `photoUrl` | 상대 경로 `/files/completions/{uuid}.jpg`(서버 주소를 앞에 붙여 쓴다. `storage.public-base-url`을 설정하면 그 값이 앞에 붙는다) |
| `bonusPoint` | 이번에 받은 보너스(재방문이면 0) |
| `totalPoint` | 누적 포인트 |

- 에러

| 코드 | HTTP | 의미 |
|---|---|---|
| FILE4001 | 400 | `photo` 파트가 없음, 빈 파일, 지원하지 않는 형식(HEIC, WebP, 손상 파일, 화소 초과 포함) |
| COMPLETION4004 | 400 | 사진 업로드 마감 시각이 지남 |
| COMPLETION403 | 403 | 내 완료 기록이 아님 |
| COMPLETION404 | 404 | 존재하지 않는 완료 기록 |
| COMPLETION4091 | 409 | 이미 사진을 올림 |
| FILE413 | 413 | 파일이 10MB를 넘음 |
| COMPLETION503 | 503 | 사진 처리 요청이 많음(동시 디코딩 한도 3개 초과). 잠시 뒤 다시 시도 |
| COMMON415 | 415 | `multipart/form-data`가 아닌 Content-Type |

#### GET /users/me/completions — 활동 일지

- 인증: 필요
- 쿼리

| 파라미터 | 필수 | 기본 | 제약 |
|---|---|---|---|
| `from` | 선택 | | `yyyy-MM-dd`. 인증 시각(한국 시간)의 날짜 기준 |
| `to` | 선택 | | `yyyy-MM-dd`. 그날 끝까지 포함. `from`보다 빠르면 검증 실패 |
| `category` | 선택 | 전체 | 카테고리 코드(하나). 없는 코드면 검증 실패 |
| `cursor` | 선택 | | 1 이상 |
| `size` | 선택 | 20 | 1 이상 50 이하 |

- 방문(인증 1건) 기준 최신순. 성공: 200

| data 필드 | 설명 |
|---|---|
| `completions[]` | `{ completionId, placeId, placeName, category, address, earnedPoint, photoUrl, completedAt }` |
| `nextCursor`, `hasNext` | 1.7절 |

`earnedPoint`는 그 방문으로 받은 합계(기본 점수 + 사진 보너스), `photoUrl`은 상대 경로이고 사진이 없으면 `null`이다. 장소를 찾을 수 없으면 `placeName`은 "알 수 없는 장소", `category`와 `address`는 빈 문자열이다.

- 에러: COMMON400

### 2.4 보상

#### GET /users/me/points — 내 포인트 요약

- 인증: 필요
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `totalPoint` | 누적 포인트 |
| `todayPoint` | 오늘(한국 시간) 적립한 포인트 |
| `completionCount` | 누적 도착 인증 횟수 |

#### GET /users/me/point-histories — 포인트 내역

- 인증: 필요
- 쿼리: `cursor`(선택, 1 이상), `size`(선택, 기본 20, 1 이상 50 이하), `type`(선택, `QUEST_COMPLETE` 또는 `PHOTO_BONUS`. 그 밖의 값은 `COMMON400`)
- 최신순. 성공: 200

| data 필드 | 설명 |
|---|---|
| `histories[]` | `{ historyId, type, point, completionId, placeId, placeName, category, createdAt }`. `createdAt`은 적립 시각이다(사진 보너스는 업로드 시각) |
| `nextCursor`, `hasNext` | 1.7절 |

- 에러: COMMON400

### 2.5 통계

#### GET /users/me/stats — 기간별 활동 통계

- 인증: 필요
- 쿼리: `period`(필수, `WEEK|MONTH|YEAR`), `date`(선택, `yyyy-MM-dd`, 기본 오늘, 미래 날짜는 검증 실패)
- 기간: 기준 날짜가 속한 주(월~일), 달, 해. 모든 값은 **포인트 적립 시각** 기준이다.
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `period`, `startDate`, `endDate` | 기간 이름과 범위(끝 날짜 포함) |
| `completionCount` | 기간에 적립된 도착 인증(QUEST_COMPLETE) 수. 사진 보너스는 세지 않는다 |
| `totalPoint` | 기간에 적립한 모든 포인트(사진 보너스 포함) |
| `placeCount` | 도착 인증한 서로 다른 장소 수 |
| `previous` | `{ completionCount, totalPoint }` 바로 이전 기간의 값 |
| `categories[]` | `{ category, count, point }`. 횟수 많은 순(같으면 점수 순, 코드 순). 장소를 찾지 못하면 `category`가 **빈 문자열**이다 |
| `timeline[]` | `{ label, count, point }`. 활동이 없는 날/달도 0으로 채운다. `WEEK`는 7개, `MONTH`는 28~31개(`label`은 `yyyy-MM-dd`), `YEAR`는 12개(`label`은 `yyyy-MM`) |

- 에러: COMMON400(`period` 누락 또는 없는 값, `date` 형식 오류 또는 미래)

### 2.6 랭킹

모두 인증이 필요하다. 순위는 표준 경쟁 순위다(같은 값이면 같은 순위, 다음은 건너뜀: 1, 2, 2, 4). 별도 캐시 없이 요청 때마다 계산한다.

#### GET /rankings/users — 사용자 포인트 랭킹

- 쿼리: `period`(필수, `DAY|WEEK|MONTH`), `size`(선택, 기본 50, 1 이상 100 이하)
- 기간: DAY는 오늘, WEEK는 이번 주 월~일, MONTH는 이번 달. 적립 시각 기준, 도착 인증(QUEST_COMPLETE) 점수 기준이다.
- **탈퇴한 사용자, 온보딩 미완료 사용자, 닉네임 없는 사용자는 목록과 `me`에서 모두 제외**된다(`me`는 `null`).
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `period`, `startDate`, `endDate` | |
| `rankings[]` | `{ rank, nickname, point, completionCount, isMe }`. 다른 사용자의 userId와 이메일은 내려가지 않는다 |
| `me` | `{ rank, nickname, point, completionCount }` 또는 `null`(기간에 점수가 없거나 제외 대상) |
| `updatedAt` | 계산 시각 |

- 에러: COMMON400(`period` 누락 또는 없는 값, `size` 범위)

#### GET /rankings/places — 인기 장소 랭킹

- 쿼리: `period`(선택, `DAY|WEEK|MONTH`, 기본 `WEEK`), `category`(선택, 카테고리 코드 하나), `size`(선택, 기본 10, 1 이상 50 이하)
- 기간 내 인증 횟수 많은 순, 같으면 서로 다른 사용자 수 많은 순. 최소 횟수 조건은 없다. 탈퇴 사용자의 기록도 센다. 비활성 장소는 뺀다.
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `period`, `startDate`, `endDate`, `updatedAt` | |
| `rankings[]` | `{ rank, placeId, placeName, category, address, completionCount, rewardPoint }` |

- 에러: COMMON400(없는 `period`, 없는 `category`, `size` 범위)

#### GET /rankings/streaks — 연속 참여 랭킹

- 쿼리: `size`(선택, 기본 10, 1 이상 50 이하)
- 연속 일수는 저장하지 않고 요청 때마다 완료 기록의 한국 시간 날짜로 계산한다. 재방문도 참여로 센다. 오늘 참여했으면 오늘부터, 오늘은 아직이고 어제 참여했으면 어제부터 거슬러 이어진 날 수가 현재 연속이다. 하루라도 빠지면 끊긴다.
- 현재 연속이 0인 사용자는 목록에 없고 `me`도 `null`이다. 탈퇴, 온보딩 미완료, 닉네임 없는 사용자는 제외한다.
- 정렬: 현재 연속 일수 내림차순, 같으면 최장 기록, 그다음 id.
- 성공: 200

| data 필드 | 설명 |
|---|---|
| `rankings[]` | `{ rank, nickname, streakDays, isMe }` |
| `me` | `{ rank, nickname, streakDays, longestStreakDays }` 또는 `null` |

- 에러: COMMON400(`size` 범위)

### 2.7 기타

#### GET /health — 서버 상태 확인

- 인증: 불필요
- 성공: 200, `data`는 `{ "status": "UP" }`

#### GET /files/{directory}/{name} — 업로드 사진 내려받기

- 인증: **필요**(앱의 이미지 로더가 `Authorization` 헤더를 붙여야 한다)
- 존재 조건: `storage.type=local`일 때만 만들어지는 개발용 경로다(배포의 S3 구성에서는 CDN이 대신한다).
- 경로: `{directory}/{name}`은 `photoUrl`의 `/files/` 뒷부분이다. 형식은 `^[a-z]+/{uuid}.jpg$`만 허용한다.
- 성공: 200, 본문은 이미지 바이트, `Content-Type: image/jpeg`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`. 공통 응답 형식이 아니다.
- 형식이 틀리거나 파일이 없으면 **본문 없는 404**(공통 응답 형식이 아님). 토큰이 없으면 `AUTH401`.

---

## 3. 에러 코드 전체 표

코드 형식은 도메인 + HTTP 상태 + (일련번호)다. 번호가 불규칙한 것(`USER4092`, `COMPLETION4091` 등)은 Notion 명세 번호를 그대로 옮겼다. 정의 위치는 각 도메인의 `*ErrorCode.kt`와 `global/error/*ErrorCode.kt`다.

### 3.1 공통 (`CommonErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| AUTH401 | 401 | 로그인이 필요합니다(토큰 없음, 만료, 위조, 탈퇴한 사용자) |
| AUTH403 | 403 | 접근 권한이 없습니다 |
| COMMON400 | 400 | 입력값을 다시 확인해 주세요(`data.errors[{field, reason}]`) |
| COMMON404 | 404 | 요청한 경로를 찾을 수 없습니다 |
| COMMON405 | 405 | 지원하지 않는 HTTP 메서드입니다 |
| COMMON415 | 415 | 지원하지 않는 Content-Type입니다 |
| COMMON500 | 500 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요 |

그 밖에 처리하지 않은 Spring의 4xx 예외(예: 406)는 `COMMON{상태코드}` 형식(예: `COMMON406`, message "요청을 처리할 수 없습니다.")으로 응답한다. 성공 코드는 `COMMON200`, `COMMON201`이다.

### 3.2 인증 (`AuthErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| AUTH4011 | 401 | 이메일 또는 비밀번호가 일치하지 않음 |
| AUTH4012 | 401 | refresh token이 만료, 위조되었거나 이미 사용됨 |
| AUTH429 | 429 | 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요(명세에 없는 추가) |

### 3.3 이메일 인증 (`EmailErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| EMAIL4001 | 400 | 인증 코드가 일치하지 않음(`data.remainingAttempts`) |
| EMAIL4002 | 400 | 인증 코드가 만료되었거나 5번 틀려서 무효됨. 다시 발송해야 함 |
| EMAIL4003 | 400 | verificationToken이 없거나 만료됨, 또는 다른 용도의 토큰 |
| EMAIL429 | 429 | 재발송 제한(1분에 1번, 하루 5번). 코드 확인 시도의 IP 제한에도 쓰임 |
| EMAIL502 | 502 | 메일 발송 실패 |

### 3.4 카카오 (`KakaoErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| KAKAO401 | 401 | 카카오 토큰이 만료되었거나 유효하지 않음 |
| KAKAO502 | 502 | 카카오 서버 응답 실패 |

### 3.5 사용자 (`UserErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| USER404 | 404 | 탈퇴했거나 존재하지 않는 사용자 |
| USER409 | 409 | 이미 가입된 이메일 |
| USER4001 | 400 | 닉네임에 금칙어 포함 |
| USER4002 | 400 | 닉네임 변경 후 30일이 지나지 않음(`data.nextChangeableAt`) |
| USER4092 | 409 | 이미 사용 중인 닉네임 |
| USER4093 | 409 | 카카오 이메일이 이미 이메일 가입 계정으로 등록됨. 이메일로 로그인해야 함 |

`USER404`: 코드에는 `/users/me` 계열이 활성 사용자만 허용하도록 되어 있지만, JWT 필터가 탈퇴 사용자를 먼저 인증 실패(`AUTH401`)로 막기 때문에 일반 흐름에서는 나오지 않는다.

### 3.6 장소 (`PlaceErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| PLACE404 | 404 | 존재하지 않는 장소 |

### 3.7 퀘스트 인증 (`CompletionErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| COMPLETION4001 | 400 | 장소의 인증 반경 밖에 있습니다 |
| COMPLETION4002 | 400 | GPS 정확도가 낮습니다. 실외로 이동해서 다시 시도해주세요 |
| COMPLETION4003 | 400 | 모의 위치는 사용할 수 없습니다 |
| COMPLETION409 | 409 | 오늘 이미 이 장소를 인증했습니다 |
| COMPLETION4004 | 400 | 사진 업로드 마감 시각이 지났습니다 |
| COMPLETION403 | 403 | 내 완료 기록이 아닙니다 |
| COMPLETION404 | 404 | 존재하지 않는 완료 기록입니다 |
| COMPLETION4091 | 409 | 이미 사진을 올렸습니다. 보너스는 1회만 받을 수 있습니다 |
| COMPLETION4031 | 403 | 온보딩을 완료해야 퀘스트를 인증할 수 있습니다(명세에 없는 추가) |
| COMPLETION503 | 503 | 사진 처리 요청이 많습니다. 잠시 후 다시 시도해주세요(명세에 없는 추가) |

### 3.8 파일 (`FileErrorCode`)

| 코드 | HTTP | 의미 |
|---|---|---|
| FILE4001 | 400 | 파일이 없거나 지원하지 않는 형식입니다 |
| FILE413 | 413 | 파일 용량은 10MB를 넘을 수 없습니다 |

---

## 4. 기존 Notion 명세와 다른 점

**경로와 메서드가 다른 API는 0개다.** 아래는 같은 경로와 메서드의 동작, 값, 에러 코드가 다른 부분이다. "결정 A1~A8"은 `docs/reports/final-report.md` 11절의 사용자 결정이다.

### 4.1 돌려주는 값

| API | Notion 명세 | 구현 |
|---|---|---|
| POST /auth/email-verifications/confirm | `verificationToken`이 base64 예시 | 이메일에서 유도하지 않는 **32바이트 난수**(base64url). 이메일과 purpose에 묶임(결정 A1) |
| POST /completions/{id}/photo | `photoUrl`이 절대 CDN 주소 | **`/files/{key}` 상대 경로**. 앱이 서버 주소를 붙여 쓴다 |
| GET /places, GET /places/{id} | `hotType`에 `POPULAR`가 나옴 | **`POPULAR`는 나오지 않는다.** `isHot`은 `hotType`이 `CURATED`일 때만 `true` |
| GET /places/recommendations | 응답 필드 예시가 적음 | `radius`, `notifyRadius`, `expectedPoint`, `hotType`, `visitedBefore`, `isCompleted`가 추가됐고, `reason` 문구는 서버가 정한다 |
| GET /users/me/stats | | `categories[].category`에 **코드가 빈 문자열("")인 항목**이 올 수 있다(장소를 찾지 못한 기록, 결정 A5) |

### 4.2 에러 코드

| API | Notion 명세 | 구현 |
|---|---|---|
| GET/DELETE /users/me | 탈퇴 사용자는 USER404 | 탈퇴 사용자의 토큰은 JWT 필터가 먼저 막아 **AUTH401** |
| POST /auth/email-verifications/confirm | 5번째 오답은 EMAIL4001 | **EMAIL4002**(코드가 무효가 됨) |
| POST /auth/email-verifications, confirm | | **EMAIL429** 추가(재발송 제한, 코드 확인의 IP 제한) |
| POST /auth/login | | **AUTH429** 추가(IP당 로그인 시도 제한, 결정 A8) |
| POST /places/{id}/completions | | **COMPLETION4031** 추가(온보딩 미완료, 결정 A4) |
| POST /completions/{id}/photo | | **FILE4001**(형식, 빈 파일, 파트 누락), **COMPLETION503**(동시 처리 한도) 추가 |
| 공통 | | COMMON404, COMMON405, COMMON415, COMMON500, AUTH403 추가 |

### 4.3 동작

| API | Notion 명세 | 구현 |
|---|---|---|
| POST /auth/reissue | 이미 쓴 refresh token은 AUTH4012 | **직전 refresh token을 약 20초 유예**한다(동시 요청, 응답 유실 대비, 결정 A2). 유예가 지나면 AUTH4012 |
| POST /places/{id}/completions | 검사 순서 없음 | 온보딩 -> 장소 없음 -> 오늘 이미 인증 -> 모의 위치 -> 정확도 -> 반경 순 |
| POST /completions/{id}/photo | HEIC 포함 | **JPEG, PNG만**. EXIF 제거 후 긴 변 1920px 이하의 JPEG로 재인코딩, 저장 키는 UUID(결정 A7). 앱이 HEIC를 JPEG로 바꿔 보낸다 |
| GET /places/recommendations | 안 가본 카테고리 우선 | **"안 가본 장소 우선 + 거리순"**, 후보는 CURATED만. 오늘 인증한 곳은 뒤로 |
| GET /rankings/users, /rankings/streaks | | 탈퇴, **온보딩 미완료, 닉네임 없는 사용자도 제외**(결정 A4) |
| GET /users/me/stats, 랭킹 | | 기준 날짜는 **포인트 적립 시각**(결정 A5). 사진 보너스는 업로드 시각에 적립되므로 인증 다음 날로 넘어갈 수 있다. 활동 일지만 인증 시각 기준 |

### 4.4 내부 구현만 다름 (응답은 같음)

| 대상 | 구현 |
|---|---|
| GET /rankings/streaks | 연속 일수를 저장하지 않고 **조회 때마다 계산**한다 |
| GET /users/me/points | `totalPoint`는 별도 합계 컬럼이 아니라 **포인트 내역의 합**으로 계산한다 |

### 4.5 구현하지 않은 것

| 항목 | 상태 |
|---|---|
| hotType `POPULAR` 집계 | 구현 안 함(`RecommendationSource` 확장 지점만 있음). 최근 7일 인기 장소는 나오지 않는다 |
| 관심사, 연령, 성별을 반영한 추천 | 구현 안 함. 온보딩 값은 저장만 한다 |
| 지오펜싱 알림(결정 A5) | 구현 안 함. `notifyRadius` 값만 내려준다 |
| 카카오 로컬 API 실호출(장소 수집)과 카카오 로그인 실서버 호출 | 미검증(가짜 클라이언트로만 검증) |

### 4.6 명세에 없는 추가

| 항목 | 설명 |
|---|---|
| GET /health | 서버 상태 확인(공개) |
| GET /files/{directory}/{name} | 업로드 사진 내려받기(인증 필요, `storage.type=local`일 때만) |
| 앱의 "퀘스트 받기" | **서버 API 없이** 기기 안(`QuestBook`, SharedPreferences, 사용자·장소·한국 날짜별, 자정 만료)에만 저장한다. 재설치나 다른 기기에서는 사라진다 |

---

## 5. 확인 필요

코드를 읽다가 요청 사항과 어긋나거나 확인하지 못한 것이다.

1. **`USER404`의 실제 도달 여부**: 요청 사항에는 "GET/DELETE /users/me에서 USER404 대신 AUTH401"이라고 되어 있고 코드가 그렇다(JWT 필터가 탈퇴 사용자를 인증하지 않음). 다만 `PATCH /users/me`, 온보딩 API도 같은 이유로 `USER404`는 동시 탈퇴 같은 경합에서만 나온다. 표에는 코드 기준으로 `USER404`를 남겨 두었다.
2. **`EMAIL429`의 중복 사용**: 재발송 제한과 코드 확인의 IP 제한이 같은 코드다. 응답만으로는 어느 쪽인지, 남은 시간이 얼마인지 알 수 없어 앱은 60초로 가정한다(`final-report.md`).
3. **`LocalDateTime` 직렬화 형식**: 시간대 없는 ISO-8601 문자열로 적었지만, 실제 응답 샘플로 확인하지는 않았다(Jackson 설정 파일에서 별도 포맷을 지정한 곳은 찾지 못함).
4. **`photoUrl`의 접두어**: `storage.public-base-url`이 비어 있으면 상대 경로이고, 값을 설정하면 그 값이 앞에 붙는다. `application.yml`, `application-local.yml`에는 이 값이 없어 로컬에서는 상대 경로다. 배포(S3/CDN) 구성은 구현되어 있지 않다.
5. **Notion 명세 28개와의 대조**: 28개는 컨트롤러 매핑을 세어 맞춘 값이다(회원 15, 장소 4, 인증 3, 보상 2, 통계 1, 랭킹 3). Notion 페이지 자체는 이 작업에서 열어 보지 않았고, 4절은 요청받은 차이 목록과 코드를 대조해 쓴 것이다.
6. **`/swagger-ui`, `/v3/api-docs`**: 공개 경로로 선언돼 있고 local, test 프로파일에서만 켜진다(`application.yml`은 꺼짐, `application-local.yml`은 켜짐). test 프로파일 설정은 확인하지 않았다.
7. **작업 트리의 다른 변경**: `git status`에 `README.md` 수정이 보인다. 이 작업과 무관한 변경이다.
