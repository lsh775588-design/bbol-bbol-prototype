# 0단계 에뮬레이터 스크린샷

촬영 환경: Android 에뮬레이터 `Medium_Phone`, Chrome. 서버는 PC에서 `local` 프로파일(H2)로 실행했고, 에뮬레이터는 `http://10.0.2.2:8080`으로 접속했다. 앱이 아직 없어서 브라우저 화면이다. 촬영일 2026-11-13.

| 파일 | 내용 |
|---|---|
| `01-health.png` | `GET /health` 응답. `{"success":true,"code":"COMMON200","message":"요청이 성공했습니다.","data":{"status":"UP"}}`. 에뮬레이터에서 PC의 로컬 서버로 연결되는 것과 공통 응답 형식을 보여준다. |
| `02-unknown-path-404.png` | 없는 경로 `GET /places` 응답. `{"success":false,"code":"COMMON404","message":"존재하지 않는 경로","data":null}`. 0-4에서 고친 404 매핑(전에는 500)을 보여준다. |
| `03-swagger-ui.png` | Swagger UI(`/swagger-ui/index.html`). 지금은 `GET /health` 하나만 있다. 명세상 공개 경로다. |

## 알아둘 점
- `02`에서 401(AUTH401)이 아니라 404가 나온 것은 `local` 프로파일의 개발 전용 고정 사용자 필터(`bbolbbol.local.fixed-user-id: 1`) 때문이다. 토큰이 없어도 사용자 1로 인증되어 보안을 통과하고 404까지 간다. 운영 설정에서는 토큰이 없으면 401이다(자동 테스트로 확인됨, 이 화면은 아니다).
- 스크린샷에서 주소창에 경고 아이콘이 보이는 것은 HTTP(암호화 없음) 접속이기 때문이다. 개발용이다.
