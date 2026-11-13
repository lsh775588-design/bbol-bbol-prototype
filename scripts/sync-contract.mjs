// 서버의 계약 골든(src/test/resources/contract)을 앱 테스트 리소스로 복사한다(미러링: 서버에 없는 파일은 지운다).
// 사용: node scripts/sync-contract.mjs [--server <서버 저장소 경로>]
import fs from 'node:fs';
import path from 'node:path';
import { appContractDir, findServerContractDir, listFiles } from './contract-lib.mjs';

const src = findServerContractDir();
const files = listFiles(src);
fs.mkdirSync(appContractDir, { recursive: true });
for (const f of listFiles(appContractDir)) {
  if (!files.includes(f)) {
    fs.rmSync(path.join(appContractDir, f));
    console.log('삭제: ' + f);
  }
}
for (const f of files) {
  const to = path.join(appContractDir, f);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.writeFileSync(to, fs.readFileSync(path.join(src, f), 'utf8').replace(/\r\n/g, '\n'));
}
console.log(`동기화 완료: ${files.length}개 파일 (${src} -> ${appContractDir})`);
