import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { request as httpRequest } from 'node:http';
import test from 'node:test';
import { createWorkerServer } from './server.mjs';
import { createDispatcher } from './dispatch.mjs';
import { createDeliveryTicker } from './delivery-jobs.mjs';

const TOKEN = Buffer.alloc(32, 3).toString('base64url');
const PRIVATE = 'SYNTHETIC_PRIVATE_MUST_NOT_ESCAPE';
const idle = () => ({ status: 'IDLE', claimed: 0, submitted: 0, unknown: 0, failed: 0 });
const processed = () => ({ status: 'PROCESSED', claimed: 1, processed: 1, retried: 0, paused: 0 });

async function server(t, overrides = {}) {
  const instance = createWorkerServer({ config: { controlToken: TOKEN }, dispatchOne: async () => idle(),
    processInbox: async () => processed(), ...overrides });
  await new Promise(resolve => instance.listen(0, '127.0.0.1', resolve));
  t.after(async () => { instance.closeAllConnections(); await new Promise(resolve => instance.close(resolve)); });
  const origin = `http://127.0.0.1:${instance.address().port}`;
  const call = async (path = '/v1/dispatch-one', options = {}) => {
    const response = await fetch(origin + path, { method: 'POST', headers: { Authorization: 'Bearer ' + TOKEN,
      'content-type': 'application/json' }, body: '{}', ...options });
    return { status: response.status, body: await response.json() };
  };
  return { origin, call };
}

test('exposes only safe health metadata and authenticates control operations', async t => {
  const h = await server(t);
  const health = await fetch(h.origin + '/healthz');
  assert.deepEqual(await health.json(), { status: 'UP', role: 'WA070_WORKER' });
  assert.deepEqual(await h.call(), { status: 200, body: idle() });
  for (const authorization of ['', 'Basic ' + PRIVATE, 'Bearer ' + PRIVATE]) {
    const result = await h.call('/v1/dispatch-one', { headers: { authorization, 'content-type': 'application/json' } });
    assert.deepEqual(result, { status: 401, body: { status: 'UNAUTHORIZED' } });
  }
});

test('rejects unknown paths, queries and methods without invoking callbacks', async t => {
  let calls = 0;
  const h = await server(t, { dispatchOne: async () => { calls++; return idle(); } });
  for (const path of ['/private', '/v1/dispatch-one?actor=foreign', '/v1/%64ispatch-one']) assert.equal((await h.call(path)).status, 404);
  assert.equal((await h.call('/v1/dispatch-one', { method: 'GET', body: undefined })).status, 405);
  assert.equal(calls, 0);
});

test('accepts only an empty JSON object with a body at most 1024 bytes', async t => {
  let calls = 0;
  const h = await server(t, { dispatchOne: async () => { calls++; return idle(); } });
  for (const body of ['', 'null', '[]', '{', JSON.stringify({ private: PRIVATE }), ' '.repeat(1025)])
    assert.deepEqual(await h.call('/v1/dispatch-one', { body }), { status: 400, body: { status: 'INVALID_REQUEST' } });
  assert.equal(calls, 0);
});

test('rejects a stalled partial body within its read budget before invoking business callbacks', async t => {
  let calls = 0;
  const h = await server(t, { bodyTimeoutMs: 15, dispatchOne: async () => { calls++; return idle(); } });
  const result = await new Promise((resolve, reject) => {
    const request = httpRequest(h.origin + '/v1/dispatch-one', { method: 'POST', headers: {
      Authorization: 'Bearer ' + TOKEN, 'content-type': 'application/json', 'content-length': '2',
    } }, response => {
      let body = '';
      response.on('data', chunk => { body += chunk; });
      response.on('end', () => resolve({ status: response.statusCode, body: JSON.parse(body) }));
    });
    request.on('error', reject);
    request.write('{');
  });
  assert.deepEqual(result, { status: 400, body: { status: 'INVALID_REQUEST' } });
  assert.equal(calls, 0);
});

test('rejects duplicate authorization headers instead of accepting a parser-selected value', async t => {
  let calls = 0;
  const h = await server(t, { dispatchOne: async () => { calls++; return idle(); } });
  const result = await new Promise((resolve, reject) => {
    const request = httpRequest(h.origin + '/v1/dispatch-one', { method: 'POST', headers: {
      Authorization: ['Bearer ' + TOKEN, 'Bearer ' + TOKEN], 'content-type': 'application/json',
    } }, response => {
      let body = '';
      response.on('data', chunk => { body += chunk; });
      response.on('end', () => resolve({ status: response.statusCode, body: JSON.parse(body) }));
    });
    request.on('error', reject);
    request.end('{}');
  });
  assert.deepEqual(result, { status: 401, body: { status: 'UNAUTHORIZED' } });
  assert.equal(calls, 0);
});

test('never exposes callback exceptions, extra fields or inconsistent result counts', async t => {
  for (const dispatchOne of [async () => { throw new Error(PRIVATE); }, async () => ({ ...idle(), secret: PRIVATE }),
    async () => ({ status: 'SUBMITTED', claimed: 0, submitted: 1, unknown: 0, failed: 0 })]) {
    const h = await server(t, { dispatchOne });
    const result = await h.call();
    assert.deepEqual(result.body, { ...idle(), status: 'UNAVAILABLE' });
    assert.equal(JSON.stringify(result).includes(PRIVATE), false);
  }
});

