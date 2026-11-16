import assert from 'node:assert/strict';
import test from 'node:test';
import { createPrivacyTicker, PRIVACY_FAMILIES, withPrivacyMaintenance } from './privacy-jobs.mjs';

const start = Date.parse('2026-11-16T10:00:00Z');
const cursor = n => 'privacycursor_' + String(n).padStart(60, '0');
const page = (family, patch = {}) => ({ status: 200, body: { status: 'APPLIED', family,
  examined: 0, erased: 0, deleted: 0, deferred: 0, wouldChange: 0,
  complete: true, nextCursor: null, serverTime: new Date(start).toISOString(), reasonCodes: [], ...patch } });
function harness(respond, options = {}) {
  const calls = [], reports = [];
  let clock = start;
  const tick = createPrivacyTicker({ enabled: true, now: () => clock,
    report: value => reports.push(value), api: { request: (path, request) => {
      assert.equal(path, '/integrations/whatsapp/v1/privacy/tick');
      assert.ok(request.deadline > clock && request.deadline <= clock + 2500);
      calls.push(structuredClone(request));
      return respond(request.body, calls.length);
    } }, ...options });
  return { tick, calls, reports, advance: ms => { clock += ms; } };
}

async function visit(h, family) {
  const before = h.calls.filter(call => call.body.family === family).length;
  for (let i = 0; i < 3; i++) {
    h.advance(60_000);
    const result = await h.tick();
    if (h.calls.filter(call => call.body.family === family).length > before) return result;
  }
  assert.fail('the round-robin did not revisit the family within three W08 calls');
}

test('disabled maintenance performs no requests and a deferred family cannot suppress the other four', async () => {
  assert.equal((await createPrivacyTicker({ enabled: false })()).status, 'DISABLED');
  const h = harness(body => page(body.family, body.family === 'TERMINAL_7D'
    ? { status: 'DEFERRED', examined: 1, deferred: 1, complete: false, reasonCodes: ['ROW_BUSY'] }
    : { examined: 1, deleted: 1 }));
  const result = await h.tick();
  assert.equal(result.status, 'DEFERRED');
  assert.equal(result.deleted, 1);
  assert.equal(h.calls.length, 2);
  await h.tick();
  assert.equal(h.calls.length, 2);
  for (let i = 0; i < 2; i++) { h.advance(60_000); await h.tick(); }
  assert.deepEqual(h.calls.slice(0, 5).map(call => call.body), PRIVACY_FAMILIES.map(family => ({ family, batchLimit: 20 })));
});

test('continuations are private, independent, drained and expire before the five-minute server TTL', async () => {
  let mode = 0;
  const h = harness(body => page(body.family, body.family === 'TECHNICAL_7D' && mode !== 1
    ? { examined: 20, complete: false, nextCursor: cursor(mode + 1) } : {}));
  assert.equal((await h.tick()).status, 'MORE');
  mode = 1;
  await visit(h, 'TECHNICAL_7D');
  let technical = h.calls.filter(call => call.body.family === 'TECHNICAL_7D');
  assert.equal(technical[1].body.cursor, cursor(1));
  mode = 2;
  await visit(h, 'TECHNICAL_7D');
  technical = h.calls.filter(call => call.body.family === 'TECHNICAL_7D');
  assert.ok(!Object.hasOwn(technical[2].body, 'cursor'));
  h.advance(240_000);
  await visit(h, 'TECHNICAL_7D');
  technical = h.calls.filter(call => call.body.family === 'TECHNICAL_7D');
  assert.ok(!Object.hasOwn(technical[3].body, 'cursor'));
  assert.ok(h.calls.filter(call => call.body.family !== 'TECHNICAL_7D').every(call => !Object.hasOwn(call.body, 'cursor')));
  for (const value of [await h.tick(), ...h.reports]) assert.ok(!JSON.stringify(value).includes('privacycursor_'));
});

test('refused and expired continuations cannot prevent other families from progressing', async () => {
  for (const status of [400, 403, 410, 503]) {
    let round = 0;
    const h = harness(body => body.family !== 'TECHNICAL_7D' ? page(body.family, { examined: 1, deleted: 1 })
      : round === 0 ? page(body.family, { examined: 1, complete: false, nextCursor: cursor(1) })
      : round === 1 ? { status, body: { private: 'SYNTHETIC_PRIVATE' } } : page(body.family));
    await h.tick(); round = 1;
    assert.equal((await visit(h, 'TECHNICAL_7D')).status, 'UNAVAILABLE');
    round = 2; await visit(h, 'TECHNICAL_7D');
    const technical = h.calls.filter(call => call.body.family === 'TECHNICAL_7D');
    assert.equal(technical[1].body.cursor, cursor(1));
    assert.ok(!Object.hasOwn(technical[2].body, 'cursor')); // expiry applies even after a transient failure
    for (const family of PRIVACY_FAMILIES) assert.ok(h.calls.some(call => call.body.family === family));
  }
});

