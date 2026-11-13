import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import {
  JONGNO_BOUNDS, accepts, buildGrid, collectPlaces, isJongno, normalizeDoc, rectParam, splitCell, toHttps, toSql,
} from '../lib/collect-lib.mjs';

const pairs = JSON.parse(readFileSync(new URL('../fixtures/kakao-sample.json', import.meta.url), 'utf8'));

test('buildGrid는 경계를 빈틈없이 덮는다', () => {
  const b = { minX: 0, minY: 0, maxX: 0.025, maxY: 0.01 };
  const cells = buildGrid(b, 0.01);
  assert.equal(cells.length, 3);
  assert.equal(cells[0].minX, 0);
  assert.equal(cells.at(-1).maxX, 0.025);
  assert.ok(cells.every((c) => c.maxY === 0.01));
});

test('종로구 경계의 셀 수는 양수이고 모든 셀이 경계 안에 있다', () => {
  const cells = buildGrid(JONGNO_BOUNDS, 0.01);
  assert.ok(cells.length > 10);
  assert.ok(cells.every((c) => c.minX >= JONGNO_BOUNDS.minX && c.maxX <= JONGNO_BOUNDS.maxX));
});

test('splitCell은 4등분하고 합집합이 원래 셀이다', () => {
  const parts = splitCell({ minX: 0, minY: 0, maxX: 2, maxY: 2 });
  assert.equal(parts.length, 4);
  assert.deepEqual(parts[0], { minX: 0, minY: 0, maxX: 1, maxY: 1 });
  assert.deepEqual(parts[3], { minX: 1, minY: 1, maxX: 2, maxY: 2 });
});

test('rectParam은 경도,위도 순서', () => {
  assert.equal(rectParam({ minX: 126.9, minY: 37.5, maxX: 127, maxY: 37.6 }), '126.9,37.5,127,37.6');
});

test('toHttps와 isJongno', () => {
  assert.equal(toHttps('http://place.map.kakao.com/1'), 'https://place.map.kakao.com/1');
  assert.equal(toHttps('https://x'), 'https://x');
  assert.equal(isJongno({ address_name: '서울 종로구 사직동 1' }), true);
  assert.equal(isJongno({ address_name: '서울 중구 명동', road_address_name: '서울 종로구 어딘가' }), true);
  assert.equal(isJongno({ address_name: '서울 중구 명동' }), false);
});

test('accepts는 category_name 힌트로 잡음을 거른다', () => {
  const q = { accept: ['공원'] };
  assert.equal(accepts(q, { category_name: '여행 > 공원' }), true);
  assert.equal(accepts(q, { category_name: '가정,생활 > 편의점' }), false);
  assert.equal(accepts({ accept: [] }, { category_name: '무엇이든' }), true);
});

test('normalizeDoc은 좌표 숫자 변환, https, 빈 값 null', () => {
  const p = normalizeDoc(pairs[1].doc, 'PARK');
  assert.equal(p.kakaoPlaceId, '1002');
  assert.equal(p.lat, 37.57);
  assert.equal(p.lng, 126.98);
  assert.equal(p.phone, null);
  assert.equal(p.roadAddress, null);
  assert.equal(p.kakaoPlaceUrl, 'https://place.map.kakao.com/1002');
  assert.equal(normalizeDoc({ id: '1', x: 'abc', y: '37' }, 'CAFE'), null);
});

test('collectPlaces: 종로구만, 잡음 제외, id 중복 제거(먼저 나온 것 우선)', () => {
  const places = collectPlaces(pairs);
  assert.deepEqual(places.map((p) => p.kakaoPlaceId), ['1001', '1002', '1005']);
  assert.equal(places[0].name, '테스트카페');
  assert.equal(places[0].category, 'CAFE');
});

test('toSql: 따옴표 이스케이프, NULL, 카테고리 서브쿼리', () => {
  const sql = toSql(collectPlaces(pairs));
  assert.match(sql, /작은 ''박물관''/);
  assert.match(sql, /SELECT id FROM place_categories WHERE code = 'MUSEUM'/);
  assert.match(sql, /'1002'.*NULL, TRUE\);/);
  assert.equal(sql.split('\n').filter((l) => l.startsWith('INSERT')).length, 3);
});