test('keeps dispatch serial across HTTP callers', async t => {
  let release;
  let entered;
  let calls = 0;
  const started = new Promise(resolve => { entered = resolve; });
  const h = await server(t, { dispatchOne: () => { calls++; entered(); return new Promise(resolve => { release = resolve; }); } });
  const first = h.call();
  await started;
  assert.deepEqual((await h.call()).body, { ...idle(), status: 'BUSY' });
  assert.equal(calls, 1);
  release(idle());
  assert.deepEqual((await first).body, idle());
});

test('exposes NOT_SUBMITTED without delivery counts and rejects inconsistent summaries', async t => {
  for (const value of [{ status: 'NOT_SUBMITTED', claimed: 1, submitted: 0, unknown: 0, failed: 0 },
    { status: 'NOT_SUBMITTED', claimed: 1, submitted: 1, unknown: 0, failed: 0 },
    { status: 'NOT_SUBMITTED', claimed: 0, submitted: 0, unknown: 0, failed: 0 }]) {
    const h = await server(t, { dispatchOne: async () => value });
    assert.deepEqual((await h.call()).body, value.claimed === 1 && value.submitted === 0 ? value : { ...idle(), status: 'UNAVAILABLE' });
  }
});

test('admits at most two independent process callbacks without changing global n8n settings', async t => {
  const releases = [];
  let entered;
  const started = new Promise(resolve => { entered = resolve; });
  const h = await server(t, { processInbox: () => new Promise(resolve => { releases.push(resolve); if (releases.length === 2) entered(); }) });
  const first = h.call('/v1/process-inbox'), second = h.call('/v1/process-inbox');
  await started;
  assert.deepEqual((await h.call('/v1/process-inbox')).body, { status: 'UNAVAILABLE', claimed: 0, processed: 0, retried: 0, paused: 0 });
  assert.equal(releases.length, 2);
  releases.forEach(resolve => resolve(processed()));
  assert.deepEqual((await first).body, processed());
  assert.deepEqual((await second).body, processed());
});

test('caller disconnection does not abort an already authorized callback or its receipt', async t => {
  let release;
  let entered;
  let finished;
  let receipts = 0;
  const started = new Promise(resolve => { entered = resolve; });
  const done = new Promise(resolve => { finished = resolve; });
  const h = await server(t, { dispatchOne: async () => { entered(); await new Promise(resolve => { release = resolve; }); receipts++; finished(); return idle(); } });
  const request = httpRequest(h.origin + '/v1/dispatch-one', { method: 'POST', headers: { Authorization: 'Bearer ' + TOKEN, 'content-type': 'application/json' } });
  request.on('error', () => {});
  request.end('{}');
  await started;
  request.destroy();
  release();
  await done;
  assert.equal(receipts, 1);
});

test('responds within its budget but keeps a stalled authorized dispatch locked', async t => {
  let calls = 0;
  const h = await server(t, { operationTimeoutMs: 15, dispatchOne: () => { calls++; return new Promise(() => {}); } });
  const started = Date.now();
  assert.deepEqual((await h.call()).body, { ...idle(), status: 'UNAVAILABLE' });
  assert.ok(Date.now() - started < 1000);
  assert.deepEqual((await h.call()).body, { ...idle(), status: 'BUSY' });
  assert.equal(calls, 1);
});

test('keeps an absent process callback unavailable without inventing business behavior', async t => {
  const h = await server(t, { processInbox: undefined });
  assert.deepEqual(await h.call('/v1/process-inbox'), { status: 503,
    body: { status: 'UNAVAILABLE', claimed: 0, processed: 0, retried: 0, paused: 0 } });
});

test('W04 sanitizes attendance counters, rejects business input and fails closed without its job credential', async t => {
  const route = '/v1/tick-attendance';
  assert.equal((await (await server(t)).call(route)).status, 503);
  const h = await server(t, { tickAttendance: async () => ({ status: 'MORE', calendarProcessed: 200, reminderProcessed: 1 }) });
  assert.deepEqual(await h.call(route), { status: 200, body: { status: 'MORE', calendarProcessed: 200, reminderProcessed: 1 } });
  assert.equal((await h.call(route, { body: '{"recipient":"synthetic"}' })).status, 400);
  assert.equal((await h.call(route, { headers: { 'content-type': 'application/json' } })).status, 401);
  const bad = await server(t, { tickAttendance: async () => ({ status: 'IDLE', calendarProcessed: 0, reminderProcessed: 0, private: PRIVATE }) });
  assert.deepEqual(await bad.call(route), { status: 503, body: { status: 'UNAVAILABLE', calendarProcessed: 0, reminderProcessed: 0 } });
});

