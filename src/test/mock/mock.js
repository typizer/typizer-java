/**
 * The Typizer conformance mock: every operation of `openapi.public.json`, with deterministic
 * scenarios chosen by the API key (`tz_test_<scenario>[__<anything>]`), and a request log every
 * SDK test suite can read back (`GET /_mock/requests?key=…`). See `SCENARIOS.md`.
 *
 * Zero dependencies (Node 20+): routes are compiled from the OpenAPI document itself, so a path the
 * API adds shows up here as "not implemented" in this package's vitest suite, not as silent drift.
 */
import { createHash, createHmac } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as fx from './lib/fixtures.js';
import { isPlainObject, resolveRef, validate } from './lib/schema.js';

const HERE = dirname(fileURLToPath(import.meta.url));

/** The API's file limit (`MAX_FILE_BYTES`, 50 MiB). */
export const MAX_FILE_BYTES = 52428800;
const MAX_BODY_BYTES = MAX_FILE_BYTES + 2 * 1024 * 1024;

/** Scenario name → what it does. The API key `tz_test_<name>` (optionally `__<suffix>`) picks it. */
export const SCENARIOS = {
  ok: 'Everything succeeds. Runs created with wait>0 finish at once (200); with wait=0 or no wait they are queued (202) and finish on the first GET.',
  needs_review:
    'Runs succeed, but the first leaf field is `warn` (checksum_mismatch) and the last is `missing` (value null).',
  run_failed: 'Runs end `failed` with run.error.code `document_unreadable`.',
  output_expired:
    'Runs succeed with output/fields null and output_expired true; GET /v1/runs/{id}/output is 404.',
  slow: 'Runs are always queued (202, whatever `wait`); GET /v1/runs/{id} answers processing twice and the final run on the 3rd poll.',
  unauthorized: 'Every authenticated call is 401 unauthorized.',
  insufficient_scope: 'Every authenticated call except GET /v1/me is 403 insufficient_scope.',
  insufficient_balance:
    'Run-creating calls (extract, createRun, retryRun, createBatch) are 402 insufficient_balance; GET /v1/balance shows 0 pages.',
  file_too_large:
    'Every upload (POST /v1/files, multipart extract/createRun/createBatch) is 413 file_too_large.',
  rate_limited:
    'The first call of each operation is 429 rate_limited with Retry-After: 1 and RateLimit-* headers; later calls succeed.',
  rate_limited_plain:
    'Like rate_limited, but the 429 comes from the network edge: text/plain body, Retry-After: 1, no X-Request-Id.',
  unavailable:
    'Every authenticated call is 503 upstream_unavailable with Retry-After: 1 (retries must give up).',
  unavailable_once:
    'The first call of each operation is 503 upstream_unavailable with Retry-After: 1; later calls succeed.',
  internal_error_once:
    'The first call of each operation is 500 internal_error (no Retry-After); later calls succeed.',
  drop_first_response:
    'The first POST of each operation is fully processed, then the connection is closed without a response; a retry with the same Idempotency-Key gets the replay.',
  slow_response: 'Every response is delayed (3 s by default) — for client timeout tests.',
  maintenance:
    'Like ok, plus a Typizer-Maintenance header on every response and an event in GET /v1/status.',
};

/** HTTP status per API error code (mirrors `@typizer/core/errors`; the vitest suite checks it). */
export const ERROR_STATUS = {
  invalid_request: 400,
  invalid_id: 400,
  invalid_schema: 400,
  unsupported_schema_feature: 400,
  file_too_large: 413,
  unsupported_file_type: 415,
  too_many_pages: 400,
  unauthorized: 401,
  forbidden: 403,
  insufficient_scope: 403,
  not_found: 404,
  conflict: 409,
  idempotency_conflict: 409,
  insufficient_balance: 402,
  rate_limited: 429,
  extraction_failed: 422,
  document_unreadable: 422,
  upstream_unavailable: 503,
  internal_error: 500,
};

/** RFC 9457 `title` per code (mirrors `@typizer/core/errors`). */
export const ERROR_TITLES = {
  invalid_request: 'Invalid request',
  invalid_id: 'Invalid id',
  invalid_schema: 'Invalid schema',
  unsupported_schema_feature: 'Unsupported schema feature',
  file_too_large: 'File too large',
  unsupported_file_type: 'Unsupported file type',
  too_many_pages: 'Too many pages',
  unauthorized: 'Unauthorized',
  forbidden: 'Forbidden',
  insufficient_scope: 'Insufficient scope',
  not_found: 'Not found',
  conflict: 'Conflict',
  idempotency_conflict: 'Idempotency conflict',
  insufficient_balance: 'Insufficient balance',
  rate_limited: 'Rate limited',
  extraction_failed: 'Extraction failed',
  document_unreadable: 'Document unreadable',
  upstream_unavailable: 'Upstream unavailable',
  internal_error: 'Internal error',
};

const RUN_CREATING = new Set(['extract', 'createRun', 'retryRun', 'createBatch']);
const UPLOADING = new Set(['createFile', 'extract', 'createRun', 'createBatch']);
const ID_PREFIX = {
  file: 'file',
  typizer: 'tz',
  run: 'run',
  batch: 'batch',
  endpoint: 'whe',
  delivery: 'whd',
};
const MAINTENANCE = {
  id: fx.makeId('mnt', 'maintenance'),
  starts_at: '2026-11-20T21:00:00.000Z',
  ends_at: '2026-11-20T22:00:00.000Z',
};

/** Where the OpenAPI document is: `$TYPIZER_OPENAPI`, a copy next to this file (the Docker
 * image), or the monorepo's `apps/docs/openapi.public.json`. */
export function findOpenApi() {
  const candidates = [
    process.env.TYPIZER_OPENAPI,
    join(HERE, 'openapi.public.json'),
    join(HERE, '../../../apps/docs/openapi.public.json'),
  ];
  const found = candidates.find((p) => p && existsSync(p));
  if (!found) throw new Error('openapi.public.json not found: set TYPIZER_OPENAPI');
  return found;
}

/** @param {string} [path] */
export function loadOpenApi(path = findOpenApi()) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

/**
 * @typedef {{ status: number, body?: unknown, raw?: string | Buffer, contentType?: string,
 *   headers?: Record<string, string> }} MockResponse
 */

/**
 * Builds the mock. `listen()` resolves to its base URL (`http://127.0.0.1:<port>`).
 * @param {{ openapi?: any, slowResponseMs?: number }} [options]
 */
