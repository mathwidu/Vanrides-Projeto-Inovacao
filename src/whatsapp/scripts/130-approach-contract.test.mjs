import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { decideDeterministically, validateDecision } from '../agent/deterministic.mjs';
import { createDispatcher } from '../worker/dispatch.mjs';
import { startProvider } from './070-worker-runtime.mjs';

const api = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url)));
const fixtures = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/fixtures/catalog.json', import.meta.url)));
const NOW = Date.parse('2030-01-01T12:00:00Z');
const iso = ms => new Date(ms).toISOString();
const id = n => '00000000-0000-4000-8000-' + String(n).padStart(12, '0');
const token = Buffer.alloc(32, 13).toString('base64url');
const address = '0'.repeat(15) + '@s.whatsapp.net';

test('the materialized producer command and consumer agree on an empty, actor-derived location payload', () => {
  const command = api.components.examples.GetDriverLocation.value;
  const fixture = fixtures.fixtures.find(value => value.id === 'GetDriverLocation');
  assert.deepEqual(command, fixture.value);
  assert.equal(fixture.availability, 'CARD_130_APPROACH');
  const schema = api.components.schemas.GetDriverLocationPayload;
  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(schema.properties, {});
  const context = { inboxEventId: command.inboxEventId, entityVersion: 9, actorState: 'PASSENGER',
    step: 'READY', registrationStep: 'READY', eventFreshness: 'CURRENT', allowedCommands: [command.commandType],
    options: [], serverTime: iso(NOW) };
  const decision = decideDeterministically({ type: 'TEXT', sourceMessageId: 'event_' + command.inboxEventId, text: 'onde está?' }, context);
  assert.deepEqual(decision, { kind: 'COMMAND', commandType: command.commandType, payload: command.payload });
  assert.doesNotThrow(() => validateDecision(decision, context));
  assert.deepEqual(new Set(api.components.schemas.OutboxStaticPinContent.required),
    new Set(['type', 'latitude', 'longitude', 'capturedAt', 'label']));
  assert.ok(api.components.schemas.OutboxClaimItem.properties.purpose.enum.includes('DRIVER_LOCATION'));
});

test('W06 reauthorizes each queued question and sends two distinct static pins with safe control/receipt bodies', async t => {
  const instance = 'wa070_synthetic_' + '1'.repeat(32), apiKey = 'synthetic-130-key';
  const provider = await startProvider(instance, address, apiKey);
  t.after(() => provider.stop());
  const pending = [id(130), id(131)], observed = [], receipts = [];
  let current, capture;
  const backend = { async request(route, options) {
    if (route.endsWith('/claim')) {
      current = pending.shift();
      return { status: 200, body: { items: current ? [{ outboxId: current, purpose: 'DRIVER_LOCATION', leaseToken: token,
        leaseExpiresAt: iso(NOW + 60000), expiresAt: iso(NOW + 120000) }] : [], serverTime: iso(NOW) } };
    }
    if (route.endsWith('/authorize-submit')) {
      capture = { latitude: 0, longitude: observed.length * 0.001, capturedAt: iso(NOW - 1000), label: 'Localização atual do veículo' };
      observed.push(capture.longitude);
      return { status: 200, body: { outboxId: current, attemptToken: token, submitBefore: iso(NOW + 10000),
        recipient: { addressType: 'JID', address }, content: { type: 'STATIC_PIN', ...capture }, serverTime: iso(NOW) } };
    }
    receipts.push(options.body);
    return { status: 200, body: { outboxId: current, state: options.body.outcome, serverTime: iso(NOW) } };
  } };
  const dispatch = createDispatcher({ api: backend, config: { evolutionBaseUrl: provider.url, instance, evolutionApiKey: apiKey }, now: () => NOW });
  for (const outboxId of [...pending]) {
    provider.enqueue({ outboxId, pinMatches: body => body.latitude === capture.latitude && body.longitude === capture.longitude
      && body.address === `Capturada em ${capture.capturedAt}` });
    const control = await dispatch();
    assert.deepEqual(control, { status: 'SUBMITTED', claimed: 1, submitted: 1, unknown: 0, failed: 0 });
    assert.equal(/latitude|longitude|capturedAt|recipient/u.test(JSON.stringify(control)), false);
  }
  assert.deepEqual(observed, [0, 0.001]);
  assert.equal((await dispatch()).status, 'IDLE');
  assert.deepEqual(provider.counts(), { requests: 2, accepted: 2, pending: 0, failures: 0 });
  assert.equal(receipts.length, 2);
  assert.ok(receipts.every(body => Object.keys(body).sort().join(',') === 'attemptToken,externalMessageId,outcome'));
});

test('authorization may replace the queued pin with unavailable text and a post-effect 400 stays UNKNOWN once', async t => {
  const instance = 'wa070_synthetic_' + '2'.repeat(32), apiKey = 'synthetic-130-key';
  const provider = await startProvider(instance, address, apiKey);
  t.after(() => provider.stop());
  let pending = true, receipt;
  const backend = { async request(route, options) {
    if (route.endsWith('/claim')) {
      const available = pending; pending = false;
      return { status: 200, body: { items: available ? [{ outboxId: id(132), purpose: 'DRIVER_LOCATION', leaseToken: token,
        leaseExpiresAt: iso(NOW + 60000), expiresAt: iso(NOW + 120000) }] : [], serverTime: iso(NOW) } };
    }
    if (route.endsWith('/authorize-submit')) return { status: 200, body: { outboxId: id(132), attemptToken: token,
      submitBefore: iso(NOW + 10000), recipient: { addressType: 'JID', address }, serverTime: iso(NOW),
      content: { type: 'TEXT', text: 'Localização indisponível.', referenceTime: iso(NOW) } } };
    receipt = options.body;
    return { status: 200, body: { outboxId: id(132), state: 'UNKNOWN', serverTime: iso(NOW) } };
  } };
  provider.enqueue({ outboxId: id(132), text: 'Localização indisponível.' });
  provider.nextPostEffect('AMBIGUOUS_AFTER_EFFECT');
  const dispatch = createDispatcher({ api: backend, config: { evolutionBaseUrl: provider.url, instance, evolutionApiKey: apiKey }, now: () => NOW });
  assert.equal((await dispatch()).status, 'UNKNOWN');
  assert.deepEqual(receipt, { attemptToken: token, outcome: 'UNKNOWN', failureCode: 'CHANNEL_UNAVAILABLE' });
  assert.equal((await dispatch()).status, 'IDLE');
  assert.deepEqual(provider.counts(), { requests: 1, accepted: 1, pending: 0, failures: 0 });
});