test('W08 authenticates its empty control body and stays unavailable without the job callback', async t => {
  const route = '/v1/reconcile-delivery';
  assert.deepEqual(await (await server(t)).call(route), { status: 503, body: { status: 'UNAVAILABLE', processed: 0 } });
  let calls = 0;
  const h = await server(t, { reconcileDelivery: async () => { calls++; return { status: 'MORE', processed: 200 }; } });
  assert.deepEqual(await h.call(route), { status: 200, body: { status: 'MORE', processed: 200 } });
  for (const body of ['', 'null', '[]', '{', '{"cursor":"private"}', '{"jobType":"HEALTH_RECONCILE"}', ' '.repeat(1025)])
    assert.deepEqual(await h.call(route, { body }), { status: 400, body: { status: 'INVALID_REQUEST' } });
  assert.equal((await h.call(route, { headers: { 'content-type': 'application/json' } })).status, 401);
  assert.equal((await h.call(route, { method: 'GET', body: undefined })).status, 405);
  assert.equal((await h.call(route + '?cursor=private')).status, 404);
  assert.equal(calls, 1);
});

test('W08 exposes no API metadata, exceptions, cursors or invalid counters', async t => {
  for (const reconcileDelivery of [async () => { throw Error(PRIVATE); },
    async () => ({ status: 'IDLE', processed: 0, nextCursor: PRIVATE }),
    async () => ({ status: 'MORE', processed: 201 }), async () => ({ status: 'IDLE', processed: 1 })]) {
    const h = await server(t, { reconcileDelivery });
    const result = await h.call('/v1/reconcile-delivery');
    assert.deepEqual(result, { status: 503, body: { status: 'UNAVAILABLE', processed: 0 } });
    assert.equal(JSON.stringify(result).includes(PRIVATE), false);
  }
});

test('W08 has its own serial limit and can reconcile while a dispatch remains in progress', async t => {
  let releaseDispatch, releaseDelivery, enteredDispatch, enteredDelivery, calls = 0;
  const dispatchStarted = new Promise(resolve => { enteredDispatch = resolve; });
  const deliveryStarted = new Promise(resolve => { enteredDelivery = resolve; });
  const h = await server(t, {
    dispatchOne: () => { enteredDispatch(); return new Promise(resolve => { releaseDispatch = resolve; }); },
    reconcileDelivery: () => { calls++; enteredDelivery(); return new Promise(resolve => { releaseDelivery = resolve; }); },
  });
  const dispatch = h.call();
  await dispatchStarted;
  const reconciliation = h.call('/v1/reconcile-delivery');
  await deliveryStarted;
  assert.deepEqual(await h.call('/v1/reconcile-delivery'), { status: 200, body: { status: 'BUSY', processed: 0 } });
  assert.equal(calls, 1);
  releaseDelivery({ status: 'PROCESSED', processed: 1 });
  assert.deepEqual(await reconciliation, { status: 200, body: { status: 'PROCESSED', processed: 1 } });
  assert.equal((await h.call()).body.status, 'BUSY');
  releaseDispatch(idle());
  assert.deepEqual((await dispatch).body, idle());
});

test('W08 keeps running with Evolution closed without invoking another connection read', async t => {
  let reads = 0, jobs = 0;
  const dispatchOne = createDispatcher({ config: { evolutionBaseUrl: 'http://127.0.0.1:9999', instance: 'synthetic', evolutionApiKey: 'synthetic' },
    api: { request: async () => { assert.fail('closed Evolution must not claim'); } },
    readConnection: async () => { reads++; return { status: 'CLOSED' }; },
  });
  const reconcileDelivery = createDeliveryTicker({ api: { request: async (path, { body }) => {
    jobs++;
    assert.equal(path, '/integrations/whatsapp/v1/jobs/tick');
    assert.equal(body.jobType, 'HEALTH_RECONCILE');
    return { status: 200, body: { jobType: body.jobType, processed: 12, nextCursor: null, serverTime: new Date().toISOString() } };
  } } });
  const h = await server(t, { dispatchOne, reconcileDelivery });
  assert.deepEqual((await h.call()).body, { ...idle(), status: 'UNAVAILABLE' });
  assert.deepEqual(await h.call('/v1/reconcile-delivery'), { status: 200, body: { status: 'PROCESSED', processed: 12 } });
  assert.equal(reads, 1);
  assert.equal(jobs, 1);
});

test('a timed-out W08 caller leaves reconciliation locked until the authorized callback settles', async t => {
  let calls = 0, release;
  const h = await server(t, { operationTimeoutMs: 15, reconcileDelivery: () => {
    calls++;
    return calls === 1 ? new Promise(resolve => { release = resolve; }) : { status: 'IDLE', processed: 0 };
  } });
  assert.deepEqual(await h.call('/v1/reconcile-delivery'), { status: 503, body: { status: 'UNAVAILABLE', processed: 0 } });
  assert.deepEqual(await h.call('/v1/reconcile-delivery'), { status: 200, body: { status: 'BUSY', processed: 0 } });
  assert.equal(calls, 1);
  release({ status: 'PROCESSED', processed: 1 });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(await h.call('/v1/reconcile-delivery'), { status: 200, body: { status: 'IDLE', processed: 0 } });
  assert.equal(calls, 2);
});
