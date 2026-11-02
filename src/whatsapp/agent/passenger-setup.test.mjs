import test from 'node:test';
import assert from 'node:assert/strict';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision, prepareModelInput } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000090';
const message = (text = 'CONFIRMAR EXEMPLOX') => ({ type: 'TEXT', text, sourceMessageId: `event_${id}` });
const option = kind => ({ kind, ref: `${kind.toLowerCase()}_synthetic090`, label: 'Opção autorizada' });
const context = (command, kinds = [], actorState = 'PASSENGER') => ({ inboxEventId: id,
  entityVersion: 12, actorState, step: 'READY', registrationStep: 'READY', eventFreshness: 'CURRENT',
  allowedCommands: ['RESPOND_TO_MESSAGE', 'REQUEST_SUPPORT', command], options: kinds.map(option),
  serverTime: '2026-10-31T12:00:00Z' });
const noModel = () => createModelDecision({ classify: () => assert.fail('passenger setup must not call the model') });
const cases = [
  ['ADVANCE_PASSENGER_SETUP', [], {}, 'PASSENGER'],
  ['REQUEST_LINK', ['CONFIRMATION'], { confirmationRef: 'confirmation_synthetic090' }, 'PASSENGER'],
  ['APPROVE_LINK', ['LINK', 'PICKUP', 'CONFIRMATION'], { linkRef: 'link_synthetic090', pickupRef: 'pickup_synthetic090', confirmationRef: 'confirmation_synthetic090' }, 'DRIVER'],
  ['CLOSE_LINK', ['LINK', 'CONFIRMATION'], { linkRef: 'link_synthetic090', confirmationRef: 'confirmation_synthetic090' }, 'DRIVER'],
  ['PROPOSE_PICKUP', ['QUESTION', 'CONFIRMATION'], { sourceMessageId: 'question_synthetic090', confirmationRef: 'confirmation_synthetic090' }, 'PASSENGER'],
  ['APPROVE_PICKUP', ['PICKUP', 'CONFIRMATION'], { pickupRef: 'pickup_synthetic090', confirmationRef: 'confirmation_synthetic090' }, 'DRIVER'],
];

for (const [commandType, kinds, payload, actor] of cases) test(`copies the complete authorized offer for ${commandType} without a model`, async () => {
  const ctx = context(commandType, kinds, actor);
  for (const input of [message(), { type: 'UNSUPPORTED', sourceMessageId: `event_${id}` },
    { type: 'STATIC_PIN_REFERENCE', sourceMessageId: `event_${id}` }]) {
    const decision = await noModel()(input, ctx, { deadline: Date.now() + 1000 });
    assert.deepEqual(decision, { kind: 'COMMAND', commandType, payload });
    validateDecision(decision, ctx);
    assert.equal(prepareModelInput(input, ctx), null);
  }
});

test('an absent guest can enter the private wizard before unsupported guidance or model fallback', async () => {
  for (const actorState of ['UNREGISTERED', 'PASSENGER']) {
    for (const registrationStep of ['WELCOME', 'ASK_NAME', 'ASK_NOTICE']) {
      const ctx = { ...context('ADVANCE_PASSENGER_SETUP', [], actorState), step: 'ONBOARDING', registrationStep };
      for (const input of [message('Nome de teste'), message('ACEITO'), message('AVISOS SIM'),
        message('referência privada de teste'), { type: 'UNSUPPORTED', sourceMessageId: `event_${id}` }]) {
        const actual = await noModel()(input, ctx, { deadline: Date.now() + 1000 });
        assert.deepEqual(actual, { kind: 'COMMAND', commandType: 'ADVANCE_PASSENGER_SETUP', payload: {} });
        validateDecision(actual, ctx);
      }
    }
  }
});

test('driver review and passenger actions remain available in pairing or ready as authorized', () => {
  for (const stage of ['PAIRING', 'READY']) {
    for (const [commandType, kinds, payload, actor] of cases) {
      const ctx = { ...context(commandType, kinds, actor), step: stage, registrationStep: stage };
      validateDecision({ kind: 'COMMAND', commandType, payload }, ctx);
    }
    for (const actor of ['DRIVER', 'PASSENGER']) {
      const ctx = { ...context('ADVANCE_PASSENGER_SETUP', [], actor), step: stage, registrationStep: stage };
      assert.equal(decideDeterministically(message(), ctx).commandType, 'ADVANCE_PASSENGER_SETUP');
      const close = { ...context('CLOSE_LINK', ['LINK', 'CONFIRMATION'], actor), step: stage, registrationStep: stage };
      assert.equal(decideDeterministically(message(), close).commandType, 'CLOSE_LINK');
    }
  }
});

test('wrong actors and incomplete stages cannot authorize passenger mutations', () => {
  for (const [commandType, kinds, payload, actor] of cases.slice(1)) {
    const wrongActors = commandType === 'CLOSE_LINK' ? ['UNREGISTERED']
      : actor === 'DRIVER' ? ['PASSENGER', 'UNREGISTERED'] : ['DRIVER', 'UNREGISTERED'];
    for (const change of [...wrongActors.map(actorState => ({ actorState })),
      { step: 'ONBOARDING' }, { registrationStep: 'ASK_NOTICE' }]) {
      const ctx = { ...context(commandType, kinds, actor), ...change };
      assert.deepEqual(decideDeterministically(message(), ctx), { kind: 'NO_ACTION' });
      assert.throws(() => validateDecision({ kind: 'COMMAND', commandType, payload }, ctx), /INVALID_DECISION/);
    }
  }
  for (const actorState of ['UNREGISTERED', 'PASSENGER', 'DRIVER']) {
    const ctx = { ...context('ADVANCE_PASSENGER_SETUP', [], actorState), step: 'READY', registrationStep: 'ASK_NAME' };
    assert.deepEqual(decideDeterministically(message(), ctx), { kind: 'NO_ACTION' });
  }
});

