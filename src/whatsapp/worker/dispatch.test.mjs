import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { createServer } from 'node:http';
import test from 'node:test';
import { createDispatcher } from './dispatch.mjs';

const NOW = Date.parse('2030-01-01T12:00:00Z');
const ID = '00000000-0000-0000-0000-000000000001';
const LEASE = Buffer.alloc(32, 4).toString('base64url');
const ATTEMPT = Buffer.alloc(32, 5).toString('base64url');
const PRIVATE = 'SYNTHETIC_PRIVATE_MUST_NOT_ESCAPE';
const config = { evolutionBaseUrl: 'https://synthetic.example.test', instance: 'synthetic_070', evolutionApiKey: 'synthetic-key' };
const counts = (status, claimed = 0) => ({ status, claimed, submitted: status === 'SUBMITTED' ? 1 : 0,
  unknown: status === 'UNKNOWN' ? 1 : 0, failed: status === 'FAILED' ? 1 : 0 });
const reservation = () => ({ outboxId: ID, attemptToken: ATTEMPT, submitBefore: new Date(NOW + 10_000).toISOString(),
  recipient: { addressType: 'JID', address: '000000000001@s.whatsapp.net' },
  content: { type: 'TEXT', text: PRIVATE, referenceTime: new Date(NOW).toISOString() }, serverTime: new Date(NOW).toISOString() });
const accepted = () => ({ attemptToken: ATTEMPT, outcome: 'SUBMITTED', externalMessageId: 'SYNTHETIC_0001' });

function harness(overrides = {}) {
  const calls = [];
  const api = { request: async (path, options) => {
    calls.push({ path, options });
    if (overrides.request) return overrides.request(path, options, calls.length);
    if (path.endsWith('/claim')) return { status: 200, body: { items: [{ outboxId: ID, purpose: overrides.purpose ?? 'COMMAND_RESPONSE',
      leaseToken: LEASE, leaseExpiresAt: new Date(NOW + 60_000).toISOString(), expiresAt: new Date(NOW + 300_000).toISOString() }], serverTime: new Date(NOW).toISOString() } };
    if (path.endsWith('/authorize-submit')) return { status: 200, body: overrides.reservation ?? reservation() };
    return { status: 200, body: { outboxId: ID, state: options.body.outcome, serverTime: new Date(NOW).toISOString() } };
  } };
  return { api, calls, dispatch: createDispatcher({ api, config: overrides.config ?? config,
    readConnection: overrides.readConnection ?? (async () => ({ status: 'OPEN' })),
    submit: overrides.submit ?? (async () => accepted()), now: overrides.now ?? (() => NOW) }) };
}

test('does not claim, consume a retry or submit when the current channel observation is not OPEN', async () => {
  for (const value of [{ status: 'CLOSED' }, { status: 'UNKNOWN' }, { status: 'OPEN', extra: PRIVATE }, null]) {
    const h = harness({ readConnection: async () => value, submit: () => assert.fail('No submission allowed') });
    assert.deepEqual(await h.dispatch(), counts('UNAVAILABLE'));
    assert.equal(h.calls.length, 0);
  }
  const h = harness({ readConnection: async () => { throw Error(PRIVATE); } });
  assert.deepEqual(await h.dispatch(), counts('UNAVAILABLE'));
  assert.equal(h.calls.length, 0);
});

