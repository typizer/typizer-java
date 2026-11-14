/**
 * Deterministic objects for the mock: ids, timestamps and every public model of
 * `openapi.public.json`. The vitest suite validates each response built from these against the
 * OpenAPI document, so a field the API adds or renames breaks the build here, not an SDK's CI.
 */
import { createHash } from 'node:crypto';

const CROCKFORD = '0123456789abcdefghjkmnpqrstvwxyz';
const EPOCH = Date.parse('2026-11-13T09:00:00.000Z');

/**
 * `<prefix>_<26 Crockford base32 chars>`, derived from `seed` so the same sequence of calls with
 * the same API key always yields the same ids. The first char is 0–7, like a real 128-bit id.
 * @param {string} prefix @param {string} seed
 */
export function makeId(prefix, seed) {
  const hash = createHash('sha256').update(seed).digest();
  let out = CROCKFORD[hash[0] % 8];
  for (let i = 1; i < 26; i++) out += CROCKFORD[hash[i] % 32];
  return `${prefix}_${out}`;
}

/** ISO timestamp `seconds` after the mock's fixed epoch (2026-11-13T09:00:00Z). */
export function at(seconds) {
  return new Date(EPOCH + seconds * 1000).toISOString();
}

export const ORG = { id: makeId('org', 'org'), name: 'ООО «Пример»' };
export const API_KEY_ID = makeId('key', 'key');
export const SCOPES = [
  'typizers:read',
  'typizers:write',
  'runs:read',
  'runs:write',
  'billing:read',
];

/** The schema used by seeded typizers and by `GET /v1/typizers/{id}` fixtures. */
export const INVOICE_SCHEMA = {
  type: 'object',
  properties: {
    invoice_number: { type: 'string' },
    invoice_date: { type: 'string', format: 'date' },
    seller: {
      type: 'object',
      properties: {
        name: { type: 'string' },
        inn: { type: 'string', 'x-typizer-check': 'inn' },
      },
    },
    total: { type: 'number' },
  },
  required: ['invoice_number', 'total'],
};

const CANNED = {
  invoice_number: 'А-1024',
  number: 'А-1024',
  invoice_date: '2026-11-05',
  date: '2026-11-05',
  name: 'ООО «Ромашка»',
  inn: '7707083893',
  kpp: '773601001',
  total: 3660,
  amount: 3660,
  vat: 660,
};

/**
 * A value of the shape `schema` asks for: canned invoice values by field name, else a typed
 * placeholder. Arrays get one item, so `fields` carries `items[0].…` paths.
 * @param {any} schema @param {string} name
 * @returns {unknown}
 */
export function sampleValue(schema, name = '') {
  if (!schema || typeof schema !== 'object') return null;
  if (Array.isArray(schema.enum) && schema.enum.length > 0) return schema.enum[0];
  if ('const' in schema) return schema.const;
  const types = Array.isArray(schema.type) ? schema.type : [schema.type];
  const type = types.find((t) => t && t !== 'null') ?? (schema.properties ? 'object' : 'string');
  switch (type) {
    case 'object': {
      /** @type {Record<string, unknown>} */
      const out = {};
      for (const [key, sub] of Object.entries(schema.properties ?? {}))
        out[key] = sampleValue(sub, key);
      return out;
    }
    case 'array':
      return [sampleValue(schema.items ?? { type: 'string' }, name)];
    case 'number':
      return typeof CANNED[name] === 'number' ? CANNED[name] : 1250.5;
    case 'integer':
      return typeof CANNED[name] === 'number' ? Math.round(CANNED[name]) : 3;
    case 'boolean':
      return true;
    default:
      if (schema.format === 'date') return '2026-11-05';
      if (schema.format === 'date-time') return '2026-11-05T10:00:00Z';
      return typeof CANNED[name] === 'string' ? CANNED[name] : `${name || 'value'}`;
  }
}

/**
 * Leaf paths of `value` in the API's `fields` notation (`seller.inn`, `items[0].amount`).
 * @param {unknown} value @param {string} [prefix]
 * @returns {Array<[string, unknown]>}
 */
