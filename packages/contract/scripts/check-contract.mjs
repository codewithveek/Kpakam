// Checks every file in @kpakam/contract. Run: npm run check -w @kpakam/contract
// Prints a short summary. Exits with code 1 when any check fails.
import { readFileSync, readdirSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { scoreLanguage } from './score-language.mjs';
import { scoreGolden } from './score-golden.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');
const readJson = (rel) => JSON.parse(read(rel));

const failures = [];
const summary = [];
const fail = (where, msg) => failures.push(`${where}: ${msg}`);
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

function section(name, fn) {
  const before = failures.length;
  let detail = '';
  try {
    detail = fn() ?? '';
  } catch (err) {
    fail(name, `crashed: ${err.stack ?? err}`);
  }
  const n = failures.length - before;
  summary.push(`${n === 0 ? 'ok  ' : 'FAIL'} ${name}${detail ? ` (${detail})` : ''}${n ? ` - ${n} problem(s)` : ''}`);
}

// ---------------------------------------------------------------- schemas
const ajv = new Ajv2020({ allErrors: true, strict: true, allowUnionTypes: true });
addFormats(ajv);
let validateDraft = () => false;
const ajvErrors = (validate) => ajv.errorsText(validate.errors, { separator: '; ' });

section('schemas compile', () => {
  validateDraft = ajv.compile(readJson('schemas/draft.schema.json'));
  ajv.compile(readJson('schemas/journal.schema.json'));
  return 'draft, journal';
});

// ---------------------------------------------------------------- golden sets
const GOLDEN = [
  { file: 'golden/en.jsonl', prefix: 'en', lines: 60, min: { multi: 8, clarify: 3, injection: 2 } },
  { file: 'golden/pcm.jsonl', prefix: 'pcm', lines: 40, min: { multi: 6, clarify: 2, injection: 2, yoruba: 4 } },
];
const TAGS = new Set(['sale', 'credit_sale', 'repayment', 'expense', 'multi', 'each', 'clarify', 'injection', 'yoruba', 'decimal', 'large']);
const KINDS = ['sale', 'credit_sale', 'repayment', 'expense'];
const LINE_KEYS = ['id', 'text', 'tags', 'expected'];
const DRAFT_KEYS = ['kind', 'item', 'quantity', 'unit', 'totalNaira', 'paidNaira', 'customerName', 'confidence'];
const MAX_TEXT = 280;
const tagCounts = {};

function checkGoldenLine(where, line, prefix, index) {
  const keys = Object.keys(line);
  if (keys.length !== LINE_KEYS.length || !LINE_KEYS.every((k) => keys.includes(k))) {
    fail(where, `keys must be exactly ${LINE_KEYS.join(', ')}; got ${keys.join(', ')}`);
  }
  const wantId = `${prefix}-${String(index).padStart(3, '0')}`;
  if (line.id !== wantId) fail(where, `id must be "${wantId}" (got ${JSON.stringify(line.id)})`);
  if (typeof line.text !== 'string' || line.text.trim() === '') fail(where, 'text must be a non-empty string');
  else {
    if (line.text.length > MAX_TEXT) fail(where, `text has ${line.text.length} chars (max ${MAX_TEXT})`);
    if (line.text !== line.text.normalize('NFC')) fail(where, 'text must be Unicode NFC');
  }
  if (!Array.isArray(line.tags) || line.tags.length === 0) fail(where, 'tags must be a non-empty array');
  const tags = new Set(Array.isArray(line.tags) ? line.tags : []);
  if (tags.size !== (line.tags?.length ?? 0)) fail(where, 'tags must be unique');
  for (const t of tags) if (!TAGS.has(t)) fail(where, `unknown tag ${JSON.stringify(t)}`);

  const exp = line.expected;
  if (!validateDraft(exp)) {
    fail(where, `expected is not valid per draft.schema.json: ${ajvErrors(validateDraft)}`);
    return tags;
  }
  // House rules for golden drafts (see golden/README.md).
  exp.drafts.forEach((d, i) => {
    const w = `${where} draft ${i}`;
    const missing = DRAFT_KEYS.filter((k) => !(k in d));
    if (missing.length) fail(w, `must set every field explicitly; missing ${missing.join(', ')}`);
    if (d.confidence !== 1) fail(w, 'confidence must be 1');
    if (d.kind === 'credit_sale') {
      if (typeof d.paidNaira !== 'number') fail(w, 'credit_sale needs a numeric paidNaira');
      else if (d.paidNaira >= d.totalNaira) fail(w, 'credit_sale with paidNaira >= totalNaira must be a sale');
    } else if (d.paidNaira !== null) fail(w, 'paidNaira must be null unless kind is credit_sale');
    if ((d.kind === 'credit_sale' || d.kind === 'repayment') && !d.customerName) {
      fail(w, `${d.kind} needs customerName`);
    }
    if (d.kind === 'repayment' && (d.item !== null || d.quantity !== null || d.unit !== null)) {
      fail(w, 'repayment must have null item, quantity and unit');
    }
    if (d.item !== null && d.item !== d.item.toLowerCase()) fail(w, 'item must be lower case');
  });
  const clarify = exp.drafts.length === 0;
  const hasQuestion = typeof exp.needsClarification === 'string' && exp.needsClarification.trim() !== '';
  if (clarify !== hasQuestion) fail(where, 'needsClarification must be a question exactly when drafts is empty');
  if (!('needsClarification' in exp)) fail(where, 'expected must set needsClarification (null when drafts exist)');
  if (clarify !== tags.has('clarify')) fail(where, 'tag "clarify" must be present exactly when drafts is empty');
  if (exp.drafts.length >= 2 !== tags.has('multi')) fail(where, 'tag "multi" must be present exactly when there are 2+ drafts');
  for (const k of KINDS) {
    if (exp.drafts.some((d) => d.kind === k) !== tags.has(k)) fail(where, `tag "${k}" must match the draft kinds`);
  }
  // The expected value must score as correct against itself.
  const self = scoreGolden(exp, exp, validateDraft);
  if (!self.valid || !self.correct) fail(where, 'scoreGolden(expected, expected) is not correct');
  return tags;
}

