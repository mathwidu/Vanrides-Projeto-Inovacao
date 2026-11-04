import test from 'node:test';
import assert from 'node:assert/strict';
import { filterEvent, classifyPolicyDrop } from './policy.mjs';

const NOW = Date.parse('2026-11-04T12:00:00Z');
const PRIVATE = 'SYNTHETIC_METADATA_NEVER_FORWARD';
const config = Object.freeze({ mode: 'HML_V1', instance: 'synthetic-metadata',
  start: NOW - 60_000, end: NOW + 60_000,
  contacts: Object.freeze([{ label: 'A', pn: '15550001111@s.whatsapp.net', lid: '27770001111@lid' }]) });
const legacy = Object.freeze({ ...config, mode: undefined });
const metadata = () => ({ stanzaId: PRIVATE, participant: '19990001111@s.whatsapp.net',
  quotedMessage: { conversation: PRIVATE }, privateGps: { latitude: 37.123456789, longitude: -111.987654321 } });
function event(type, value) {
  return { event: 'messages.upsert', instance: config.instance, date_time: new Date(NOW).toISOString(),
    data: { key: { id: 'synthetic-message', fromMe: false, remoteJid: config.contacts[0].pn,
      remoteJidAlt: config.contacts[0].lid }, messageType: type,
      message: { [type]: value, messageContextInfo: metadata() } } };
}

test('HML accepts an object messageContextInfo beside one supported content and discards it completely', () => {
  const fixtures = [
    ['conversation', 'AJUDA', { conversation: 'AJUDA' }, undefined],
    ['extendedTextMessage', { text: 'SIM', contextInfo: { stanzaId: 'synthetic-reply', quotedMessage: { conversation: PRIVATE } } },
      { conversation: 'SIM' }, { stanzaId: 'synthetic-reply' }],
    ['locationMessage', { degreesLatitude: -29.9, degreesLongitude: -51.1, isLive: false,
      contextInfo: { stanzaId: 'synthetic-location-reply', quotedMessage: { conversation: PRIVATE } } },
      { locationMessage: { degreesLatitude: -29.9, degreesLongitude: -51.1, isLive: false } },
      { stanzaId: 'synthetic-location-reply' }],
  ];
  for (const [type, value, cleanMessage, reply] of fixtures) {
    const envelope = event(type, value), before = structuredClone(envelope);
    const result = filterEvent(envelope, config, NOW);
    assert.ok(result, type);
    assert.equal(classifyPolicyDrop(envelope, config, NOW), null);
    assert.deepEqual(result.envelope.data.message, cleanMessage);
    assert.deepEqual(result.envelope.data.contextInfo, reply);
    assert.equal(Object.hasOwn(result.envelope.data.message, 'messageContextInfo'), false);
    for (const sentinel of [PRIVATE, '19990001111', '37.123456789', '-111.987654321'])
      assert.equal(JSON.stringify(result).includes(sentinel), false, 'metadata discarded');
    assert.deepEqual(envelope, before, 'input stays intact');
    assert.equal(filterEvent(envelope, legacy, NOW), null, 'legacy still rejects sibling metadata');
  }
});

test('metadata never permits invalid metadata, mixed bodies, wrappers or an unconfirmed LID', () => {
  for (const invalid of [null, [], PRIVATE, 1, true]) {
    const envelope = event('conversation', 'AJUDA');
    envelope.data.message.messageContextInfo = invalid;
    assert.equal(filterEvent(envelope, config, NOW), null);
    assert.equal(classifyPolicyDrop(envelope, config, NOW), 'HML_DROP_MESSAGE_SHAPE');
  }
  const bodies = [
    { messageContextInfo: metadata() },
    { conversation: 'AJUDA', extendedTextMessage: { text: 'SIM' }, messageContextInfo: metadata() },
    { conversation: 'AJUDA', locationMessage: { degreesLatitude: 1, degreesLongitude: 2 }, messageContextInfo: metadata() },
    { ephemeralMessage: { message: { conversation: 'AJUDA' } }, messageContextInfo: metadata() },
    { viewOnceMessage: { message: { conversation: 'AJUDA' } }, messageContextInfo: metadata() },
    { conversation: 'AJUDA', unknownProviderField: PRIVATE, messageContextInfo: metadata() },
  ];
  for (const message of bodies) {
    const envelope = event('conversation', 'AJUDA'); envelope.data.message = message;
    assert.equal(filterEvent(envelope, config, NOW), null);
    assert.equal(classifyPolicyDrop(envelope, config, NOW), 'HML_DROP_MESSAGE_SHAPE');
  }
  const unknown = event('conversation', 'AJUDA'); unknown.data.key.remoteJidAlt = '29990001111@lid';
  assert.equal(filterEvent(unknown, config, NOW), null);
  assert.equal(classifyPolicyDrop(unknown, config, NOW), 'HML_DROP_LID_UNCONFIRMED');
});
