import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { createServer } from 'node:http';
import test from 'node:test';
import { submitEvolutionText } from './evolution-text.mjs';

const NOW = Date.parse('2030-01-01T12:00:00Z');
const TOKEN = Buffer.alloc(32, 7).toString('base64url');
const API_KEY = 'synthetic-api-key-not-a-secret';
const MESSAGE_ID = 'SYNTHETIC_MESSAGE_0001';
const PRIVATE = 'SYNTHETIC_PRIVATE_VALUE_MUST_NOT_BE_LOGGED';

function reservation() {
  return {
    outboxId: '00000000-0000-0000-0000-000000000002',
    attemptToken: TOKEN,
    submitBefore: new Date(NOW + 5000).toISOString(),
    recipient: { addressType: 'JID', address: '000000000001@s.whatsapp.net' },
    content: { type: 'TEXT', text: 'Resposta sintética\nsegunda linha ✓', referenceTime: new Date(NOW - 1000).toISOString() },
    serverTime: new Date(NOW).toISOString(),
  };
}
function options(overrides = {}) {
  return {
    baseUrl: 'https://synthetic-provider.example.test',
    instance: 'synthetic_050-test', apiKey: API_KEY,
    reservation: reservation(), now: () => NOW,
    readConnection: async () => ({ status: 'OPEN' }),
    ...overrides,
  };
}
function accepted() {
  return new Response(JSON.stringify({ key: { id: MESSAGE_ID } }), { status: 201 });
}
function submitted() { return { attemptToken: TOKEN, outcome: 'SUBMITTED', externalMessageId: MESSAGE_ID }; }
function rejected() { return { attemptToken: TOKEN, outcome: 'FAILED', failureCode: 'REJECTED' }; }
function unknown(code = 'CHANNEL_UNAVAILABLE') { return { attemptToken: TOKEN, outcome: 'UNKNOWN', failureCode: code }; }
function notSubmitted() { return { attemptToken: TOKEN, outcome: 'NOT_SUBMITTED', failureCode: 'CHANNEL_UNAVAILABLE' }; }

async function provider(t, handler) {
  const server = createServer(handler);
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  t.after(async () => {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  });
  return `http://127.0.0.1:${server.address().port}`;
}

test('posts the exact authorized PN/text payload and apikey once to the local provider', async t => {
  const requests = [];
  const baseUrl = await provider(t, (request, response) => {
    let body = '';
    request.setEncoding('utf8');
    request.on('data', chunk => { body += chunk; });
    request.on('end', () => {
      requests.push({ method: request.method, path: request.url, headers: request.headers, body: JSON.parse(body) });
      response.writeHead(201, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ key: { id: MESSAGE_ID }, status: 'PENDING' }));
    });
  });
  assert.deepEqual(await submitEvolutionText(options({ baseUrl })), submitted());
  assert.equal(requests.length, 1);
  assert.equal(requests[0].method, 'POST');
  assert.equal(requests[0].path, '/message/sendText/synthetic_050-test');
  assert.equal(requests[0].headers.apikey, API_KEY);
  assert.equal(requests[0].headers['content-type'], 'application/json');
  assert.deepEqual(requests[0].body, { number: reservation().recipient.address, text: reservation().content.text, linkPreview: false });
});

test('preserves LID and leading zeros without inferring or selecting a PN', async () => {
  let calls = 0;
  const value = reservation();
  value.recipient = { addressType: 'LID', address: '00000000000000000000000000000001@lid' };
  const result = await submitEvolutionText(options({ reservation: value, fetchImpl: async (url, request) => {
    calls++;
    assert.equal(url, 'https://synthetic-provider.example.test/message/sendText/synthetic_050-test');
    assert.deepEqual(JSON.parse(request.body), { number: value.recipient.address, text: value.content.text, linkPreview: false });
    assert.equal(request.redirect, 'manual');
    return accepted();
  } }));
  assert.deepEqual(result, submitted());
  assert.equal(calls, 1);
});

test('accepts the 1 and 32 digit alias boundaries for both authorized suffixes', async () => {
  for (const [addressType, suffix] of [['JID', '@s.whatsapp.net'], ['LID', '@lid']]) {
    for (const number of ['0', '0'.repeat(32)]) {
      const value = reservation();
      value.recipient = { addressType, address: number + suffix };
      let calls = 0;
      assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: async (_, request) => {
        calls++;
        assert.equal(JSON.parse(request.body).number, number + suffix);
        return accepted();
      } })), submitted());
      assert.equal(calls, 1);
    }
  }
});

