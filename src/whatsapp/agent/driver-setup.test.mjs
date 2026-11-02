import test from 'node:test';
import assert from 'node:assert/strict';
import { decideDeterministically, validateDecision, validateContext } from './deterministic.mjs';
import { createModelDecision, prepareModelInput } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000080';
const message = (text = 'CONFIRMAR EXEMPLOX') => ({ type: 'TEXT', text, sourceMessageId: `event_${id}` });
const option = kind => ({ kind, ref: `${kind.toLowerCase()}_synthetic080`, label: 'Opção autorizada' });
const context = (command, kinds = []) => ({ inboxEventId: id, entityVersion: 12, actorState: 'DRIVER',
  step: 'PAIRING', registrationStep: 'PAIRING', eventFreshness: 'CURRENT',
  allowedCommands: ['RESPOND_TO_MESSAGE', command], options: kinds.map(option), serverTime: '2026-10-31T12:00:00Z' });

// These are the consumer-facing protocol examples, not values extracted from
// DRIVER_PAYLOADS. Changing the implementation cannot change the expected shape.
const cases = [
  ['ADVANCE_DRIVER_SETUP', [], {}],
  ['CONFIGURE_ROUTINE', ['ROUTINE', 'CONFIRMATION'], { routineRef: 'routine_synthetic080', confirmationRef: 'confirmation_synthetic080' }],
  ['SET_STOP_ORDER', ['ROUTINE', 'CONFIRMATION'], { routineRef: 'routine_synthetic080', confirmationRef: 'confirmation_synthetic080' }],
  ['SET_ROUTINE_STATE', ['ROUTINE', 'CONFIRMATION'], { routineRef: 'routine_synthetic080', confirmationRef: 'confirmation_synthetic080' }],
  ['CREATE_INVITE', ['CONFIRMATION'], { confirmationRef: 'confirmation_synthetic080' }],
  ['REVOKE_INVITE', ['INVITE', 'CONFIRMATION'], { inviteRef: 'invite_synthetic080', confirmationRef: 'confirmation_synthetic080' }],
  ['CANCEL_OCCURRENCE', ['OCCURRENCE', 'CONFIRMATION'], { occurrenceRef: 'occurrence_synthetic080', confirmationRef: 'confirmation_synthetic080' }],
  ['GET_SUMMARY', ['SUMMARY'], { summaryRef: 'summary_synthetic080' }],
];

for (const [commandType, kinds, payload] of cases) test(`unpaired driver uses the API references for ${commandType}, with no model call`, async () => {
  const ctx = context(commandType, kinds);
  const decide = createModelDecision({ classify: () => assert.fail('driver setup must not call the model') });
  const actual = await decide(message(), ctx, { deadline: Date.now() + 1000 });
  assert.deepEqual(actual, { kind: 'COMMAND', commandType, payload });
  validateDecision(actual, ctx);
  assert.equal(prepareModelInput(message(), ctx), null);
});

test('pin and typed draft values are handled by the API, never copied into command payloads', async () => {
  const ctx = context('ADVANCE_DRIVER_SETUP');
  const decide = createModelDecision({ classify: () => assert.fail('draft fields must stay private') });
  for (const input of [message('Ida exemplo'), message('1,2,3,4,5'), message('06:00'), message('MANTER'),
    { type: 'STATIC_PIN_REFERENCE', sourceMessageId: `event_${id}` }]) {
    assert.deepEqual(await decide(input, ctx, { deadline: Date.now() + 1000 }),
      { kind: 'COMMAND', commandType: 'ADVANCE_DRIVER_SETUP', payload: {} });
  }
});

test('routine data, identity and arbitrary references cannot be smuggled into a confirmation', () => {
  const ctx = context('CONFIGURE_ROUTINE', ['ROUTINE', 'CONFIRMATION']);
  const valid = decideDeterministically(message(), ctx);
  for (const [field, value] of [['actorId', id], ['name', 'Outra rotina'], ['direction', 'RETURN'],
    ['latitude', -1], ['routineRef', 'routine_other_actor'], ['confirmationRef', 'confirmation_other_revision']]) {
    assert.throws(() => validateDecision({ ...valid, payload: { ...valid.payload, [field]: value } }, ctx), /INVALID_DECISION/);
  }
  assert.throws(() => validateDecision({ ...valid, commandType: 'SET_ROUTINE_STATE' }, ctx), /INVALID_DECISION/);
});