export function createMock(options = {}) {
  const openapi = options.openapi ?? loadOpenApi();
  const slowResponseMs =
    options.slowResponseMs ?? Number(process.env.MOCK_SLOW_RESPONSE_MS ?? 3000);
  const routes = compileRoutes(openapi);
  const openapiText = JSON.stringify(openapi);
  const openapiEtag = `"${createHash('sha256').update(openapiText).digest('hex').slice(0, 32)}"`;
  /** @type {any[]} */
  const log = [];
  /** @type {Map<string, State>} */
  const states = new Map();
  let seq = 0;

  /** @param {string} key */
  function stateFor(key) {
    let s = states.get(key);
    if (!s) {
      s = new State(key);
      states.set(key, s);
    }
    return s;
  }

  const server = createServer((req, res) => {
    handle(req, res).catch((err) => {
      console.error('[typizer-mock] handler crashed:', err);
      if (!res.headersSent) {
        res.writeHead(500, { 'content-type': 'application/problem+json' });
        res.end(
          JSON.stringify(
            problemBody('internal_error', String(err?.message ?? err), undefined, 'req_mock'),
          ),
        );
      }
    });
  });

  /** @param {import('node:http').IncomingMessage} req @param {import('node:http').ServerResponse} res */
  async function handle(req, res) {
    const url = new URL(req.url ?? '/', 'http://mock');
    if (url.pathname.startsWith('/_mock/')) return control(req, res, url);

    const requestId = requestIdFrom(req.headers['x-request-id']);
    const route = matchRoute(routes, req.method ?? 'GET', url.pathname);
    const raw = await readBody(req);
    const ct = String(req.headers['content-type'] ?? '');
    const parsed = raw.tooLarge ? { kind: 'too_large' } : await parseBody(raw.buf, ct);
    const auth = String(req.headers.authorization ?? '');
    const key = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
    const scenario = scenarioOf(key);
    const entry = {
      seq: ++seq,
      key,
      scenario: scenario.name,
      operation_id: route?.opId ?? null,
      method: req.method,
      path: url.pathname,
      query: Object.fromEntries(url.searchParams),
      headers: Object.fromEntries(
        Object.entries(req.headers).map(([k, v]) => [
          k,
          Array.isArray(v) ? v.join(', ') : String(v),
        ]),
      ),
      body: logBody(parsed, ct, raw.size),
      response:
        /** @type {{ status: number, idempotent_replayed: boolean, dropped: boolean } | null} */ (
          null
        ),
    };
    log.push(entry);

    /** @param {MockResponse} r @param {{ replayed?: boolean, bare?: boolean }} [o] */
    const send = (r, o = {}) => {
      entry.response = {
        status: r.status,
        idempotent_replayed: Boolean(o.replayed),
        dropped: false,
      };
      /** @type {Record<string, string>} */
      const headers = o.bare
        ? {}
        : {
            'x-request-id': requestId,
            'ratelimit-limit': '3000',
            'ratelimit-remaining': String(Math.max(0, 2999 - (states.get(key)?.calls ?? 0))),
            'ratelimit-reset': '60',
          };
      if (!o.bare && scenario.name === 'maintenance') {
        headers['typizer-maintenance'] =
          `${MAINTENANCE.id}; starts=${MAINTENANCE.starts_at}; ends=${MAINTENANCE.ends_at}`;
      }
      if (o.replayed) headers['idempotent-replayed'] = 'true';
      Object.assign(headers, r.headers ?? {});
      if (r.body !== undefined) {
        headers['content-type'] =
          r.contentType ?? (r.status >= 400 ? 'application/problem+json' : 'application/json');
        res.writeHead(r.status, headers);
        res.end(JSON.stringify(r.body));
      } else if (r.raw !== undefined) {
        headers['content-type'] = r.contentType ?? 'application/octet-stream';
        res.writeHead(r.status, headers);
        res.end(r.raw);
      } else {
        res.writeHead(r.status, headers);
        res.end();
      }
    };
    const fail = (code, detail, param, extra) =>
      send({
        status: ERROR_STATUS[code],
        body: { ...problemBody(code, detail, param, requestId), ...extra },
      });

    if (!route) return fail('not_found', `No route ${req.method} ${url.pathname}`);
    const op = route.op;
    const needsAuth = (op.security ?? openapi.security ?? []).length > 0;

    if (needsAuth) {
      if (!key) return fail('unauthorized', 'Missing API key: send Authorization: Bearer <key>');
      if (!scenario.known) {
        return fail(
          'invalid_request',
          `Unknown mock scenario "${scenario.name}" (known: ${Object.keys(SCENARIOS).join(', ')})`,
          'Authorization',
        );
      }
    }
    const state = stateFor(key || 'anonymous');
    state.calls++;
    const opKey = `${req.method} ${route.path}`;
    const firstHit = !state.hits.has(opKey);
    state.hits.add(opKey);

    if (needsAuth) {
      if (scenario.name === 'slow_response') await sleep(slowResponseMs);
      if (scenario.name === 'unauthorized') return fail('unauthorized', 'Invalid API key');
      if (scenario.name === 'insufficient_scope' && route.opId !== 'getMe') {
        return fail('insufficient_scope', 'This API key lacks the scope this operation needs');
      }
      if (scenario.name === 'unavailable') {
        return send({
          status: 503,
          body: problemBody(
            'upstream_unavailable',
            'Processing is temporarily unavailable',
            undefined,
            requestId,
          ),
          headers: { 'retry-after': '1' },
        });
      }
      if (firstHit && scenario.name === 'rate_limited') {
        return send({
          status: 429,
          body: problemBody(
            'rate_limited',
            'Too many requests: 20 per second',
            undefined,
            requestId,
          ),
          headers: {
            'retry-after': '1',
            'ratelimit-limit': '20',
            'ratelimit-remaining': '0',
            'ratelimit-reset': '1',
          },
        });
      }
      if (firstHit && scenario.name === 'rate_limited_plain') {
        return send(
          {
            status: 429,
            raw: 'Too Many Requests',
            contentType: 'text/plain; charset=utf-8',
            headers: { 'retry-after': '1' },
          },
          { bare: true },
        );
      }
      if (firstHit && scenario.name === 'unavailable_once') {
        return send({
          status: 503,
          body: problemBody(
            'upstream_unavailable',
            'Processing is temporarily unavailable',
            undefined,
            requestId,
          ),
          headers: { 'retry-after': '1' },
        });
      }
      if (firstHit && scenario.name === 'internal_error_once') {
        return fail('internal_error', 'Internal error');
      }
    }

    if (parsed.kind === 'too_large')
      return fail('file_too_large', `The request body exceeds ${MAX_FILE_BYTES} bytes`);
    if (parsed.kind === 'invalid_json')
      return fail('invalid_request', 'The request body is not valid JSON', 'body');

    // Path and query parameters, as declared.
    const params = Object.fromEntries(
      route.names.map((n, i) => [n, decodeURIComponent(route.match[i + 1])]),
    );
    /** @type {Record<string, unknown>} */
    const query = {};
    for (const p of op.parameters ?? []) {
      const param = p.$ref ? resolveRef(openapi, p.$ref) : p;
      if (param.in === 'path') {
        const err = validate(
          coerce(params[param.name], param.schema),
          param.schema,
          openapi,
          param.name,
        );
        if (err) return fail('invalid_request', `${param.name} ${err.message}`, param.name);
        params[param.name] = coerce(params[param.name], param.schema);
      } else if (param.in === 'query') {
        const rawValue = url.searchParams.get(param.name);
        if (rawValue === null) {
          if (param.required)
            return fail('invalid_request', `${param.name} is required`, param.name);
          continue;
        }
        const value = coerce(rawValue, param.schema);
        const err = validate(value, param.schema, openapi, param.name);
        if (err) return fail('invalid_request', `${param.name} ${err.message}`, param.name);
        query[param.name] = value;
      } else if (param.in === 'header' && param.name === 'Idempotency-Key') {
        const v = req.headers['idempotency-key'];
        if (v !== undefined) {
          const err = validate(String(v), param.schema, openapi, 'Idempotency-Key');
          if (err)
            return fail('invalid_request', `Idempotency-Key ${err.message}`, 'Idempotency-Key');
        }
      }
    }

    // The body: JSON validated against the operation's schema; multipart turned into the same
    // shape (schema/metadata parsed from their JSON text) and validated the same way.
    const content = op.requestBody?.content ?? {};
    /** @type {Record<string, any> | undefined} */
    let body;
    /** @type {any[]} */
    let files = [];
    if (parsed.kind === 'json') {
      if (!content['application/json'])
        return fail('invalid_request', `${route.opId} does not take a JSON body`, 'body');
      body = parsed.value;
    } else if (parsed.kind === 'multipart') {
      if (!content['multipart/form-data'])
        return fail('invalid_request', `${route.opId} does not take multipart`, 'body');
      files = parsed.files;
      body = {};
      for (const [name, value] of Object.entries(parsed.fields)) {
        if (name === 'schema' || name === 'metadata') {
          try {
            body[name] = JSON.parse(String(value));
          } catch {
            return fail('invalid_request', `${name} must be a JSON object`, name);
          }
        } else {
          body[name] = value;
        }
      }
    } else if (parsed.kind === 'other') {
      return fail('invalid_request', `Unsupported Content-Type ${ct}`, 'Content-Type');
    }
    if (op.requestBody?.required && body === undefined)
      return fail('invalid_request', 'A request body is required', 'body');
    const jsonSchema = content['application/json']?.schema;
    if (body !== undefined && jsonSchema && route.opId !== 'createBatch') {
      const err = validate(body, jsonSchema, openapi);
      if (err)
        return fail(
          err.param === 'schema' ? 'invalid_schema' : 'invalid_request',
          `${err.param || 'body'} ${err.message}`,
          err.param || 'body',
        );
    }

    // Idempotency: same key + same request → the first response again; same key + another
    // request → 409. Only 2xx responses are kept (an error frees the key, like the API).
    const idemKey = req.method === 'POST' ? req.headers['idempotency-key'] : undefined;
    const takesIdem = (op.parameters ?? []).some(
      (p) => p.$ref === '#/components/parameters/IdempotencyKey',
    );
    const fingerprint =
      idemKey && takesIdem
        ? sha256(
            stableStringify({
              op: route.opId,
              params,
              query,
              body,
              files: files.map((f) => [f.field, f.filename, f.sha256]),
            }),
          )
        : null;
    if (fingerprint && idemKey) {
      const prior = state.idempotency.get(String(idemKey));
      if (prior) {
        if (prior.fingerprint !== fingerprint) {
          return fail(
            'idempotency_conflict',
            'This Idempotency-Key was used with a different request',
          );
        }
        return send(prior.replay, { replayed: true });
      }
    }

    if (
      needsAuth &&
      scenario.name === 'file_too_large' &&
      UPLOADING.has(route.opId) &&
      (route.opId === 'createFile' || files.length > 0)
    ) {
      return fail('file_too_large', `The file exceeds ${MAX_FILE_BYTES} bytes`);
    }
    if (files.some((f) => f.size > MAX_FILE_BYTES))
      return fail('file_too_large', `The file exceeds ${MAX_FILE_BYTES} bytes`);
    if (needsAuth && scenario.name === 'insufficient_balance' && RUN_CREATING.has(route.opId)) {
      return fail('insufficient_balance', 'Not enough pages on the balance: 1 needed, 0 available');
    }

    const handler = HANDLERS[route.opId];
    if (!handler) return fail('not_found', `${route.opId} is not implemented by the mock`);
    /** @type {Ctx} */
    const ctx = {
      state,
      scenario: scenario.name,
      params,
      query,
      body,
      files,
      req,
      openapi,
      openapiText,
      openapiEtag,
      key,
    };
    const result = handler(ctx);
    if ('error' in result) return fail(result.error, result.detail, result.param, result.extra);

    if (fingerprint && idemKey && result.status < 300) {
      state.idempotency.set(String(idemKey), { fingerprint, replay: result.replay ?? result });
    }
    if (needsAuth && scenario.name === 'drop_first_response' && req.method === 'POST' && firstHit) {
      entry.response = { status: result.status, idempotent_replayed: false, dropped: true };
      req.socket.destroy();
      return;
    }
    return send(result);
  }

  /** @param {import('node:http').IncomingMessage} req @param {import('node:http').ServerResponse} res @param {URL} url */
  async function control(req, res, url) {
    const json = (status, body) => {
      res.writeHead(status, { 'content-type': 'application/json' });
      res.end(JSON.stringify(body));
    };
    const p = url.pathname;
    if (p === '/_mock/health') return json(200, { ok: true });
    if (p === '/_mock/scenarios') {
      return json(200, {
        object: 'list',
        data: Object.entries(SCENARIOS).map(([name, description]) => ({
          name,
          key: `tz_test_${name}`,
          description,
        })),
      });
    }
    if (p === '/_mock/requests' && req.method === 'GET') {
      const f = Object.fromEntries(url.searchParams);
      const data = log.filter(
        (e) =>
          (!f.key || e.key === f.key) &&
          (!f.operation_id || e.operation_id === f.operation_id) &&
          (!f.scenario || e.scenario === f.scenario),
      );
      return json(200, { object: 'list', data });
    }
    if (p === '/_mock/reset' && req.method === 'POST') {
      const k = url.searchParams.get('key');
      if (k) {
        states.delete(k);
        for (let i = log.length - 1; i >= 0; i--) if (log[i].key === k) log.splice(i, 1);
      } else {
        states.clear();
        log.length = 0;
      }
      return json(200, { ok: true });
    }
    if (p === '/_mock/webhooks/sample' && req.method === 'GET') {
      const secret =
        url.searchParams.get('secret') ?? 'whsec_J8G0iNL6JM6usEvy6InDrrH8E1crlPG4a9TjsmmHN/A=';
      const type = url.searchParams.get('type') ?? 'run.succeeded';
      return json(200, signedSample(secret, type));
    }
    if (p === '/_mock/webhooks/vectors' && req.method === 'GET') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(readFileSync(join(HERE, 'fixtures/webhook-vectors.json')));
      return;
    }
    return json(404, { error: `unknown mock control endpoint ${req.method} ${p}` });
  }

  return {
    server,
    /** All logged requests (newest last). */
    requests: (filter = {}) =>
      log.filter((e) => Object.entries(filter).every(([k, v]) => e[k] === v)),
    reset() {
      states.clear();
      log.length = 0;
    },
    /** @param {number} [port] @param {string} [host] */
    listen(port = 0, host = '127.0.0.1') {
      return new Promise((resolve) => {
        server.listen(port, host, () => {
          const addr = /** @type {import('node:net').AddressInfo} */ (server.address());
          resolve(`http://${host.includes(':') ? `[${host}]` : host}:${addr.port}`);
        });
      });
    },
    close() {
      return new Promise((resolve) => {
        server.closeAllConnections?.();
        server.close(() => resolve(undefined));
      });
    },
  };
}