test('accepts only configured HTTPS or strict HTTP loopback origins', async () => {
  for (const baseUrl of ['https://synthetic-provider.example.test', 'https://synthetic-provider.example.test:9443/',
    'http://127.0.0.1:12345', 'http://localhost:12345/', 'http://[::1]:12345']) {
    assert.deepEqual(await submitEvolutionText(options({ baseUrl, fetchImpl: async () => accepted() })), submitted());
  }
  for (const baseUrl of ['http://synthetic-provider.example.test', 'http://192.0.2.1', 'http://127.1', 'http://2130706433',
    'http://0x7f000001', 'http://127.0.0.2', 'http://localhost.', 'https://user:synthetic-password@example.test', 'https://@example.test',
    'https://example.test/path', 'https://example.test/?', 'https://example.test/#', 'https://example.test?x=y',
    ' https://example.test', 'https://example.test ', 'file:///synthetic', 'http://127.0.0.1:0']) {
    let calls = 0;
    assert.deepEqual(await submitEvolutionText(options({ baseUrl, fetchImpl: async () => { calls++; return accepted(); } })), rejected());
    assert.equal(calls, 0);
  }
});

test('rejects invalid instance or header configuration before HTTP without exposing values', async () => {
  for (const patch of [{ instance: '../synthetic' }, { instance: '' }, { instance: 'synthetic/other' },
    { instance: 'synthetic?other' }, { apiKey: '' }, { apiKey: `${PRIVATE}\r\nheader:value` }, { fetchImpl: null }, { now: null }]) {
    let calls = 0;
    const input = options({ fetchImpl: async () => { calls++; return accepted(); }, ...patch });
    assert.deepEqual(await submitEvolutionText(input), rejected());
    assert.equal(calls, 0);
  }
});

test('rejects malformed, extra, or model-selected reservation fields before HTTP', async () => {
  const mutations = [
    value => { value.outboxId = PRIVATE; },
    value => { value.recipient = { addressType: 'JID', address: '00000@lid' }; },
    value => { value.recipient = { addressType: 'LID', address: '00000@s.whatsapp.net' }; },
    value => { value.recipient.address = '0'.repeat(33) + '@s.whatsapp.net'; },
    value => { value.recipient.address = '00000@g.us'; },
    value => { value.recipient.modelRecipient = PRIVATE; },
    value => { value.recipient = null; },
    value => { value.content.type = 'STATIC_PIN'; },
    value => { value.content.text = ''; },
    value => { value.content.text = 'a'.repeat(2001); },
    value => { value.content.text = { private: PRIVATE }; },
    value => { value.content.latitude = 0; },
    value => { value.content.referenceTime = '2030-02-30T00:00:00Z'; },
    value => { value.content.referenceTime = '2030-01-01T12:00:01Z'; },
    value => { value.submitBefore = value.serverTime; },
    value => { value.serverTime = '2030-01-01T12:00:00+00:00'; },
    value => { value.extra = PRIVATE; },
  ];
  let calls = 0;
  for (const mutate of mutations) {
    const value = reservation(); mutate(value);
    assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: async () => { calls++; return accepted(); } })), rejected());
  }
  assert.equal(calls, 0);
});

test('returns the fixed non-postable error when the attempt token cannot be validated', async () => {
  for (const invalid of [undefined, null, PRIVATE, 'A'.repeat(42), 'A'.repeat(42) + 'B']) {
    const value = reservation(); value.attemptToken = invalid;
    assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: () => assert.fail('HTTP forbidden') })),
      { outcome: 'FAILED', failureCode: 'REJECTED' });
  }
  assert.deepEqual(await submitEvolutionText(), { outcome: 'FAILED', failureCode: 'REJECTED' });
  assert.deepEqual(await submitEvolutionText(null), { outcome: 'FAILED', failureCode: 'REJECTED' });
  const malicious = new Proxy({}, { get() { throw new Error(PRIVATE); } });
  assert.deepEqual(await submitEvolutionText({ reservation: malicious }), { outcome: 'FAILED', failureCode: 'REJECTED' });
});

