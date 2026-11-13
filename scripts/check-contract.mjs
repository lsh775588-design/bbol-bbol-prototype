// 서버 골든과 앱 쪽 복사본이 같은지 해시로 비교한다. 다르면 종료 코드 1.
// 사용: node scripts/check-contract.mjs [--server <서버 저장소 경로>]
import path from 'node:path';
import { appContractDir, findServerContractDir, hashOf, listFiles } from './contract-lib.mjs';

const src = findServerContractDir();
const a = listFiles(src);
const b = listFiles(appContractDir);
const problems = [];
for (const f of a) {
  if (!b.includes(f)) problems.push(`앱에 없음: ${f}`);
  else if (hashOf(path.join(src, f)) !== hashOf(path.join(appContractDir, f))) problems.push(`내용이 다름: ${f}`);
}
for (const f of b) if (!a.includes(f)) problems.push(`서버에 없음(앱에만 있음): ${f}`);
if (problems.length) {
  console.error('계약 골든이 서버와 앱에서 다르다:\n  ' + problems.join('\n  '));
  console.error('서버 골든이 기준이다. node scripts/sync-contract.mjs 로 앱 복사본을 갱신하라.');
  process.exit(1);
}
console.log(`계약 골든 일치: ${a.length}개 파일`);