// ---------------------------------------------------------------------------------------------
// Per-key state

class State {
  /** @param {string} key */
  constructor(key) {
    this.key = key;
    this.counter = 0;
    this.clock = 0;
    this.calls = 0;
    /** @type {Set<string>} */
    this.hits = new Set();
    /** @type {Map<string, { fingerprint: string, replay: MockResponse }>} */
    this.idempotency = new Map();
    /** @type {Map<string, any>} */ this.files = new Map();
    /** @type {Map<string, Buffer>} */ this.fileBytes = new Map();
    /** @type {Map<string, any>} */ this.typizers = new Map();
    /** @type {Map<string, any[]>} */ this.versions = new Map();
    /** @type {Map<string, any>} */ this.runs = new Map();
    /** @type {Map<string, { polls: number, final: any }>} */ this.pending = new Map();
    /** @type {Map<string, any>} */ this.batches = new Map();
    /** @type {Map<string, any>} */ this.endpoints = new Map();
    /** @type {Map<string, any>} */ this.deliveries = new Map();
    /** @type {Set<string>} */ this.favorites = new Set();
    this.seed();
  }

  /** @param {keyof typeof ID_PREFIX} kind */
  id(kind) {
    return fx.makeId(ID_PREFIX[kind], `${this.key}:${kind}:${++this.counter}`);
  }

  now() {
    return fx.at(++this.clock);
  }

  seed() {
    const fileId = this.id('file');
    this.files.set(
      fileId,
      fx.file({
        id: fileId,
        filename: 'invoice.pdf',
        mime: 'application/pdf',
        size: 9,
        created_at: fx.at(-100000),
      }),
    );
    this.fileBytes.set(fileId, Buffer.from('%PDF-1.4\n'));
    const tzId = this.id('typizer');
    const t = fx.typizer({
      id: tzId,
      name: 'Счета поставщиков',
      schema: fx.INVOICE_SCHEMA,
      created_at: fx.at(-90000),
    });
    this.typizers.set(tzId, t);
    this.versions.set(tzId, [fx.typizerVersion(t, 1, t.created_at)]);
    // 25 finished runs, newest first in lists: enough for three pages of 10.
    for (let i = 25; i >= 1; i--) {
      const id = this.id('run');
      const failed = i % 7 === 0;
      const created = fx.at(-i * 600);
      const done = failed
        ? {
            status: 'failed',
            error: { code: 'document_unreadable', message: 'Документ не читается.' },
            pages: { physical: 1, billed: 0 },
          }
        : {
            status: 'succeeded',
            ...fx.extraction(fx.INVOICE_SCHEMA),
            pages: { physical: 1, billed: 1 },
          };
      this.runs.set(
        id,
        fx.run({
          id,
          file_id: fileId,
          created_at: created,
          typizer_id: tzId,
          typizer_version: 1,
          file: { filename: 'invoice.pdf', mime: 'application/pdf' },
          ...done,
          completed_at: fx.at(-i * 600 + 5),
        }),
      );
    }
    const batchId = this.id('batch');
    this.batches.set(
      batchId,
      fx.batch({ id: batchId, typizer_id: tzId, total: 3, created_at: fx.at(-50000) }),
    );
    const wheId = this.id('endpoint');
    this.endpoints.set(
      wheId,
      fx.webhookEndpoint({
        id: wheId,
        url: 'https://hooks.example.ru/typizer',
        events: ['run.succeeded', 'run.failed'],
        secret: secretFor(this.key, wheId),
        created_at: fx.at(-80000),
      }),
    );
    const whdId = this.id('delivery');
    this.deliveries.set(whdId, {
      endpoint_id: wheId,
      ...fx.webhookDelivery({
        id: whdId,
        event_id: fx.makeId('evt', `${this.key}:seed`),
        created_at: fx.at(-1000),
      }),
    });
  }
}

