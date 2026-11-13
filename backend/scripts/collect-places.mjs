#!/usr/bin/env node
// 서울 종로구 장소를 카카오 로컬 API로 한 번 수집한다(Gray Area A2). 사용법은 scripts/README.md.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  JONGNO_BOUNDS, QUERIES, buildGrid, collectPlaces, rectParam, splitCell, toSql,
} from './lib/collect-lib.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const OUT_JSON = resolve(here, 'out/places.json');
const OUT_SQL = resolve(here, 'out/places.sql');

const API = 'https://dapi.kakao.com/v2/local/search';
const PAGE_SIZE = 15;      // 카카오 최대
const MAX_PAGES = 3;       // 15 x 3 = 45
const CAP = 45;            // 질의당 최대 노출 개수
const CELL_DEG = 0.01;     // 초기 셀(약 1km)
const MIN_CELL_DEG = 0.0005; // 이보다 작게는 더 쪼개지 않는다
const DELAY_MS = 150;
const MAX_RETRY = 5;

function parseArgs(argv) {
  const a = { dryRun: false, sqlOnly: false, in: OUT_JSON, out: OUT_JSON, sqlOut: OUT_SQL };
  for (let i = 0; i < argv.length; i++) {
    const v = argv[i];
    if (v === '--dry-run') a.dryRun = true;
    else if (v === '--sql') a.sqlOnly = true;
    else if (v === '--in') a.in = resolve(argv[++i]);
    else if (v === '--out') a.out = resolve(argv[++i]);
    else if (v === '--sql-out') a.sqlOut = resolve(argv[++i]);
    else if (v === '-h' || v === '--help') {
      console.log('사용법: node collect-places.mjs [--dry-run] | [--sql [--in places.json] [--sql-out places.sql]]');
      process.exit(0);
    } else {
      console.error(`알 수 없는 옵션: ${v}`);
      process.exit(2);
    }
  }
  return a;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function kakaoGet(key, path, params) {
  const url = `${API}/${path}?${new URLSearchParams(params)}`;
  for (let attempt = 0; ; attempt++) {
    await sleep(DELAY_MS);
    let res;
    try {
      res = await fetch(url, { headers: { Authorization: `KakaoAK ${key}` } });
    } catch (e) {
      if (attempt >= MAX_RETRY) throw e;
      await sleep(500 * 2 ** attempt);
      continue;
    }
    if (res.ok) return res.json();
    if ((res.status === 429 || res.status >= 500) && attempt < MAX_RETRY) {
      await sleep(1000 * 2 ** attempt);
      continue;
    }
    // 키 값은 출력하지 않는다.
    throw new Error(`카카오 API 오류 HTTP ${res.status} (${path})`);
  }
}

function baseParams(query, cell) {
  const p = { rect: rectParam(cell), size: String(PAGE_SIZE) };
  if (query.type === 'category') p.category_group_code = query.code;
  else p.query = query.query;
  return p;
}

/** 한 셀을 질의한다. 결과가 45개 상한에 걸리면 4등분해 재귀한다. [{doc, query}]를 돌려준다. */
async function searchCell(key, query, cell, stats) {
  const path = query.type === 'category' ? 'category.json' : 'keyword.json';
  const first = await kakaoGet(key, path, { ...baseParams(query, cell), page: '1' });
  stats.calls++;
  const total = first.meta?.total_count ?? 0;
  const size = cell.maxX - cell.minX;
  if (total > CAP && size / 2 >= MIN_CELL_DEG) {
    stats.splits++;
    const out = [];
    for (const sub of splitCell(cell)) out.push(...(await searchCell(key, query, sub, stats)));
    return out;
  }
  if (total > CAP) stats.capped++; // 더 쪼갤 수 없는 셀: 일부 누락 가능
  const docs = [...(first.documents ?? [])];
  let end = first.meta?.is_end ?? true;
  for (let page = 2; !end && page <= MAX_PAGES; page++) {
    const r = await kakaoGet(key, path, { ...baseParams(query, cell), page: String(page) });
    stats.calls++;
    docs.push(...(r.documents ?? []));
    end = r.meta?.is_end ?? true;
  }
  return docs.map((doc) => ({ doc, query }));
}

async function collect(key) {
  const cells = buildGrid(JONGNO_BOUNDS, CELL_DEG);
  const stats = { calls: 0, splits: 0, capped: 0 };
  const pairs = [];
  for (const query of QUERIES) {
    const label = query.type === 'category' ? query.code : query.query;
    for (const cell of cells) pairs.push(...(await searchCell(key, query, cell, stats)));
    console.log(`질의 완료: ${label} (누적 원본 ${pairs.length}건, API 호출 ${stats.calls}회)`);
  }
  return { places: collectPlaces(pairs), stats };
}

function writeJson(path, places, stats) {
  mkdirSync(dirname(path), { recursive: true });
  const body = { collectedAt: new Date().toISOString(), district: '서울 종로구', stats, places };
  writeFileSync(path, JSON.stringify(body, null, 2) + '\n', 'utf8');
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.sqlOnly) {
    const data = JSON.parse(readFileSync(args.in, 'utf8'));
    mkdirSync(dirname(args.sqlOut), { recursive: true });
    writeFileSync(args.sqlOut, toSql(data.places), 'utf8');
    console.log(`SQL 시드 ${data.places.length}건을 ${args.sqlOut}에 썼습니다. 검토 후 사용하세요.`);
    return;
  }

  if (args.dryRun) {
    const cells = buildGrid(JONGNO_BOUNDS, CELL_DEG);
    console.log('[dry-run] 네트워크 호출 없음');
    console.log(`초기 셀 ${cells.length}개 x 질의 ${QUERIES.length}개 = 최소 ${cells.length * QUERIES.length}회 첫 페이지 호출`);
    console.log(`(45개 상한에 걸린 셀은 4등분되어 호출이 늘어납니다, 최소 셀 ${MIN_CELL_DEG}도)`);
    QUERIES.forEach((q) => console.log(` - ${q.type === 'category' ? `카테고리 ${q.code}` : `키워드 "${q.query}"`} -> ${q.category}`));
    console.log(`출력: ${args.out}`);
    return;
  }

  const key = process.env.KAKAO_REST_API_KEY;
  if (!key) {
    console.error('환경변수 KAKAO_REST_API_KEY가 없습니다. 카카오 디벨로퍼스의 REST API 키를 환경변수로 설정한 뒤 다시 실행하세요.');
    console.error('예: KAKAO_REST_API_KEY=... node scripts/collect-places.mjs   (키를 파일이나 코드에 적지 마세요)');
    process.exit(1);
  }

  const { places, stats } = await collect(key);
  writeJson(args.out, places, stats);
  console.log(`수집 완료: 종로구 장소 ${places.length}건 -> ${args.out}`);
  if (stats.capped > 0) console.warn(`경고: 45개 상한을 못 벗어난 셀 ${stats.capped}개(일부 누락 가능)`);
  console.log('SQL 시드가 필요하면: node scripts/collect-places.mjs --sql');
}

main().catch((e) => {
  console.error(`실패: ${e.message}`);
  process.exit(1);
});
