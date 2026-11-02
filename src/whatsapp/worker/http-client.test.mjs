import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import test from 'node:test';
import { createApiClient, WorkerError } from './http-client.mjs';

const NOW = Date.parse('2030-01-01T12:00:00Z');
const TOKEN = Buffer.alloc(32, 1).toString('base64url');
const LEASE = Buffer.alloc(32, 2).toString('base64url');
const ID = '00000000-0000-0000-0000-000000000001';
const PREFIX = '/integrations/whatsapp/v1/';
const PRIVATE = 'SYNTHETIC_PRIVATE_MUST_NOT_ESCAPE';
const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
const client = fetchImpl => createApiClient({ baseUrl: 'https://synthetic.example.test', token: TOKEN, fetchImpl, now: () => NOW });

test('sends one bounded request with its configured Bearer credential and exact closed path', async () => {
  let calls = 0;
  const api = client(async (url, request) => {
    calls++;
    assert.equal(url, 'https://synthetic.example.test' + PREFIX + 'inbox/claim');
    assert.equal(request.headers.Authorization, 'Bearer ' + TOKEN);
    assert.equal(request.redirect, 'manual');
    assert.deepEqual(JSON.parse(request.body), { batchLimit: 1, leaseSeconds: 60 });
    return json({ items: [], serverTime: new Date(NOW).toISOString() });
  });
  assert.equal((await api.request(PREFIX + 'inbox/claim', { body: { batchLimit: 1, leaseSeconds: 60 } })).status, 200);
  assert.equal(calls, 1);
});

test('supports canonical context GET and the controlled command headers', async () => {
  const api = client(async (_, request) => {
    assert.equal(request.method, 'GET');
    assert.equal(request.body, undefined);
    assert.equal(request.headers['X-Inbox-Lease'], LEASE);
    assert.equal(request.headers['Idempotency-Key'], 'synthetic-command-0001');
    return json({ ready: true });
  });
  assert.deepEqual((await api.request(PREFIX + 'context?inboxEventId=' + ID, { method: 'GET',
    headers: { 'X-Inbox-Lease': LEASE, 'Idempotency-Key': 'synthetic-command-0001' } })).body, { ready: true });
});

test('rejects foreign, traversing, unknown and noncanonical query paths before HTTP', async () => {
  let calls = 0;
  const api = client(async () => { calls++; return json({}); });
  for (const path of ['https://foreign.example.test/', '//foreign.example.test', PREFIX + '../commands', PREFIX + 'commands?actor=foreign',
    PREFIX + 'context?inboxEventId=' + ID + '&extra=true', PREFIX + 'context?inboxEventId=%30' + ID.slice(1), PREFIX + 'unknown'])
    await assert.rejects(api.request(path, { body: {} }), error => error instanceof WorkerError && error.code === 'INVALID_REQUEST');
  assert.equal(calls, 0);
});

test('rejects credential overrides and oversized request bodies without HTTP', async () => {
  let calls = 0;
  const api = client(async () => { calls++; return json({}); });
  for (const options of [{ body: {}, headers: { Authorization: PRIVATE } }, { body: {}, headers: { 'X-Inbox-Lease': PRIVATE } },
    { body: { private: 'x'.repeat(32769) } }, { body: [] }]) await assert.rejects(api.request(PREFIX + 'commands', options), WorkerError);
  assert.equal(calls, 0);
});

test('keeps non-success HTTP responses bounded without throwing their contents', async () => {
  assert.deepEqual(await client(async () => json({ errorCode: 'DEPENDENCY_UNAVAILABLE' }, 503)).request(PREFIX + 'outbox/claim', { body: {} }),
    { status: 503, body: { errorCode: 'DEPENDENCY_UNAVAILABLE' } });
});

test('never follows redirects or retries network failures and sanitizes exceptions', async () => {
  for (const action of [async () => new Response(PRIVATE, { status: 307 }), async () => { throw new Error(PRIVATE + TOKEN); }]) {
    let calls = 0;
    const api = client(async (...args) => { calls++; return action(...args); });
    await assert.rejects(api.request(PREFIX + 'outbox/claim', { body: {} }), error => {
      assert.equal(error instanceof WorkerError, true);
      assert.equal(error.message.includes(PRIVATE), false);
      assert.equal(error.stack.includes(TOKEN), false);
      assert.equal(error.cause, undefined);
      return true;
    });
    assert.equal(calls, 1);
  }
});

test('rejects an expired deadline before fetching', async () => {
  let calls = 0;
  await assert.rejects(client(async () => { calls++; return json({}); }).request(PREFIX + 'outbox/claim', { body: {}, deadline: NOW }),
    error => error.code === 'TIMEOUT');
  assert.equal(calls, 0);
});

test('bounds both a stalled fetch and a stalled response stream', async () => {
  for (const fetchImpl of [() => new Promise(() => {}), async () => new Response(new ReadableStream({ pull() {} }))]) {
    const started = Date.now();
    await assert.rejects(client(fetchImpl).request(PREFIX + 'outbox/claim', { body: {}, deadline: NOW + 15 }), error => error.code === 'TIMEOUT');
    assert.ok(Date.now() - started < 1000);
  }
});

test('cancels oversized response streams after at most 32KiB', async () => {
  let canceled = 0;
  let pulls = 0;
  const stream = new ReadableStream({ pull(controller) { pulls++; controller.enqueue(new Uint8Array(16384)); }, cancel() { canceled++; } });
  await assert.rejects(client(async () => new Response(stream)).request(PREFIX + 'outbox/claim', { body: {} }), error => error.code === 'INVALID_RESPONSE');
  assert.equal(canceled, 1);
  assert.ok(pulls <= 4);
});

test('accepts exactly 32KiB while malformed or non-object JSON yields closed errors', async () => {
  const body = '{"ready":true}' + ' '.repeat(32768 - 14);
  assert.deepEqual((await client(async () => new Response(body)).request(PREFIX + 'outbox/claim', { body: {} })).body, { ready: true });
  for (const invalid of ['{', 'null', '[]', new Uint8Array([255])])
    await assert.rejects(client(async () => new Response(invalid)).request(PREFIX + 'outbox/claim', { body: {} }), WorkerError);
});
