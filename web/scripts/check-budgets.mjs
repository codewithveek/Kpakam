// Enforces the PRD bundle budgets on web/dist. Run after `vite build`.
// Usage: node scripts/check-budgets.mjs
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const DIST = new URL('../dist/', import.meta.url).pathname;
const KB = 1024;
const BUDGETS = {
  shellJsKb: 170, // PRD: initial JS (gzip), shell route
  routeChunkKb: 60, // PRD: largest route chunk (gzip)
};
const ROUTES = ['src/features/ledger/index.ts', 'src/features/customers/index.ts', 'src/features/settings/index.ts', 'src/features/insights/index.ts'];

const manifest = JSON.parse(readFileSync(join(DIST, '.vite/manifest.json'), 'utf8'));
const gz = (file) => gzipSync(readFileSync(join(DIST, file))).length;

/** Collects a chunk and its static imports (what the browser must load before running it). */
function closure(key, seen = new Set()) {
  if (seen.has(key)) return seen;
  seen.add(key);
  for (const dep of manifest[key]?.imports ?? []) closure(dep, seen);
  return seen;
}

const sizeOf = (keys) => [...keys].reduce((sum, key) => sum + gz(manifest[key].file), 0);
const entry = Object.keys(manifest).find((key) => manifest[key].isEntry);
const shellKeys = closure(entry);
const shell = sizeOf(shellKeys);
const bootKey = Object.keys(manifest).find((key) => key.endsWith('app/bootstrap.ts'));
const boot = bootKey ? sizeOf(closure(bootKey, new Set(shellKeys))) : shell;

const routes = ROUTES.filter((key) => manifest[key]).map((key) => {
  const own = [...closure(key)].filter((k) => !shellKeys.has(k));
  return { route: key.split('/')[2], kb: sizeOf(own) / KB };
});

const rows = [
  ['Shell JS (entry + static imports)', shell / KB, BUDGETS.shellJsKb],
  ...routes.map((r) => [`Route chunk: ${r.route}`, r.kb, BUDGETS.routeChunkKb]),
];
let failed = false;
for (const [name, kb, limit] of rows) {
  const ok = kb <= limit;
  failed ||= !ok;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${name.padEnd(40)} ${kb.toFixed(1).padStart(7)} KB  (budget ${limit} KB)`);
}
console.log(`info Boot JS incl. Firebase (loaded after first paint) ${(boot / KB).toFixed(1)} KB`);
process.exit(failed ? 1 : 0);
