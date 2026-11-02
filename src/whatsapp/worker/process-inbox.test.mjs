import test from 'node:test';
import assert from 'node:assert/strict';
import { createInboxProcessor } from './process-inbox.mjs';

const id = '00000000-0000-4000-8000-000000000001';
const now = Date.parse('2026-10-31T12:00:00Z');
function fixture(overrides = {}) {
  const calls = [];
  const item = { inboxEventId: id, inboxLease: 'A'.repeat(43), leaseExpiresAt: new Date(now + 60000).toISOString(),
    message: { type: 'TEXT', text: 'oi', sourceMessageId: `event_${id}` } };
  const context = { inboxEventId: id, entityVersion: 0, actorState: 'UNREGISTERED', step: 'ONBOARDING',
    registrationStep: 'WELCOME', eventFreshness: 'CURRENT', allowedCommands: ['START_ONBOARDING', 'RESPOND_TO_MESSAGE'],
    options: [], serverTime: new Date(now).toISOString() };
  const api = { async request(path, options) {
    calls.push({ path, options });
    if (overrides.request) return overrides.request(path, options);
    if (path.endsWith('/claim')) return overrides.claim ?? { status: 200, body: { items: [item] } };
    if (path.includes('/context?')) return overrides.context ?? { status: 200, body: context };
    if (path.endsWith('/commands')) return overrides.command ?? { status: 200, body: { commandId: id, outcome: 'APPLIED' } };
    return overrides.result ?? { status: 200, body: { inboxEventId: id, state: 'PROCESSED' } };
  } };
  return { calls, item, context, api, process: createInboxProcessor({ api, now: () => now, ...overrides.processor }) };
}
test('one leased message follows context-command-ack and returns counters only', async () => {
  const f = fixture(); assert.deepEqual(await f.process(), { status: 'PROCESSED', claimed: 1, processed: 1, retried: 0, paused: 0 });
  assert.equal(f.calls.length, 4);
  const command = f.calls[2].options;
  assert.deepEqual(command.body, { commandType: 'START_ONBOARDING', inboxEventId: id, expectedVersion: 0, payload: {} });
  assert.equal(command.headers['Idempotency-Key'], `wa070_${id}`);
  assert.ok(f.calls.every(c => c.options.deadline === now + 5000));
});
test('empty inbox does not call context or commands', async () => {
  const f = fixture({ claim: { status: 200, body: { items: [] } } });
  assert.equal((await f.process()).status, 'IDLE'); assert.equal(f.calls.length, 1);
});
test('malformed/expired claim cannot select endpoint or execute a command', async () => {
  for (const bad of [{}, { inboxEventId: '../../commands', inboxLease: 'A'.repeat(43) }]) {
    const f = fixture({ claim: { status: 200, body: { items: [bad] } } });
    assert.equal((await f.process()).status, 'UNAVAILABLE'); assert.equal(f.calls.length, 1);
  }
});
test('context failure retries inbox without business action', async () => {
  const f = fixture({ context: { status: 503, body: {} }, result: { status: 200, body: { inboxEventId: id, state: 'PENDING' } } });
  assert.equal((await f.process()).status, 'RETRY'); assert.equal(f.calls.some(c => c.path.endsWith('/commands')), false);
  assert.deepEqual(f.calls.at(-1).options.body, { outcome: 'RETRY', failureCode: 'DEPENDENCY_UNAVAILABLE', retryAfterSeconds: 5 });
});
test('lost command response never causes a second command in the same attempt', async () => {
  const f = fixture({ command: { status: 503, body: {} } });
  assert.equal((await f.process()).status, 'PROCESSED');
  assert.equal(f.calls.filter(c => c.path.endsWith('/commands')).length, 1);
  assert.equal(f.calls.at(-1).options.body.outcome, 'RETRY'); // API receipt dominates ACK outcome.
});
test('controlled rejection already committed becomes processed without a fallback command', async () => {
  const f = fixture({ command: { status: 403, body: { code: 'ACTION_NOT_ALLOWED' } } });
  assert.equal((await f.process()).status, 'PROCESSED'); assert.equal(f.calls.length, 4);
  assert.equal(f.calls.at(-1).options.body.outcome, 'PAUSED');
});
test('invalid proposal pauses with no authority leakage or command', async () => {
  for (const decide of [() => ({ kind: 'COMMAND', commandType: 'GRANT_ADMIN', payload: {} }),
    () => ({ kind: 'COMMAND', commandType: 'START_ONBOARDING', payload: { actorId: 'injected' } }),
    () => ({ kind: 'NO_ACTION' }), () => null, () => { throw new Error('PRIVATE_SENTINEL'); }]) {
    const f = fixture({ processor: { decide }, result: { status: 200, body: { inboxEventId: id, state: 'PAUSED' } } });
    assert.equal((await f.process()).status, 'PAUSED'); assert.equal(f.calls.some(c => c.path.endsWith('/commands')), false);
  }
});
test('exceptions never escape private details and never create an infinite retry loop', async () => {
  const f = fixture({ request: async () => { throw new Error('PRIVATE_SENTINEL'); } });
  assert.deepEqual(await f.process(), { status: 'UNAVAILABLE', claimed: 0, processed: 0, retried: 0, paused: 0 });
  assert.equal(f.calls.length, 1);
});
test('lost ACK is not reported as processed and does not repeat command', async () => {
  const f = fixture({ result: { status: 503, body: { private: 'PRIVATE_SENTINEL' } } });
  assert.equal((await f.process()).status, 'UNAVAILABLE'); assert.equal(f.calls.length, 4);
});

test('asynchronous decision receives a deadline reserving command and ACK time', async () => {
  let received;
  const f = fixture({ processor: { decide: async (_message, _context, options) => {
    received = options.deadline;
    return { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'HELP' } };
  } } });
  assert.equal((await f.process()).status, 'PROCESSED');
  assert.equal(received, now + 15000);
  assert.equal(f.calls.filter(x => x.path.endsWith('/commands')).length, 1);
});
test('late asynchronous proposal never reaches command API', async () => {
  let current = now;
  const f = fixture({ processor: { now: () => current, decide: async () => {
    current += 15001;
    return { kind: 'COMMAND', commandType: 'START_ONBOARDING', payload: {} };
  } }, result: { status: 200, body: { inboxEventId: id, state: 'PAUSED' } } });
  assert.equal((await f.process()).status, 'PAUSED');
  assert.equal(f.calls.some(x => x.path.endsWith('/commands')), false);
});
test('a hung decider is bounded independently of its own network implementation', async () => {
  let current = now;
  const f = fixture({ processor: { now: () => current, decide: async () => new Promise(() => {}) },
    result: { status: 200, body: { inboxEventId: id, state: 'PAUSED' } } });
  const original = f.api.request;
  f.api.request = async (...args) => {
    const response = await original(...args);
    if (args[0].includes('/context?')) current += 14980;
    return response;
  };
  assert.equal((await f.process()).status, 'PAUSED');
  assert.equal(f.calls.some(x => x.path.endsWith('/commands')), false);
});