test('missing, ambiguous or substituted references never become a command or a model input', async () => {
  for (const [commandType, kinds, payload, actor] of cases.slice(1)) {
    for (const missing of kinds) {
      const ctx = context(commandType, kinds.filter(kind => kind !== missing), actor);
      assert.deepEqual(await noModel()(message(), ctx, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
    }
    const duplicate = context(commandType, kinds, actor);
    duplicate.options.push({ ...option(kinds[0]), ref: 'second_target_synthetic090' });
    assert.deepEqual(await noModel()(message(), duplicate, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
    const ctx = context(commandType, kinds, actor);
    for (const field of Object.keys(payload))
      assert.throws(() => validateDecision({ kind: 'COMMAND', commandType,
        payload: { ...payload, [field]: 'different_actor_or_revision090' } }, ctx), /INVALID_DECISION/);
    for (const field of ['actorId', 'owner', 'inviteCode', 'name', 'reference', 'latitude', 'preference', 'action'])
      assert.throws(() => validateDecision({ kind: 'COMMAND', commandType,
        payload: { ...payload, [field]: 'must_stay_in_server' } }, ctx), /INVALID_DECISION/);
  }
});

test('pin proposal source is taken from QUESTION rather than the current message or a pickup option', () => {
  const ctx = context('PROPOSE_PICKUP', ['QUESTION', 'CONFIRMATION']);
  const actual = decideDeterministically(message('CONFIRMAR EXEMPLOX'), ctx);
  assert.equal(actual.payload.sourceMessageId, 'question_synthetic090');
  assert.notEqual(actual.payload.sourceMessageId, `event_${id}`);
  const changed = { ...actual, payload: { ...actual.payload, sourceMessageId: `event_${id}` } };
  assert.throws(() => validateDecision(changed, ctx), /INVALID_DECISION/);
});

test('passenger, driver and future offers cannot be selected in a conflicting context', async () => {
  for (const extra of ['ADVANCE_DRIVER_SETUP', 'REQUEST_LINK', 'SET_PREFERENCE', 'FUTURE_UNKNOWN']) {
    const ctx = context('ADVANCE_PASSENGER_SETUP'); ctx.allowedCommands.push(extra);
    assert.deepEqual(await noModel()(message('conteúdo privado'), ctx, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
    assert.throws(() => validateDecision({ kind: 'COMMAND', commandType: 'ADVANCE_PASSENGER_SETUP', payload: {} }, ctx), /INVALID_DECISION/);
  }
  const driver = context('ADVANCE_DRIVER_SETUP', [], 'DRIVER'); driver.allowedCommands.push('SET_PREFERENCE');
  assert.deepEqual(decideDeterministically(message(), driver), { kind: 'NO_ACTION' });
  assert.throws(() => validateDecision({ kind: 'COMMAND', commandType: 'ADVANCE_DRIVER_SETUP', payload: {} }, driver), /INVALID_DECISION/);
});

test('partial passenger offers, including a missing command, cannot disclose private input to a model', async () => {
  for (const actorState of ['UNREGISTERED', 'PASSENGER', 'DRIVER']) {
    for (const kind of ['LINK', 'PICKUP', 'QUESTION', 'CONFIRMATION']) {
      const ctx = context('ADVANCE_PASSENGER_SETUP', [kind], actorState);
      ctx.allowedCommands = ['RESPOND_TO_MESSAGE', 'REQUEST_SUPPORT'];
      assert.deepEqual(await noModel()(message('referência privada de teste'), ctx, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
      assert.equal(prepareModelInput(message('referência privada de teste'), ctx), null);
      assert.equal(decideDeterministically(message('AJUDA'), ctx).payload.responseCode, 'HELP');
      assert.equal(decideDeterministically(message('FALAR COM SUPORTE'), ctx).commandType, 'REQUEST_SUPPORT');
    }
  }
});

test('pause, stale or unknown freshness and explicit support precede passenger offers', async () => {
  const ctx = context('ADVANCE_PASSENGER_SETUP');
  for (const change of [{ step: 'PAUSED' }, { registrationStep: 'PAUSED' }])
    assert.deepEqual(await noModel()(message(), { ...ctx, ...change }, { deadline: Date.now() + 1000 }), { kind: 'NO_ACTION' });
  for (const eventFreshness of ['STALE', 'UNKNOWN']) {
    const stale = { ...ctx, eventFreshness };
    assert.deepEqual(await noModel()({ type: 'UNSUPPORTED', sourceMessageId: `event_${id}` }, stale, { deadline: Date.now() + 1000 }),
      { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'STALE_MESSAGE' } });
    assert.throws(() => validateDecision({ kind: 'COMMAND', commandType: 'ADVANCE_PASSENGER_SETUP', payload: {} }, stale), /INVALID_DECISION/);
  }
  assert.equal(decideDeterministically(message('AJUDA'), ctx).payload.responseCode, 'HELP');
  assert.equal(decideDeterministically(message('FALAR COM SUPORTE'), ctx).commandType, 'REQUEST_SUPPORT');
});

test('expired or unoffered confirmation markers use fixed guidance with zero model calls', async () => {
  const ctx = context('ADVANCE_PASSENGER_SETUP'); ctx.allowedCommands = ['RESPOND_TO_MESSAGE'];
  assert.deepEqual(await noModel()(message(), ctx, { deadline: Date.now() + 1000 }),
    { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'CONTINUE' } });
});