for (const g of GOLDEN) {
  section(g.file, () => {
    const lines = read(g.file).split('\n');
    if (lines.at(-1) === '') lines.pop();
    if (lines.length !== g.lines) fail(g.file, `has ${lines.length} lines, expected ${g.lines}`);
    const counts = Object.fromEntries([...TAGS].map((t) => [t, 0]));
    const ids = new Set();
    lines.forEach((raw, i) => {
      const where = `${g.file}:${i + 1}`;
      let line;
      try {
        line = JSON.parse(raw);
      } catch (err) {
        fail(where, `not JSON: ${err.message}`);
        return;
      }
      if (!isObj(line)) {
        fail(where, 'line must be a JSON object');
        return;
      }
      if (ids.has(line.id)) fail(where, `duplicate id ${line.id}`);
      ids.add(line.id);
      for (const t of checkGoldenLine(where, line, g.prefix, i + 1)) if (t in counts) counts[t] += 1;
    });
    for (const [tag, n] of Object.entries(g.min)) {
      if (counts[tag] < n) fail(g.file, `needs at least ${n} lines tagged "${tag}" (has ${counts[tag]})`);
    }
    tagCounts[g.file] = counts;
    return `${lines.length} lines`;
  });
}

// ---------------------------------------------------------------- scoreGolden self-checks
section('scoreGolden self-checks', () => {
  const d = (o) => ({ kind: 'sale', item: 'rice', quantity: 3, unit: 'bag', totalNaira: 45000, paidNaira: null, customerName: null, confidence: 1, ...o });
  const credit = d({ kind: 'credit_sale', paidNaira: 0, customerName: 'Mama Bisi' });
  const one = (x) => ({ drafts: [x], needsClarification: null });
  const ask = { drafts: [], needsClarification: 'How much?' };
  const cases = [
    ['exact match', one(d()), one(d({ confidence: 0.4 })), { valid: true, correct: true }],
    ['credit_sale paid null equals 0', one(credit), one({ ...credit, paidNaira: null }), { correct: true, paidNaira: true }],
    ['customer name case and spaces', one(credit), one({ ...credit, customerName: '  mama   BISI ' }), { correct: true, customerName: true }],
    ['customer null vs name', one(d()), one(d({ customerName: 'Tunde' })), { correct: false, customerName: false }],
    ['item and unit are soft', one(d()), one(d({ item: 'ofada rice', unit: 'bags' })), { correct: true, item: false, unit: false }],
    ['wrong total', one(d()), one(d({ totalNaira: 4500 })), { correct: false, totalNaira: false }],
    ['total at kobo precision', one(d()), one(d({ totalNaira: 45000.001 })), { correct: true }],
    ['paid ignored for sale', one(d()), one(d({ paidNaira: 500 })), { correct: true, paidNaira: null }],
    ['wrong paid on credit_sale', one(credit), one({ ...credit, paidNaira: 5000 }), { correct: false, paidNaira: false }],
    ['quantity not stated is ignored', one(d({ quantity: null })), one(d({ quantity: 2 })), { correct: true, quantity: null }],
    ['wrong quantity', one(d()), one(d({ quantity: 2 })), { correct: false, quantity: false }],
    ['fully paid credit_sale is the wrong kind', one(d()), one(d({ kind: 'credit_sale', paidNaira: 45000 })), { correct: false, kind: false }],
    ['extra draft', one(d()), { drafts: [d(), d()], needsClarification: null }, { correct: false, kind: false, item: false }],
    ['invalid output', one(d()), { drafts: [{ kind: 'sale', totalNaira: 45000 }] }, { valid: false, correct: false, kind: false }],
    ['not an object', one(d()), null, { valid: false, correct: false }],
    ['clarify answered', ask, { drafts: [], needsClarification: 'Wetin be the price?' }, { correct: true, kind: null }],
    ['clarify with empty question', ask, { drafts: [], needsClarification: '  ' }, { valid: true, correct: false }],
    ['clarify with drafts', ask, one(d()), { valid: true, correct: false }],
    ['drafts expected, clarify given', one(d()), ask, { valid: true, correct: false, totalNaira: false }],
  ];
  for (const [name, expected, actual, want] of cases) {
    const got = scoreGolden(expected, actual, validateDraft);
    for (const [k, v] of Object.entries(want)) {
      const g = k in got ? got[k] : got.fields[k];
      if (g !== v) fail(`scoreGolden "${name}"`, `${k} = ${JSON.stringify(g)}, expected ${JSON.stringify(v)}`);
    }
  }
  return `${cases.length} cases`;
});

