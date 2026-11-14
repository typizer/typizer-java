#!/usr/bin/env node
/**
 * Runs the Typizer conformance mock: `node server.js` (PORT, default 4010; HOST, default
 * 127.0.0.1 — the Docker image sets 0.0.0.0). Point an SDK at `http://127.0.0.1:4010` with the API
 * key `tz_test_<scenario>`; the scenarios are listed in SCENARIOS.md and at `GET /_mock/scenarios`.
 */
import { createMock, findOpenApi, SCENARIOS } from './mock.js';

const port = Number(process.env.PORT ?? 4010);
const host = process.env.HOST ?? '127.0.0.1';
const mock = createMock();
const url = await mock.listen(port, host);

console.log(`Typizer conformance mock on ${url} (spec: ${findOpenApi()})`);
console.log(`Scenarios (API key tz_test_<name>): ${Object.keys(SCENARIOS).join(', ')}`);
console.log(`Request log: GET ${url}/_mock/requests?key=<api key>; reset: POST ${url}/_mock/reset`);

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    mock.close().then(() => process.exit(0));
  });
}
