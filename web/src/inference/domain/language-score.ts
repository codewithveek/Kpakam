import lexicon from '@contract/language-lexicon.json';

const DIACRITICS = new Set(Array.from('ẹọṣàáèéìíòóùúń'));
const MARKERS: Readonly<Record<string, number>> = lexicon.markers;
const PHRASES: ReadonlyArray<readonly [string, number]> = Object.entries(lexicon.phrases);

/**
 * Scores how likely a note is Pidgin or Yoruba, from 0 to 1 (routing.md "Language score").
 * Pure and deterministic; the Android app implements the same algorithm.
 */
export function scoreLanguage(text: string): number {
  const lower = text.toLowerCase().normalize('NFC');
  const diacritics = Array.from(lower).filter((ch) => DIACRITICS.has(ch)).length;
  const tokens = lower.split(/[^\p{L}']+/u).filter((token) => token !== '');
  const spaced = ` ${tokens.join(' ')} `;
  let hits = 0;
  for (const token of tokens) hits += MARKERS[token] ?? 0;
  for (const [phrase, weight] of PHRASES) {
    if (spaced.includes(` ${phrase} `)) hits += weight;
  }
  const raw = ((hits + 0.5 * diacritics) / Math.max(3, tokens.length)) * 2;
  return Math.round(Math.min(1, raw) * 100) / 100;
}