test('a stale OPEN or late completion of a stalled read never continues into claim or send', async () => {
  let current = NOW;
  const stale = harness({ now: () => current, readConnection: async () => { current += 2000; return { status: 'OPEN' }; } });
  assert.deepEqual(await stale.dispatch(), counts('UNAVAILABLE'));
  assert.equal(stale.calls.length, 0);
  let release;
  let signal;
  const late = harness({ readConnection: async input => {
    signal = input.signal; return new Promise(resolve => { release = resolve; });
  }, submit: () => assert.fail('Late read must not send') });
  const first = late.dispatch();
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(await late.dispatch(), counts('BUSY'));
  assert.deepEqual(await first, counts('UNAVAILABLE'));
  assert.equal(signal.aborted, true);
  release({ status: 'OPEN' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(late.calls.length, 0);
});

test('resumes after a new OPEN observation while preserving the total 25-second dispatch budget', async () => {
  let open = false;
  let current = NOW;
  const h = harness({ now: () => current, readConnection: async input => {
    assert.equal(input.deadline, current + 2000);
    current += 1000;
    return { status: open ? 'OPEN' : 'CLOSED' };
  } });
  assert.deepEqual(await h.dispatch(), counts('UNAVAILABLE'));
  assert.equal(h.calls.length, 0);
  open = true;
  const started = current;
  assert.deepEqual(await h.dispatch(), counts('SUBMITTED', 1));
  assert.ok(h.calls.every(call => call.options.deadline <= started + 25_000));
});

test('authorizes a scheduled reminder before a single submission and rejects future purposes before authorization', async () => {
  for (const purpose of ['ATTENDANCE_REMINDER','APPROACH','SUPPORT','PRIVATE']) {
    const h = harness({ purpose });
    const supported = purpose === 'ATTENDANCE_REMINDER';
    assert.deepEqual(await h.dispatch(), counts(supported ? 'SUBMITTED' : 'UNAVAILABLE', 1));
    assert.equal(h.calls.length, supported ? 3 : 1);
  }
});

test('claims one, authorizes, sends once and posts the exact adapter result body', async () => {
  const result = accepted();
  let submissions = 0;
  const h = harness({ submit: async input => {
    submissions++;
    assert.equal(input.apiKey, config.evolutionApiKey);
    assert.deepEqual(input.reservation, reservation());
    return result;
  } });
  const response = await h.dispatch();
  assert.deepEqual(response, counts('SUBMITTED', 1));
  assert.deepEqual(h.calls[0].options.body, { batchLimit: 1, leaseSeconds: 60 });
  assert.deepEqual(h.calls[1].options.body, { leaseToken: LEASE });
  assert.equal(h.calls[2].options.body, result);
  assert.equal(h.calls.length, 3);
  assert.equal(submissions, 1);
  assert.equal(JSON.stringify(response).includes(PRIVATE), false);
  assert.equal(JSON.stringify(response).includes(ID), false);
  assert.ok(h.calls.every(call => call.options.deadline <= NOW + 25_000));
});

test('returns IDLE without authorizing or sending when there is no claim', async () => {
  const h = harness({ request: async () => ({ status: 200, body: { items: [], serverTime: new Date(NOW).toISOString() } }),
    submit: () => assert.fail('No delivery allowed') });
  assert.deepEqual(await h.dispatch(), counts('IDLE'));
  assert.equal(h.calls.length, 1);
});

test('keeps one local dispatch active and returns BUSY to concurrent calls', async () => {
  let release;
  let started;
  const entered = new Promise(resolve => { started = resolve; });
  const h = harness({ submit: () => { started(); return new Promise(resolve => { release = resolve; }); } });
  const first = h.dispatch();
  await entered;
  assert.deepEqual(await h.dispatch(), counts('BUSY'));
  assert.equal(h.calls.length, 2);
  release(accepted());
  assert.deepEqual(await first, counts('SUBMITTED', 1));
});

test('does not send after authorize failure or an invalid attempt token', async () => {
  const h = harness({ reservation: { ...reservation(), attemptToken: PRIVATE }, submit: () => assert.fail('No delivery allowed') });
  assert.deepEqual(await h.dispatch(), counts('UNAVAILABLE', 1));
  assert.equal(h.calls.length, 2);
  const failed = harness({ submit: () => assert.fail('No delivery allowed') });
  const request = failed.api.request;
  let authorizations = 0;
  failed.api.request = async (path, options) => {
    if (path.endsWith('/authorize-submit')) { authorizations++; return { status: 503, body: { errorCode: 'DEPENDENCY_UNAVAILABLE' } }; }
    return request(path, options);
  };
  assert.deepEqual(await failed.dispatch(), counts('UNAVAILABLE', 1));
  assert.equal(authorizations, 1);
});

test('records an expired reservation as rejected without calling the provider', async () => {
  const h = harness({ reservation: { ...reservation(), submitBefore: new Date(NOW).toISOString() }, submit: () => assert.fail('Expired delivery forbidden') });
  assert.deepEqual(await h.dispatch(), counts('FAILED', 1));
  assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'FAILED', failureCode: 'REJECTED' });
});

test('posts the exact safe result once when connection is lost between authorization and submission', async () => {
  const { submitEvolutionText } = await import('../transport/evolution-text.mjs');
  let probes = 0;
  let posts = 0;
  const h = harness({ readConnection: async () => ({ status: ++probes === 1 ? 'OPEN' : 'CLOSED' }),
    submit: input => submitEvolutionText({ ...input, fetchImpl: async () => { posts++; throw Error(PRIVATE); } }) });
  const request = h.api.request;
  h.api.request = async (path, input) => {
    const result = await request(path, input);
    if (path.endsWith('/result')) result.body.state = 'PENDING';
    return result;
  };
  assert.deepEqual(await h.dispatch(), counts('NOT_SUBMITTED', 1));
  assert.equal(probes, 2); assert.equal(posts, 0);
  assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'NOT_SUBMITTED', failureCode: 'CHANNEL_UNAVAILABLE' });
  assert.equal(h.calls.length, 3);
});

test('accepts safe result terminal receipts without claiming a retry and rejects invented safe failure codes', async () => {
  for (const state of ['PENDING', 'EXPIRED', 'CANCELED', 'FAILED']) {
    const h = harness({ submit: async () => ({ attemptToken: ATTEMPT, outcome: 'NOT_SUBMITTED', failureCode: 'CHANNEL_UNAVAILABLE' }) });
    const request = h.api.request;
    h.api.request = async (path, input) => {
      const result = await request(path, input);
      if (path.endsWith('/result')) result.body.state = state;
      return result;
    };
    assert.deepEqual(await h.dispatch(), counts('NOT_SUBMITTED', 1));
    assert.equal(h.calls.length, 3);
  }
  for (const failureCode of ['TIMEOUT', 'REJECTED']) {
    const h = harness({ submit: async () => ({ attemptToken: ATTEMPT, outcome: 'NOT_SUBMITTED', failureCode }) });
    assert.deepEqual(await h.dispatch(), counts('UNKNOWN', 1));
    assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'UNKNOWN', failureCode: 'CHANNEL_UNAVAILABLE' });
  }
});