// ---------------------------------------------------------------- journal cases
section('journal-cases.json', () => {
  const j = readJson('journal-cases.json');
  if (!Array.isArray(j.cases) || j.cases.length === 0) fail('journal-cases.json', 'cases must be a non-empty array');
  for (const c of j.cases ?? []) {
    if (!validateDraft({ drafts: [c.draft] })) {
      fail(`journal case "${c.name}"`, `draft is not valid: ${ajvErrors(validateDraft)}`);
    }
  }
  return `${j.cases?.length ?? 0} drafts`;
});

// ---------------------------------------------------------------- routing cases
function routingCodes() {
  const md = read('routing.md');
  const table = md.split('## Reason codes')[1] ?? '';
  const reasons = new Set([...table.matchAll(/^\|\s*`([A-Z_]+)`\s*\|/gm)].map((m) => m[1]));
  const queuePara = (table.split('Queue reasons:')[1] ?? '').split(/\n\s*\n/)[0];
  const queues = new Set([...queuePara.matchAll(/`([A-Z_]+)`/g)].map((m) => m[1]));
  return { reasons, queues };
}

section('routing-cases.json', () => {
  const { reasons, queues } = routingCodes();
  if (reasons.size === 0 || queues.size === 0) fail('routing.md', 'could not read reason or queue codes');
  const r = readJson('routing-cases.json');
  const config = readJson('remote-config.defaults.json');
  const signalKeys = new Set(Object.keys(r.defaultSignals ?? {}));
  if (signalKeys.size === 0) fail('routing-cases.json', 'defaultSignals missing');
  const names = new Set();
  let n = 0;
  for (const group of ['text', 'speech']) {
    if (!Array.isArray(r[group]) || r[group].length === 0) {
      fail('routing-cases.json', `"${group}" must be a non-empty array`);
      continue;
    }
    for (const c of r[group]) {
      n += 1;
      const where = `routing ${group} "${c.name}"`;
      if (typeof c.name !== 'string' || c.name === '') fail(where, 'name must be a non-empty string');
      if (names.has(c.name)) fail(where, 'duplicate name');
      names.add(c.name);
      if (!isObj(c.signals)) fail(where, 'signals must be an object');
      if (!isObj(c.expected)) {
        fail(where, 'expected must be an object');
        continue;
      }
      const e = c.expected;
      if (!['device', 'cloud', 'queue'].includes(e.target)) fail(where, `bad target ${JSON.stringify(e.target)}`);
      if (!reasons.has(e.reason)) fail(where, `reason ${JSON.stringify(e.reason)} is not a code in routing.md`);
      if (e.queueReason !== null && !queues.has(e.queueReason)) {
        fail(where, `queueReason ${JSON.stringify(e.queueReason)} is not a queue code in routing.md`);
      }
      if ((e.target === 'queue') !== (e.queueReason !== null)) fail(where, 'queueReason must be set exactly when target is queue');
      if (group === 'text') {
        if (!['normal', 'low'].includes(e.confidence)) fail(where, `bad confidence ${JSON.stringify(e.confidence)}`);
        for (const k of Object.keys(c.signals ?? {})) if (!signalKeys.has(k)) fail(where, `unknown signal ${k}`);
        for (const k of Object.keys(c.config ?? {})) if (!(k in config)) fail(where, `config key ${k} is not in remote-config.defaults.json`);
      }
    }
  }
  if (!Array.isArray(r.speechCheck) || r.speechCheck.length === 0) fail('routing-cases.json', '"speechCheck" must be a non-empty array');
  for (const c of r.speechCheck ?? []) {
    n += 1;
    const where = `routing speechCheck "${c.name}"`;
    if (names.has(c.name)) fail(where, 'duplicate name');
    names.add(c.name);
    if (typeof c.transcript !== 'string') fail(where, 'transcript must be a string');
    if (c.confidence !== null && typeof c.confidence !== 'number') fail(where, 'confidence must be a number or null');
    if (typeof c.expectedMixed !== 'boolean') fail(where, 'expectedMixed must be a boolean');
  }
  return `${n} cases`;
});

