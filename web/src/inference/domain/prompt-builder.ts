/** Prompt bodies keyed by id, loaded by the caller (Vite `?raw` in the app, fs in scripts). */
export interface PromptLibrary {
  readonly parse: string;
  readonly parseRetry: string;
  readonly voice: string;
  readonly receipt: string;
  readonly summary: string;
  readonly insights: string;
}

export interface PromptVersion {
  readonly id: string;
  readonly body: string;
}

const MAX_PREVIOUS_OUTPUT = 2000;

/**
 * Splits a prompt file into its version id and body (prompts/README.md).
 * @throws Error when the front matter is missing; prompt files are bundled, so this is a bug.
 */
export function parsePromptFile(file: string): PromptVersion {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$/.exec(file);
  const id = match?.[1] ? /^id:\s*(.+)$/m.exec(match[1])?.[1]?.trim() : undefined;
  if (!match?.[2] || id === undefined) throw new Error('Prompt file has no version header');
  return { id, body: match[2].trim() };
}

/** Removes the delimiters so trader text cannot close the note section. */
export function sanitiseNote(note: string): string {
  return note.replaceAll('<<<', '').replaceAll('>>>', '').trim();
}

/** Builds the parse prompt for one note. */
export function buildParsePrompt(parseBody: string, note: string): string {
  return `${parseBody}\n\nNote:\n<<<\n${sanitiseNote(note)}\n>>>`;
}

export interface RetryPromptInput {
  readonly parseBody: string;
  readonly retryBody: string;
  readonly note: string;
  readonly previousOutput: string;
  readonly errors: readonly string[];
}

/** Builds the retry prompt with the validation errors from the first attempt. */
export function buildRetryPrompt(input: RetryPromptInput): string {
  const errors = input.errors.map((line) => `- ${line}`).join('\n');
  const previous = input.previousOutput.trim().slice(0, MAX_PREVIOUS_OUTPUT);
  const retry = input.retryBody.replace('{{errors}}', errors);
  return `${buildParsePrompt(input.parseBody, input.note)}\n\nYour last JSON:\n${previous}\n\n${retry}`;
}

/** Builds the voice or receipt prompt; the media goes in a separate inline-data part. */
export function buildMediaPrompt(mediaBody: string, parseBody: string): string {
  return mediaBody.replace('{{parseRules}}', parseBody);
}

/** Fills a summary or insights template with compact JSON. */
export function fillDataPrompt(body: string, fill: { placeholder: 'totals' | 'week'; data: unknown }): string {
  return body.replace(`{{${fill.placeholder}}}`, JSON.stringify(fill.data));
}