test('dispatcher timeout aborts the real adapter and a late probe cannot POST after the receipt', async () => {
  const { submitEvolutionText } = await import('../transport/evolution-text.mjs');
  let release;
  let probes = 0;
  let posts = 0;
  const h = harness({ reservation: { ...reservation(), submitBefore: new Date(NOW + 20).toISOString() },
    readConnection: () => ++probes === 1 ? Promise.resolve({ status: 'OPEN' }) : new Promise(resolve => { release = resolve; }),
    submit: input => submitEvolutionText({ ...input, fetchImpl: async () => { posts++; return new Response('{}', { status: 201 }); } }) });
  assert.deepEqual(await h.dispatch(), counts('UNKNOWN', 1));
  assert.equal(h.calls.length, 3);
  release({ status: 'OPEN' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts, 0);
  assert.equal(h.calls.length, 3);
});

test('keeps a post-effect provider 400 UNKNOWN with one send and one receipt attempt', async t => {
  let effects = 0;
  const provider = createServer((request, response) => {
    request.resume();
    request.on('end', () => { effects++; response.writeHead(400); response.end(PRIVATE); });
  });
  await new Promise(resolve => provider.listen(0, '127.0.0.1', resolve));
  t.after(async () => { provider.closeAllConnections(); await new Promise(resolve => provider.close(resolve)); });
  const { submitEvolutionText } = await import('../transport/evolution-text.mjs');
  const h = harness({ config: { ...config, evolutionBaseUrl: `http://127.0.0.1:${provider.address().port}` }, submit: submitEvolutionText });
  assert.deepEqual(await h.dispatch(), counts('UNKNOWN', 1));
  assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'UNKNOWN', failureCode: 'CHANNEL_UNAVAILABLE' });
  assert.equal(h.calls.length, 3);
  assert.equal(effects, 1);
});

test('does not resubmit when a committed result loses its HTTP ACK', async () => {
  let sends = 0;
  let receipts = 0;
  const h = harness({ submit: async () => { sends++; return accepted(); } });
  const request = h.api.request;
  h.api.request = async (path, input) => {
    if (path.endsWith('/result')) { receipts++; await request(path, input); throw new Error(PRIVATE); }
    if (sends > 0 && path.endsWith('/claim')) return { status: 200, body: { items: [], serverTime: new Date(NOW).toISOString() } };
    return request(path, input);
  };
  assert.deepEqual(await h.dispatch(), counts('UNKNOWN', 1));
  assert.deepEqual(await h.dispatch(), counts('IDLE'));
  assert.equal(sends, 1);
  assert.equal(receipts, 1);
});

test('sanitizes a throwing or malformed adapter into a single UNKNOWN receipt', async () => {
  for (const submit of [async () => { throw new Error(PRIVATE); }, async () => ({ ...accepted(), private: PRIVATE })]) {
    const h = harness({ submit });
    const result = await h.dispatch();
    assert.deepEqual(result, counts('UNKNOWN', 1));
    assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'UNKNOWN', failureCode: 'CHANNEL_UNAVAILABLE' });
    assert.equal(JSON.stringify(result).includes(PRIVATE), false);
  }
});

test('bounds an injected stalled adapter and records TIMEOUT without another send', async () => {
  let sends = 0;
  const h = harness({ reservation: { ...reservation(), submitBefore: new Date(NOW + 15).toISOString() },
    submit: () => { sends++; return new Promise(() => {}); } });
  assert.deepEqual(await h.dispatch(), counts('UNKNOWN', 1));
  assert.deepEqual(h.calls[2].options.body, { attemptToken: ATTEMPT, outcome: 'UNKNOWN', failureCode: 'TIMEOUT' });
  assert.equal(sends, 1);
});

test('uses a maximum 23s budget for claim, authorize, send and receipt', async () => {
  let current = NOW;
  const h = harness({ now: () => current, submit: async () => { current += 10_000; return accepted(); } });
  const request = h.api.request;
  h.api.request = async (path, options) => {
    const result = await request(path, options);
    current += path.endsWith('/result') ? 5000 : 4000;
    if (path.endsWith('/authorize-submit')) result.body.submitBefore = new Date(current + 10_000).toISOString();
    return result;
  };
  assert.deepEqual(await h.dispatch(), counts('SUBMITTED', 1));
  assert.equal(current - NOW, 23_000);
  assert.ok(h.calls.every(call => call.options.deadline <= NOW + 25_000));
});