// ---------------------------------------------------------------- remote config
const CONFIG_TYPES = {
  cloud_model_name: 'string',
  route_max_device_chars: 'integer',
  route_language_threshold: 'number',
  route_min_battery: 'number',
  prompt_version_parse: 'string',
  insights_enabled: 'boolean',
  cloud_enabled: 'boolean',
  cloud_rate_limit_per_minute: 'integer',
};

section('remote-config.defaults.json', () => {
  const c = readJson('remote-config.defaults.json');
  const keys = Object.keys(c);
  for (const k of keys) if (!(k in CONFIG_TYPES)) fail('remote-config', `unexpected key ${k}`);
  for (const [k, type] of Object.entries(CONFIG_TYPES)) {
    if (!(k in c)) {
      fail('remote-config', `missing key ${k}`);
      continue;
    }
    const ok = type === 'integer' ? Number.isInteger(c[k]) : typeof c[k] === type;
    if (!ok) fail('remote-config', `${k} must be ${type} (got ${JSON.stringify(c[k])})`);
  }
  const prompts = readdirSync(join(root, 'prompts')).map((f) => basename(f, '.md'));
  if (!prompts.includes(c.prompt_version_parse)) fail('remote-config', `prompt_version_parse "${c.prompt_version_parse}" has no file in prompts/`);
  return `${keys.length} keys`;
});

// ---------------------------------------------------------------- language score
section('language-cases.json', () => {
  const l = readJson('language-cases.json');
  for (const c of l.cases) {
    const got = scoreLanguage(c.text);
    if (got !== c.expectedScore) fail('language-cases', `"${c.text}" scores ${got}, expected ${c.expectedScore}`);
  }
  return `${l.cases.length} cases`;
});

// ---------------------------------------------------------------- prompts
section('prompts/*.md', () => {
  const files = readdirSync(join(root, 'prompts')).filter((f) => f.endsWith('.md') && f !== 'README.md');
  for (const f of files) {
    const where = `prompts/${f}`;
    const m = read(`prompts/${f}`).match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
    if (!m) {
      fail(where, 'missing front-matter header');
      continue;
    }
    const header = Object.fromEntries(
      m[1]
        .split(/\r?\n/)
        .map((l) => l.match(/^(\w+):\s*(.*)$/))
        .filter(Boolean)
        .map((x) => [x[1], x[2].trim()]),
    );
    for (const k of ['id', 'task', 'output']) if (!header[k]) fail(where, `header needs "${k}"`);
    if (header.id && header.id !== basename(f, '.md')) fail(where, `id "${header.id}" does not match the file name`);
    if (header.output && header.output !== 'text') {
      try {
        read(header.output);
      } catch {
        fail(where, `output "${header.output}" is not "text" or a file in the package`);
      }
    }
  }
  return `${files.length} prompts`;
});

// ---------------------------------------------------------------- report
console.log('@kpakam/contract check');
for (const s of summary) console.log(`  ${s}`);
for (const [file, counts] of Object.entries(tagCounts)) {
  const parts = Object.entries(counts)
    .filter(([, n]) => n > 0)
    .map(([t, n]) => `${t} ${n}`);
  console.log(`  tags ${file}: ${parts.join(', ')}`);
}
if (failures.length) {
  console.error(`\n${failures.length} problem(s):`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log('All checks passed.');
