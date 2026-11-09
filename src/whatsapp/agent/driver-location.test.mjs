import assert from 'node:assert/strict';
import test from 'node:test';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision, prepareModelInput } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000130';
const message = text => ({ type: 'TEXT', sourceMessageId: 'event_' + id, text });
const context = overrides => ({ inboxEventId: id, entityVersion: 8, actorState: 'PASSENGER', step: 'READY',
  registrationStep: 'READY', eventFreshness: 'CURRENT', allowedCommands: ['GET_DRIVER_LOCATION', 'RESPOND_TO_MESSAGE'],
  options: [], serverTime: '2030-01-01T12:00:00Z', ...overrides });
const expected = { kind: 'COMMAND', commandType: 'GET_DRIVER_LOCATION', payload: {} };

test('copies only the API location offer for questions and explicit server-side menu choices', () => {
  for (const actorState of ['PASSENGER', 'DRIVER']) for (const text of ['onde está?', '1', '2', 'onde esta a van']) {
    const ctx = context({ actorState });
    assert.deepEqual(decideDeterministically(message(text), ctx), expected);
    assert.doesNotThrow(() => validateDecision(expected, ctx));
  }
});

test('location offers never invoke the model or copy selected ids, menu labels or coordinates', async () => {
  let calls = 0;
  const decide = createModelDecision({ classify: async () => { calls++; throw Error('MODEL_MUST_NOT_RUN'); }, now: () => 1 });
  const ctx = context({ options: [{ kind: 'RUN', ref: 'run_synthetic130', label: 'Escolha uma saída' }] });
  assert.equal(prepareModelInput(message('2'), ctx), null);
  assert.deepEqual(await decide(message('2'), ctx, { deadline: 1000 }), expected);
  assert.equal(calls, 0);
  for (const payload of [{ runRef: 'run_synthetic130' }, { occurrenceRef: 'foreign_ref' }, { latitude: 0 }, { choice: 1 }])
    assert.throws(() => validateDecision({ ...expected, payload }, ctx), /INVALID_DECISION/);
});

test('missing, conflicting, paused or stale offers never query a vehicle', () => {
  for (const actorState of ['PASSENGER', 'DRIVER']) for (const overrides of [{ allowedCommands: ['RESPOND_TO_MESSAGE'] }, { actorState: 'UNREGISTERED' },
    { allowedCommands: ['GET_DRIVER_LOCATION', 'GET_SUMMARY', 'RESPOND_TO_MESSAGE'] },
    { step: 'PAUSED' }, { eventFreshness: 'STALE' }, { registrationStep: 'ASK_NAME' }]) {
    const ctx = context({ actorState, ...overrides }), result = decideDeterministically(message('onde está?'), ctx);
    assert.notEqual(result.commandType, 'GET_DRIVER_LOCATION');
    assert.throws(() => validateDecision(expected, ctx), /INVALID_DECISION/);
  }
});