// ---------------------------------------------------------------------------------------------
// Handlers: one per operationId of openapi.public.json.

/**
 * @typedef {{ state: State, scenario: string, params: Record<string, any>, query: Record<string, any>,
 *   body: Record<string, any> | undefined, files: any[], req: import('node:http').IncomingMessage,
 *   openapi: any, openapiText: string, openapiEtag: string, key: string }} Ctx
 * @typedef {(MockResponse & { replay?: MockResponse }) | { error: string, detail: string, param?: string, extra?: Record<string, unknown> }} HandlerResult
 */

/** @type {Record<string, (ctx: Ctx) => HandlerResult>} */
export const HANDLERS = {
  getStatus: ({ scenario }) => ({
    status: 200,
    body: {
      object: 'status',
      data:
        scenario === 'maintenance'
          ? [
              {
                id: MAINTENANCE.id,
                object: 'maintenance_event',
                kind: 'planned',
                title: 'Плановые работы',
                message_ru: 'Обновление базы данных.',
                components: ['api', 'processing'],
                starts_at: MAINTENANCE.starts_at,
                ends_at: MAINTENANCE.ends_at,
                status: 'scheduled',
              },
            ]
          : [],
    },
  }),
  getOpenApiDocument: ({ req, openapiText, openapiEtag }) => {
    const headers = { etag: openapiEtag, 'cache-control': 'public, max-age=300' };
    if (req.headers['if-none-match'] === openapiEtag) return { status: 304, headers };
    return { status: 200, raw: openapiText, contentType: 'application/json', headers };
  },
  getMe: () => ({ status: 200, body: fx.me() }),
  getOrganization: () => ({ status: 200, body: fx.organization() }),

  // Files
  createFile: ({ state, files }) => {
    const f = files.find((x) => x.field === 'file');
    if (!f)
      return {
        error: 'invalid_request',
        detail: 'Send the document as the multipart part "file"',
        param: 'file',
      };
    const created = storeFile(state, f);
    return { status: 201, body: created };
  },
  getFile: (ctx) => withEntity(ctx, 'files', 'file', (file) => ({ status: 200, body: file })),
  deleteFile: (ctx) =>
    withEntity(ctx, 'files', 'file', (file) => {
      ctx.state.files.delete(file.id);
      return { status: 200, body: { id: file.id, object: 'file', deleted: true } };
    }),
  getFileContent: (ctx) =>
    withEntity(ctx, 'files', 'file', (file) => ({
      status: 200,
      raw: ctx.state.fileBytes.get(file.id) ?? Buffer.alloc(0),
      contentType: 'application/octet-stream',
    })),

  // Typizers
  listTypizers: ({ state, query }) => page(sorted(state.typizers), query),
  createTypizer: ({ state, body }) => {
    const bad = schemaProblem(body?.schema);
    if (bad) return bad;
    const id = state.id('typizer');
    const t = fx.typizer({ id, ...body, created_at: state.now() });
    state.typizers.set(id, t);
    state.versions.set(id, [fx.typizerVersion(t, 1, t.created_at)]);
    return { status: 201, body: t };
  },
  generateTypizer: ({ state, body }) => {
    if (!body?.description && !body?.file_id)
      return {
        error: 'invalid_request',
        detail: 'Send description, file_id or both',
        param: 'description',
      };
    if (body.file_id && !state.files.has(body.file_id))
      return { error: 'not_found', detail: 'No such file', param: 'file_id' };
    return {
      status: 200,
      body: {
        object: 'typizer_draft',
        name: 'Счета поставщиков',
        schema: fx.INVOICE_SCHEMA,
        instructions: '',
      },
    };
  },
  getTypizer: (ctx) => withEntity(ctx, 'typizers', 'typizer', (t) => ({ status: 200, body: t })),
  updateTypizer: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) => {
      const { body = {} } = ctx;
      if (body.schema !== undefined) {
        const bad = schemaProblem(body.schema);
        if (bad) return bad;
      }
      const updated = { ...t, ...body, updated_at: ctx.state.now() };
      if (body.schema !== undefined || body.instructions !== undefined) {
        updated.version = t.version + 1;
        ctx.state.versions
          .get(t.id)
          ?.push(fx.typizerVersion(updated, updated.version, updated.updated_at));
      }
      ctx.state.typizers.set(t.id, updated);
      return { status: 200, body: updated };
    }),
  deleteTypizer: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) => {
      ctx.state.typizers.delete(t.id);
      return { status: 200, body: { id: t.id, object: 'typizer', deleted: true } };
    }),
  listTypizerVersions: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) =>
      page(
        [...(ctx.state.versions.get(t.id) ?? [])].reverse(),
        ctx.query,
        'starting_after',
        (v) => v.id,
      ),
    ),
  getTypizerVersion: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) => {
      const v = ctx.state.versions.get(t.id)?.find((x) => x.version === ctx.params.n);
      return v
        ? { status: 200, body: v }
        : {
            error: 'not_found',
            detail: `Typizer ${t.id} has no version ${ctx.params.n}`,
            param: 'n',
          };
    }),
  getTypizerXsd: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) => ({
      status: 200,
      raw: xsdFor(t.schema),
      contentType: 'application/xml; charset=utf-8',
    })),

  // Catalog
  listCatalog: ({ state, query }) => {
    let items = fx.CATALOG.filter(
      (c) =>
        (!query.category || c.category === query.category) &&
        (!query.kind || c.kind === query.kind) &&
        (!query.publisher || c.publisher.slug === query.publisher) &&
        (!query.favorites || state.favorites.has(c.id)) &&
        (!query.q ||
          `${c.title} ${c.summary} ${Object.keys(c.schema.properties).join(' ')}`
            .toLowerCase()
            .includes(String(query.q).toLowerCase())),
    );
    items = items.map((c) =>
      fx.catalogBrief(c, {
        favorited: state.favorites.has(c.id),
        typizerCount: countFromCatalog(state, c.id),
      }),
    );
    const limit = Number(query.limit ?? 20);
    const offset = query.cursor
      ? Number(
          Buffer.from(String(query.cursor), 'base64url').toString('utf8').replace('offset:', ''),
        )
      : 0;
    if (!Number.isInteger(offset) || offset < 0)
      return { error: 'invalid_request', detail: 'Unknown cursor', param: 'cursor' };
    const data = items.slice(offset, offset + limit);
    const more = offset + limit < items.length;
    return {
      status: 200,
      body: {
        object: 'list',
        data,
        has_more: more,
        next_cursor: more ? Buffer.from(`offset:${offset + limit}`).toString('base64url') : null,
      },
    };
  },
  getCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => ({
      status: 200,
      body: fx.catalogEntry(c, { favorited: ctx.state.favorites.has(c.id) }),
    })),
  getCommunityCatalogEntry: (ctx) =>
    withCatalog(ctx, `${ctx.params.publisher}/${ctx.params.slug}`, (c) => ({
      status: 200,
      body: fx.catalogEntry(c, { favorited: ctx.state.favorites.has(c.id) }),
    })),
  getCatalogPublisher: ({ params }) => {
    const p = fx.catalogPublisher(params.slug);
    return p
      ? { status: 200, body: p }
      : { error: 'not_found', detail: 'No such publisher', param: 'slug' };
  },
  listCatalogVersions: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => ({ status: 200, body: fx.catalogVersions(c) })),
  installCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => {
      const existing = sorted(ctx.state.typizers).find(
        (t) => t.catalog_source?.entry_id === c.id && t.catalog_source.relation === 'installed',
      );
      if (existing) return { status: 200, body: existing };
      return { status: 201, body: typizerFromCatalog(ctx.state, c, 'installed', ctx.body?.name) };
    }),
  forkCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => ({
      status: 201,
      body: typizerFromCatalog(ctx.state, c, 'forked', ctx.body?.name),
    })),
  favoriteCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => {
      ctx.state.favorites.add(c.id);
      return { status: 200, body: { object: 'catalog_favorite', entry_id: c.id, favorited: true } };
    }),
  unfavoriteCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => {
      ctx.state.favorites.delete(c.id);
      return {
        status: 200,
        body: { object: 'catalog_favorite', entry_id: c.id, favorited: false },
      };
    }),
  reportCatalogEntry: (ctx) =>
    withCatalog(ctx, ctx.params.entry, (c) => {
      if (c.kind === 'official' && !['broken', 'other'].includes(ctx.body?.reason)) {
        return {
          error: 'invalid_request',
          detail: 'Official schemas can only be reported as broken or other',
          param: 'reason',
        };
      }
      return {
        status: 201,
        body: {
          id: fx.makeId('crp', `${ctx.key}:${c.id}:${ctx.state.counter++}`),
          object: 'catalog_report',
          status: 'open',
        },
      };
    }),
  getTypizerCatalogUpdate: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) =>
      t.catalog_source
        ? { status: 200, body: fx.catalogUpdate(t) }
        : { error: 'conflict', detail: 'This typizer was not made from the catalog' },
    ),
  applyTypizerCatalogUpdate: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) => {
      if (!t.catalog_source)
        return { error: 'conflict', detail: 'This typizer was not made from the catalog' };
      const updated = { ...t, version: t.version + 1, updated_at: ctx.state.now() };
      ctx.state.typizers.set(t.id, updated);
      return { status: 200, body: updated };
    }),

  // Runs
  extract: (ctx) => {
    const { body = {} } = ctx;
    if ((body.schema === undefined) === (body.catalog === undefined)) {
      return {
        error: 'invalid_request',
        detail: 'Send exactly one of schema and catalog',
        param: 'schema',
      };
    }
    if (body.catalog !== undefined && body.instructions !== undefined) {
      return {
        error: 'invalid_request',
        detail: 'instructions cannot be combined with catalog',
        param: 'instructions',
      };
    }
    let schema = body.schema;
    let catalog = null;
    if (body.catalog !== undefined) {
      const c = fx.findCatalog(String(body.catalog));
      if (!c)
        return {
          error: 'not_found',
          detail: `No catalog schema "${body.catalog}"`,
          param: 'catalog',
        };
      schema = c.schema;
      catalog = { entry_id: c.id, ref: c.ref, version: c.version };
    } else {
      const bad = schemaProblem(schema);
      if (bad) return bad;
    }
    return startRun(ctx, { schema, catalog, typizer: null });
  },
  createRun: (ctx) =>
    withEntity(ctx, 'typizers', 'typizer', (t) =>
      startRun(ctx, { schema: t.schema, catalog: null, typizer: t }),
    ),
  listRuns: ({ state, query }) => {
    if (
      query.created_after !== undefined &&
      Number.isNaN(Date.parse(String(query.created_after)))
    ) {
      return {
        error: 'invalid_request',
        detail: 'created_after must be a date-time',
        param: 'created_after',
      };
    }
    const items = sorted(state.runs)
      .filter(
        (r) =>
          (!query.status || r.status === query.status) &&
          (!query.typizer_id || r.typizer_id === query.typizer_id) &&
          (!query.batch_id || r.batch_id === query.batch_id) &&
          (!query.source || r.source === query.source) &&
          (!query.created_after ||
            r.created_at > new Date(String(query.created_after)).toISOString()) &&
          (!query.created_before ||
            r.created_at < new Date(String(query.created_before)).toISOString()),
      )
      .map((r) => ({ ...r, output: null, fields: null }));
    return page(items, query);
  },
  getRun: (ctx) =>
    withEntity(ctx, 'runs', 'run', (r) => {
      const pending = ctx.state.pending.get(r.id);
      if (!pending) return { status: 200, body: r };
      pending.polls++;
      const needed = ctx.scenario === 'slow' ? 3 : 1;
      if (pending.polls < needed) {
        const processing = { ...r, status: 'processing' };
        ctx.state.runs.set(r.id, processing);
        return { status: 200, body: processing };
      }
      const done = { ...r, ...pending.final };
      ctx.state.pending.delete(r.id);
      ctx.state.runs.set(r.id, done);
      return { status: 200, body: done };
    }),
  getRunOutput: (ctx) =>
    withEntity(ctx, 'runs', 'run', (r) => {
      if (r.status !== 'succeeded')
        return { error: 'conflict', detail: `Run ${r.id} is ${r.status}, not succeeded` };
      if (r.output_expired) return { error: 'not_found', detail: 'output expired' };
      const format = ctx.query.format ?? r.format;
      const disposition = (ext) => ({
        'content-disposition': `attachment; filename="${r.id}.${ext}"`,
      });
      if (format === 'json')
        return {
          status: 200,
          raw: JSON.stringify(r.output),
          contentType: 'application/json',
          headers: disposition('json'),
        };
      if (format === 'xml')
        return {
          status: 200,
          raw: xmlFor(r.output),
          contentType: 'application/xml; charset=utf-8',
          headers: disposition('xml'),
        };
      if (format === 'csv')
        return {
          status: 200,
          raw: csvFor(r.output),
          contentType: 'text/csv; charset=utf-8',
          headers: disposition('csv'),
        };
      return {
        status: 200,
        raw: XLSX_PLACEHOLDER,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: disposition('xlsx'),
      };
    }),
  retryRun: (ctx) =>
    withEntity(ctx, 'runs', 'run', (r) => {
      if (r.status !== 'failed')
        return {
          error: 'conflict',
          detail: `Only a failed run can be retried; ${r.id} is ${r.status}`,
        };
      const t = r.typizer_id ? ctx.state.typizers.get(r.typizer_id) : null;
      const created = startRun(
        {
          ...ctx,
          query: {},
          body: { file_id: r.file_id, format: r.format, mode: r.mode, metadata: r.metadata },
        },
        {
          schema: t?.schema ?? fx.INVOICE_SCHEMA,
          catalog: r.catalog,
          typizer: t,
          forceQueued: true,
        },
      );
      return created;
    }),

  // Batches
  createBatch: (ctx) => {
    const { state, body = {}, files } = ctx;
    const t = state.typizers.get(String(body.typizer_id ?? ''));
    if (!t)
      return {
        error: body.typizer_id ? 'not_found' : 'invalid_request',
        detail: 'Unknown typizer_id',
        param: 'typizer_id',
      };
    let fileIds = body.file_ids;
    if (files.length > 0) {
      fileIds = files.filter((f) => f.field === 'files').map((f) => storeFile(state, f).id);
    } else {
      const err = validate(body, ctx.openapi.components.schemas.CreateBatchRequest, ctx.openapi);
      if (err)
        return {
          error: 'invalid_request',
          detail: `${err.param} ${err.message}`,
          param: err.param,
        };
      const missing = fileIds.findIndex((id) => !state.files.has(id));
      if (missing >= 0)
        return {
          error: 'not_found',
          detail: `No such file ${fileIds[missing]}`,
          param: `file_ids[${missing}]`,
        };
    }
    if (!fileIds?.length)
      return { error: 'invalid_request', detail: 'Send file_ids or files', param: 'file_ids' };
    const id = state.id('batch');
    const created = state.now();
    let failed = 0;
    for (const fileId of fileIds) {
      const r = startRun(
        {
          ...ctx,
          query: { wait: ctx.scenario === 'slow' ? 0 : 60 },
          body: {
            file_id: fileId,
            format: body.format,
            mode: body.mode,
            metadata: typeof body.metadata === 'object' ? body.metadata : {},
          },
        },
        { schema: t.schema, catalog: null, typizer: t, batchId: id },
      );
      if (r.body?.status === 'failed') failed++;
    }
    const b = fx.batch({
      id,
      typizer_id: t.id,
      status: ctx.scenario === 'slow' ? 'processing' : 'completed',
      total: fileIds.length,
      succeeded: ctx.scenario === 'slow' ? 0 : fileIds.length - failed,
      failed,
      created_at: created,
    });
    state.batches.set(id, b);
    return { status: 201, body: b, headers: { location: `/v1/batches/${id}` } };
  },
  listBatches: ({ state, query }) => page(sorted(state.batches), query),
  getBatch: (ctx) => withEntity(ctx, 'batches', 'batch', (b) => ({ status: 200, body: b })),
  exportBatch: (ctx) =>
    withEntity(ctx, 'batches', 'batch', (b) => {
      if (b.status !== 'completed')
        return { error: 'conflict', detail: 'The batch is still processing' };
      const runs = sorted(ctx.state.runs).filter((r) => r.batch_id === b.id);
      const disposition = (ext) => ({
        'content-disposition': `attachment; filename="${b.id}.${ext}"`,
      });
      if (ctx.query.format === 'csv') {
        const rows = ['run_id,status', ...runs.map((r) => `${r.id},${r.status}`)];
        return {
          status: 200,
          raw: `${rows.join('\n')}\n`,
          contentType: 'text/csv; charset=utf-8',
          headers: disposition('csv'),
        };
      }
      if (ctx.query.format === 'zip')
        return {
          status: 200,
          raw: ZIP_PLACEHOLDER,
          contentType: 'application/zip',
          headers: disposition('zip'),
        };
      return {
        status: 200,
        raw: XLSX_PLACEHOLDER,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: disposition('xlsx'),
      };
    }),

  // Webhooks
  listWebhookEndpoints: ({ state, query }) => page(sorted(state.endpoints), query),
  createWebhookEndpoint: ({ state, body = {}, key }) => {
    if (!/^https:\/\/[^/@\s]+/.test(String(body.url)))
      return { error: 'invalid_request', detail: 'url must be a public https: URL', param: 'url' };
    if (sorted(state.endpoints).some((e) => e.url === body.url))
      return {
        error: 'conflict',
        detail: 'An endpoint with this url already exists',
        param: 'url',
      };
    const id = state.id('endpoint');
    const e = fx.webhookEndpoint({
      id,
      url: body.url,
      events: [...new Set(body.events)],
      secret: secretFor(key, id),
      created_at: state.now(),
    });
    state.endpoints.set(id, e);
    // A replay never repeats the secret (docs: "секрет не повторяется в ответах-повторах").
    return { status: 201, body: e, replay: { status: 201, body: { ...e, secret: null } } };
  },
  getWebhookEndpoint: (ctx) =>
    withEntity(ctx, 'endpoints', 'endpoint', (e) => ({ status: 200, body: e })),
  updateWebhookEndpoint: (ctx) =>
    withEntity(ctx, 'endpoints', 'endpoint', (e) => {
      const { body = {} } = ctx;
      if (body.url !== undefined && !/^https:\/\//.test(String(body.url)))
        return {
          error: 'invalid_request',
          detail: 'url must be a public https: URL',
          param: 'url',
        };
      const updated = {
        ...e,
        ...body,
        ...(body.status === 'active' ? { failing_since: null } : {}),
      };
      ctx.state.endpoints.set(e.id, updated);
      return { status: 200, body: updated };
    }),
  deleteWebhookEndpoint: (ctx) =>
    withEntity(ctx, 'endpoints', 'endpoint', (e) => {
      ctx.state.endpoints.delete(e.id);
      return { status: 200, body: { id: e.id, object: 'webhook_endpoint', deleted: true } };
    }),
  testWebhookEndpoint: (ctx) =>
    withEntity(ctx, 'endpoints', 'endpoint', (e) => {
      if (e.status === 'disabled') return { error: 'conflict', detail: 'The endpoint is disabled' };
      const id = ctx.state.id('delivery');
      const d = fx.webhookDelivery({
        id,
        event_id: fx.makeId('evt', `${ctx.key}:${id}`),
        status: 'pending',
        response_code: null,
        created_at: ctx.state.now(),
      });
      ctx.state.deliveries.set(id, { endpoint_id: e.id, ...d });
      return { status: 201, body: d };
    }),
  listWebhookDeliveries: (ctx) =>
    withEntity(ctx, 'endpoints', 'endpoint', (e) =>
      page(
        sorted(ctx.state.deliveries)
          .filter((d) => d.endpoint_id === e.id)
          .map(({ endpoint_id: _, ...d }) => d),
        ctx.query,
      ),
    ),
  retryWebhookDelivery: (ctx) =>
    withEntity(ctx, 'deliveries', 'delivery', ({ endpoint_id: _, ...d }) => {
      const retried = {
        ...d,
        status: 'pending',
        attempt: d.attempt + 1,
        response_code: d.response_code,
      };
      ctx.state.deliveries.set(d.id, { endpoint_id: _, ...retried });
      return { status: 200, body: retried };
    }),

  // Billing
  getBalance: ({ scenario }) => ({
    status: 200,
    body: fx.balance(scenario === 'insufficient_balance' ? 0 : 950),
  }),
  getUsage: ({ state, query }) => {
    const to = String(query.to ?? '2026-11-13');
    const from = String(query.from ?? '2026-10-14');
    if (from > to)
      return { error: 'invalid_request', detail: 'from must not be after to', param: 'from' };
    const groupBy = String(query.group_by ?? 'day');
    const runs = sorted(state.runs);
    const ok = runs.filter((r) => r.status === 'succeeded').length;
    const key =
      groupBy === 'day'
        ? to
        : groupBy === 'typizer'
          ? (runs[0]?.typizer_id ?? null)
          : fx.API_KEY_ID;
    return {
      status: 200,
      body: {
        object: 'usage',
        from,
        to,
        group_by: groupBy,
        data: [
          { key, runs: runs.length, succeeded: ok, failed: runs.length - ok, pages_billed: ok },
        ],
        has_more: false,
      },
    };
  },
};

