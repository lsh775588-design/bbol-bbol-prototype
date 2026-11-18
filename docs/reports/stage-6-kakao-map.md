# 6단계: 지도를 카카오맵 SDK로 교체

브랜치 `feat/11-kakao-map`(`feat/2-integration` 기준). 날짜 2026-11-18.

## 한 일
- 앱의 지도를 osmdroid 6.1.20(OSM)에서 **카카오맵 SDK v2 2.15.2**로 바꿨다. 서버와 HTTP 계약은 건드리지 않았다.
- 핀 모델과 좌표 계산을 `MapLogic.kt`로 분리하고 단위 테스트를 추가했다.
- 키는 `app/local.properties`의 `KAKAO_NATIVE_APP_KEY`에서 읽어 `BuildConfig`와 매니페스트로 넘긴다. 키가 없으면 SDK를 초기화하지 않고 "지도 키가 설정되지 않았어요"를 보여 준다. 빌드는 실패하지 않는다.
- review(읽기 전용)가 지적한 중간 3건과 낮음 일부를 반영했다: 카카오 저장소를 `com.kakao` 그룹으로 제한, 최소 줌(`setCameraMinLevel`) 설정, 오류 종류별 복구(네트워크성은 "다시 시도", 인증 오류는 안내 유지), 키 이스케이프, 경계값 테스트.
- 테스트가 `kakaoLevel`에 `+Infinity`가 들어가면 최소 레벨로 처리되는 버그를 잡아 고쳤다.

## 바뀐 파일
`app/gradle/libs.versions.toml`, `app/settings.gradle.kts`, `app/app/build.gradle.kts`, `AppContainer.kt`, `ui/map/MapScreen.kt`, `ui/map/MapMarkers.kt`, `ui/map/MapLogic.kt`(신규), `ui/map/MapLogicTest.kt`(신규), README와 `final-report.md`의 키 안내.

## 기존 동작과 달라진 점
- 흐린 배경: 타일 색 필터 대신 SDK의 `DimScreenLayer`로 흰색 반투명을 덮는다. 핀은 덮지 않는다.
- 내 위치 후광: 정확도(m) 반경 대신 줌과 무관한 고정 dp 크기(18~60dp, 정확도에 비례)다.
- 줌은 정수 레벨이라 둘러보기 맞춤이 14~15로 제한된다(이전 13.5~15.0).
- OSM 출처 표기 오버레이는 없다(카카오 로고는 SDK가 표시한다).
- 핀 탭, 퀘스트 받기, 도착 인증 흐름과 `QuestBook`은 바꾸지 않았다.

## 테스트 결과
- 앱 단위 테스트 debug **194개 통과, 실패 0**(기존 183개 + 새 11개). 직접 결과 파일로 확인했다.
- `assembleDebug` 통과.

## 실기기 확인 (Galaxy A13, arm64-v8a, Android 14)
- 카카오맵이 정상으로 뜨고 종로구 장소 33곳의 카테고리 핀이 보인다. 스크린샷 `docs/screenshots/stage-6/kakao-map-phone.png`.
- 서버는 USB `adb reverse tcp:8120 tcp:8120`와 `-PapiBaseUrl=http://localhost:8120/` 빌드로 연결했다. 휴대폰이 PC와 같은 네트워크에 있지 않았기 때문이다.
- 휴대폰 확인용 복제본 `C:\dev\bbolbbol-phone`에는 같은 커밋 4개를 옮겼다(`network_security_config.xml`의 휴대폰용 수정은 그대로 둠).

## 겪은 문제와 원인
- **지도 인증 403 Forbidden**: 카카오 무료 일간 쿼터는 카카오맵을 처음 활성화한 앱에만 주어지고, 새 앱에서 켜려면 비즈월렛과 요금이 필요해 토글이 꺼졌다. 기존 앱에 이 앱의 Android 플랫폼(패키지명, 디버그 키 해시)을 추가하고 그 앱의 네이티브 앱 키를 쓰자 해결됐다. 두 프로젝트가 쿼터를 공유한다.
- **x86_64 에뮬레이터 불가**: SDK 네이티브 라이브러리(`libK3fAndroid.so`)가 arm64-v8a, armeabi-v7a만 있다. APK를 arm 전용으로 만들었으므로 x86_64 에뮬레이터(`Medium_Phone`, `Pixel_4_XL`)는 시험하지 않았고 실기기로 확인했다.
- **키 노출 위험**: SDK가 인증 요청 헤더(키 포함)를 logcat에 찍고, 빌드 오류 로그가 `BuildConfig.java` 줄을 보여 준다. 커밋된 파일에는 키가 없다(`git grep`로 확인).

## 내가 결정해야 할 것
1. 브랜치 `feat/11-kakao-map`을 `feat/2-integration`에 병합할지(지금까지 단계 브랜치는 병합했다).
2. 에뮬레이터 스크린샷 절차를 arm64 AVD(시스템 이미지 설치 필요)로 바꿀지, 실기기로 대체할지.
3. 정식 배포 전에 카카오맵용 별도 앱과 월렛 연결을 할지(지금은 기존 앱과 쿼터 공유).

## 미검증
- 핀 탭 → 퀘스트 받기 → 도착 인증 흐름, 내 위치 점과 위치 권한 화면, 흐린 배경 농도 조정, 최소 줌 동작, 지도 오류 복구 화면("다시 시도")은 휴대폰에서 아직 확인하지 않았다(지도 표시만 확인).
- x86_64 에뮬레이터의 ARM 변환 동작, release 빌드와 R8(난독화), release 단위 테스트.
- Android 8~13 기기.
