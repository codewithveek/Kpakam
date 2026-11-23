import { scoreLanguage } from '../domain/language-score';
import { createScriptedInferenceClient, type ScriptedReply } from './scripted-inference-client';
import type { InferenceClient, InferenceRequest, InferenceTarget } from './inference-client';

const AMOUNT = /(\d[\d,]*(?:\.\d+)?)\s*(k|m)?\b/gi;

function naira(match: RegExpMatchArray): number {
  const base = Number((match[1] ?? '0').replace(/,/g, ''));
  const unit = (match[2] ?? '').toLowerCase();
  return unit === 'k' ? base * 1000 : unit === 'm' ? base * 1_000_000 : base;
}

function noteOf(request: InferenceRequest): string {
  return /<<<\n([\s\S]*?)\n>>>/.exec(request.prompt)?.[1] ?? '';
}

function mediaReply(request: InferenceRequest): ScriptedReply {
  const receipt = { kind: 'expense', item: 'receipt items', totalNaira: 12500, confidence: 0.9 };
  const transcript = request.media?.mimeType.startsWith('audio') === true ? 'I sell rice 5k' : null;
  return { text: JSON.stringify({ drafts: [receipt], needsClarification: null, transcript }) };
}

function draftFor(note: string, amounts: readonly number[]): object | null {
  const total = amounts[0];
  if (total === undefined) return null;
  const owes = /(\b[A-Z][a-z]+(?: [A-Z][a-z]+)?) owes/.exec(note)?.[1];
  if (owes !== undefined)
    return { kind: 'credit_sale', item: 'goods', totalNaira: total, paidNaira: total - (amounts[1] ?? 0), customerName: owes, confidence: 0.9 };
  const expense = /\b(bought|paid|spent|transport|rent)\b/i.test(note);
  return { kind: expense ? 'expense' : 'sale', item: 'goods', totalNaira: total, confidence: scoreLanguage(note) > 0.35 ? 0.6 : 0.9 };
}

/** A tiny deterministic "model" for Playwright runs, where no real model exists. */
function reply(_target: InferenceTarget, request: InferenceRequest): ScriptedReply {
  if (request.output === 'text') return { text: 'A steady day of sales.' };
  if (request.media !== undefined) return mediaReply(request);
  const note = noteOf(request);
  const draft = draftFor(
    note,
    [...note.matchAll(AMOUNT)].map(naira).filter((n) => n >= 50),
  );
  if (draft === null) return { text: JSON.stringify({ drafts: [], needsClarification: 'How much was it?' }) };
  return { text: JSON.stringify({ drafts: [draft], needsClarification: null }) };
}

/** `InferenceClient` for the e2e build (`VITE_FAKE_INFERENCE=true`). Never bundled otherwise. */
export function createE2eInferenceClient(): InferenceClient {
  return createScriptedInferenceClient({ deviceModel: 'available', fallback: reply }).client;
}
