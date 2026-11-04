import test from 'node:test';
import assert from 'node:assert/strict';
import { request as httpRequest } from 'node:http';
import { once } from 'node:events';
import { validateConfig } from './config.mjs';
import { filterEvent } from './policy.mjs';
import { createLiveIngressServer } from './server.mjs';

const NOW = Date.parse('2026-11-03T12:00:00Z');
const TOKEN = Buffer.alloc(32, 7).toString('base64url');
const WEBHOOK = Buffer.alloc(32, 8).toString('base64url');
const PRIVATE = 'DO_NOT_FORWARD_OR_LOG_THIS_QUOTED_BODY';
const clone = value => structuredClone(value);

function rawConfig() {
  return { mode: 'HML_V1', environment: 'vanrides-mvp-hml', instance: 'synthetic-hml',
    webhookToken: WEBHOOK, channelToken: TOKEN,
    apiOrigin: 'https://vanrides-inovacao-api.matheusduarte.dev.br',
    startsAt: '2026-11-03T00:00:00Z', endsAt: '2026-12-03T00:00:00Z',
    listenHost: '127.0.0.1', listenPort: 7180,
    contacts: ['A', 'B', 'C', 'D'].map((label, index) => ({ label, approved: true,
      pn: '1000000000' + (index + 1) + '@s.whatsapp.net', lid: '2000000000' + (index + 1) + '@lid' })),
    limitsPerMinute: { human: 4, perContact: 1, status: 1, connection: 1 } };
}
function human(label = 'A', clock = NOW) {
  const index = ['A', 'B', 'C', 'D'].indexOf(label) + 1;
  return { event: 'messages.upsert', instance: 'synthetic-hml', date_time: new Date(clock).toISOString(),
    data: { key: { id: 'synthetic-' + label + '-' + clock, fromMe: false,
      remoteJid: '1000000000' + index + '@s.whatsapp.net', remoteJidAlt: '2000000000' + index + '@lid' },
      messageType: 'conversation', messageTimestamp: Math.floor(clock / 1000), message: { conversation: 'oi' } } };
}
function callback(kind, clock = NOW) {
  return { event: kind === 'status' ? 'messages.update' : 'connection.update', instance: 'synthetic-hml',
    date_time: new Date(clock).toISOString(), data: kind === 'status'
      ? { keyId: 'synthetic-response', fromMe: true, status: 'READ' } : { state: 'open' } };
}
const receipt = (status = 'PERSISTED', environment = 'vanrides-mvp-hml') => new Response(JSON.stringify({
  receiptId: '00000000-0000-4000-8000-000000000001', status, serverTime: new Date(NOW).toISOString(),
}), { status: 202, headers: { 'content-type': 'application/json',
  ...(environment === undefined ? {} : { 'x-vanrides-environment': environment }) } });

async function server(t, { raw = rawConfig(), now = () => NOW, fetchImpl } = {}) {
  const calls = [], logs = [];
  const config = validateConfig(raw);
  const ingress = createLiveIngressServer({ config, now, log: row => logs.push(row),
    fetchImpl: fetchImpl ?? (async (url, input) => { calls.push({ url, input }); return receipt(); }) });
  ingress.listen(0, '127.0.0.1'); await once(ingress, 'listening');
  t.after(() => ingress.shutdown());
  return { ingress, config, calls, logs, port: ingress.address().port };
}
function request(port, event, token = WEBHOOK) {
  const bytes = Buffer.from(JSON.stringify(event));
  return new Promise((resolve, reject) => {
    const req = httpRequest({ hostname: '127.0.0.1', port, path: '/evolution/webhook', method: 'POST', agent: false,
      headers: { Authorization: 'Bearer ' + token, 'content-type': 'application/json', 'content-length': bytes.length } }, res => {
      const chunks = []; res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks).toString() }));
    });
    req.on('error', reject); req.end(bytes);
  });
}