// ---------------------------------------------------------------------------------------------
// Helpers

/**
 * Creates a run for extract/createRun/retry/batch. With `wait` > 0 (and not the `slow` scenario)
 * the run is finished in the response (200); otherwise it is queued (202) and finishes on polls.
 * @param {Ctx} ctx
 * @param {{ schema: any, catalog: any, typizer: any, batchId?: string, forceQueued?: boolean }} o
 * @returns {HandlerResult}
 */
function startRun(ctx, o) {
  const { state, body = {}, files, scenario } = ctx;
  const sources = [
    files.some((f) => f.field === 'file'),
    body.file_id !== undefined,
    body.url !== undefined,
  ].filter(Boolean).length;
  if (sources !== 1)
    return {
      error: 'invalid_request',
      detail: 'Send the document exactly one way: file, file_id or url',
      param: 'file',
    };

  let fileId;
  let fileInfo;
  const part = files.find((f) => f.field === 'file');
  if (part) {
    const f = storeFile(state, part);
    fileId = f.id;
    fileInfo = { filename: f.filename, mime: f.mime };
  } else if (body.file_id !== undefined) {
    const f = state.files.get(String(body.file_id));
    if (!isValidId(String(body.file_id), 'file'))
      return { error: 'invalid_id', detail: 'file_id is not a file id', param: 'file_id' };
    if (!f) return { error: 'not_found', detail: `No such file ${body.file_id}`, param: 'file_id' };
    fileId = f.id;
    fileInfo = { filename: f.filename, mime: f.mime };
  } else {
    if (!/^https?:\/\//.test(String(body.url)))
      return { error: 'invalid_request', detail: 'url must be http(s)', param: 'url' };
    const name = decodeURIComponent(
      new URL(String(body.url)).pathname.split('/').pop() || 'document.pdf',
    );
    const f = storeFile(state, {
      filename: name,
      content_type: 'application/pdf',
      bytes: Buffer.from('%PDF-1.4\n'),
      size: 9,
    });
    fileId = f.id;
    fileInfo = { filename: f.filename, mime: f.mime };
  }
  if (body.webhook_url !== undefined && !/^https:\/\//.test(String(body.webhook_url))) {
    return { error: 'invalid_request', detail: 'webhook_url must be https', param: 'webhook_url' };
  }

  const id = state.id('run');
  const created = state.now();
  const queued = fx.run({
    id,
    file_id: fileId,
    file: fileInfo,
    created_at: created,
    typizer_id: o.typizer?.id ?? null,
    typizer_version: o.typizer?.version ?? null,
    mode: body.mode ?? o.typizer?.mode ?? 'standard',
    format: body.format ?? o.typizer?.default_format ?? 'json',
    metadata: isPlainObject(body.metadata) ? body.metadata : {},
    catalog: o.catalog,
    batch_id: o.batchId ?? null,
    pages: { physical: 1, billed: null },
  });
  const final = finalState(o.schema, scenario, state);
  const wait = Number(ctx.query.wait ?? 0);
  const location = { location: `/v1/runs/${id}` };
  if (!o.forceQueued && scenario !== 'slow' && wait > 0) {
    const done = { ...queued, ...final };
    state.runs.set(id, done);
    return { status: 200, body: done, headers: location };
  }
  state.runs.set(id, queued);
  state.pending.set(id, { polls: 0, final });
  return { status: 202, body: queued, headers: location };
}

/** @param {any} schema @param {string} scenario @param {State} state */
function finalState(schema, scenario, state) {
  const completed_at = fx.at(state.clock + 2);
  if (scenario === 'run_failed') {
    return {
      status: 'failed',
      error: {
        code: 'document_unreadable',
        message: 'Документ не читается: страница пустая или повреждена.',
      },
      pages: { physical: 1, billed: 0 },
      completed_at,
    };
  }
  if (scenario === 'output_expired') {
    return {
      status: 'succeeded',
      output: null,
      fields: null,
      output_expired: true,
      pages: { physical: 1, billed: 1 },
      completed_at,
    };
  }
  return {
    status: 'succeeded',
    ...fx.extraction(schema, { review: scenario === 'needs_review' }),
    pages: { physical: 1, billed: 1 },
    completed_at,
  };
}

/** @param {State} state @param {{ filename?: string, content_type?: string, bytes: Buffer, size: number }} part */
function storeFile(state, part) {
  const id = state.id('file');
  const f = fx.file({
    id,
    filename: sanitizeName(part.filename),
    mime: sniff(part.bytes, part.content_type),
    size: part.size,
    created_at: state.now(),
  });
  state.files.set(id, f);
  state.fileBytes.set(id, part.bytes);
  return f;
}

/** @param {State} state @param {any} c @param {'installed' | 'forked'} relation @param {string} [name] */
function typizerFromCatalog(state, c, relation, name) {
  const id = state.id('typizer');
  const t = fx.typizer({
    id,
    name: name ?? c.title,
    schema: c.schema,
    catalog_source: fx.catalogSource(c, relation),
    created_at: state.now(),
  });
  state.typizers.set(id, t);
  state.versions.set(id, [fx.typizerVersion(t, 1, t.created_at)]);
  return t;
}

/** @param {State} state @param {string} entryId */
function countFromCatalog(state, entryId) {
  return sorted(state.typizers).filter((t) => t.catalog_source?.entry_id === entryId).length;
}

/**
 * @param {Ctx} ctx @param {'files' | 'typizers' | 'runs' | 'batches' | 'endpoints' | 'deliveries'} map
 * @param {keyof typeof ID_PREFIX} kind @param {(entity: any) => HandlerResult} fn
 * @returns {HandlerResult}
 */
function withEntity(ctx, map, kind, fn) {
  const id = String(ctx.params.id);
  if (!isValidId(id, kind))
    return { error: 'invalid_id', detail: `"${id}" is not a ${ID_PREFIX[kind]}_… id`, param: 'id' };
  const entity = ctx.state[map].get(id);
  return entity ? fn(entity) : { error: 'not_found', detail: `No ${kind} ${id}` };
}

/** @param {Ctx} _ctx @param {string} ref @param {(c: any) => HandlerResult} fn @returns {HandlerResult} */
function withCatalog(_ctx, ref, fn) {
  const c = fx.findCatalog(ref);
  return c ? fn(c) : { error: 'not_found', detail: `No catalog schema "${ref}"`, param: 'entry' };
}

/** @param {string} id @param {keyof typeof ID_PREFIX} kind */
function isValidId(id, kind) {
  return new RegExp(`^${ID_PREFIX[kind]}_[0-9a-hjkmnp-tv-z]{26}$`).test(id);
}

/** The API's own checks on a schema, reduced to what an SDK can trip: root must be an object. */
function schemaProblem(schema) {
  if (!isPlainObject(schema))
    return { error: 'invalid_schema', detail: 'schema must be a JSON object', param: 'schema' };
  if (schema.type !== 'object' || !isPlainObject(schema.properties)) {
    return {
      error: 'invalid_schema',
      detail: 'The schema root must be {"type": "object", "properties": {…}}',
      param: 'schema',
    };
  }
  return null;
}

/** Newest first, the order of every list in the API. @param {Map<string, any>} map */
function sorted(map) {
  return [...map.values()].sort((a, b) =>
    a.created_at < b.created_at ? 1 : a.created_at > b.created_at ? -1 : 0,
  );
}

/**
 * A `{object: 'list', data, has_more, next_cursor}` page. The cursor is the last id of the page,
 * passed back as `starting_after`.
 * @param {any[]} items @param {Record<string, any>} query
 */
function page(items, query, cursorParam = 'starting_after', idOf = (x) => x.id) {
  const limit = Number(query.limit ?? 20);
  let start = 0;
  if (query[cursorParam] !== undefined) {
    const idx = items.findIndex((x) => idOf(x) === query[cursorParam]);
    if (idx < 0)
      return {
        error: 'invalid_request',
        detail: `${cursorParam} is not an id from this list`,
        param: cursorParam,
      };
    start = idx + 1;
  }
  const data = items.slice(start, start + limit);
  const more = start + limit < items.length;
  return {
    status: 200,
    body: {
      object: 'list',
      data,
      has_more: more,
      next_cursor: more ? idOf(data[data.length - 1]) : null,
    },
  };
}

/** @param {ReturnType<typeof compileRoutes>} routes @param {string} method @param {string} path */
function matchRoute(routes, method, path) {
  for (const r of routes) {
    if (r.method !== method) continue;
    const match = r.re.exec(path);
    if (match) return { ...r, match };
  }
  return null;
}

/** @param {any} openapi */
export function compileRoutes(openapi) {
  const routes = [];
  for (const [path, item] of Object.entries(openapi.paths)) {
    for (const method of ['get', 'post', 'put', 'patch', 'delete']) {
      const op = item[method];
      if (!op) continue;
      /** @type {string[]} */
      const names = [];
      const pattern = path.replace(/\{([^}]+)\}/g, (_, n) => {
        names.push(n);
        return '([^/]+)';
      });
      const re = new RegExp(`^${pattern}$`);
      routes.push({
        method: method.toUpperCase(),
        path,
        re,
        names,
        op,
        opId: /** @type {string} */ (op.operationId),
      });
    }
  }
  // Literal segments win over parameters: /v1/typizers/generate before /v1/typizers/{id}.
  return routes.sort((a, b) => a.names.length - b.names.length);
}

