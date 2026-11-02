import assert from 'node:assert/strict';
import test from 'node:test';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision, prepareModelInput } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000100';
const question = { ref: 'question_' + id, kind: 'QUESTION', label: 'Presença' };
const message = { type: 'REPLY', sourceMessageId: 'event_' + id, text: 'resposta privada' };
const base = { inboxEventId: id, entityVersion: 4, actorState: 'PASSENGER', step: 'READY', registrationStep: 'READY',
  eventFreshness: 'CURRENT', serverTime: '2026-10-31T12:00:00Z', allowedCommands: ['SET_ATTENDANCE','RESPOND_TO_MESSAGE'],
  options: [question], attendanceChoice: 'GOING' };
const deterministic = createModelDecision({ classify() { assert.fail('attendance must remain private'); } });
for (const choice of ['GOING','NOT_GOING']) test('copies only the authenticated question and choice ' + choice, async () => {
  const context = { ...base, attendanceChoice: choice };
  const result = await deterministic(message, context, { deadline: Date.now()+1000 });
  assert.deepEqual(result, { kind: 'COMMAND', commandType: 'SET_ATTENDANCE', payload: { questionRef: question.ref, choice } });
  validateDecision(result, context);
  assert.equal(prepareModelInput(message, context), null);
  for (const change of [{ choice: choice === 'GOING' ? 'NOT_GOING' : 'GOING' }, { questionRef: 'question_someone_else' },
    { actorId: id }, { localDate: '2026-10-31' }])
    assert.throws(() => validateDecision({ ...result, payload: { ...result.payload, ...change } }, context));
});
test('incomplete or conflicting presence offers never delegate a decision to the model', async () => {
  for (const context of [{ ...base, actorState: 'DRIVER' }, { ...base, attendanceChoice: 'PENDING' },
    { ...base, options: [] }, { ...base, options: [{ ...question, ref: 'question_without_uuid' }] },
    { ...base, options: [question, { ...question, ref: question.ref.replace('100','101') }] },
    { ...base, allowedCommands: ['SET_ATTENDANCE','ADVANCE_PASSENGER_SETUP'] }, { ...base, registrationStep: 'PAIRING' }]) {
    assert.deepEqual(await deterministic(message, context, { deadline: Date.now()+1000 }), { kind: 'NO_ACTION' });
    assert.equal(prepareModelInput(message, context), null);
  }
});
test('stale inputs do not set attendance and both roles can request an explicit selection', () => {
  const stale = decideDeterministically(message, { ...base, eventFreshness: 'STALE' });
  assert.deepEqual(stale.payload, { responseCode: 'STALE_MESSAGE' });
  for (const [role,stage] of [['PASSENGER','READY'],['DRIVER','PAIRING'],['DRIVER','READY']]) {
    const context = { ...base, actorState: role, step: stage, registrationStep: stage, allowedCommands: ['ADVANCE_ATTENDANCE'], options: [] };
    delete context.attendanceChoice;
    const result = decideDeterministically(message, context);
    assert.deepEqual(result, { kind: 'COMMAND', commandType: 'ADVANCE_ATTENDANCE', payload: {} });
    validateDecision(result, context);
  }
});
