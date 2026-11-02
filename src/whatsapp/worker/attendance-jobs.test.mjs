import assert from 'node:assert/strict';
import test from 'node:test';
import { createAttendanceTicker, safeAttendanceSummary } from './attendance-jobs.mjs';

const start = Date.parse('2026-10-31T22:30:51Z');
const cursor = i => 'jobcursor_' + String(i).padStart(32,'0');
const ok = (req, processed = 0, nextCursor = null) => ({ status: 200, body: {
  jobType: req.jobType, processed, nextCursor, serverTime: new Date(start).toISOString(),
} });
function ticker(respond) {
  const calls = []; let clock = start;
  const tick = createAttendanceTicker({ now: () => clock, api: { request: async (path, { body, deadline }) => {
    assert.equal(path, '/integrations/whatsapp/v1/jobs/tick');
    assert.ok(deadline > clock && deadline <= clock+9000); calls.push(body);
    return respond(body, calls.length);
  } } });
  return { calls, tick, advance(seconds) { clock += seconds*1000; } };
}

test('uses a closed server-minute input, distinct job types and at most 100 per page', async () => {
  const h = ticker(body => ok(body)); const result = await h.tick();
  assert.deepEqual(result, { status: 'IDLE', calendarProcessed: 0, reminderProcessed: 0 });
  assert.deepEqual(h.calls, ['MATERIALIZE_OCCURRENCES','DUE_REMINDERS'].map(jobType =>
    ({ jobType, windowKey: '2026-10-31T22:30:00Z', batchLimit: 100 })));
});
test('pages stay bound to the issuing minute, make room for reminders and disappear after draining', async () => {
  const h = ticker((body, i) => ok(body, i < 3 ? 100 : 1, i < 3 ? cursor(i) : null));
  assert.deepEqual(await h.tick(), { status: 'MORE', calendarProcessed: 200, reminderProcessed: 1 });
  h.advance(60);
  assert.deepEqual(await h.tick(), { status: 'PROCESSED', calendarProcessed: 1, reminderProcessed: 1 });
  assert.equal(h.calls[3].cursor, cursor(2)); assert.equal(h.calls[3].windowKey, h.calls[0].windowKey);
  assert.equal(h.calls[4].windowKey, '2026-10-31T22:31:00Z'); assert.ok(!Object.hasOwn(h.calls[4], 'cursor'));
});
test('expired and refused cursors are discarded but a transient rejection does not drop a continuation', async () => {
  for (const [advance, status, retained] of [[60, 409, false], [60, 503, true], [241, 503, false]]) {
    const h = ticker((body,i) => i <= 2 ? ok(body, 100, cursor(i)) : i === 4 ? { status } : ok(body));
    await h.tick(); h.advance(advance);
    assert.equal((await h.tick()).status, 'UNAVAILABLE'); await h.tick();
    assert.equal(h.calls[3].cursor, advance < 240 ? cursor(2) : undefined);
    assert.equal(h.calls[5].cursor, retained ? cursor(2) : undefined);
  }
});
test('a malformed page, repeated cursor, wrong job or thrown credential never surfaces in n8n', async () => {
  for (const mutate of [r => { r.body.secret = 'private'; }, r => { r.body.processed = 101; },
    r => { r.body.jobType = 'RETENTION'; }, r => { r.body.nextCursor = 'untrusted'; }, () => { throw Error('private'); }]) {
    const h = ticker((body,i) => { const r = ok(body); if (i === 1) mutate(r); return r; });
    assert.deepEqual(await h.tick(), { status: 'UNAVAILABLE', calendarProcessed: 0, reminderProcessed: 0 });
  }
  const h = ticker((body,i) => i <= 2 ? ok(body, 100, cursor(1)) : ok(body));
  assert.deepEqual(await h.tick(), { status: 'UNAVAILABLE', calendarProcessed: 100, reminderProcessed: 0 });
});
test('control rejects forged counters and extra output fields', () => {
  const closed = { status: 'UNAVAILABLE', calendarProcessed: 0, reminderProcessed: 0 };
  for (const value of [null, { ...closed, raw: 'private' }, { ...closed, status: 'PROCESSED' },
    { ...closed, reminderProcessed: 201 }, { ...closed, status: 'BUSY', calendarProcessed: 1 }])
    assert.deepEqual(safeAttendanceSummary(value), closed);
});
