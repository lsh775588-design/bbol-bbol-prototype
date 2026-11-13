# 앱-서버 계약 테스트

앱(Kotlin, Retrofit + kotlinx-serialization)과 서버(Kotlin, Spring Boot, Jackson 3)가 주고받는 JSON의 키 이름, 타입, null 여부가 어긋나면 **사람이 아니라 테스트가** 잡게 한다. 서버가 만든 골든 파일이 계약이고, 앱은 같은 파일을 복사해 가서 자기 DTO에 대어 본다.

## 무엇을 지키나
- 응답: 서버 `ContractGoldenTest`가 엔드포인트를 실제로 호출하고, 응답을 "모양"(키 경로 -> 타입, null 가능 여부)으로 줄여 `contract/responses/*.json`과 비교한다. 값(id, 토큰, 시각, 점수)은 기록하지 않는다. 에러 응답(COMMON400 `data.errors[]`, AUTH401, 엔드포인트별 업무 에러)도 같다.
- 요청: `contract/requests/<id>.json`이 앱이 보내는 메서드, 경로, 쿼리, 본문의 계약이다. 서버는 이 값을 그대로 보내 받아들여지는지 확인하고, 본문 키와 필수 쿼리를 하나씩 빼서 COMMON400 + `data.errors[].field`가 나오는지 본다. `auth: true`면 토큰 없이 호출해 AUTH401이 나오는지도 본다.
- 앱 `ContractTest`(JVM 단위 테스트): (a) 골든 모양으로 만든 표본을 앱의 실제 DTO와 `ApiJson`으로 읽는다. (b) kotlinx-serialization 디스크립터로 키를 양방향 점검한다(서버 키를 DTO가 모르면 실패, 기본값 없는 non-null DTO 프로퍼티가 서버에 없으면 실패, 서버가 null을 줄 수 있는데 DTO가 non-null이면 실패, 타입 불일치 실패). (c) 앱의 실제 Retrofit 호출이 요청 골든과 같은 메서드, 경로, 쿼리 이름, 본문 키와 타입, Authorization 헤더인지 MockWebServer로 확인한다. 실패 메시지에는 엔드포인트와 키가 들어 있다.

## 명령
```
# 서버(서버 저장소): 골든 비교(기본). 의도한 변경이면 갱신 후 git diff로 검토해 커밋
./gradlew test --tests '*ContractGoldenTest'
./gradlew test --tests '*ContractGoldenTest' -Dcontract.update=true

# 바깥 저장소: 서버 골든을 앱으로 복사하고, 같은지 해시로 확인
node scripts/sync-contract.mjs [--server <서버 저장소 경로>]   # 환경변수 BBOL_SERVER_DIR도 가능
node scripts/check-contract.mjs

# 앱
cd app && ./gradlew testDebugUnitTest
```
순서: 서버 변경 -> 서버 골든 갱신 -> `sync-contract` -> 앱 테스트 실패를 보고 DTO 수정 -> `check-contract`.

## 새 엔드포인트 추가 절차
1. 서버 `contract/requests/<id>.json` 작성(`method`, `path`, `auth`, `query`, `requiredQuery`, 본문이 있으면 `body`). `<id>`는 `kebab-case`(예: `photos-upload`).
2. 서버 `ContractGoldenTest.responseContracts`에 단계를 추가한다: 성공 호출과 주요 에러(`golden(spec, "success", 200, "COMMON200", 표본...)`, `"error-<코드>"`). null 가능한 필드는 값이 있는 표본과 null인 표본을 함께 넘기면 `string|null`로 합쳐진다. 빈 배열만 나오는 표본은 원소 모양이 비므로 데이터를 채워서 호출한다.
3. `-Dcontract.update=true`로 골든을 만들고 diff를 읽어 의도한 모양인지 확인한 뒤 커밋한다. 필수 키 누락 테스트는 요청 골든에서 자동으로 만들어진다.
4. 바깥 저장소에서 `sync-contract` 실행 후 앱 `ContractTest`의 `responseDtos`(응답 DTO)와 `calls`(BbolApi 호출)에 `<id>`를 등록한다. 등록하지 않으면 테스트가 "등록되지 않았다"로 실패한다.
5. 앱 테스트가 알려 주는 대로 DTO를 맞춘다. 서버 키를 일부러 안 쓰면 `intentionallyIgnored`, 서버 null을 기본값으로 받아도 되면 `coercedNulls`에 **이유와 함께** 등록한다(안 쓰게 되면 테스트가 지우라고 알려 준다).

## 알아 둘 한계
- 값은 비교하지 않는다. 문구(message), enum 값 목록(`hotType`, `type`, `loginType`), 정수 폭(Int/Long), 시각의 시간대 의미는 계약에 넣지 않았다. 시각은 문자열 모양(`yyyy-MM-ddTHH:mm:ss`, 시간대 없음)만 본다.
- 표본에 한 번도 나오지 않은 변형(예: 항상 null인 `photoUrl`)은 타입을 알 수 없어 `null`로만 기록된다. 값이 생기면 골든이 바뀌어 테스트가 알려 준다.
- 앱은 `ApiJson`의 `ignoreUnknownKeys = true`라 서버가 키를 추가해도 런타임에는 깨지지 않는다. 이 테스트가 추가를 알려 주는 유일한 장치다.
- 서버에서 지원하지만 앱이 보내지 않는 쿼리(예: `excludeCompletedToday`)는 요청 골든에 넣지 않는다. 앱이 쓰기 시작하면 골든에 추가한다.
