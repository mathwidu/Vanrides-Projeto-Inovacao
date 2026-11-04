import test from 'node:test';
import assert from 'node:assert/strict';
import { request as httpRequest } from 'node:http';
import { once } from 'node:events';
import { validateConfig } from './config.mjs';
import { filterEvent, classifyPolicyDrop } from './policy.mjs';
import { createLiveIngressServer } from './server.mjs';

const NOW = Date.parse('2026-11-04T12:00:00Z');
const TOKEN = Buffer.alloc(32, 7).toString('base64url');
const WEBHOOK = Buffer.alloc(32, 8).toString('base64url');
const PRIVATE = 'SENTINEL_PRIVATE_TEXT_PN_LID_GPS_NEVER_LOG';
const PN = '15550001111@s.whatsapp.net';
const LID = '27770001111@lid';
const UNKNOWN_LID = '29990001111@lid';
function config(hml = true) {
  const raw = { instance: 'synthetic-diagnostics', webhookToken: WEBHOOK, channelToken: TOKEN,
    apiOrigin: hml ? 'https://example.test' : 'http://backend:8080',
    startsAt: new Date(NOW - 60_000).toISOString(), endsAt: new Date(NOW + 900_000).toISOString(),
    listenHost: '127.0.0.1', listenPort: 7180,
    contacts: [{ label: 'A', pn: PN, lid: LID },
      { label: 'B', pn: '15550002222@s.whatsapp.net', lid: '27770002222@lid' }] };
  if (hml) Object.assign(raw, { mode: 'HML_V1', environment: 'vanrides-mvp-hml',
    contacts: raw.contacts.map(contact => ({ ...contact, approved: true })),
    limitsPerMinute: { human: 4, perContact: 2, status: 4, connection: 4 } });
  return validateConfig(raw);
}
function human() {
  return { event: 'messages.upsert', instance: 'synthetic-diagnostics', date_time: new Date(NOW).toISOString(),
    data: { key: { id: PRIVATE, fromMe: false, remoteJid: PN, remoteJidAlt: LID },
      messageType: 'conversation', messageTimestamp: NOW / 1000, message: { conversation: PRIVATE },
      pushName: PRIVATE, contextInfo: { quotedMessage: { conversation: PRIVATE }, participant: PRIVATE },
      privateGps: { latitude: 37.123456789, longitude: -111.987654321 } } };
}

const cases = [
  ['HML_DROP_ENVELOPE_SHAPE', () => null],
  ['HML_DROP_INSTANCE', value => { value.instance = PRIVATE; }],
  ['HML_DROP_DATA_SHAPE', value => { value.data = null; }],
  ['HML_DROP_EVENT_TIMESTAMP', value => { value.date_time = PRIVATE; }],
  ['HML_DROP_EVENT_TIMESTAMP', value => { value.date_time = new Date(NOW + 31_000).toISOString(); }],
  ['HML_DROP_EVENT_KIND', value => { value.event = PRIVATE; }],
  ['HML_DROP_DIRECTION', value => { value.data.key.fromMe = true; }],
  ['HML_DROP_ALIAS_FORMAT', value => { value.data.key.remoteJidAlt = PRIVATE; }],
  ['HML_DROP_LID_UNCONFIRMED', value => { value.data.key.remoteJidAlt = UNKNOWN_LID; }],
  ['HML_DROP_CONTACT_NOT_ADMITTED', value => {
    value.data.key.remoteJid = '18880001111@s.whatsapp.net'; delete value.data.key.remoteJidAlt;
  }],
  ['HML_DROP_ALIAS_CONFLICT', value => { value.data.key.remoteJidAlt = '27770002222@lid'; }],
  ['HML_DROP_MESSAGE_SHAPE', value => { value.data.message.messageContextInfo = null; }],
  ['HML_DROP_MESSAGE_TYPE', value => { value.data.messageType = PRIVATE; }],
  ['HML_DROP_MESSAGE_TIMESTAMP', value => { value.data.messageTimestamp = PRIVATE; }],
  ['HML_DROP_LOCATION_SHAPE', value => {
    value.data.message = { locationMessage: { degreesLatitude: 91, degreesLongitude: -111.987654321 } };
  }],
  ['HML_DROP_CALLBACK_SHAPE', value => {
    value.event = 'messages.update'; value.data = { keyId: PRIVATE, fromMe: true, status: PRIVATE };
  }],
];

test('HML diagnostics share rejection points without accepting unknown aliases or invalid messageContextInfo', () => {
  const hml = config(), legacy = config(false);
  assert.ok(filterEvent(human(), hml, NOW));
  assert.equal(classifyPolicyDrop(human(), hml, NOW), null);
  for (const [code, mutate] of cases) {
    let value = human(); const replacement = mutate(value);
    if (replacement === null) value = null;
    assert.equal(filterEvent(value, hml, NOW), null, code);
    assert.equal(classifyPolicyDrop(value, hml, NOW), code);
    assert.equal(filterEvent(value, legacy, NOW), null, code);
    assert.equal(classifyPolicyDrop(value, legacy, NOW), null);
  }
  assert.equal(classifyPolicyDrop(human(), hml, hml.end), 'HML_DROP_WINDOW');
});

function send(port, value) {
  return new Promise((resolve, reject) => {
    const request = httpRequest({ host: '127.0.0.1', port, method: 'POST', path: '/evolution/webhook', agent: false,
      headers: { authorization: 'Bearer ' + WEBHOOK, 'content-type': 'application/json' } }, response => {
      const chunks = []; response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => resolve({ status: response.statusCode, body: Buffer.concat(chunks).toString() }));
    });
    request.on('error', reject); request.end(JSON.stringify(value));
  });
}

test('HTTP keeps 204 and counters; only HML logs fixed codes, never payload sentinels', async t => {
  for (const hml of [true, false]) {
    const logs = []; let calls = 0;
    const server = createLiveIngressServer({ config: config(hml), now: () => NOW, log: row => logs.push(row),
      fetchImpl: async () => { calls++; throw new Error('UNEXPECTED_UPSTREAM'); } });
    server.listen(0, '127.0.0.1'); await once(server, 'listening');
    t.after(() => server.shutdown());
    for (const [code, mutate] of cases) {
      let value = human(); const replacement = mutate(value);
      if (replacement === null) value = null;
      const result = await send(server.address().port, value);
      assert.deepEqual(result, { status: 204, body: '' });
      assert.deepEqual(Object.keys(logs.at(-1)).sort(), ['code', 'counters']);
      assert.equal(logs.at(-1).code, hml ? code : 'POLICY_DROP');
    }
    const counts = server.snapshot();
    assert.equal(counts.dropped, cases.length); assert.equal(counts.human, 0);
    assert.equal(counts.forwarded, 0); assert.equal(counts.unauthorized, 0); assert.equal(calls, 0);
    const serialized = JSON.stringify(logs);
    for (const sentinel of [PRIVATE, PN, LID, UNKNOWN_LID, TOKEN, WEBHOOK, new Date(NOW).toISOString(),
      '37.123456789', '-111.987654321', 'synthetic-diagnostics', '@s.whatsapp.net', '@lid'])
      assert.equal(serialized.includes(sentinel), false, 'private diagnostic output');
  }
});
