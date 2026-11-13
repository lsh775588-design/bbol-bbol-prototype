# 3단계 수정 재검증 (에뮬레이터) 보고서

- 일자: 2026-11-14. 앱: `feat/7-recheck`(= `feat/2-integration`, 수정 병합본) 디버그 빌드, 서버: local 프로파일, `JWT_EXPIRATION=75s`, 고정 사용자 비움, 포트 8120. 도중에 서버를 한 번 다시 띄웠다(세션 만료 확인과 요청 로그용 `DispatcherServlet=DEBUG`). 위치는 경복궁 고정. 앱 데이터는 `pm clear`로 처음부터 시작했고 위치 권한은 `pm grant`로 줬다.
- 시스템 사진 선택기 화면은 저장하지 않았다.

| 항목 | 내용 | 결과 | 스크린샷 |
|---|---|---|---|
| 1 | 이메일 가입 직후 온보딩으로 이동 | **실패**: 홈으로 감(서버는 isNewUser true). 앱을 껐다 켜면 온보딩이 나옴 | 06-after-signup-went-home-BUG, 07-onboarding-save-disabled |
| 1 | 온보딩 완료 후 홈 인사말/아바타에 닉네임 | OK (Recheck1 / R) | 09-after-onboarding |
| 2 | 개발 카카오 로그인 `90002` 후 온보딩 | **실패**: 홈으로 감, 인사말 비고 아바타 "?"(재시작 후 온보딩, 완료하면 홈이 닉네임으로 갱신됨) | 20-kakao-went-home-no-nickname-BUG, 23-kakao-home-after-onboarding |
| 3 | 도착 인증 후 홈 탭 값 | OK: 홈 300P/1회로 갱신 | 15-home-after-arrival |
| 3 | 같은 시점 마이 탭 값 | **실패**: 마이 탭이 0P/0회/기록 없음으로 남음(탭 재진입 때 갱신 안 됨, 앱 재시작 후 300P/1회) | 16-my-stale-0P-BUG, 17-my-after-restart-300P |
| 4 | 오늘 완료한 장소 | OK: "오늘 완료" 비활성 버튼, "오늘 완료" 칩 | 14-completed-disabled-button |
| 5 | 틀린 이메일 코드 | OK: "남은 시도 4번" 한 줄만 | 04-wrong-code |
| 6 | 로그아웃 확인 창 | OK: 취소/로그아웃, 로그아웃 후 만료 문구 없이 로그인 화면 | 10-logout-confirm, 18-after-logout |
| 7 | 75초 경과 후 요청 | OK: 로그아웃 안 됨, 서버 로그에 `POST /auth/reissue` 1번(05:24:10) 후 요청들 정상 | 22-after-expiry-no-logout |
| 7 | 서버 재시작 후 요청(재발급 실패) | OK: 로그인 화면에 "로그인 시간이 만료되어 다시 로그인해 주세요." | 21-session-expired-after-restart |
| 8 | 빈 입력의 버튼 | 부분 OK: 로그인, 인증 코드 받기, 인증하기, 온보딩 저장하기는 비활성. **가입하기는 비밀번호/닉네임이 비어도 켜져 있음** | 01-login-disabled, 02-signup-empty, 05-signup-submit-enabled-when-empty |
| - | logcat | 앱(com.bbolbbol.app) 크래시 없음(`-b crash`와 `*:E`에서 앱 관련 항목 없음; 11-13 날짜의 gms 항목만 있음) | - |

## 남은 버그
1. **가입/카카오 로그인 직후에도 온보딩이 아니라 홈으로 간다**(이메일 1/1, 카카오 2/2). 수정 의도(`postLoginRoute`가 `session.onboardingRequired.value`를 읽음)는 코드상 맞아 보인다. 추정 원인(미확인): `AppNav`의 `NavHost(startDestination = if (loggedIn) HOME else LOGIN)`이 로그인 순간 바뀌면서 그래프가 다시 만들어져 HOME으로 초기화되고, `resetTo(ONBOARDING)`를 덮어쓴다. 시작 destination을 고정하거나 로그인 상태에 따라 바꾸지 않는 쪽을 확인해 볼 것.
2. 마이 탭이 재진입 때 갱신되지 않는다(홈은 갱신). 같은 방식의 조용한 갱신이 필요하다.
3. 가입하기 버튼이 빈 입력에서 활성이다.
4. 카카오 신규 사용자는 온보딩 전까지 닉네임이 비어 홈 인사말이 빈다(1번이 고쳐지면 온보딩에서 정하므로 영향 줄어듦). 카카오 온보딩 화면의 닉네임 칸은 비어 있었다(제안값 없음, 기존 13번 그대로).

## 참고
- 재시작 후 온보딩에서 한 번 저장하자 홈에 닉네임이 바로 반영됐다(스크린샷은 요청 완료 전에 찍으면 빈 값이 보이니 몇 초 기다릴 것).
- 서버는 기본 설정에서 요청 로그를 남기지 않아 재발급 횟수 확인에는 `DispatcherServlet=DEBUG`가 필요했다.
