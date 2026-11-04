import assert from 'node:assert/strict';
import test from 'node:test';
import { createPacedDispatch } from './academic-dispatch-pacing.mjs';
import { createDispatcher } from '../worker/dispatch.mjs';
import { summary } from './070-worker-common.mjs';
const token = Buffer.alloc(32, 1).toString('base64url');
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');
function fixture({ recipients = 1, inspectOverride, responseOverride, maxWaitMs } = {}) {
  let clock = 100000, claims = 0, sends = 0, requests = 0;
  const rows = [], history = [], sleeps = [], observedWaits = [];
  const recipientOf = row => id(1000 + row.index % recipients);
  const recent = actor => history.filter(entry => entry.at > clock - 60000 && (!actor || entry.actor === actor));
  const slot = (entries, limit) => entries.length < limit ? 0 : entries.at(-limit).at + 60000 - clock;
  const api = { async request(route) {
    if (route.endsWith('/claim')) {
      claims++;
      const row = rows.find(item => item.state === 'PENDING');
      if (!row || recent().length >= 30 || recent(recipientOf(row)).length >= 12)
        return { status: 200, body: { items: [], serverTime: new Date(clock).toISOString() } };
      return { status: 200, body: { items: [{ outboxId: row.outboxId, purpose: 'COMMAND_RESPONSE',
        leaseToken: token, leaseExpiresAt: new Date(clock + 60000).toISOString(),
        expiresAt: new Date(clock + 300000).toISOString() }], serverTime: new Date(clock).toISOString() } };
    }
    const row = rows.find(item => route.includes(item.outboxId));
    assert.ok(row);
    if (route.endsWith('/authorize-submit')) {
      history.push({ at: clock, actor: recipientOf(row) });
      return { status: 200, body: { outboxId: row.outboxId, attemptToken: token,
        submitBefore: new Date(clock + 10000).toISOString() } };
    }
    assert.ok(route.endsWith('/result'));
    row.state = 'SUBMITTED';
    return { status: 200, body: { outboxId: row.outboxId, state: row.state, serverTime: new Date(clock).toISOString() } };
  } };
  const dispatch = createDispatcher({ api, now: () => clock,
    config: { evolutionBaseUrl: 'http://127.0.0.1:1', instance: 'synthetic', evolutionApiKey: 'synthetic' },
    readConnection: async () => ({ status: 'OPEN' }),
    submit: async ({ reservation }) => { sends++; return { attemptToken: reservation.attemptToken,
      outcome: 'SUBMITTED', externalMessageId: 'synthetic_' + sends }; },
  });
  const controls = createPacedDispatch({
    worker: { async request() { requests++; return responseOverride ?? { status: 200, data: await dispatch() }; } },
    provider: { enqueue(item) { rows.push({ ...item, state: 'PENDING', index: rows.length }); } },
    scopeFor: item => ({ outboxId: item.outboxId, recipient: id(1000 + rows.length % recipients) }),
    inspect: target => {
      if (typeof inspectOverride === 'function') return inspectOverride(ms => { clock += ms; });
      if (inspectOverride) return inspectOverride;
      const row = rows.find(item => item.outboxId === target.outboxId);
      return { scopeValid: !!row && recipientOf(row) === target.recipient, state: row?.state ?? 'MISSING',
        expiresInMs: 300000, delayMs: row ? Math.max(0, slot(recent(), 30), slot(recent(recipientOf(row)), 12)) : 0 };
    },
    wait: async ms => { sleeps.push(ms); clock += ms; }, now: () => clock,
    assertActive() {}, onWait: detail => observedWaits.push(detail), maxWaitMs,
  });
  return { controls, rows, sleeps, observedWaits, counts: () => ({ claims, sends, requests }),
    async send(n) {
      controls.provider.enqueue({ outboxId: id(n) });
      const response = await controls.worker.request('/v1/dispatch-one');
      summary(response.data, 'dispatch', 'SUBMITTED', response.status);
    } };
}
test('the thirteenth rapid response waits for the actual recipient window and is submitted once', async () => {
  const f = fixture();
  for (let n = 1; n <= 13; n++) await f.send(n);
  assert.equal(f.counts().sends, 13);
  assert.equal(f.counts().claims, 13);
  assert.equal(f.sleeps.length, 1);
  assert.ok(f.sleeps[0] >= 60000 && f.sleeps[0] <= 60001);
  assert.deepEqual(Object.keys(f.observedWaits[0]), ['waitMs']);
  assert.equal(f.controls.stats().windowWaits, 1);
});
test('the thirty-first cross-recipient response respects the instance window', async () => {
  const f = fixture({ recipients: 3 });
  for (let n = 1; n <= 31; n++) await f.send(n);
  assert.equal(f.counts().sends, 31);
  assert.equal(f.sleeps.length, 1);
});
test('synthetic acquisition preparation runs after quota admission and before the single dispatch', async () => {
  const f = fixture();
  for (let n = 1; n <= 12; n++) await f.send(n);
  let preparations = 0;
  f.controls.provider.enqueue({ outboxId: id(13), beforeDispatch: async () => {
    preparations++;
    assert.equal(f.sleeps.length, 1);
    assert.equal(f.counts().claims, 12);
  } });
  await f.controls.worker.request('/v1/dispatch-one');
  assert.equal(preparations, 1);
  assert.equal(f.counts().claims, 13);
  assert.equal(f.counts().sends, 13);
});
test('a truly missing or foreign response fails before dispatch and is never interpreted as quota', async () => {
  for (const [state, scopeValid] of [['MISSING', false], ['SUBMITTED', true], ['PENDING', false]]) {
    const f = fixture({ inspectOverride: { scopeValid, state, expiresInMs: 300000, delayMs: 0 } });
    await assert.rejects(() => f.send(1));
    assert.equal(f.counts().claims, 0);
    assert.equal(f.sleeps.length, 0);
  }
});
test('a quota wait beyond its bound fails without sending or arbitrary sleeping', async () => {
  const f = fixture({ maxWaitMs: 20,
    inspectOverride: { scopeValid: true, state: 'PENDING', expiresInMs: 300000, delayMs: 60000 } });
  await assert.rejects(() => f.send(1));
  assert.equal(f.counts().claims, 0);
  assert.equal(f.sleeps.length, 0);
});
for (const status of ['IDLE', 'UNKNOWN', 'FAILED']) {
  test('unexpected ' + status + ' remains a failure and is never retried as a submission', async () => {
    const data = { status, claimed: status === 'IDLE' ? 0 : 1, submitted: 0,
      unknown: status === 'UNKNOWN' ? 1 : 0, failed: status === 'FAILED' ? 1 : 0 };
    const f = fixture({ responseOverride: { status: 200, data } });
    await assert.rejects(() => f.send(1), /CONTROL_SUMMARY_INVALID/);
    assert.equal(f.counts().requests, 1);
    assert.equal(f.sleeps.length, 0);
  });
}