test('rejects an exact or passed submit deadline before calling HTTP', async () => {
  let calls = 0;
  for (const currentTime of [NOW + 5000, NOW + 6000, Number.NaN]) {
    assert.deepEqual(await submitEvolutionText(options({ now: () => currentTime, fetchImpl: async () => { calls++; return accepted(); } })), rejected());
  }
  assert.equal(calls, 0);
});

test('accepts Unicode text length and Java UTC nanosecond timestamp formatting', async () => {
  const value = reservation();
  value.content.text = '✓'.repeat(2000);
  value.content.referenceTime = '2030-01-01T11:59:59.123456789Z';
  value.serverTime = '2030-01-01T12:00:00.000000001Z';
  value.submitBefore = '2030-01-01T12:00:05.123456789Z';
  assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: async () => accepted() })), submitted());
});

test('maps definite HTTP rejections to FAILED without reading private response bodies', async () => {
  for (const status of [401, 403, 404]) {
    let calls = 0;
    let canceled = 0;
    const result = await submitEvolutionText(options({ fetchImpl: async () => {
      calls++;
      return { status, body: { cancel() { canceled++; } }, get headers() { throw new Error(PRIVATE); } };
    } }));
    assert.deepEqual(result, rejected());
    assert.equal(calls, 1);
    assert.equal(canceled, 1);
  }
});

test('keeps a post-send HTTP 400 UNKNOWN after the provider records its effect, without retry', async t => {
  let calls = 0;
  const effects = [];
  const baseUrl = await provider(t, (request, response) => {
    calls++;
    let body = '';
    request.setEncoding('utf8');
    request.on('data', chunk => { body += chunk; });
    request.on('end', () => {
      effects.push(JSON.parse(body));
      response.writeHead(400, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ message: PRIVATE }));
    });
  });
  const result = await submitEvolutionText(options({ baseUrl }));
  assert.deepEqual(result, unknown());
  assert.equal(calls, 1);
  assert.deepEqual(effects, [{ number: reservation().recipient.address, text: reservation().content.text, linkPreview: false }]);
  assert.equal(JSON.stringify(result).includes(PRIVATE), false);
});

test('does not follow redirects or retry the provider request', async t => {
  let calls = 0;
  const baseUrl = await provider(t, (_, response) => {
    calls++;
    response.writeHead(307, { location: '/private-redirect-destination' });
    response.end(PRIVATE);
  });
  assert.deepEqual(await submitEvolutionText(options({ baseUrl })), unknown());
  assert.equal(calls, 1);
});

test('maps other HTTP statuses conservatively to UNKNOWN and never retries', async () => {
  for (const status of [200, 202, 400, 409, 422, 429, 500, 503]) {
    let calls = 0;
    assert.deepEqual(await submitEvolutionText(options({ fetchImpl: async () => {
      calls++; return new Response(PRIVATE, { status });
    } })), unknown());
    assert.equal(calls, 1);
  }
});

test('times out while awaiting headers even if an injected fetch ignores AbortSignal', async () => {
  const value = reservation(); value.submitBefore = new Date(NOW + 20).toISOString();
  let calls = 0;
  let signal;
  const started = Date.now();
  assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: (_, request) => {
    calls++; signal = request.signal; return new Promise(() => {});
  } })), unknown('TIMEOUT'));
  assert.equal(calls, 1);
  assert.equal(signal.aborted, true);
  assert.ok(Date.now() - started < 1000);
});

test('includes streamed body time in the local HTTP timeout', async t => {
  let calls = 0;
  const baseUrl = await provider(t, (_, response) => {
    calls++;
    response.writeHead(201, { 'content-type': 'application/json' });
    response.flushHeaders();
    response.write('{"key":');
  });
  const value = reservation(); value.submitBefore = new Date(NOW + 40).toISOString();
  assert.deepEqual(await submitEvolutionText(options({ baseUrl, reservation: value })), unknown('TIMEOUT'));
  assert.equal(calls, 1);
});

test('cancels a late response from a fetch implementation that ignored the timeout', async () => {
  let resolveFetch;
  let canceled = 0;
  const value = reservation(); value.submitBefore = new Date(NOW + 10).toISOString();
  assert.deepEqual(await submitEvolutionText(options({ reservation: value, fetchImpl: () => new Promise(resolve => { resolveFetch = resolve; }) })), unknown('TIMEOUT'));
  resolveFetch({ status: 201, body: { cancel() { canceled++; }, getReader() { assert.fail('Late response must not be read'); } } });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(canceled, 1);
});