test('HML requires explicit approved A-D contacts, HTTPS and bounded immutable 30-day windows', () => {
  const raw = rawConfig(), config = validateConfig(raw);
  assert.equal(config.end - config.start, 30 * 24 * 60 * 60_000);
  raw.contacts[0].pn = 'foreign'; raw.endsAt = '2099-01-01T00:00:00Z'; raw.limitsPerMinute.human = 99999;
  assert.equal(config.contacts[0].pn, '10000000001@s.whatsapp.net');
  assert.equal(config.limitsPerMinute.human, 4);
  assert.ok(Object.isFrozen(config) && Object.isFrozen(config.contacts) && Object.isFrozen(config.limitsPerMinute));
  for (const secret of [TOKEN, WEBHOOK, '@s.whatsapp.net', '@lid']) assert.equal(JSON.stringify(config).includes(secret), false);
  for (let count = 1; count <= 4; count++) {
    const raw = rawConfig(); raw.contacts = raw.contacts.slice(0, count);
    assert.equal(validateConfig(raw).contacts.length, count);
  }
  const changes = [raw => { raw.contacts = []; }, raw => { raw.contacts[0].approved = false; },
    raw => { delete raw.contacts[0].approved; }, raw => { raw.contacts[0].label = 'E'; },
    raw => { raw.contacts[1].label = 'A'; }, raw => { raw.contacts[1].lid = raw.contacts[0].lid; },
    raw => { raw.contacts.push({ label: 'E', approved: true, pn: '999@s.whatsapp.net' }); },
    raw => { raw.environment = 'production'; }, raw => { raw.mode = 'HML'; },
    raw => { raw.apiOrigin = 'http://backend:8080'; }, raw => { raw.apiOrigin = 'https://example.test:0'; },
    raw => { raw.apiOrigin += '/events'; },
    raw => { raw.endsAt = '2026-12-03T00:00:00.001Z'; }, raw => { raw.endsAt = raw.startsAt; },
    raw => { raw.limitsPerMinute.status = 1201; }, raw => { raw.limitsPerMinute.human = 0; },
    raw => { raw.limitsPerMinute.perContact = 5; }, raw => { raw.extra = true; }];
  for (const change of changes) {
    const raw = rawConfig(); change(raw);
    assert.throws(() => validateConfig(raw), /^Error: CONFIG_INVALID$/);
  }
  const single = rawConfig(); single.contacts = [single.contacts[2]];
  assert.equal(filterEvent(human('A'), validateConfig(single), NOW), null);
  assert.equal(filterEvent(human('C'), validateConfig(single), NOW).contact, 'C');
});

test('HML rebuilds static PIN and reply metadata in backend precedence without quoted bodies', () => {
  const config = validateConfig(rawConfig()), event = human('C');
  event.apikey = PRIVATE; event.data.pushName = PRIVATE;
  event.data.messageType = 'locationMessage';
  event.data.contextInfo = { stanzaId: 'provider-top-reply', quotedMessage: { conversation: PRIVATE }, participant: PRIVATE };
  event.data.message = { locationMessage: { degreesLatitude: -29.9, degreesLongitude: -51.1, isLive: false,
    name: PRIVATE, address: PRIVATE, jpegThumbnail: PRIVATE,
    contextInfo: { stanzaId: 'provider-nested-reply', quotedMessage: { conversation: PRIVATE } } } };
  const result = filterEvent(event, config, NOW);
  assert.equal(result.contact, 'C');
  assert.deepEqual(result.envelope.data.message, { locationMessage: {
    degreesLatitude: -29.9, degreesLongitude: -51.1, isLive: false } });
  assert.deepEqual(result.envelope.data.contextInfo, { stanzaId: 'provider-top-reply' });
  assert.equal(JSON.stringify(result).includes(PRIVATE), false);
  const text = human('D'); text.data.messageType = 'extendedTextMessage';
  text.data.message = { extendedTextMessage: { text: 'SIM',
    contextInfo: { stanzaId: 'provider-text-reply', quotedMessage: { conversation: PRIVATE } } } };
  assert.deepEqual(filterEvent(text, config, NOW).envelope.data.contextInfo, { stanzaId: 'provider-text-reply' });
  const changes = [value => { value.data.message.locationMessage.isLive = true; },
    value => { value.data.message.locationMessage.isLive = null; },
    value => { value.data.message.locationMessage.degreesLatitude = 91; },
    value => { value.data.message.locationMessage.degreesLongitude = '1'; },
    value => { value.data.message.conversation = 'mixed'; },
    value => { value.data.message = { liveLocationMessage: { degreesLatitude: 1, degreesLongitude: 1 } }; },
    value => { value.data.key.remoteJidAlt = '20000000002@lid'; }, value => { value.data.key.fromMe = true; }];
  for (const change of changes) { const value = clone(event); change(value); assert.equal(filterEvent(value, config, NOW), null); }
});

