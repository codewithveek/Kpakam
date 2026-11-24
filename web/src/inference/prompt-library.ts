import insightsFile from '@contract/prompts/insights-v1.md?raw';
import parseFile from '@contract/prompts/parse-v2.md?raw';
import retryFile from '@contract/prompts/parse-retry-v1.md?raw';
import receiptFile from '@contract/prompts/receipt-v1.md?raw';
import summaryFile from '@contract/prompts/summary-v1.md?raw';
import voiceFile from '@contract/prompts/voice-v1.md?raw';
import { parsePromptFile } from './domain/prompt-builder';

/** The bundled prompt versions, parsed once. */
export const PROMPTS = {
  parse: parsePromptFile(parseFile),
  parseRetry: parsePromptFile(retryFile),
  voice: parsePromptFile(voiceFile),
  receipt: parsePromptFile(receiptFile),
  summary: parsePromptFile(summaryFile),
  insights: parsePromptFile(insightsFile),
} as const;
