import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decideDeterministically, attendanceIntent, validateDecision } from './deterministic.mjs';

const id = '00000000-0000-4000-8000-000000000001';
const corpus = JSON.parse(readFileSync(new URL('./corpus.json', import.meta.url), 'utf8'));
function context(c) {
  const allowed = ['RESPOND_TO_MESSAGE', 'REQUEST_SUPPORT'];
  if (c.stage === 'WELCOME') allowed.push('START_ONBOARDING');
  if (c.stage === 'ASK_NAME') allowed.push('SET_PROFILE_NAME');
  if (c.stage === 'ASK_NOTICE') allowed.push('ACCEPT_NOTICE');
  if (c.codeAction) allowed.push(c.codeAction);
  return { inboxEventId: id, entityVersion: 1, actorState: c.role ?? (c.stage === 'WELCOME' ? 'UNREGISTERED' : 'PASSENGER'),
    step: ['READY', 'PAIRING', 'PAUSED'].includes(c.stage) ? c.stage : 'ONBOARDING',
    registrationStep: c.stage, eventFreshness: c.freshness ?? 'CURRENT', allowedCommands: allowed,
    options: [], serverTime: '2026-10-31T12:00:00Z' };
}
test('frozen corpus contains at least sixty synthetic cases', () => {
  assert.equal(corpus.syntheticOnly, true); assert.ok(corpus.cases.length >= 60);
  assert.equal(new Set(corpus.cases.map(c => c.id)).size, corpus.cases.length);
});
for (const c of corpus.cases) test(c.id, () => {
  const input = context(c);
  const message = { sourceMessageId: `event_${id}`, type: c.kind ?? 'TEXT' };
  if (c.text !== null) message.text = c.text;
  const actual = decideDeterministically(message, input);
  assert.equal(actual.kind, 'COMMAND'); assert.equal(actual.commandType, c.expectedCommand);
  if (c.expectedCode) assert.deepEqual(actual.payload, { responseCode: c.expectedCode });
  if (c.expectedPayload) assert.deepEqual(actual.payload, c.expectedPayload);
  validateDecision(actual, input);
  assert.equal(Object.hasOwn(actual.payload, 'actorId'), false);
  assert.equal(Object.hasOwn(actual.payload, 'recipient'), false);
});
test('negation and day/direction remain intent, never an occurrence ID', () => {
  assert.deepEqual(attendanceIntent('não vou amanhã na volta'), { attendance: 'NOT_GOING', day: 'TOMORROW', direction: 'VOLTA' });
  assert.deepEqual(attendanceIntent('VOU'), { attendance: 'CONFIRMED', day: null, direction: null });
  for (const text of ['não sei se vou', 'vou amanhã mas não na volta', 'vou como outra pessoa'])
    assert.equal(attendanceIntent(text), null);
});
test('expired notice requests a fresh prompt instead of pausing or granting consent', () => {
  const ctx = context({ stage: 'ASK_NOTICE' }); ctx.allowedCommands = ['RESPOND_TO_MESSAGE'];
  const actual = decideDeterministically({ sourceMessageId: `event_${id}`, type: 'TEXT', text: 'ACEITO' }, ctx);
  assert.deepEqual(actual, { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'CONTINUE' } });
});
test('questions and explicit invitation preferences choose fixed guidance without starting or advancing registration', () => {
  for (const stage of ['WELCOME', 'ASK_NAME', 'ASK_NOTICE', 'PAIRING', 'READY']) {
    const ctx = context({ stage });
    for (const text of ['Como funciona o VanRides?', 'Como faço para me cadastrar?', 'Preciso baixar o app?',
      'Como faço para criar uma rotina?', 'Como convido passageiros?', 'Como confirmo minha presença?', 'Posso tirar uma dúvida?',
      'Aceito receber convites', 'PARAR CONVITES']) {
      const decision = decideDeterministically({ sourceMessageId: `event_${id}`, type: 'TEXT', text }, ctx);
      assert.deepEqual(decision, { kind: 'COMMAND', commandType: 'RESPOND_TO_MESSAGE', payload: { responseCode: 'FAQ' } }, text);
      validateDecision(decision, ctx);
    }
  }
  const ctx = context({ stage: 'ASK_NOTICE' });
  for (const text of ['Não aceito receber convites', 'Aceito receber convites?', 'Parar convites amanhã']) {
    const decision = decideDeterministically({ sourceMessageId: `event_${id}`, type: 'TEXT', text }, ctx);
    assert.equal(decision.commandType, 'RESPOND_TO_MESSAGE');
    assert.notEqual(decision.payload.responseCode, 'FAQ');
  }
  const stale = decideDeterministically({ sourceMessageId: `event_${id}`, type: 'TEXT', text: 'Como funciona?' },
    context({ stage: 'ASK_NOTICE', freshness: 'STALE' }));
  assert.deepEqual(stale.payload, { responseCode: 'STALE_MESSAGE' });
});
test('natural onboarding phrases do not get saved as a name', () => {
  const message = { sourceMessageId: `event_${id}`, type: 'TEXT' };
  for (const text of ['Quero ser passageiro', 'Quero começar meu cadastro', 'Quero cadastrar meu perfil']) {
    assert.equal(decideDeterministically({ ...message, text }, context({ stage: 'WELCOME' })).commandType, 'START_ONBOARDING');
    assert.deepEqual(decideDeterministically({ ...message, text }, context({ stage: 'ASK_NAME' })).payload,
      { responseCode: 'CLARIFY' });
  }
  const driver = context({ stage: 'ASK_NAME', role: 'DRIVER' });
  assert.deepEqual(decideDeterministically({ ...message, text: 'João123' }, driver).payload, { responseCode: 'CLARIFY' });
  assert.deepEqual(decideDeterministically({ ...message, text: 'Quero retomar meu cadastro' }, driver).payload, { responseCode: 'CONTINUE' });
  assert.deepEqual(decideDeterministically({ ...message, text: 'Me chamo João D’Ávila' }, driver).payload, { name: 'João D’Ávila' });
});
test('paused and missing authorization never become actions', () => {
  for (const c of corpus.cases) {
    const ctx = context(c); ctx.allowedCommands = [];
    const msg = { sourceMessageId: `event_${id}`, type: c.kind ?? 'TEXT', ...(c.text === null ? {} : { text: c.text }) };
    assert.equal(decideDeterministically(msg, ctx).kind, 'NO_ACTION');
  }
  const ctx = context({ stage: 'PAUSED' });
  assert.equal(decideDeterministically({ sourceMessageId: `event_${id}`, type: 'TEXT', text: 'ACEITO' }, ctx).kind, 'NO_ACTION');
});
test('context/message reject injected actor, GPS, aliases and secrets', () => {
  const ctx = context({ stage: 'ASK_NAME' });
  const msg = { sourceMessageId: `event_${id}`, type: 'TEXT', text: 'Ana Exemplo' };
  for (const field of ['actorId', 'driverId', 'recipient', 'latitude', 'longitude', 'token', 'role']) {
    assert.throws(() => decideDeterministically(msg, { ...ctx, [field]: 'injected' }), /INVALID_CONTEXT/);
    assert.throws(() => decideDeterministically({ ...msg, [field]: 'injected' }, ctx), /INVALID_MESSAGE/);
  }
});
test('proposals are closed and cannot escalate beyond API authorization', () => {
  const ctx = context({ stage: 'ASK_NAME' });
  const valid = { kind: 'COMMAND', commandType: 'SET_PROFILE_NAME', payload: { name: 'Ana Exemplo' } };
  validateDecision(valid, ctx);
  for (const field of ['actorId', 'driverId', 'recipient', 'latitude', 'authorization'])
    assert.throws(() => validateDecision({ ...valid, payload: { ...valid.payload, [field]: 'injected' } }, ctx), /INVALID_DECISION/);
  assert.throws(() => validateDecision({ ...valid, commandType: 'CONFIRM_PAIRING', payload: {} }, ctx), /INVALID_DECISION/);
  assert.throws(() => validateDecision(valid, { ...ctx, eventFreshness: 'STALE' }), /INVALID_DECISION/);
});
