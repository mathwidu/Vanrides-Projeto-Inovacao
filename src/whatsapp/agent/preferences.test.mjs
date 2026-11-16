import assert from 'node:assert/strict';
import test from 'node:test';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision, prepareModelInput } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000140';
const message = text => ({ type: 'TEXT', sourceMessageId: 'event_' + id, text });
const context = overrides => ({ inboxEventId: id, entityVersion: 4, actorState: 'PASSENGER', step: 'READY',
  registrationStep: 'READY', eventFreshness: 'CURRENT', allowedCommands: ['RESPOND_TO_MESSAGE', 'SET_PREFERENCE'],
  options: [], serverTime: '2030-01-01T12:00:00Z', ...overrides });

test('an active passenger can explicitly enable arrival notices using the offered preference command', () => {
  const expected = { kind: 'COMMAND', commandType: 'SET_PREFERENCE',
    payload: { preference: 'APPROACH_MESSAGES', enabled: true } };
  assert.deepEqual(decideDeterministically(message('LIGAR AVISOS CHEGADA'), context()), expected);
  assert.doesNotThrow(() => validateDecision(expected, context()));
});

test('the other three explicit choices preserve preference identity and the requested boolean', () => {
  for (const [text, preference, enabled] of [
    ['DESLIGAR AVISOS CHEGADA', 'APPROACH_MESSAGES', false],
    ['LIGAR AVISOS PRESENÇA', 'ATTENDANCE_REMINDERS', true],
    ['\uFEFFDESLIGAR\u00A0AVISOS PRESENÇA!', 'ATTENDANCE_REMINDERS', false],
  ]) {
    const result = decideDeterministically(message(text), context());
    assert.deepEqual(result, { kind: 'COMMAND', commandType: 'SET_PREFERENCE', payload: { preference, enabled } });
    assert.doesNotThrow(() => validateDecision(result, context()));
  }
});

test('negated, compound and signup-only phrases never change post-signup preferences', () => {
  for (const text of ['não ligar avisos chegada', 'quero desligar avisos chegada?', 'ligar avisos chegada e presença',
    'ligar avisos chegada para outra pessoa', 'AVISOS CHEGADA', 'AVISOS NENHUM'])
    assert.notEqual(decideDeterministically(message(text), context()).commandType, 'SET_PREFERENCE');
});

test('missing, conflicting, stale and non-passenger offers cannot authorize the preference payload', () => {
  const decision = { kind: 'COMMAND', commandType: 'SET_PREFERENCE', payload: { preference: 'APPROACH_MESSAGES', enabled: false } };
  for (const overrides of [{ actorState: 'DRIVER' }, { actorState: 'UNREGISTERED' }, { registrationStep: 'ASK_NOTICE' },
    { step: 'PAUSED' }, { eventFreshness: 'STALE' }, { eventFreshness: 'UNKNOWN' },
    { allowedCommands: ['RESPOND_TO_MESSAGE'] }, { allowedCommands: ['RESPOND_TO_MESSAGE', 'SET_PREFERENCE', 'SET_PROFILE_NAME'] }]) {
    const ctx = context(overrides);
    assert.notEqual(decideDeterministically(message('DESLIGAR AVISOS CHEGADA'), ctx).commandType, 'SET_PREFERENCE');
    assert.throws(() => validateDecision(decision, ctx), /INVALID_DECISION/);
  }
  for (const payload of [{ preference: 'LOCATION', enabled: true }, { preference: 'APPROACH_MESSAGES', enabled: 'false' },
    { preference: 'APPROACH_MESSAGES', enabled: false, identityId: id }])
    assert.throws(() => validateDecision({ ...decision, payload }, context()), /INVALID_DECISION/);
});

test('explicit preference choices do not require or invoke model classification', async () => {
  let calls = 0;
  const decide = createModelDecision({ classify: async () => { calls++; throw Error('MODEL_MUST_NOT_RUN'); }, now: () => 1 });
  assert.equal(prepareModelInput(message('LIGAR AVISOS CHEGADA'), context()), null);
  assert.equal((await decide(message('LIGAR AVISOS CHEGADA'), context(), { deadline: 1000 })).commandType, 'SET_PREFERENCE');
  assert.equal(calls, 0);
});