test('caps the full attempt timeout at ten seconds without a ten second test wait', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const value = reservation(); value.submitBefore = new Date(NOW + 30_000).toISOString();
  let finished = false;
  const result = submitEvolutionText(options({ reservation: value, fetchImpl: () => new Promise(() => {}) }));
  result.then(() => { finished = true; });
  await new Promise(resolve => setImmediate(resolve));
  t.mock.timers.tick(9999);
  await Promise.resolve();
  assert.equal(finished, false);
  t.mock.timers.tick(1);
  assert.deepEqual(await result, unknown('TIMEOUT'));
});

test('reads the real connection endpoint after authorization and performs exactly one POST only when OPEN', async t => {
  const requests = [];
  let open = false;
  const baseUrl = await provider(t, (request, response) => {
    requests.push([request.method, request.url]);
    request.resume();
    request.on('end', () => {
      assert.equal(request.headers.apikey, API_KEY);
      response.writeHead(request.method === 'GET' ? 200 : 201, { 'content-type': 'application/json' });
      response.end(JSON.stringify(request.method === 'GET'
        ? { instance: { instanceName: 'synthetic_050-test', state: open ? 'open' : 'close' } }
        : { key: { id: MESSAGE_ID } }));
    });
  });
  assert.deepEqual(await submitEvolutionText(options({ baseUrl, readConnection: undefined })), notSubmitted());
  assert.deepEqual(requests, [['GET', '/instance/connectionState/synthetic_050-test']]);
  open = true;
  assert.deepEqual(await submitEvolutionText(options({ baseUrl, readConnection: undefined })), submitted());
  assert.deepEqual(requests.slice(1), [['GET', '/instance/connectionState/synthetic_050-test'], ['POST', '/message/sendText/synthetic_050-test']]);
});

test('closed, unknown, malformed or failing probes report NOT_SUBMITTED with zero POST calls', async () => {
  for (const readConnection of [async () => ({ status: 'CLOSED' }), async () => ({ status: 'UNKNOWN' }),
    async () => ({ status: 'OPEN', private: PRIVATE }), async () => null, async () => { throw Error(PRIVATE); }]) {
    let posts = 0;
    assert.deepEqual(await submitEvolutionText(options({ readConnection, fetchImpl: async () => { posts++; return accepted(); } })), notSubmitted());
    assert.equal(posts, 0);
  }
});

test('an expired probe that ignores cancellation cannot send later, even when its clock is frozen', async () => {
  let release;
  let posts = 0;
  let signal;
  const value = reservation(); value.submitBefore = new Date(NOW + 20).toISOString();
  const result = await submitEvolutionText(options({ reservation: value,
    readConnection: input => { signal = input.signal; return new Promise(resolve => { release = resolve; }); },
    fetchImpl: async () => { posts++; return accepted(); } }));
  assert.deepEqual(result, notSubmitted());
  assert.equal(signal.aborted, true);
  release({ status: 'OPEN' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts, 0);
});

test('external cancellation before or during a probe is definitive and prevents late POST', async () => {
  for (const initiallyAborted of [true, false]) {
    const controller = new AbortController();
    let release;
    let posts = 0;
    if (initiallyAborted) controller.abort();
    const result = submitEvolutionText(options({ signal: controller.signal,
      readConnection: () => new Promise(resolve => { release = resolve; }),
      fetchImpl: async () => { posts++; return accepted(); } }));
    await new Promise(resolve => setImmediate(resolve));
    controller.abort();
    assert.deepEqual(await result, notSubmitted());
    release?.({ status: 'OPEN' });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(posts, 0);
  }
});

test('rechecks submitBefore after the probe and never submits at its exact boundary', async () => {
  let current = NOW;
  let posts = 0;
  assert.deepEqual(await submitEvolutionText(options({ now: () => current,
    readConnection: async () => { current += 5000; return { status: 'OPEN' }; },
    fetchImpl: async () => { posts++; return accepted(); } })), notSubmitted());
  assert.equal(posts, 0);
});

test('a synchronous fetch throw or cancellation after invocation is UNKNOWN, never NOT_SUBMITTED', async () => {
  let posts = 0;
  assert.deepEqual(await submitEvolutionText(options({ fetchImpl: () => { posts++; throw Error(PRIVATE); } })), unknown());
  const controller = new AbortController();
  let release;
  let entered;
  const started = new Promise(resolve => { entered = resolve; });
  const result = submitEvolutionText(options({ signal: controller.signal, fetchImpl: () => {
    posts++; entered(); return new Promise(resolve => { release = resolve; });
  } }));
  await started;
  controller.abort();
  assert.deepEqual(await result, unknown());
  release(accepted());
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts, 2);
});