/** @param {string | undefined} raw @param {any} schema */
function coerce(raw, schema) {
  if (raw === undefined) return raw;
  const types = Array.isArray(schema?.type) ? schema.type : [schema?.type];
  if ((types.includes('integer') || types.includes('number')) && /^-?\d+(\.\d+)?$/.test(raw))
    return Number(raw);
  if (types.includes('null') && raw === 'null') return null;
  return raw;
}

/** @param {import('node:http').IncomingMessage} req */
function readBody(req) {
  return new Promise((resolve, reject) => {
    /** @type {Buffer[]} */
    const chunks = [];
    let size = 0;
    let tooLarge = false;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY_BYTES) tooLarge = true;
      else chunks.push(c);
    });
    req.on('end', () => resolve({ buf: Buffer.concat(chunks), size, tooLarge }));
    req.on('error', reject);
  });
}

/** @param {Buffer} buf @param {string} ct */
async function parseBody(buf, ct) {
  if (buf.length === 0) return { kind: 'none' };
  if (/^application\/(.+\+)?json/i.test(ct)) {
    try {
      return { kind: 'json', value: JSON.parse(buf.toString('utf8')) };
    } catch {
      return { kind: 'invalid_json' };
    }
  }
  if (/^multipart\/form-data/i.test(ct)) {
    const form = await new Request('http://mock/', {
      method: 'POST',
      headers: { 'content-type': ct },
      body: buf,
    }).formData();
    /** @type {Record<string, string | string[]>} */
    const fields = {};
    const files = [];
    for (const [name, value] of form.entries()) {
      if (typeof value === 'string') {
        const prev = fields[name];
        fields[name] =
          prev === undefined ? value : [...(Array.isArray(prev) ? prev : [prev]), value];
      } else {
        const bytes = Buffer.from(await value.arrayBuffer());
        files.push({
          field: name,
          filename: value.name,
          content_type: value.type || null,
          size: bytes.length,
          sha256: sha256(bytes),
          bytes,
        });
      }
    }
    return { kind: 'multipart', fields, files };
  }
  return { kind: 'other' };
}

