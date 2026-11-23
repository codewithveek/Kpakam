import draftSchemaJson from '@contract/schemas/draft.schema.json';

type JsonObject = { [key: string]: unknown };

function isObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function inline(node: unknown, defs: JsonObject): unknown {
  if (Array.isArray(node)) return node.map((item) => inline(item, defs));
  if (!isObject(node)) return node;
  const ref = node['$ref'];
  if (typeof ref === 'string' && ref.startsWith('#/$defs/')) return inline(defs[ref.slice('#/$defs/'.length)], defs);
  const out: JsonObject = {};
  for (const [key, value] of Object.entries(node)) {
    if (key !== '$schema' && key !== '$id' && key !== '$defs' && key !== 'description') out[key] = inline(value, defs);
  }
  return out;
}

/**
 * The contract's draft JSON Schema with `$defs` inlined, for model output constraints:
 * `responseJsonSchema` in the cloud and `responseConstraint` on device.
 */
export function modelDraftSchema(): JsonObject {
  const defs = isObject(draftSchemaJson.$defs) ? (draftSchemaJson.$defs as JsonObject) : {};
  const result = inline(draftSchemaJson, defs);
  return isObject(result) ? result : {};
}
