import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { decideDeterministically, validateDecision } from '../agent/deterministic.mjs';

test('the available preference contract is copied by the worker without changing its closed payload', () => {
  const api = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url)));
  const catalog = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/fixtures/catalog.json', import.meta.url)));
  const fixture = catalog.fixtures.find(value => value.id === 'SetPreference');
  assert.equal(fixture.availability, 'CARD_140_PREFERENCES');
  assert.equal(api.components.schemas.SetPreferenceCommand['x-vanrides-availability'], fixture.availability);
  assert.deepEqual(fixture.value, api.components.examples.SetPreference.value);
  assert.equal(api.components.schemas.SetPreferencePayload.additionalProperties, false);
  assert.deepEqual(api.components.schemas.SetPreferencePayload.required, ['preference', 'enabled']);
  const context = { inboxEventId: fixture.value.inboxEventId, entityVersion: 1, actorState: 'PASSENGER', step: 'READY',
    registrationStep: 'READY', eventFreshness: 'CURRENT', allowedCommands: ['RESPOND_TO_MESSAGE', 'SET_PREFERENCE'],
    options: [], serverTime: '2030-01-01T12:00:00Z' };
  const decision = decideDeterministically({ type: 'TEXT', sourceMessageId: 'event_' + context.inboxEventId,
    text: 'LIGAR AVISOS CHEGADA' }, context);
  assert.deepEqual(decision, { kind: 'COMMAND', commandType: fixture.value.commandType, payload: fixture.value.payload });
  assert.doesNotThrow(() => validateDecision(decision, context));
});