export function leafPaths(value, prefix = '') {
  if (Array.isArray(value)) {
    if (value.length === 0) return prefix ? [[prefix, value]] : [];
    return value.flatMap((v, i) => leafPaths(v, `${prefix}[${i}]`));
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value);
    if (entries.length === 0) return prefix ? [[prefix, value]] : [];
    return entries.flatMap(([k, v]) => leafPaths(v, prefix ? `${prefix}.${k}` : k));
  }
  return prefix ? [[prefix, value]] : [];
}

/**
 * `output` and `fields` for a schema. `review: true` makes the first leaf `warn`
 * (`checksum_mismatch`) and the last one `missing` (its value `null`) — the shape of a run a human
 * should look at.
 * @param {any} schema @param {{ review?: boolean }} [opts]
 */
export function extraction(schema, { review = false } = {}) {
  const output = /** @type {Record<string, unknown>} */ (sampleValue(schema));
  const leaves = leafPaths(output);
  /** @type {Record<string, unknown>} */
  const fields = {};
  leaves.forEach(([path, value], i) => {
    const source = {
      page: 1,
      bbox: [0.1, 0.05 + i * 0.04, 0.45, 0.08 + i * 0.04],
      text: String(value),
    };
    if (review && i === leaves.length - 1 && leaves.length > 1) {
      setAtPath(output, path, null);
      fields[path] = { status: 'missing', sources: [] };
    } else if (review && i === 0) {
      fields[path] = { status: 'warn', reason: 'checksum_mismatch', sources: [source] };
    } else {
      fields[path] = { status: 'ok', sources: [source] };
    }
  });
  return { output, fields };
}

/** @param {Record<string, any>} obj @param {string} path @param {unknown} value */
function setAtPath(obj, path, value) {
  const segs = path.match(/[^.[\]]+/g) ?? [];
  let node = obj;
  for (let i = 0; i < segs.length - 1; i++) node = node?.[segs[i]];
  if (node && typeof node === 'object') node[segs[segs.length - 1]] = value;
}

/** @param {Partial<Record<string, unknown>>} o */
export function run(o) {
  return {
    id: o.id,
    object: 'run',
    status: 'queued',
    typizer_id: null,
    typizer_version: null,
    mode: 'standard',
    format: 'json',
    source: 'api',
    pages: { physical: null, billed: null },
    output: null,
    fields: null,
    output_expired: false,
    error: null,
    file_id: o.file_id,
    file: null,
    batch_id: null,
    metadata: {},
    catalog: null,
    created_at: o.created_at,
    completed_at: null,
    ...o,
  };
}

/** @param {{ id: string, filename: string, mime: string, size: number, created_at: string }} o */
export function file(o) {
  return {
    id: o.id,
    object: 'file',
    filename: o.filename,
    mime: o.mime,
    size_bytes: o.size,
    pages_estimate: 1,
    source: 'api',
    expires_at: null,
    created_at: o.created_at,
  };
}

/** @param {Record<string, any>} o */
export function typizer(o) {
  return {
    id: o.id,
    object: 'typizer',
    name: o.name,
    description: o.description ?? null,
    version: o.version ?? 1,
    schema: o.schema,
    instructions: o.instructions ?? '',
    default_format: o.default_format ?? 'json',
    mode: o.mode ?? 'standard',
    catalog_source: o.catalog_source ?? null,
    created_at: o.created_at,
    updated_at: o.updated_at ?? o.created_at,
  };
}

/** @param {Record<string, any>} t the typizer @param {number} version @param {string} createdAt */
export function typizerVersion(
  t,
  version,
  createdAt,
  schema = t.schema,
  instructions = t.instructions,
) {
  return {
    id: makeId('tzv', `${t.id}:${version}`),
    object: 'typizer_version',
    typizer_id: t.id,
    version,
    schema,
    instructions,
    created_at: createdAt,
  };
}

/** @param {Record<string, any>} o */
export function batch(o) {
  return {
    id: o.id,
    object: 'batch',
    typizer_id: o.typizer_id,
    status: o.status ?? 'completed',
    total: o.total,
    succeeded: o.succeeded ?? o.total,
    failed: o.failed ?? 0,
    created_at: o.created_at,
    completed_at: o.status === 'processing' ? null : (o.completed_at ?? o.created_at),
  };
}