test('invalid output never enters logs or a cursor and dry-run cannot report real deletion', async () => {
  const privateValue = 'SYNTHETIC_PRIVATE_MUST_NOT_ESCAPE';
  for (const patch of [{ private: privateValue }, { family: 'FOREIGN' }, { status: 'PURGED' },
    { erased: -1 }, { examined: 21 }, { deleted: 0.5 }, { deleted: Number.MAX_SAFE_INTEGER + 1 },
    { nextCursor: privateValue }, { complete: true, nextCursor: cursor(1) }, { reasonCodes: [privateValue + '123'] },
    { serverTime: 'invalid' }, { status: 'DRY_RUN', erased: 1 }]) {
    const h = harness(body => page(body.family, patch));
    assert.equal((await h.tick()).status, 'UNAVAILABLE');
    assert.equal(h.calls.length, 2);
    assert.ok(!JSON.stringify(h.reports).includes(privateValue));
    h.advance(60_000); await h.tick();
    assert.equal(h.reports.length, 1); // unchanged failure is not logged every minute
    assert.ok(h.calls.every(call => !Object.hasOwn(call.body, 'cursor')));
  }
});

test('timeout releases the bounded call, does not accept a late cursor and suppresses simultaneous work', async () => {
  let release, entered;
  const ready = new Promise(resolve => { entered = resolve; });
  const h = harness((body, number) => number === 1 ? (entered(), new Promise(resolve => { release = resolve; })) : page(body.family),
    { operationTimeoutMs: 15 });
  const running = h.tick(); await ready;
  assert.equal((await h.tick()).status, 'BUSY');
  assert.equal((await running).status, 'UNAVAILABLE');
  release(page('TERMINAL_7D', { complete: false, nextCursor: cursor(1) }));
  await new Promise(resolve => setImmediate(resolve));
  h.advance(60_000); await h.tick();
  assert.ok(h.calls.every(call => !Object.hasOwn(call.body, 'cursor')));
});

test('a late response cannot advance its family even when the overall five-second window remains open', async () => {
  const h = harness((body, number) => {
    if (number === 1) {
      h.advance(2501);
      return page(body.family, { examined: 1, erased: 1, complete: false, nextCursor: cursor(1) });
    }
    return page(body.family);
  });
  const result = await h.tick();
  assert.equal(result.status, 'UNAVAILABLE');
  assert.equal(result.erased, 0);
  assert.equal(h.calls.length, 2);
  h.advance(60_000); await h.tick();
  assert.ok(h.calls.every(call => !Object.hasOwn(call.body, 'cursor')));
});

test('logs only changed aggregate counts, including dry-run previews, and ignores a failed logger', async () => {
  const h = harness(body => page(body.family, { status: 'DRY_RUN', examined: 1, wouldChange: 1 }));
  const result = await h.tick();
  assert.equal(result.wouldChange, 2);
  h.advance(60_000); await h.tick();
  assert.equal(h.reports.length, 1);
  assert.deepEqual(Object.keys(h.reports[0]), ['status', 'examined', 'erased', 'deleted', 'deferred', 'wouldChange']);
  const badLogger = harness(body => page(body.family, { examined: 1 }), { report: () => { throw Error('PRIVATE'); } });
  assert.equal((await badLogger.tick()).status, 'PROCESSED');
});

test('W08 retains its exact two-field summary and executes privacy even when delivery is unavailable', async () => {
  let called = 0;
  const privacy = async () => { called++; return { status: 'DEFERRED', examined: 20, erased: 0, deleted: 0, deferred: 1, wouldChange: 0 }; };
  assert.deepEqual(await withPrivacyMaintenance(async () => ({ status: 'PROCESSED', processed: 3 }), privacy)(),
    { status: 'PROCESSED', processed: 3 });
  assert.deepEqual(await withPrivacyMaintenance(async () => { throw Error('PRIVATE'); }, privacy)(),
    { status: 'UNAVAILABLE', processed: 0 });
  assert.equal(called, 2);
  for (const output of [undefined, {}, { status: 'UNAVAILABLE' }, { status: 'PROCESSED', nextCursor: cursor(1) }])
    assert.deepEqual(await withPrivacyMaintenance(async () => ({ status: 'MORE', processed: 200 }),
      async () => output)(), { status: 'UNAVAILABLE', processed: 200 });
});

test('rejects enabling without a job client or expanding the existing control deadline', () => {
  for (const options of [{ enabled: true }, { enabled: 'true' }, { operationTimeoutMs: 5001 }, { report: null }])
    assert.throws(() => createPrivacyTicker(options), error => error.code === 'CONFIG_INVALID');
});
