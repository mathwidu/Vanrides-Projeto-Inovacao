import test from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { createServer } from 'node:http';
import { readEvolutionConnection } from './evolution-connection.mjs';

const INPUT = { baseUrl: 'https://provider.invalid', instance: 'synthetic-120', apiKey: 'synthetic-key' };
const body = state => ({ instance: { instanceName: INPUT.instance, state } });
const answer = value => new Response(JSON.stringify(value), { status: 200 });

test('reads the exact instance using one GET, without connect/send or exposing provider data', async t => {
  const calls = [];
  const server = createServer((request, response) => {
    calls.push({ method: request.method, path: request.url, key: request.headers.apikey });
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify(body('open')));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const result = await readEvolutionConnection({ ...INPUT, baseUrl: `http://127.0.0.1:${server.address().port}` });
  assert.deepEqual(result, { status: 'OPEN' });
  assert.deepEqual(calls, [{ method: 'GET', path: '/instance/connectionState/synthetic-120', key: INPUT.apiKey }]);
});

test('requires the exact instance and a recognized state; closed/connecting/refused never authorize', async () => {
  for (const state of ['close', 'connecting', 'refused']) {
    assert.deepEqual(await readEvolutionConnection({ ...INPUT, fetchImpl: async () => answer(body(state)) }), { status: 'CLOSED' });
  }
  for (const value of [body('connected'), { instance: { instanceName: 'another', state: 'open' } },
    { instance: { state: 'open' } }, { ...body('open'), extra: 'provider-secret' }, null, []]) {
    assert.deepEqual(await readEvolutionConnection({ ...INPUT, fetchImpl: async () => answer(value) }), { status: 'UNKNOWN' });
  }
});

test('rejects invalid destinations, credentials and expired deadlines without network calls', async () => {
  let calls = 0;
  for (const change of [{ baseUrl: 'http://example.com' }, { baseUrl: 'http://127.1' },
    { baseUrl: 'https://user:password@example.com' }, { baseUrl: 'https://example.com/path' },
    { baseUrl: 'https://example.com?key=x' }, { instance: '../other' }, { apiKey: 'bad\nheader' },
    { deadline: 999, now: () => 1000 }]) {
    assert.deepEqual(await readEvolutionConnection({ ...INPUT, ...change,
      fetchImpl: async () => { calls++; return answer(body('open')); } }), { status: 'UNKNOWN' });
  }
  assert.equal(calls, 0);
});

test('does not follow redirects or retry provider errors', async () => {
  for (const status of [301, 401, 404, 429, 500]) {
    let calls = 0;
    const result = await readEvolutionConnection({ ...INPUT, fetchImpl: async (_url, options) => {
      calls++; assert.equal(options.redirect, 'manual'); assert.equal(options.method, 'GET');
      return new Response('private provider response', { status });
    } });
    assert.deepEqual(result, { status: 'UNKNOWN' }); assert.equal(calls, 1);
  }
});

test('timeout stays UNKNOWN even when an injected fetch ignores cancellation and resolves late', async () => {
  let release;
  let signal;
  const promise = readEvolutionConnection({ ...INPUT, deadline: Date.now() + 25,
    fetchImpl: async (_url, options) => { signal = options.signal; return new Promise(resolve => { release = resolve; }); } });
  assert.deepEqual(await promise, { status: 'UNKNOWN' });
  assert.equal(signal.aborted, true);
  release(answer(body('open')));
  await delay(10);
  assert.deepEqual(await promise, { status: 'UNKNOWN' });
});

test('caller cancellation before entry makes no request and cancellation during a body never reports OPEN', async () => {
  const stopped = new AbortController(); stopped.abort(); let calls = 0;
  assert.deepEqual(await readEvolutionConnection({ ...INPUT, signal: stopped.signal,
    fetchImpl: async () => { calls++; return answer(body('open')); } }), { status: 'UNKNOWN' });
  assert.equal(calls, 0);
  const controller = new AbortController();
  const result = readEvolutionConnection({ ...INPUT, signal: controller.signal, deadline: Date.now() + 40,
    fetchImpl: async () => new Response(new ReadableStream({ start(stream) {
      stream.enqueue(new TextEncoder().encode('{"instance":'));
      setTimeout(() => controller.abort(), 5);
    } })) });
  assert.deepEqual(await result, { status: 'UNKNOWN' });
});

test('bounds declared/streamed body size and rejects invalid JSON/UTF-8', async () => {
  const responses = [new Response('private', { headers: { 'content-length': '17000' } }),
    new Response('x'.repeat(17000)), new Response('not-json'), new Response(new Uint8Array([0xff, 0xfe]))];
  for (const response of responses) {
    assert.deepEqual(await readEvolutionConnection({ ...INPUT, fetchImpl: async () => response }), { status: 'UNKNOWN' });
  }
});

test('rechecks the deadline after reading a response instead of trusting an old OPEN observation', async () => {
  let current = 1000;
  assert.deepEqual(await readEvolutionConnection({ ...INPUT, now: () => current, deadline: 1020,
    fetchImpl: async () => { current = 1020; return answer(body('open')); } }), { status: 'UNKNOWN' });
});