/** The request log's view of a body: JSON as sent, multipart fields and file metadata. */
function logBody(parsed, ct, size) {
  switch (parsed.kind) {
    case 'json':
      return { type: 'json', value: parsed.value };
    case 'multipart':
      return {
        type: 'multipart',
        fields: parsed.fields,
        files: parsed.files.map(({ bytes: _, ...f }) => f),
      };
    case 'none':
      return { type: 'none' };
    default:
      return { type: 'raw', content_type: ct || null, size };
  }
}

/** @param {string} code @param {string} detail @param {string | undefined} param @param {string} requestId */
function problemBody(code, detail, param, requestId) {
  const type = `https://docs.typizer.ru/errors/${code}`;
  return {
    type,
    title: ERROR_TITLES[code],
    status: ERROR_STATUS[code],
    detail,
    code,
    ...(param ? { param } : {}),
    request_id: requestId,
    doc_url: type,
  };
}

/** @param {string} key */
function scenarioOf(key) {
  const m = /^tz_(?:test|live)_([a-z0-9_]+?)(?:__.*)?$/.exec(key);
  const name = m ? m[1] : key ? 'invalid' : 'none';
  return { name, known: name in SCENARIOS };
}

/** The client's own X-Request-Id if it is sane, else a fresh `req_…`. */
let requestCounter = 0;
function requestIdFrom(header) {
  const v = Array.isArray(header) ? header[0] : header;
  if (typeof v === 'string' && /^[A-Za-z0-9._:-]{1,128}$/.test(v)) return v;
  return fx.makeId('req', `request:${++requestCounter}`);
}

