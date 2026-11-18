# 카카오 종로구 장소 수집 스크립트

Gray Area A2: 서울 종로구 장소를 카카오 로컬 API로 **한 번** 수집해 DB에 넣고 팀이 검토한다. 서버는 실시간으로 카카오를 호출하지 않는다.
Node 18+ 필요(내장 `fetch` 사용). 외부 패키지 없음.

## 사용법 (저장소 루트에서)

```bash
# 1) 계획만 확인(네트워크, 키 불필요)
node scripts/collect-places.mjs --dry-run

# 2) 수집(키는 환경변수로만. 파일, 코드, 로그에 적지 않는다)
KAKAO_REST_API_KEY=<REST API 키> node scripts/collect-places.mjs
#    -> scripts/out/places.json

# 3) 검토용 SQL 시드 생성(네트워크 불필요)
node scripts/collect-places.mjs --sql
#    -> scripts/out/places.sql

# 단위 테스트(네트워크 없음)
cd scripts && node --test
```

옵션: `--out <json>`, `--in <json>`, `--sql-out <sql>`. 키가 없으면 한국어 안내와 함께 종료 코드 1로 끝난다(`--dry-run`, `--sql` 제외).
`scripts/out/`은 git에서 제외된다(`scripts/.gitignore`).

## 동작
- 카카오 검색은 질의당 최대 45개(15개 x 3페이지)만 돌려주므로, 종로구를 감싸는 사각형을 약 0.01도(약 1km) 셀로 나눠 셀마다 질의한다.
  `meta.total_count`가 45를 넘는 셀은 4등분해 재귀하고, 최소 셀(0.0005도)에서도 넘으면 경고를 출력한다(일부 누락 가능).
- 질의: 카페는 카테고리 그룹 `CE7`, 나머지(스터디카페, 공원, 도서관, 영화관, 박물관, 고궁/유적지, 백화점/쇼핑센터)는 키워드 검색이다. 정의는 `scripts/lib/collect-lib.mjs`의 `QUERIES`.
  키워드 결과는 `category_name`에 힌트 문자열이 있는 것만 채택한다(잡음 제거).
- 카카오 장소 id로 중복을 제거하고(먼저 선언된 질의의 카테고리가 우선), 주소가 `서울 종로구`로 시작하는 것만 남긴다.
- `kakaoPlaceUrl`은 https로 바꿔 저장한다.
- 호출 사이 150ms 대기, 429와 5xx는 지수 백오프로 최대 5회 재시도.
- 카테고리 코드는 `place_categories`(CAFE, PARK, SHOPPING, MOVIE, LIBRARY, STUDY_CAFE, MUSEUM, HERITAGE)와 같다.

## 수집 후 검토할 것
`places.sql`은 기본값(`radius_m=100`, `notify_radius_m=200`, `reward_point=NULL`, `hot_type=NULL`)으로 만든다. 검토하며 다음을 고친다.
폐업 장소 제거, 궁궐과 큰 공원의 반경(300/400), 특별 카페 `reward_point=150`, 뽈뽈 추천 15곳 `hot_type='CURATED'`.
확정된 시드는 `src/main/resources/db/seed-local`(개발용) 또는 V900+ 기준 데이터 마이그레이션에 둔다(PostgreSQL 17과 H2 PostgreSQL 모드에서 모두 돌아야 한다).

## 검증 상태
- 순수 함수(그리드, 분할, 중복 제거, 종로구 필터, SQL 변환)는 `node --test`로 검증했다.
- **실제 카카오 API 호출은 검증하지 못했다(UNVERIFIED)**: 이 환경에 키가 없다. 첫 실행 때 응답 필드(`total_count`, `is_end`, `category_name`)와 키워드 힌트의 잡음/누락을 확인한다.