test('HML authenticates callbacks and recovers human and callback quotas after a rolling minute', async t => {
  let clock = NOW;
  const lab = await server(t, { now: () => clock });
  assert.equal((await request(lab.port, human(), TOKEN)).status, 401);
  assert.equal(lab.calls.length, 0);
  for (const contact of ['A', 'B', 'C', 'D']) assert.equal((await request(lab.port, human(contact, clock))).status, 202);
  let limited = await request(lab.port, human('A', clock));
  assert.equal(limited.status, 429); assert.equal(limited.headers['retry-after'], '60');
  for (const kind of ['status', 'connection']) {
    const value = callback(kind, clock); value.data.extra = PRIVATE;
    assert.equal((await request(lab.port, value)).status, 202);
    assert.equal((await request(lab.port, value)).status, 429);
  }
  clock = NOW + 59_999;
  limited = await request(lab.port, human('A', clock));
  assert.equal(limited.status, 429); assert.equal(limited.headers['retry-after'], '1');
  clock = NOW + 60_000;
  assert.equal((await request(lab.port, human('A', clock))).status, 202);
  for (const kind of ['status', 'connection']) assert.equal((await request(lab.port, callback(kind, clock))).status, 202);
  const counts = lab.ingress.snapshot();
  assert.equal(counts.human, 5); assert.equal(counts.contactA, 2); assert.equal(counts.status, 2); assert.equal(counts.connection, 2);
  for (const call of lab.calls) {
    assert.equal(call.url, 'https://vanrides-inovacao-api.matheusduarte.dev.br/integrations/whatsapp/v1/events');
    assert.equal(call.input.headers.Authorization, 'Bearer ' + TOKEN); assert.equal(call.input.redirect, 'manual');
    assert.equal(call.input.body.includes(PRIVATE), false);
  }
  for (const secret of [TOKEN, WEBHOOK, PRIVATE, '10000000001', '20000000001'])
    assert.equal(JSON.stringify(lab.logs).includes(secret), false);
});

test('HML ends remain immutable and renewal requires a newly validated deployment window', async t => {
  let clock = NOW;
  const raw = rawConfig(), old = await server(t, { raw, now: () => clock });
  raw.endsAt = '2027-01-01T00:00:00Z';
  clock = old.config.end;
  assert.equal((await request(old.port, human('A', clock))).status, 204);
  assert.equal(old.calls.length, 0);
  const renewed = rawConfig(); renewed.startsAt = new Date(clock).toISOString();
  renewed.endsAt = new Date(clock + 30 * 24 * 60 * 60_000).toISOString();
  const next = await server(t, { raw: renewed, now: () => clock });
  assert.equal((await request(next.port, human('A', clock))).status, 202);
  assert.equal((await request(old.port, human('A', clock))).status, 204);
});

test('HML preserves backend IGNORED receipts and rejects a missing or foreign environment response', async t => {
  const ignored = await server(t, { fetchImpl: async () => receipt('IGNORED') });
  const result = await request(ignored.port, callback('connection'));
  assert.equal(result.status, 202); assert.equal(JSON.parse(result.body).status, 'IGNORED');
  assert.equal(ignored.ingress.snapshot().ignored, 1); assert.equal(ignored.ingress.snapshot().forwarded, 0);
  for (const environment of ['vanrides-team-hml', null]) {
    let calls = 0;
    const upstream = await server(t, { fetchImpl: async () => {
      calls++;
      return new Response(JSON.stringify({ receiptId: '00000000-0000-4000-8000-000000000001',
        status: 'PERSISTED', serverTime: new Date(NOW).toISOString() }), { status: 202,
        headers: { 'content-type': 'application/json', ...(environment ? { 'x-vanrides-environment': environment } : {}) } });
    } });
    assert.equal((await request(upstream.port, human())).status, 503); assert.equal(calls, 1);
  }
});
