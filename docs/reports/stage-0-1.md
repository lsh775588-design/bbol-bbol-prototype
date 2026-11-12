# 0-1단계 보고서: repo 뼈대

- 일자: 2026-11-12
- 담당: 오케스트레이터
- 브랜치: `main` (초기 커밋 1개, 로컬 전용)

## 한 일
- `C:\dev\bbolbbol`을 만들고 `git init -b main`을 했다.
- 루트 파일 `.gitignore`, `.env.example`, `README.md`를 만들었다.
- `backend/`, `app/`, `docs/` 폴더를 만들었다. `app/`은 `.gitkeep`만 있는 빈 폴더다.
- 초기 커밋 `70cc359 chore: 프로젝트 뼈대 추가`를 만들었다. 작성자는 이세현이고 이메일은 noreply 주소다. Claude 관련 문구와 `Co-Authored-By` 줄은 넣지 않았다.

## 바뀐 파일
`.gitignore`, `.env.example`, `README.md`, `app/.gitkeep`, `backend/.gitkeep`, `docs/.gitkeep`

## 테스트 결과
해당 없음(코드 없음). `.env`가 `.gitignore`에 들어 있는 것을 확인했다. `.env.example`에는 변수 이름만 있고 값은 비어 있다(`JWT_EXPIRATION`, `MYSQL_DATABASE`, `MYSQL_PORT`, `REDIS_PORT`의 기본값 제외).

## 결정이 필요한 것
- `.gitattributes`(줄바꿈 LF 고정)를 추가할지. 이 PC는 git `autocrlf` 설정 때문에 LF가 CRLF로 바뀐다는 경고가 뜬다. Docker나 리눅스 CI를 쓸 계획이면 추가를 권한다.

## 미검증
- GitHub 원격 저장소는 연결하지 않았다.