test('a window that never opens hits the total wait bound before dispatch', async () => {
  const f = fixture({ inspectOverride: { scopeValid: true, state: 'PENDING', expiresInMs: 300000, delayMs: 60000 } });
  await assert.rejects(() => f.send(1), /QUOTA_WAIT_TIMEOUT/);
  assert.equal(f.sleeps.length, 1);
  assert.equal(f.counts().requests, 0);
});
test('a response expiring before its slot is a failure, not a delayed success', async () => {
  const f = fixture({ inspectOverride: { scopeValid: true, state: 'PENDING', expiresInMs: 5000, delayMs: 10000 } });
  await assert.rejects(() => f.send(1), /EXPECTED_RESPONSE_EXPIRES_BEFORE_QUOTA/);
  assert.equal(f.sleeps.length, 0);
  assert.equal(f.counts().requests, 0);
});
test('an idle request without any expected response stays idle without a quota wait', async () => {
  const f = fixture();
  const result = await f.controls.worker.request('/v1/dispatch-one');
  assert.equal(result.data.status, 'IDLE');
  assert.equal(f.sleeps.length, 0);
});

test('inspection time is included in the total wait bound even if a slot then opens', async () => {
  const f = fixture({ maxWaitMs: 20, inspectOverride: advance => {
    advance(21);
    return { scopeValid: true, state: 'PENDING', expiresInMs: 300000, delayMs: 0 };
  } });
  await assert.rejects(() => f.send(1), /QUOTA_WAIT_TIMEOUT/);
  assert.equal(f.counts().requests, 0);
});
