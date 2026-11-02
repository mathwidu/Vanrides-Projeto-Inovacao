import assert from 'node:assert/strict';
import test from 'node:test';
import { createDeliveryTicker, safeDeliverySummary } from './delivery-jobs.mjs';

const start = Date.parse('2026-11-01T03:30:51Z');
const cursor = i => 'jobcursor_' + String(i).padStart(32, '0');
const ok = (req, processed = 0, nextCursor = null) => ({ status: 200, body: {
  jobType: req.jobType, processed, nextCursor, serverTime: new Date(start).toISOString(),
} });
function ticker(respond) {
  const calls = []; let clock = start;
  const tick = createDeliveryTicker({ now: () => clock, api: { request: (path, { body, deadline }) => {
    assert.equal(path, '/integrations/whatsapp/v1/jobs/tick');
    calls.push({ body: structuredClone(body), deadline });
    return respond(body, calls.length, milliseconds => { clock += milliseconds; });
  } } });
  return { calls, tick, advance(seconds) { clock += seconds * 1000; } };
}

test('uses only HEALTH_RECONCILE, UTC minute and a bounded SYSTEM_JOB page', async () => {
  const h = ticker(body => ok(body));
  assert.deepEqual(await h.tick(), { status: 'IDLE', processed: 0 });
  assert.deepEqual(h.calls, [{ body: { jobType: 'HEALTH_RECONCILE', windowKey: '2026-11-01T03:30:00Z', batchLimit: 100 },
    deadline: start + 18_000 }]);
});

test('processes at most two pages, retains its private cursor minute and discards a drained continuation', async () => {
  const h = ticker((body, i) => ok(body, i <= 2 ? 100 : i === 3 ? 1 : 0, i <= 2 ? cursor(i) : null));
  assert.deepEqual(await h.tick(), { status: 'MORE', processed: 200 });
  assert.equal(h.calls.length, 2);
  assert.equal(h.calls[1].body.cursor, cursor(1));
  h.advance(60);
  assert.deepEqual(await h.tick(), { status: 'PROCESSED', processed: 1 });
  assert.equal(h.calls[2].body.cursor, cursor(2));
  assert.equal(h.calls[2].body.windowKey, h.calls[0].body.windowKey);
  h.advance(60);
  assert.deepEqual(await h.tick(), { status: 'IDLE', processed: 0 });
  assert.equal(h.calls[3].body.windowKey, '2026-11-01T03:32:00Z');
  assert.equal(Object.hasOwn(h.calls[3].body, 'cursor'), false);
});

test('drops expired or refused cursors and preserves a transient continuation', async () => {
  for (const [advance, status, retained] of [[60, 400, false], [60, 409, false], [60, 410, false], [60, 503, true], [241, 503, false]]) {
    const h = ticker((body, i) => i <= 2 ? ok(body, 100, cursor(i)) : i === 3 ? { status } : ok(body));
    assert.equal((await h.tick()).status, 'MORE');
    h.advance(advance);
    assert.deepEqual(await h.tick(), { status: 'UNAVAILABLE', processed: 0 });
    await h.tick();
    assert.equal(h.calls[2].body.cursor, advance < 240 ? cursor(2) : undefined);
    assert.equal(h.calls[3].body.cursor, retained ? cursor(2) : undefined);
  }
});

test('fails closed on malformed, unexpected or oversized API pages without exposing the body', async () => {
  const privateValue = 'SYNTHETIC_PRIVATE_MUST_NOT_ESCAPE';
  for (const mutate of [r => { r.status = 503; }, r => { r.body.private = privateValue; },
    r => { r.body.processed = 101; }, r => { r.body.processed = -1; }, r => { r.body.processed = 0.5; },
    r => { r.body.jobType = 'RETENTION'; }, r => { r.body.nextCursor = privateValue; },
    r => { r.body.nextCursor = 1; }, r => { r.body.nextCursor = cursor(1).toUpperCase(); },
    r => { r.body.serverTime = 'invalid'; }, r => { delete r.body.nextCursor; },
    r => { r.body = []; }, r => { r.body = null; }, () => { throw Error(privateValue); }]) {
    const h = ticker(body => { const response = ok(body); mutate(response); return response; });
    const result = await h.tick();
    assert.deepEqual(result, { status: 'UNAVAILABLE', processed: 0 });
    assert.equal(h.calls.length, 1);
    assert.equal(JSON.stringify(result).includes(privateValue), false);
  }
});

test('a repeated cursor stops before counting its page and retries only the accepted continuation', async () => {
  const h = ticker((body, i) => ok(body, i < 3 ? 100 : 1, i < 3 ? cursor(1) : null));
  assert.deepEqual(await h.tick(), { status: 'UNAVAILABLE', processed: 100 });
  assert.deepEqual(await h.tick(), { status: 'PROCESSED', processed: 1 });
  assert.equal(h.calls[2].body.cursor, cursor(1));
});

test('both pages share one deadline and a result at that deadline cannot start more work', async () => {
  const h = ticker((body, i, advance) => { advance(9000); return ok(body, 100, cursor(i)); });
  assert.deepEqual(await h.tick(), { status: 'UNAVAILABLE', processed: 100 });
  assert.equal(h.calls.length, 2);
  assert.deepEqual(h.calls.map(call => call.deadline), [start + 18_000, start + 18_000]);
  const expired = ticker((body, i, advance) => { advance(18_000); return ok(body, 100, cursor(i)); });
  assert.deepEqual(await expired.tick(), { status: 'UNAVAILABLE', processed: 0 });
  assert.equal(expired.calls.length, 1);
});

test('a stalled API is bounded and its late result cannot advance the private cursor', async () => {
  const calls = []; let finish;
  const tick = createDeliveryTicker({ operationTimeoutMs: 15, api: { request: (_path, { body }) => {
    calls.push(structuredClone(body));
    return calls.length === 1 ? new Promise(resolve => { finish = resolve; }) : ok(body);
  } } });
  const started = Date.now();
  assert.deepEqual(await tick(), { status: 'UNAVAILABLE', processed: 0 });
  assert.ok(Date.now() - started < 1000);
  finish(ok(calls[0], 100, cursor(1)));
  await Promise.resolve();
  assert.deepEqual(await tick(), { status: 'IDLE', processed: 0 });
  assert.equal(Object.hasOwn(calls[1], 'cursor'), false);
});

test('the control summary contains only its closed status and 0..200 known processed count', () => {
  const unavailable = { status: 'UNAVAILABLE', processed: 0 };
  for (const value of [null, [], { ...unavailable, private: 'hidden' }, { status: 'FAILED', processed: 0 },
    { status: 'PROCESSED', processed: 0 }, { status: 'IDLE', processed: 1 }, { status: 'BUSY', processed: 1 },
    { status: 'MORE', processed: 201 }, { status: 'MORE', processed: -1 }, { status: 'MORE', processed: 0.5 }])
    assert.deepEqual(safeDeliverySummary(value), unavailable);
  for (const value of [{ status: 'IDLE', processed: 0 }, { status: 'BUSY', processed: 0 },
    { status: 'MORE', processed: 200 }, { status: 'PROCESSED', processed: 1 }, { status: 'UNAVAILABLE', processed: 100 }])
    assert.deepEqual(safeDeliverySummary(value), value);
});

test('rejects an invalid API or a deadline beyond the global limit before starting work', () => {
  for (const options of [{ api: {} }, { api: { request() {} }, operationTimeoutMs: 18_001 },
    { api: { request() {} }, operationTimeoutMs: 0 }])
    assert.throws(() => createDeliveryTicker(options), { code: 'CONFIG_INVALID' });
});