/** @param {string} key @param {string} endpointId */
function secretFor(key, endpointId) {
  return `whsec_${createHash('sha256').update(`${key}:${endpointId}`).digest('base64')}`;
}

/** A webhook signed now with `secret` (Standard Webhooks, the way Typizer signs deliveries). */
export function signedSample(
  secret,
  type = 'run.succeeded',
  nowSec = Math.floor(Date.now() / 1000),
) {
  const id = fx.makeId('evt', `sample:${nowSec}`);
  const runId = fx.makeId('run', 'sample');
  const object =
    type === 'batch.completed'
      ? fx.batch({
          id: fx.makeId('batch', 'sample'),
          typizer_id: fx.makeId('tz', 'sample'),
          total: 2,
          created_at: fx.at(0),
        })
      : fx.run({
          id: runId,
          file_id: fx.makeId('file', 'sample'),
          created_at: fx.at(0),
          ...(type === 'run.failed'
            ? {
                status: 'failed',
                error: { code: 'document_unreadable', message: 'Документ не читается.' },
                pages: { physical: 1, billed: 0 },
              }
            : {
                status: 'succeeded',
                ...fx.extraction(fx.INVOICE_SCHEMA),
                pages: { physical: 1, billed: 1 },
              }),
          completed_at: fx.at(3),
        });
  const body = JSON.stringify({
    id,
    type,
    created_at: new Date(nowSec * 1000).toISOString(),
    data: { object },
  });
  const encoded = secret.startsWith('whsec_') ? secret.slice(6) : secret;
  const sig = createHmac('sha256', Buffer.from(encoded, 'base64'))
    .update(`${id}.${nowSec}.${body}`)
    .digest('base64');
  return {
    secret,
    headers: {
      'webhook-id': id,
      'webhook-timestamp': String(nowSec),
      'webhook-signature': `v1,${sig}`,
    },
    body,
  };
}

/** @param {Buffer | string} data */
function sha256(data) {
  return createHash('sha256').update(data).digest('hex');
}

/** JSON with sorted keys, so two equal requests fingerprint the same. */
function stableStringify(v) {
  if (Array.isArray(v)) return `[${v.map(stableStringify).join(',')}]`;
  if (isPlainObject(v)) {
    return `{${Object.keys(v)
      .filter((k) => v[k] !== undefined)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${stableStringify(v[k])}`)
      .join(',')}}`;
  }
  return JSON.stringify(v) ?? 'null';
}

/** @param {Buffer} bytes @param {string | null | undefined} declared */
function sniff(bytes, declared) {
  if (bytes.subarray(0, 5).toString('latin1') === '%PDF-') return 'application/pdf';
  if (bytes[0] === 0x89 && bytes.subarray(1, 4).toString('latin1') === 'PNG') return 'image/png';
  if (bytes[0] === 0xff && bytes[1] === 0xd8) return 'image/jpeg';
  if (declared && declared !== 'application/octet-stream') return declared.split(';')[0];
  return 'application/pdf';
}

/** @param {string | undefined} name */
function sanitizeName(name) {
  const last =
    String(name ?? '')
      .split(/[\\/]/)
      .pop() ?? '';
  const base = [...last].filter((ch) => ch.charCodeAt(0) >= 0x20).join('');
  return base || 'document';
}

/** @param {number} ms */
function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** @param {unknown} v @param {string} name */
function xmlNode(v, name) {
  if (v === null || v === undefined) return `<${name} xsi:nil="true"/>`;
  if (Array.isArray(v)) return `<${name}>${v.map((x) => xmlNode(x, 'item')).join('')}</${name}>`;
  if (typeof v === 'object')
    return `<${name}>${Object.entries(v)
      .map(([k, x]) => xmlNode(x, k))
      .join('')}</${name}>`;
  return `<${name}>${String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;')}</${name}>`;
}

function xmlFor(output) {
  const inner = xmlNode(output, 'document').replace(
    '<document>',
    '<document xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">',
  );
  return `<?xml version="1.0" encoding="UTF-8"?>\n${inner}\n`;
}

function csvFor(output) {
  const leaves = fx.leafPaths(output);
  const cell = (v) => (v === null || v === undefined ? '' : `"${String(v).replace(/"/g, '""')}"`);
  return `${leaves.map(([p]) => p).join(',')}\n${leaves.map(([, v]) => cell(v)).join(',')}\n`;
}

function xsdFor(schema) {
  const el = (name, s) => {
    const t = Array.isArray(s?.type) ? s.type.find((x) => x !== 'null') : s?.type;
    if (t === 'object') {
      return `<xs:element name="${name}" minOccurs="0" nillable="true"><xs:complexType><xs:sequence>${Object.entries(
        s.properties ?? {},
      )
        .map(([k, v]) => el(k, v))
        .join('')}</xs:sequence></xs:complexType></xs:element>`;
    }
    const xs =
      { number: 'xs:decimal', integer: 'xs:integer', boolean: 'xs:boolean' }[t] ??
      (s?.format === 'date' ? 'xs:date' : 'xs:string');
    return `<xs:element name="${name}" type="${xs}" minOccurs="0" nillable="true"/>`;
  };
  return `<?xml version="1.0" encoding="UTF-8"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">${el('document', schema).replace(' minOccurs="0" nillable="true"', '')}</xs:schema>\n`;
}

// Binary outputs are opaque to an SDK (it streams bytes to the caller), so the mock serves
// recognisable placeholder bytes with the right Content-Type instead of real spreadsheets.
const XLSX_PLACEHOLDER = Buffer.from('PK\u0003\u0004typizer-mock-xlsx', 'latin1');
const ZIP_PLACEHOLDER = Buffer.from('PK\u0003\u0004typizer-mock-zip', 'latin1');
