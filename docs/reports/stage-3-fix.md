# 3단계 E2E 발견 앱 버그 수정 보고서

- 일자: 2026-11-14, 브랜치 `feat/6-fix`. 서버 변경 없음. 에뮬레이터 재확인은 오케스트레이터가 한다(미검증).

## 수정한 것
| E2E 번호 | 내용 | 변경 |
|---|---|---|
| 1 (높음) | 가입/카카오 로그인 직후 온보딩 대신 홈으로 감 | `AppNav`가 `collectAsState` 값 대신 `session.onboardingRequired.value`를 직접 읽는다(`postLoginRoute`). 카카오 신규 사용자(`isNewUser`)도 같은 경로로 온보딩에 간다. |
| 2 (중간) | 홈이 탭 재진입 때 갱신되지 않음, 온보딩 뒤 인사말/아바타 비어 있음 | `HomeViewModel.start`가 두 번째 호출부터 화면을 비우지 않고 조용히 갱신(`refresh`). 탭에 들어올 때마다 `LaunchedEffect`가 호출하므로 도착 인증, 온보딩 뒤에도 반영된다. 실패하면 이전 값 유지. |
| 4 | "남은 시도" 중복 | 입력칸 supportingText 제거(오류 문구에 이미 포함). |
| 5 | 재설정 성공 문구가 남음 | 로그인에 성공하면 `resetSucceeded` 해제. |
| 6 | 세션 만료 안내 없음 | `AuthSession.expire()`(재발급 실패, AUTH401)가 `sessionExpired`를 켜고 로그인 화면이 "로그인 시간이 만료되어 다시 로그인해 주세요."를 표시. 로그아웃/탈퇴/로그인 성공 때는 꺼진다. |
| 3 | 오늘 완료한 장소의 버튼 | 상세 응답의 `isCompleted`로 "오늘 완료" 비활성 버튼. |
| 7 | 둘러보기 초기 줌 | 14 -> 15(`BROWSE_ZOOM`). 장소 범위에 맞추는 계산은 하지 않았다. |
| 11 | 로그아웃 확인, 빈 입력 버튼 | 로그아웃 확인 창. "인증 코드 받기"는 이메일이 비면, 온보딩 "저장하기"는 필수값(닉네임, 연령대, 성별)이 비면 비활성. |

## 테스트
- `./gradlew testDebugUnitTest testReleaseUnitTest` 통과: debug 161개, release 160개, 실패 0. `assembleDebug` 성공.
- 새로 추가/바꾼 테스트: `Stage3FixTest` 9개(로그인 직후 경로 2, 세션 만료 안내 4, 코드 받기 버튼, 온보딩 저장 조건, 오늘 완료 상태), `HomeTest` 2개(조용한 갱신, 실패 시 이전 값 유지; 기존 `startRunsOnlyOnce`는 새 동작에 맞게 교체).

## 못 한 것 / 미검증
- Compose 화면 동작(온보딩 이동, 홈 갱신, 다이얼로그, 비활성 버튼)은 에뮬레이터 확인 전이다.
- 둘러보기 줌은 고정 15로 바꾼 것뿐이다. 지도 마커 아이콘, 상태바 겹침, 통계 타일 높이, 단상 쏠림, 닉네임 제안 재시작 유실(8~10, 12, 13번)은 범위 밖이라 손대지 않았다.

## 추가 수정 (재검증 `stage-3-recheck.md` 반영)
- **가입 직후 온보딩 미이동(높음, 원인 확정)**: `NavHost(startDestination = if (loggedIn) ...)`가 `loggedIn` 변화마다 그래프를 새로 만들어 백스택을 홈으로 초기화했다. 시작 화면을 `remember`로 한 번만 정하고, 이동 결정은 `loginStateTarget(loggedIn, route, session)` 순수 함수로 분리해 `resetTo`로만 처리한다.
- **마이 탭 재진입 갱신(중간)**: `MeViewModel.onEnter`, `RecentActivityViewModel.onEnter`가 첫 진입은 건너뛰고 재진입 때 포인트, 닉네임, 기록을 화면을 비우지 않고 갱신한다(실패하면 이전 값 유지).
- **가입하기 버튼(낮음)**: 비밀번호나 닉네임이 비면 비활성(`SignupProfileState.canSubmit`).
- **카카오 닉네임 제안(낮음)**: 메모리 제안값이 없으면(앱 재시작 뒤) 서버의 닉네임으로 채운다. 서버가 카카오 신규 사용자 닉네임을 비워 두면 재시작 뒤에는 빈 칸이다(제안값 자체는 서버에 저장되지 않아 복원 불가).
- 테스트: `Stage3Fix2Test` 5개 추가. debug 166개 / release 165개 통과(실패 0), `assembleDebug` 성공. `NavHost` 재구성 자체는 단위 테스트가 불가능해 이동 결정 로직만 검증했다. 에뮬레이터 확인은 미실시.
