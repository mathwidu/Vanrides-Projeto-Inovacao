// Cross-repository producer/consumer check; not shipped in the private worker image.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PASSENGER_PAYLOADS } from '../agent/passenger-setup.mjs';
import { decideDeterministically, validateDecision } from '../agent/deterministic.mjs';

const contract = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url), 'utf8'));
const catalog = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/fixtures/catalog.json', import.meta.url), 'utf8'));
const schemas = contract.components.schemas;
const id = '00000000-0000-4000-8000-000000000090';
// Consumer expectations are explicit, independent of either schema or payload table.
const cases = [
  ['AdvancePassengerSetup', 'ADVANCE_PASSENGER_SETUP', {}, 'UNREGISTERED'],
  ['RequestLink', 'REQUEST_LINK', { confirmationRef: 'CONFIRMATION' }, 'PASSENGER'],
  ['ApproveLink', 'APPROVE_LINK', { linkRef: 'LINK', pickupRef: 'PICKUP', confirmationRef: 'CONFIRMATION' }, 'DRIVER'],
  ['CloseLink', 'CLOSE_LINK', { linkRef: 'LINK', confirmationRef: 'CONFIRMATION' }, 'DRIVER'],
  ['ProposePickup', 'PROPOSE_PICKUP', { sourceMessageId: 'QUESTION', confirmationRef: 'CONFIRMATION' }, 'PASSENGER'],
  ['ApprovePickup', 'APPROVE_PICKUP', { pickupRef: 'PICKUP', confirmationRef: 'CONFIRMATION' }, 'DRIVER'],
];

test('090 advertises exactly the six versioned operations and keeps SET_PREFERENCE reserved', () => {
  const expected = cases.map(([, command]) => command).sort();
  assert.deepEqual(Object.keys(PASSENGER_PAYLOADS).sort(), expected);
  assert.deepEqual([...contract.paths['/integrations/whatsapp/v1/commands'].post['x-vanrides-supported-commands-in-090']].sort(), expected);
  assert.equal(new Set(schemas.CommandType.enum).size, schemas.CommandType.enum.length);
  assert.ok(expected.every(c => schemas.CommandType.enum.includes(c)));
  assert.equal(schemas.SetPreferenceCommand['x-vanrides-availability'], 'FUTURE');
  assert.equal(schemas.SetPreferenceCommand['x-vanrides-implementation-card'], 'VR-WA-140-FINISH-SUPPORT');
});

for (const [name, commandType, fields, actorState] of cases) test(`${commandType} forwards exactly the producer schema and authorized reference kinds`, () => {
  assert.deepEqual(PASSENGER_PAYLOADS[commandType], fields);
  const candidates = Object.values(schemas).filter(schema => schema.properties?.commandType?.enum?.length === 1
    && schema.properties.commandType.enum[0] === commandType);
  assert.equal(candidates.length, 1);
  const envelope = candidates[0];
  assert.equal(envelope.additionalProperties, false);
  assert.equal(envelope['x-vanrides-implementation-card'], 'VR-WA-090-PASSENGERS');
  assert.equal(envelope['x-vanrides-availability'], 'CARD_090_PASSENGER_SETUP');
  assert.deepEqual([...envelope.required].sort(), ['commandType', 'expectedVersion', 'inboxEventId', 'payload']);
  const payload = schemas[envelope.properties.payload.$ref.split('/').at(-1)];
  assert.equal(payload.additionalProperties, false);
  assert.deepEqual(Object.keys(payload.properties ?? {}).sort(), Object.keys(fields).sort());
  assert.deepEqual([...(payload.required ?? [])].sort(), Object.keys(fields).sort());
  for (const [field, kind] of Object.entries(fields)) {
    assert.equal(payload.properties[field].$ref, '#/components/schemas/ContextReference');
    assert.ok(schemas.ContextOption.properties.kind.enum.includes(kind));
  }
  const options = Object.values(fields).map(kind => ({ kind, ref: `synthetic090_${kind.toLowerCase()}`, label: 'Opção autorizada' }));
  const context = { inboxEventId: id, entityVersion: 3, actorState,
    step: actorState === 'UNREGISTERED' ? 'ONBOARDING' : 'PAIRING',
    registrationStep: actorState === 'UNREGISTERED' ? 'WELCOME' : 'PAIRING',
    eventFreshness: 'CURRENT', allowedCommands: ['RESPOND_TO_MESSAGE', commandType], options,
    serverTime: '2026-10-31T12:00:00Z' };
  const actual = decideDeterministically({ type: 'UNSUPPORTED', sourceMessageId: `event_${id}` }, context);
  assert.deepEqual(actual, { kind: 'COMMAND', commandType,
    payload: Object.fromEntries(Object.entries(fields).map(([field, kind]) => [field, `synthetic090_${kind.toLowerCase()}`])) });
  validateDecision(actual, context);
  const fixture = catalog.fixtures.find(f => f.id === name);
  assert.equal(fixture.schema, name+'Command');
  assert.equal(fixture.availability, envelope['x-vanrides-availability']);
  assert.deepEqual(fixture.value, contract.components.examples[name].value);
  assert.deepEqual(Object.keys(fixture.value.payload).sort(), Object.keys(fields).sort());
});

test('090 context capabilities retain the closed and bounded common representation', () => {
  const options = schemas.ContextOption;
  assert.equal(options.additionalProperties, false);
  assert.deepEqual(Object.keys(options.properties).sort(), ['kind', 'label', 'ref']);
  assert.deepEqual([...options.required].sort(), ['kind', 'label', 'ref']);
  assert.equal(schemas.ContextReference.minLength, 8);
  assert.equal(schemas.ContextReference.maxLength, 160);
  assert.equal(schemas.ContextReference.pattern, '^[A-Za-z0-9_-]+$');
  assert.equal(schemas.ConversationContextResponse.properties.allowedCommands.maxItems, schemas.CommandType.enum.length);
  assert.equal(PASSENGER_PAYLOADS.PROPOSE_PICKUP.sourceMessageId, 'QUESTION');
});

test('090 schemas reject raw invite, reference text, action and authority selection structurally', () => {
  for (const [name] of cases) {
    const schema = schemas[name+'Payload'];
    for (const field of ['inviteCode', 'reference', 'action', 'reason', 'actorId', 'driverId', 'owner', 'latitude', 'name', 'preference'])
      assert.equal(Object.hasOwn(schema.properties, field), false, `${name}.${field}`);
  }
  for (const name of ['RequestLink', 'ApproveLink', 'CloseLink', 'ProposePickup', 'ApprovePickup'])
    assert.ok(catalog.negativeFixtures.some(f => f.id === name+'RequiresConfirmation090'));
});