/** @param {Record<string, any>} o */
export function webhookEndpoint(o) {
  return {
    id: o.id,
    object: 'webhook_endpoint',
    url: o.url,
    events: o.events,
    status: o.status ?? 'active',
    secret: o.secret,
    failing_since: null,
    created_at: o.created_at,
  };
}

/** @param {Record<string, any>} o */
export function webhookDelivery(o) {
  return {
    id: o.id,
    object: 'webhook_delivery',
    event_id: o.event_id,
    event_type: o.event_type ?? 'run.succeeded',
    status: o.status ?? 'succeeded',
    attempt: o.attempt ?? 0,
    response_code: o.response_code ?? 200,
    next_attempt_at: null,
    created_at: o.created_at,
  };
}

export function me() {
  return {
    object: 'me',
    auth: 'api_key',
    organization: { ...ORG },
    user: null,
    role: null,
    api_key: { id: API_KEY_ID, name: 'Конформанс', scopes: [...SCOPES] },
    features: { billing_enabled: true, edo_diadoc: false, catalog_community: true, inbound_mail: false },
  };
}

export function organization() {
  return {
    id: ORG.id,
    object: 'organization',
    name: ORG.name,
    settings: { file_retention: 'd7', output_retention: 'd30' },
  };
}

/** @param {number} available */
export function balance(available) {
  return {
    object: 'balance',
    pages_available: available,
    pages_reserved: 0,
    lots:
      available > 0
        ? [
            {
              source: 'purchase',
              pages_left: available - 50,
              expires_at: '2027-11-13T00:00:00.000Z',
            },
            { source: 'free', pages_left: 50, expires_at: null },
          ]
        : [],
  };
}

// ---------------------------------------------------------------------------------------------
// Catalog

const TYPIZER_PUBLISHER = {
  id: null,
  slug: 'typizer',
  display_name: 'Typizer',
  badges: ['official'],
};
const ACME_PUBLISHER = {
  id: makeId('pub', 'acme'),
  slug: 'acme',
  display_name: 'ООО «Акме»',
  badges: ['verified'],
};

const SCHET_SCHEMA = {
  type: 'object',
  properties: {
    number: { type: 'string' },
    date: { type: 'string', format: 'date' },
    seller: {
      type: 'object',
      properties: { name: { type: 'string' }, inn: { type: 'string' }, kpp: { type: 'string' } },
    },
    total: { type: 'number' },
    vat: { type: 'number' },
  },
  required: ['number', 'total'],
};

const AKT_SCHEMA = {
  type: 'object',
  properties: {
    number: { type: 'string' },
    date: { type: 'string', format: 'date' },
    amount: { type: 'number' },
  },
};

/** The catalog: two official schemas and one community schema. */
export const CATALOG = [
  {
    id: makeId('cat', 'schet-faktura'),
    ref: 'schet-faktura',
    kind: 'official',
    slug: 'schet-faktura',
    title: 'Счёт-фактура',
    summary: 'Номер, дата, продавец, покупатель, суммы и НДС.',
    category: 'invoices',
    publisher: TYPIZER_PUBLISHER,
    schema: SCHET_SCHEMA,
    version: '1.2.0',
    web_url: 'https://typizer.ru/catalog/schet-faktura',
  },
  {
    id: makeId('cat', 'upd'),
    ref: 'upd',
    kind: 'official',
    slug: 'upd',
    title: 'УПД',
    summary: 'Универсальный передаточный документ: статус, стороны, строки, итоги.',
    category: 'invoices',
    publisher: TYPIZER_PUBLISHER,
    schema: SCHET_SCHEMA,
    version: '2.0.1',
    web_url: 'https://typizer.ru/catalog/upd',
  },
  {
    id: makeId('cat', 'acme/akt-sverki'),
    ref: 'acme/akt-sverki',
    kind: 'community',
    slug: 'akt-sverki',
    title: 'Акт сверки',
    summary: 'Сальдо и обороты по акту сверки взаиморасчётов.',
    category: 'registries',
    publisher: ACME_PUBLISHER,
    schema: AKT_SCHEMA,
    version: '1.0.0',
    web_url: null,
  },
];

/** A catalog entry by `cat_…` id, slug of an official schema, or `publisher/slug` ref. */
export function findCatalog(ref) {
  const bare = ref.split('@')[0];
  return CATALOG.find(
    (c) => c.id === bare || c.ref === bare || (c.kind === 'official' && c.slug === bare),
  );
}

