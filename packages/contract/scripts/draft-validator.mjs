// Node-only: compiles schemas/draft.schema.json with Ajv once.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
let cachedValidate = null;

/**
 * Returns a validator for schemas/draft.schema.json, compiled on the first call.
 * @returns {(data: unknown) => boolean} Ajv validate function.
 */
export function draftValidator() {
  if (!cachedValidate) {
    const ajv = new Ajv2020({ allErrors: true, strict: true, allowUnionTypes: true });
    addFormats(ajv);
    cachedValidate = ajv.compile(JSON.parse(readFileSync(join(root, 'schemas/draft.schema.json'), 'utf8')));
  }
  return cachedValidate;
}
