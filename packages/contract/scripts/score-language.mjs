// Reference implementation of the language score in routing.md.
// Usage: node scripts/score-language.mjs            -> prints language-cases.json scores
//        node scripts/score-language.mjs --write    -> rewrites expected scores in language-cases.json
// Importing this module has no side effects beyond reading language-lexicon.json.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const lexicon = JSON.parse(readFileSync(join(root, 'language-lexicon.json'), 'utf8'));

const DIACRITICS = new Set([...'ẹọṣàáèéìíòóùúń']);

/**
 * Pidgin/Yoruba language score of a trader note, as defined in routing.md.
 * @param {string} text The note.
 * @returns {number} Score from 0 to 1, rounded to 2 decimals.
 */
export function scoreLanguage(text) {
  const lower = text.toLowerCase().normalize('NFC');
  const diacritics = [...lower].filter((ch) => DIACRITICS.has(ch)).length;
  const tokens = lower.split(/[^\p{L}']+/u).filter(Boolean);
  const spaced = tokens.join(' ');
  let hits = 0;
  for (const token of tokens) hits += lexicon.markers[token] ?? 0;
  for (const [phrase, weight] of Object.entries(lexicon.phrases)) {
    if (` ${spaced} `.includes(` ${phrase} `)) hits += weight;
  }
  const raw = ((hits + 0.5 * diacritics) / Math.max(3, tokens.length)) * 2;
  return Math.round(Math.min(1, raw) * 100) / 100;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const casesPath = join(root, 'language-cases.json');
  const cases = JSON.parse(readFileSync(casesPath, 'utf8'));
  for (const c of cases.cases) c.expectedScore = scoreLanguage(c.text);
  if (process.argv.includes('--write')) {
    writeFileSync(casesPath, `${JSON.stringify(cases, null, 2)}\n`);
  }
  for (const c of cases.cases) console.log(c.expectedScore.toFixed(2), c.text);
}