/** @param {(typeof CATALOG)[number]} c */
function versionBrief(c) {
  return {
    id: makeId('catv', `${c.ref}@${c.version}`),
    object: 'catalog_version',
    version: c.version,
    major: Number(c.version.split('.')[0]),
    breaking: false,
    changelog: 'Уточнены описания полей.',
    field_count: Object.keys(c.schema.properties).length,
    published_at: '2026-11-01T00:00:00.000Z',
  };
}

/** @param {(typeof CATALOG)[number]} c @param {{ favorited?: boolean, typizerCount?: number }} viewer */
export function catalogBrief(c, viewer = {}) {
  return {
    id: c.id,
    object: 'catalog_entry',
    ref: c.ref,
    kind: c.kind,
    slug: c.slug,
    title: c.title,
    summary: c.summary,
    category: c.category,
    tags: [c.category],
    publisher: c.publisher,
    featured: c.kind === 'official',
    latest_version: versionBrief(c),
    fields_preview: Object.keys(c.schema.properties).slice(0, 6),
    stats: { orgs_30d: c.kind === 'official' ? 120 : null, favorites: 7 },
    web_url: c.web_url,
    updated_at: '2026-11-01T00:00:00.000Z',
    viewer: { favorited: Boolean(viewer.favorited), typizer_count: viewer.typizerCount ?? 0 },
  };
}

/** @param {(typeof CATALOG)[number]} c @param {{ favorited?: boolean }} viewer */
export function catalogEntry(c, viewer = {}) {
  const { viewer: _brief, ...brief } = catalogBrief(c, viewer);
  return {
    ...brief,
    description: c.kind === 'community' ? 'Схема для актов сверки.' : null,
    prompt: null,
    visibility: 'public',
    version: {
      ...versionBrief(c),
      schema: c.schema,
      instructions: '',
      example_output: c.kind === 'official' ? sampleValue(c.schema) : null,
      changes: [{ path: 'seller.kpp', kind: 'description_changed', breaking: false }],
    },
    majors: [{ major: Number(c.version.split('.')[0]), latest: c.version }],
    lineage: null,
    viewer: { favorited: Boolean(viewer.favorited), typizers: [] },
    created_at: '2026-10-01T00:00:00.000Z',
  };
}

/** @param {(typeof CATALOG)[number]} c */
export function catalogVersions(c) {
  return { object: 'list', data: [versionBrief(c)], has_more: false, next_cursor: null };
}

/** @param {(typeof CATALOG)[number]} c */
export function catalogSource(c, relation = 'installed') {
  return {
    entry_id: c.id,
    ref: c.ref,
    title: c.title,
    publisher: c.publisher,
    relation,
    version: c.version,
    status: 'active',
    auto_update: c.kind === 'official' && relation === 'installed',
    update: null,
  };
}

/** @param {string} slug */
export function catalogPublisher(slug) {
  const entries = CATALOG.filter((c) => c.publisher.slug === slug);
  if (entries.length === 0) return null;
  const p = entries[0].publisher;
  return {
    id: p.id,
    object: 'catalog_publisher',
    slug: p.slug,
    display_name: p.display_name,
    badges: p.badges,
    about: null,
    website: slug === 'typizer' ? 'https://typizer.ru' : null,
    since: '2026-10-01T00:00:00.000Z',
    stats: { entries: entries.length, orgs_30d: null },
    entries: entries.map((c) => catalogBrief(c)),
  };
}

/** @param {Record<string, any>} t a typizer made from the catalog */
export function catalogUpdate(t) {
  const c = findCatalog(t.catalog_source.ref);
  return {
    object: 'catalog_update',
    typizer_id: t.id,
    relation: t.catalog_source.relation,
    from: t.catalog_source.version,
    to: c?.version ?? t.catalog_source.version,
    breaking: false,
    changelog: [
      {
        version: c?.version ?? '1.0.0',
        notes: 'Уточнены описания полей.',
        published_at: '2026-11-01T00:00:00.000Z',
      },
    ],
    changes: [
      {
        path: 'seller.kpp',
        kind: 'description_changed',
        breaking: false,
        upstream: { type: 'string' },
        local: { type: 'string' },
        conflict: false,
      },
    ],
  };
}