test('treats malformed JSON, invalid UTF-8 and missing or invalid ids as UNKNOWN', async () => {
  for (const body of ['{', '{}', 'null', '[]', '{"key":{}}', '{"key":{"id":""}}',
    JSON.stringify({ key: { id: 'x'.repeat(161) } }), JSON.stringify({ key: { id: `${PRIVATE} private` } }),
    new Uint8Array([0xff, 0xfe])]) {
    assert.deepEqual(await submitEvolutionText(options({ fetchImpl: async () => new Response(body, { status: 201 }) })), unknown());
  }
});

test('accepts an exactly 32KiB JSON response read in chunks', async () => {
  const json = JSON.stringify({ key: { id: MESSAGE_ID } });
  const bytes = Buffer.from(json + ' '.repeat(32 * 1024 - Buffer.byteLength(json)));
  const stream = new ReadableStream({ start(controller) {
    controller.enqueue(bytes.subarray(0, 19));
    controller.enqueue(bytes.subarray(19));
    controller.close();
  } });
  assert.deepEqual(await submitEvolutionText(options({ fetchImpl: async () => new Response(stream, { status: 201 }) })), submitted());
});

test('cancels an oversized stream instead of reading it without bound', async () => {
  let canceled = 0;
  let pulls = 0;
  const stream = new ReadableStream({
    pull(controller) { pulls++; controller.enqueue(new Uint8Array(16 * 1024)); },
    cancel() { canceled++; },
  });
  assert.deepEqual(await submitEvolutionText(options({ fetchImpl: async () => new Response(stream, { status: 201 }) })), unknown());
  assert.equal(canceled, 1);
  assert.ok(pulls <= 4);
});

test('rejects a declared oversized body before obtaining a reader', async () => {
  let canceled = 0;
  const response = {
    status: 201,
    headers: new Headers({ 'content-length': String(32 * 1024 + 1) }),
    body: { getReader() { assert.fail('Oversized response must not be read'); }, cancel() { canceled++; } },
  };
  assert.deepEqual(await submitEvolutionText(options({ fetchImpl: async () => response })), unknown());
  assert.equal(canceled, 1);
});

test('treats a truncated HTTP response as UNKNOWN', async t => {
  let calls = 0;
  let receivedStatus;
  const baseUrl = await provider(t, (_, response) => {
    calls++;
    response.writeHead(201, { 'content-type': 'application/json', 'content-length': '200' });
    response.flushHeaders();
    response.write('{"key":');
    setTimeout(() => response.destroy(), 10);
  });
  assert.deepEqual(await submitEvolutionText(options({ baseUrl, fetchImpl: async (...args) => {
    const response = await fetch(...args); receivedStatus = response.status; return response;
  } })), unknown());
  assert.equal(receivedStatus, 201);
  assert.equal(calls, 1);
});

test('sanitizes network errors and emits no logs or raw private fields', async t => {
  const logs = [];
  for (const method of ['log', 'error', 'warn', 'info', 'debug']) t.mock.method(console, method, (...args) => { logs.push(args); });
  let calls = 0;
  const value = reservation(); value.content.text = PRIVATE;
  const result = await submitEvolutionText(options({ reservation: value, fetchImpl: async () => {
    calls++; throw new Error(`${PRIVATE} ${API_KEY} ${value.recipient.address}`);
  } }));
  assert.deepEqual(result, unknown());
  assert.equal(calls, 1);
  assert.equal(JSON.stringify(result).includes(PRIVATE), false);
  assert.equal(JSON.stringify(result).includes(API_KEY), false);
  assert.equal(JSON.stringify(result).includes(value.recipient.address), false);
  assert.deepEqual(logs, []);
});

test('sanitizes configuration exceptions after a validated token without sending HTTP', async () => {
  let calls = 0;
  const result = await submitEvolutionText(options({ now() { throw new Error(PRIVATE); },
    fetchImpl: async () => { calls++; return accepted(); } }));
  assert.deepEqual(result, rejected());
  assert.equal(calls, 0);
  assert.equal(JSON.stringify(result).includes(PRIVATE), false);
});
