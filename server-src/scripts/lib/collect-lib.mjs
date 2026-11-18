// 카카오 로컬 API 수집용 순수 함수 모음(네트워크 없음). collect-places.mjs와 테스트가 함께 쓴다.

export const JONGNO_PREFIX = '서울 종로구';

// 종로구를 넉넉히 덮는 사각형(경도 x, 위도 y). 실제 종로구 여부는 주소로 다시 거른다.
export const JONGNO_BOUNDS = { minX: 126.955, minY: 37.565, maxX: 127.02, maxY: 37.635 };

/**
 * 수집 질의 정의. type=category는 카카오 카테고리 그룹 코드, type=keyword는 키워드 검색.
 * accept: 결과의 category_name에 이 중 하나가 들어 있어야 채택한다(키워드 검색의 잡음 제거). 비면 모두 채택.
 * 같은 장소가 여러 질의에 걸리면 먼저 선언된(앞선) 질의의 카테고리가 이긴다.
 */
export const QUERIES = [
  { type: 'category', code: 'CE7', category: 'CAFE', accept: [] },
  { type: 'keyword', query: '스터디카페', category: 'STUDY_CAFE', accept: ['스터디카페'] },
  { type: 'keyword', query: '공원', category: 'PARK', accept: ['공원'] },
  { type: 'keyword', query: '도서관', category: 'LIBRARY', accept: ['도서관'] },
  { type: 'keyword', query: '영화관', category: 'MOVIE', accept: ['영화관'] },
  { type: 'keyword', query: '박물관', category: 'MUSEUM', accept: ['박물관', '미술관'] },
  { type: 'keyword', query: '고궁', category: 'HERITAGE', accept: ['문화유적', '고궁', '사적', '유적'] },
  { type: 'keyword', query: '유적지', category: 'HERITAGE', accept: ['문화유적', '고궁', '사적', '유적'] },
  { type: 'keyword', query: '백화점', category: 'SHOPPING', accept: ['백화점', '쇼핑', '대형마트'] },
  { type: 'keyword', query: '쇼핑센터', category: 'SHOPPING', accept: ['백화점', '쇼핑', '대형마트'] },
];

/** bounds를 cellDeg 크기 사각형들로 나눈다. 각 셀은 {minX,minY,maxX,maxY}. */
export function buildGrid(bounds, cellDeg) {
  const cells = [];
  const cols = Math.ceil((bounds.maxX - bounds.minX) / cellDeg - 1e-9);
  const rows = Math.ceil((bounds.maxY - bounds.minY) / cellDeg - 1e-9);
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      cells.push({
        minX: round6(bounds.minX + c * cellDeg),
        minY: round6(bounds.minY + r * cellDeg),
        maxX: round6(Math.min(bounds.maxX, bounds.minX + (c + 1) * cellDeg)),
        maxY: round6(Math.min(bounds.maxY, bounds.minY + (r + 1) * cellDeg)),
      });
    }
  }
  return cells;
}

/** 셀을 4등분한다(결과가 45개 상한에 걸릴 때 재귀 분할용). */
export function splitCell(cell) {
  const mx = round6((cell.minX + cell.maxX) / 2);
  const my = round6((cell.minY + cell.maxY) / 2);
  return [
    { minX: cell.minX, minY: cell.minY, maxX: mx, maxY: my },
    { minX: mx, minY: cell.minY, maxX: cell.maxX, maxY: my },
    { minX: cell.minX, minY: my, maxX: mx, maxY: cell.maxY },
    { minX: mx, minY: my, maxX: cell.maxX, maxY: cell.maxY },
  ];
}

/** 카카오 rect 파라미터: "minX,minY,maxX,maxY" (경도,위도 순). */
export function rectParam(cell) {
  return `${cell.minX},${cell.minY},${cell.maxX},${cell.maxY}`;
}

export function toHttps(url) {
  return typeof url === 'string' ? url.replace(/^http:\/\//i, 'https://') : url;
}

export function isJongno(doc) {
  const addr = doc.address_name || '';
  const road = doc.road_address_name || '';
  return addr.startsWith(JONGNO_PREFIX) || road.startsWith(JONGNO_PREFIX);
}

/** 이 질의에서 이 문서를 채택하는가. */
export function accepts(query, doc) {
  if (!query.accept || query.accept.length === 0) return true;
  const cat = doc.category_name || '';
  return query.accept.some((h) => cat.includes(h));
}

/** 카카오 문서 -> 우리 장소 레코드. 좌표나 id가 비정상이면 null. */
export function normalizeDoc(doc, category) {
  const lat = Number(doc.y);
  const lng = Number(doc.x);
  if (!doc.id || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return {
    kakaoPlaceId: String(doc.id),
    name: doc.place_name,
    category,
    address: doc.address_name,
    roadAddress: doc.road_address_name || null,
    phone: doc.phone || null,
    lat,
    lng,
    kakaoPlaceUrl: toHttps(doc.place_url) || `https://place.map.kakao.com/${doc.id}`,
  };
}

/**
 * (문서, 질의) 쌍들에서 종로구 장소만 골라 카카오 id로 중복 제거한다. 먼저 나온 항목이 이긴다.
 * @param pairs [{doc, query}]
 */
export function collectPlaces(pairs) {
  const byId = new Map();
  for (const { doc, query } of pairs) {
    if (!isJongno(doc) || !accepts(query, doc)) continue;
    const place = normalizeDoc(doc, query.category);
    if (place && !byId.has(place.kakaoPlaceId)) byId.set(place.kakaoPlaceId, place);
  }
  return [...byId.values()].sort((a, b) => Number(a.kakaoPlaceId) - Number(b.kakaoPlaceId));
}

function sqlStr(v) {
  return v == null ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`;
}

/** 수집 결과 -> 검토용 SQL 시드(INSERT). 반경, 점수, 추천 여부는 기본값(검토 때 사람이 고친다). */
export function toSql(places) {
  const lines = [
    '-- 카카오 로컬 API 수집 결과(검토용). 폐업 제거, 장소별 radius/notify_radius/reward_point, 뽈뽈 추천(hot_type)은 검토하며 수정한다.',
    '-- 기본값: radius_m=100, notify_radius_m=200, reward_point=NULL(카테고리 점수), hot_type=NULL',
  ];
  for (const p of places) {
    lines.push(
      'INSERT INTO places (category_id, name, address, road_address, phone, lat, lng, radius_m, notify_radius_m, ' +
        'reward_point, kakao_place_id, kakao_place_url, hot_type, active) VALUES (' +
        `(SELECT id FROM place_categories WHERE code = ${sqlStr(p.category)}), ${sqlStr(p.name)}, ${sqlStr(p.address)}, ` +
        `${sqlStr(p.roadAddress)}, ${sqlStr(p.phone)}, ${p.lat}, ${p.lng}, 100, 200, NULL, ` +
        `${sqlStr(p.kakaoPlaceId)}, ${sqlStr(p.kakaoPlaceUrl)}, NULL, TRUE);`,
    );
  }
  return lines.join('\n') + '\n';
}

function round6(n) {
  return Math.round(n * 1e6) / 1e6;
}