test('two offered driver commands or two matching targets do not get silently selected', async () => {
  const contexts = [context('CONFIGURE_ROUTINE', ['ROUTINE', 'CONFIRMATION']), context('ADVANCE_DRIVER_SETUP')];
  contexts[0].options.push({ ...option('ROUTINE'), ref: 'routine_second080' });
  contexts[1].allowedCommands.push('CREATE_INVITE');
  const decide = createModelDecision({ classify: () => assert.fail('ambiguous API context must not reach the model') });
  for (const ctx of contexts) {
    assert.deepEqual(await decide(message(), ctx, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
    assert.throws(() => validateDecision({ kind: 'COMMAND', commandType: ctx.allowedCommands[1], payload: {} }, ctx), /INVALID_DECISION/);
  }
});

test('missing confirmation, wrong role, incomplete registration and stale messages never authorize setup', () => {
  const missing = context('CONFIGURE_ROUTINE', ['ROUTINE']);
  assert.deepEqual(decideDeterministically(message(), missing), { kind: 'NO_ACTION' });
  const valid = { kind: 'COMMAND', commandType: 'ADVANCE_DRIVER_SETUP', payload: {} };
  for (const change of [{ actorState: 'PASSENGER' }, { actorState: 'UNREGISTERED' },
    { registrationStep: 'ASK_NOTICE' }, { step: 'ONBOARDING' }, { eventFreshness: 'STALE' }, { step: 'PAUSED' }]) {
    const ctx = { ...context('ADVANCE_DRIVER_SETUP'), ...change };
    assert.throws(() => validateDecision(valid, ctx), /INVALID_DECISION/);
    assert.notEqual(decideDeterministically(message(), ctx).commandType, 'ADVANCE_DRIVER_SETUP');
  }
});

test('context options are closed, bounded and cannot contain raw fields or duplicate references', () => {
  for (const options of [[{ ...option('ROUTINE'), actorId: id }], [{ ...option('ROUTINE'), kind: 'ADMIN' }],
    [{ ...option('ROUTINE'), ref: 'https://example.invalid' }], [{ ...option('ROUTINE'), label: '' }],
    [option('ROUTINE'), option('ROUTINE')], [null]]) {
    assert.throws(() => validateContext({ ...context('ADVANCE_DRIVER_SETUP'), options }, id), /INVALID_CONTEXT/);
  }
});

test('help and explicit support retain their existing priority during driver setup', () => {
  const ctx = context('ADVANCE_DRIVER_SETUP'); ctx.allowedCommands.push('REQUEST_SUPPORT');
  assert.deepEqual(decideDeterministically(message('AJUDA'), ctx),
    { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'HELP' } });
  assert.equal(decideDeterministically(message('FALAR COM SUPORTE'), ctx).commandType, 'REQUEST_SUPPORT');
  assert.equal(decideDeterministically(message('CONTINUAR'), ctx).commandType, 'ADVANCE_DRIVER_SETUP');
});

test('setup references without an offered command cannot fall through to the model', async () => {
  const ctx = context('ADVANCE_DRIVER_SETUP', ['ROUTINE', 'CONFIRMATION']);
  ctx.allowedCommands = ['RESPOND_TO_MESSAGE', 'REQUEST_SUPPORT'];
  const decide = createModelDecision({ classify: () => assert.fail('partial setup context must not invoke the model') });
  for (const text of ['CONFIRMAR EXEMPLOX', 'Ida Sintética', 'não é isso'])
    assert.deepEqual(await decide(message(text), ctx, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
  assert.equal(decideDeterministically(message('AJUDA'), ctx).payload.responseCode, 'HELP');
  assert.equal(decideDeterministically(message('FALAR COM SUPORTE'), ctx).commandType, 'REQUEST_SUPPORT');
});

test('a confirmation marker without a setup offer receives fixed guidance, never model interpretation', async () => {
  const ctx = context('ADVANCE_DRIVER_SETUP'); ctx.allowedCommands = ['RESPOND_TO_MESSAGE'];
  const decide = createModelDecision({ classify: () => assert.fail('confirmation marker must not invoke the model') });
  assert.deepEqual(await decide(message(), ctx, { deadline: Date.now() + 1000 }),
    { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'CONTINUE' } });
});
