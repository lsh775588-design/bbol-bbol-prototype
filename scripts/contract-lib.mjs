// 서버 계약 골든(contract/) 위치 찾기와 해시 비교. sync-contract.mjs, check-contract.mjs가 함께 쓴다.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const appContractDir = path.join(repoRoot, 'app', 'app', 'src', 'test', 'resources', 'contract');
const SERVER_REL = path.join('src', 'test', 'resources', 'contract');

/** 서버 저장소 위치: --server <경로> > 환경변수 BBOL_SERVER_DIR > 자동 탐색(같은 이름의 서버 worktree, server/). */
export function findServerContractDir() {
  const args = process.argv.slice(2);
  const i = args.indexOf('--server');
  const base = path.basename(repoRoot);
  const candidates = [
    i >= 0 ? args[i + 1] : null,
    process.env.BBOL_SERVER_DIR,
    path.join(repoRoot, '..', '..', 'server', '.worktrees', base),
    path.join(repoRoot, '..', '..', 'server'),
    path.join(repoRoot, '..', 'server'),
    path.join(repoRoot, 'server'),
  ].filter(Boolean);
  for (const c of candidates) {
    const d = path.join(path.resolve(c), SERVER_REL);
    if (fs.existsSync(d)) return d;
  }
  console.error('서버 계약 폴더를 찾지 못했다. --server <서버 저장소 경로> 또는 BBOL_SERVER_DIR 를 지정하라.');
  console.error('찾아본 곳: ' + candidates.join(', '));
  process.exit(2);
}

export function listFiles(dir) {
  const out = [];
  const walk = (d) => {
    if (!fs.existsSync(d)) return;
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else out.push(path.relative(dir, p).split(path.sep).join('/'));
    }
  };
  walk(dir);
  return out.sort();
}

/** CRLF/LF 차이로 해시가 달라지지 않게 줄바꿈을 LF로 맞춰 해시한다. */
export function hashOf(file) {
  const text = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
  return crypto.createHash('sha256').update(text).digest('hex');
}
