/**
 * A small JSON Schema validator for the subset `openapi.public.json` uses in request bodies and
 * parameters ($ref, type incl. null, enum, const, properties, required, additionalProperties,
 * items, min/maxItems, min/maxLength, minimum/maximum, pattern, allOf/anyOf/oneOf).
 *
 * The mock uses it to reject malformed requests the way the real API does (400 `invalid_request`
 * with `param`), with zero dependencies so `node server.js` runs anywhere. It is deliberately not a
 * full validator: formats are not checked, and the first error wins.
 */

/** @typedef {{ param: string, message: string }} SchemaError */

/**
 * @param {unknown} value
 * @param {Record<string, unknown>} schema
 * @param {Record<string, unknown>} root the OpenAPI document (for `$ref`)
 * @param {string} [path]
 * @returns {SchemaError | null}
 */
export function validate(value, schema, root, path = '') {
  if (!schema || typeof schema !== 'object') return null;
  if (typeof schema.$ref === 'string')
    return validate(value, resolveRef(root, schema.$ref), root, path);

  if (Array.isArray(schema.allOf)) {
    for (const sub of schema.allOf) {
      const err = validate(value, sub, root, path);
      if (err) return err;
    }
  }
  if (Array.isArray(schema.anyOf) && !schema.anyOf.some((s) => !validate(value, s, root, path))) {
    return { param: path, message: 'does not match any allowed shape' };
  }
  if (Array.isArray(schema.oneOf)) {
    const matches = schema.oneOf.filter((s) => !validate(value, s, root, path)).length;
    if (matches !== 1) return { param: path, message: 'must match exactly one allowed shape' };
  }

  if (schema.type !== undefined) {
    const types = Array.isArray(schema.type) ? schema.type : [schema.type];
    if (!types.some((t) => typeMatches(value, t))) {
      return { param: path, message: `must be ${types.join(' or ')}` };
    }
  }
  if (Array.isArray(schema.enum) && !schema.enum.some((e) => e === value)) {
    return { param: path, message: `must be one of ${schema.enum.map(String).join(', ')}` };
  }
  if ('const' in schema && schema.const !== value) {
    return { param: path, message: `must be ${String(schema.const)}` };
  }

  if (typeof value === 'string') {
    const len = [...value].length;
    if (typeof schema.minLength === 'number' && len < schema.minLength) {
      return { param: path, message: `must be at least ${schema.minLength} characters` };
    }
    if (typeof schema.maxLength === 'number' && len > schema.maxLength) {
      return { param: path, message: `must be at most ${schema.maxLength} characters` };
    }
    if (typeof schema.pattern === 'string' && !new RegExp(schema.pattern, 'u').test(value)) {
      return { param: path, message: 'has an invalid format' };
    }
  }
  if (typeof value === 'number') {
    if (typeof schema.minimum === 'number' && value < schema.minimum) {
      return { param: path, message: `must be >= ${schema.minimum}` };
    }
    if (typeof schema.maximum === 'number' && value > schema.maximum) {
      return { param: path, message: `must be <= ${schema.maximum}` };
    }
  }
  if (Array.isArray(value)) {
    if (typeof schema.minItems === 'number' && value.length < schema.minItems) {
      return { param: path, message: `must have at least ${schema.minItems} items` };
    }
    if (typeof schema.maxItems === 'number' && value.length > schema.maxItems) {
      return { param: path, message: `must have at most ${schema.maxItems} items` };
    }
    if (schema.items) {
      for (let i = 0; i < value.length; i++) {
        const err = validate(value[i], schema.items, root, `${path}[${i}]`);
        if (err) return err;
      }
    }
  }
  if (isPlainObject(value)) {
    const props = /** @type {Record<string, unknown>} */ (schema.properties ?? {});
    for (const name of /** @type {string[]} */ (schema.required ?? [])) {
      if (!(name in value)) return { param: join(path, name), message: 'is required' };
    }
    for (const [name, v] of Object.entries(value)) {
      if (name in props) {
        const err = validate(
          v,
          /** @type {Record<string, unknown>} */ (props[name]),
          root,
          join(path, name),
        );
        if (err) return err;
      } else if (schema.additionalProperties === false) {
        return { param: join(path, name), message: 'is not allowed' };
      } else if (isPlainObject(schema.additionalProperties)) {
        const err = validate(v, schema.additionalProperties, root, join(path, name));
        if (err) return err;
      }
    }
  }
  return null;
}

/** @param {Record<string, unknown>} root @param {string} ref */
export function resolveRef(root, ref) {
  if (!ref.startsWith('#/')) throw new Error(`unsupported $ref ${ref}`);
  /** @type {any} */
  let node = root;
  for (const part of ref.slice(2).split('/'))
    node = node?.[part.replaceAll('~1', '/').replaceAll('~0', '~')];
  if (!node) throw new Error(`unresolved $ref ${ref}`);
  return node;
}

/** @param {unknown} v @returns {v is Record<string, unknown>} */
export function isPlainObject(v) {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** @param {unknown} v @param {string} t */
function typeMatches(v, t) {
  switch (t) {
    case 'null':
      return v === null;
    case 'string':
      return typeof v === 'string';
    case 'boolean':
      return typeof v === 'boolean';
    case 'integer':
      return Number.isInteger(v);
    case 'number':
      return typeof v === 'number' && Number.isFinite(v);
    case 'array':
      return Array.isArray(v);
    case 'object':
      return isPlainObject(v);
    default:
      return true;
  }
}

/** @param {string} path @param {string} name */
function join(path, name) {
  return path ? `${path}.${name}` : name;
}
