import test from 'node:test';
import assert from 'node:assert/strict';
import { request as httpRequest, createServer } from 'node:http';
import { connect } from 'node:net';
import { once } from 'node:events';
import { mkdtempSync, writeFileSync, chmodSync, symlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { strictJson, timestamp } from './strict-json.mjs';
import { validateConfig, loadConfig } from './config.mjs';
import { filterEvent } from './policy.mjs';
import { startPost } from './post.mjs';
import { createLiveIngressServer } from './server.mjs';

const NOW = Date.parse('2026-11-01T12:05:00Z');
const SECRET = 'PROVIDER_SECRET_SENTINEL_NEVER_FORWARD';
const TOKEN = Buffer.alloc(32, 7).toString('base64url');
const WEBHOOK_TOKEN = Buffer.alloc(32, 8).toString('base64url');
const RECEIPT = { receiptId: '00000000-0000-4000-8000-000000000001', status: 'PERSISTED', serverTime: new Date(NOW).toISOString() };
const clone = value => structuredClone(value);
function rawConfig() {
  return { instance: 'synthetic-wa120', webhookToken: WEBHOOK_TOKEN, channelToken: TOKEN, apiOrigin: 'http://wa120-api:8080',
    startsAt: '2026-11-01T12:00:00Z', endsAt: '2026-11-01T12:30:00Z', listenHost: '127.0.0.1', listenPort: 7180,
    contacts: [{ label: 'A', pn: '10000000001@s.whatsapp.net', lid: '20000000001@lid' },
      { label: 'B', pn: '10000000002@s.whatsapp.net', lid: '20000000002@lid' }] };
}
function human() {
  return { event: 'messages.upsert', instance: 'synthetic-wa120', date_time: new Date(NOW).toISOString(),
    data: { key: { id: 'synthetic-message', fromMe: false, remoteJid: '10000000001@s.whatsapp.net', remoteJidAlt: '20000000001@lid' },
      messageType: 'conversation', messageTimestamp: NOW / 1000, message: { conversation: 'Onde a van está?' } } };
}
const status = () => ({ event: 'messages.update', instance: 'synthetic-wa120', date_time: new Date(NOW).toISOString(),
  data: { keyId: 'synthetic-external', fromMe: true, status: 'DELIVERY_ACK' } });
const connection = () => ({ event: 'connection.update', instance: 'synthetic-wa120', date_time: new Date(NOW).toISOString(), data: { state: 'open' } });
const response = (body = RECEIPT, code = 202) => new Response(JSON.stringify(body), { status: code, headers: { 'content-type': 'application/json' } });
async function laboratory(t, options = {}) {
  const calls = [], logs = [];
  const config = validateConfig(options.raw ?? rawConfig());
  const server = createLiveIngressServer({ config, now: options.now ?? (() => NOW), log: item => logs.push(item),
    fetchImpl: options.fetchImpl ?? (async (url, input) => { calls.push({ url, input }); return response(); }) });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => server.shutdown());
  return { server, calls, logs, config, port: server.address().port };
}
function request(port, value, { headers = {}, method = 'POST', path = '/evolution/webhook', bytes, auth = true } = {}) {
  const payload = bytes ?? Buffer.from(JSON.stringify(value));
  return new Promise((resolve, reject) => {
    const req = httpRequest({ hostname: '127.0.0.1', port, method, path, agent: false,
      headers: { 'content-type': 'application/json', ...(auth ? { Authorization: `Bearer ${WEBHOOK_TOKEN}` } : {}), ...headers } }, res => {
      const chunks = []; res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(chunks).toString('utf8'), headers: res.headers }));
    });
    req.on('error', reject); req.end(payload);
  });
}
function rawHttp(port, headers, payload = '') {
  return new Promise((resolve, reject) => {
    const socket = connect(port, '127.0.0.1'); let result = '';
    socket.on('error', reject);
    socket.on('data', part => { result += part.toString('utf8'); });
    socket.on('end', () => resolve(Number(/^HTTP\/1\.1 ([0-9]{3})/.exec(result)?.[1])));
    socket.on('connect', () => socket.write(`POST /evolution/webhook HTTP/1.1\r\nHost: localhost\r\n${headers}\r\n\r\n${payload}`));
    socket.setTimeout(2000, () => { socket.destroy(); reject(new Error('TEST_SOCKET_TIMEOUT')); });
  });
}
async function until(predicate) {
  for (let count = 0; count < 200; count++) {
    if (predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  assert.fail('TEST_CONDITION_TIMEOUT');
}

test('strict JSON rejects duplicate decoded keys, malformed UTF-8, lone surrogates and depth overflow', () => {
  for (const value of ['{"a":1,"a":2}', '{"nested":{"id":1,"\\u0069d":2}}', '{"__proto__":1,"__proto__":2}',
    '{"a":"\\ud800"}', '{"a":01}', '{"a":1,}', '[1,]', 'null trailing', '\ufeff{}', '['.repeat(34) + '0' + ']'.repeat(34)])
    assert.throws(() => strictJson(Buffer.from(value)));
  assert.throws(() => strictJson(Buffer.from([0x7b, 0x22, 0xc0, 0xaf, 0x22, 0x3a, 0x31, 0x7d])));
  const parsed = strictJson(Buffer.from('{"__proto__":{"safe":true},"text":"Olá 🚐","n":-1.25e2}'));
  assert.equal(Object.getPrototypeOf(parsed), null);
  assert.equal(parsed.text, 'Olá 🚐'); assert.equal(parsed.n, -125);
});

test('configuration is bounded, private, copied and immutable, with explicit aliases only', t => {
  const raw = rawConfig(), config = validateConfig(raw);
  raw.contacts[0].pn = '999@s.whatsapp.net'; raw.channelToken = SECRET;
  assert.equal(config.contacts[0].pn, '10000000001@s.whatsapp.net'); assert.equal(config.channelToken, TOKEN);
  assert.ok(Object.isFrozen(config.contacts[0]));
  assert.equal(JSON.stringify(config).includes(TOKEN), false); assert.equal(JSON.stringify(config).includes(WEBHOOK_TOKEN), false);
  assert.equal(JSON.stringify(config).includes('@lid'), false);
  const cases = [value => { value.endsAt = '2026-11-01T12:30:01Z'; }, value => { value.endsAt = value.startsAt; },
    value => { value.contacts.push({ label: 'C', pn: '3@s.whatsapp.net' }); }, value => { value.contacts[1].pn = value.contacts[0].pn; },
    value => { value.contacts[0].lid = null; }, value => { value.contacts[0].pn = 'x@g.us'; },
    value => { value.channelToken = 'x'.repeat(43); }, value => { value.webhookToken = value.channelToken; },
    value => { value.webhookToken = 'invalid'; }, value => { value.extra = true; }, value => { value.listenHost = 'example.test'; }];
  for (const apiOrigin of ['https://public.example.test', 'http://2130706433:8080', 'http://127.1:8080', 'http://wa120-api:8080/events',
    'http://wa120-api:8080#x', 'http://user:pass@wa120-api:8080', 'http://192.168.001.1:8080', 'http://8.8.8.8:8080'])
    cases.push(value => { value.apiOrigin = apiOrigin; });
  for (const mutate of cases) { const value = rawConfig(); mutate(value); assert.throws(() => validateConfig(value), /^Error: CONFIG_INVALID$/); }
  const directory = mkdtempSync(join(tmpdir(), 'wa120-ingress-test-')); t.after(() => rmSync(directory, { recursive: true, force: true }));
  const file = join(directory, 'synthetic-private.json');
  writeFileSync(file, JSON.stringify(rawConfig()), { mode: 0o600 });
  assert.equal(loadConfig(file).instance, 'synthetic-wa120');
  chmodSync(file, 0o644); assert.throws(() => loadConfig(file), /CONFIG_INVALID/); chmodSync(file, 0o600);
  const link = join(directory, 'symlink.json'); symlinkSync(file, link); assert.throws(() => loadConfig(link), /CONFIG_INVALID/);
  writeFileSync(file, JSON.stringify(rawConfig()).replace('"instance":', '"instance":"duplicate","instance":'));
  assert.throws(() => loadConfig(file), /CONFIG_INVALID/); assert.throws(() => loadConfig('relative.json'), /CONFIG_INVALID/);
});

test('calendar-valid timestamps preserve offsets and reject impossible dates and missing offsets', () => {
  assert.equal(timestamp('2026-11-01T09:05:00-03:00'), NOW);
  assert.equal(timestamp('2026-11-01T12:05:00.123456789Z'), NOW + 123);
  for (const time of ['2026-02-30T12:00:00Z', '2026-11-01T24:00:00Z', '2026-11-01T12:00:60Z',
    '2026-11-01T12:00:00', '2026-11-01T12:00:00+18:01', '2026-11-01T12:00:00+03:60']) assert.equal(timestamp(time), null);
});

test('only configured PN/LID pairs and human direct plain text can reach the upstream', async t => {
  const lab = await laboratory(t);
  const cases = [value => { value.instance = 'other-instance'; }, value => { value.data.key.fromMe = true; },
    value => { value.data.key.fromMe = 'false'; }, value => { delete value.data.key.remoteJid; },
    value => { value.data.key.remoteJidAlt = '20000000002@lid'; },
    value => { value.data.key.remoteJid = '20000000002@lid'; },
    value => { value.data.key.remoteJidAlt = '10000000002@s.whatsapp.net'; },
    value => { value.data.messageType = 'imageMessage'; }, value => { value.data.message = {}; },
    value => { value.data.message.conversation = ' '.repeat(4); }, value => { value.data.message.conversation = 'x'.repeat(2001); },
    value => { value.data.message.conversation = 'x\0y'; }, value => { value.date_time = '2026-11-01T11:59:59Z'; },
    value => { value.date_time = '2026-11-01T12:05:31Z'; }, value => { value.data.messageTimestamp = NOW / 1000 - 3600; },
    value => { value.data.messageTimestamp = String(NOW / 1000); }, value => { value.data.key.id = 'id with spaces'; },
    value => { value.data.message.extendedTextMessage = { text: 'same' }; },
    value => { value.data.message = { ephemeralMessage: { message: { conversation: 'wrapped' } } }; }];
  for (const alias of ['bad', '', null, [], {}, '9@g.us', 'status@broadcast', '9@lid:device', 'unknown@lid', '99999@lid'])
    cases.push(value => { value.data.key.remoteJidAlt = alias; });
  for (const alias of ['9@g.us', 'status@broadcast', '99999@s.whatsapp.net', null]) cases.push(value => { value.data.key.remoteJid = alias; });
  for (const field of ['imageMessage', 'audioMessage', 'videoMessage', 'documentMessage', 'stickerMessage', 'locationMessage',
    'liveLocationMessage', 'protocolMessage', 'editedMessage', 'contactMessage', 'contactsArrayMessage', 'reactionMessage',
    'pollCreationMessage', 'buttonsMessage', 'templateMessage', 'listMessage', 'messageContextInfo'])
    cases.push(value => { value.data.message[field] = { nested: SECRET }; });
  for (const event of ['messages.edited', 'send.message', 'send.message.update', 'unknown']) cases.push(value => { value.event = event; });
  for (const mutate of cases) { const event = human(); mutate(event); assert.equal((await request(lab.port, event)).status, 204); }
  assert.equal(lab.calls.length, 0);
  assert.equal(lab.logs.some(value => JSON.stringify(value).includes(SECRET)), false);
});

test('whitelist rebuild removes secrets, profiles, citation and routing fields, including extended text context', async t => {
  const lab = await laboratory(t);
  const event = human();
  for (const field of ['apikey', 'sender', 'destination', 'server_url', 'profile', 'extra']) event[field] = SECRET;
  event.data.pushName = SECRET; event.data.contextInfo = { stanzaId: SECRET, quotedMessage: { conversation: SECRET } };
  event.data.key.participant = SECRET;
  assert.equal((await request(lab.port, event)).status, 202);
  const forwarded = JSON.parse(lab.calls[0].input.body);
  assert.deepEqual(forwarded, { event: 'messages.upsert', instance: 'synthetic-wa120', date_time: event.date_time,
    data: { key: { id: 'synthetic-message', fromMe: false, remoteJid: event.data.key.remoteJid, remoteJidAlt: event.data.key.remoteJidAlt },
      message: { conversation: event.data.message.conversation }, messageTimestamp: NOW / 1000 } });
  const extended = human(); extended.data.messageType = 'extendedTextMessage';
  extended.data.message = { extendedTextMessage: { text: 'Texto simples', contextInfo: { stanzaId: SECRET, quotedMessage: { conversation: SECRET } } } };
  delete extended.data.key.remoteJidAlt; extended.data.key.remoteJid = '20000000001@lid';
  assert.equal((await request(lab.port, extended)).status, 202);
  assert.deepEqual(JSON.parse(lab.calls[1].input.body).data.message, { conversation: 'Texto simples' });
  for (const call of lab.calls) {
    assert.equal(call.url, 'http://wa120-api:8080/integrations/whatsapp/v1/events');
    assert.equal(call.input.redirect, 'manual'); assert.equal(call.input.method, 'POST');
    assert.deepEqual(Object.keys(call.input.headers).sort(), ['Authorization', 'content-type']);
    assert.equal(call.input.headers.Authorization, `Bearer ${TOKEN}`);
    assert.equal(JSON.stringify(call.input).includes(WEBHOOK_TOKEN), false);
    assert.equal(call.input.body.includes(SECRET), false);
  }
  const transcript = JSON.stringify(lab.logs);
  for (const secret of [SECRET, TOKEN, WEBHOOK_TOKEN, '10000000001', '20000000001', 'Texto simples']) assert.equal(transcript.includes(secret), false);
});

test('status and connection callbacks contain no identities or recipients and retain original connection timestamp', async t => {
  const lab = await laboratory(t);
  for (const state of ['SERVER_ACK', 'DELIVERY_ACK', 'READ', 'PLAYED', 'ERROR']) {
    const event = status(); event.data.status = state; event.data.remoteJid = SECRET; event.data.sender = SECRET;
    event.data.key = { remoteJid: SECRET }; event.apikey = SECRET;
    assert.equal((await request(lab.port, event)).status, 202);
    assert.deepEqual(JSON.parse(lab.calls.at(-1).input.body).data, { keyId: 'synthetic-external', fromMe: true, status: state });
  }
  for (const state of ['open', 'connecting', 'close', 'refused']) {
    const event = connection(); event.data.state = state; event.date_time = '2026-11-01T09:05:00-03:00'; event.data.extra = SECRET;
    assert.equal((await request(lab.port, event)).status, 202);
    assert.deepEqual(JSON.parse(lab.calls.at(-1).input.body), { event: 'connection.update', instance: event.instance,
      date_time: '2026-11-01T09:05:00-03:00', data: { state } });
  }
  const previous = lab.calls.length;
  for (const mutate of [value => { value.data.fromMe = false; }, value => { value.data.fromMe = 'true'; },
    value => { value.data.status = 3; }, value => { value.data.status = 'DELIVERED'; }, value => { delete value.data.keyId; }]) {
    const event = status(); mutate(event); assert.equal((await request(lab.port, event)).status, 204);
  }
  for (const date of ['invalid', '2026-02-30T12:05:00Z', '2026-11-01T12:05:30.001Z', '2026-11-01T11:59:59Z']) {
    const event = connection(); event.date_time = date; assert.equal((await request(lab.port, event)).status, 204);
  }
  const event = connection(); event.date_time = '2026-11-01T12:05:30Z';
  assert.equal((await request(lab.port, event)).status, 202);
  assert.equal(lab.calls.length, previous + 1);
  assert.equal(JSON.stringify(lab.calls.map(call => call.input.body)).includes(SECRET), false);
});

test('HTTP authenticates before body; duplicate headers, Origin and compression are rejected without POST', async t => {
  const lab = await laboratory(t);
  assert.equal((await request(lab.port, human(), { headers: { Authorization: `Bearer ${TOKEN}` } })).status, 401);
  assert.equal(await rawHttp(lab.port, 'Content-Type: application/json\r\nContent-Length: 999999'), 401);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer wrong\r\nContent-Type: application/json\r\nContent-Length: 999999`), 401);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${WEBHOOK_TOKEN}\r\nauthorization: Bearer ${WEBHOOK_TOKEN}\r\nContent-Length: 999999`), 401);
  const base = `Authorization: Bearer ${WEBHOOK_TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 100`;
  for (const extra of ['Origin: https://example.test', 'Content-Encoding: identity', 'Content-Encoding: gzip',
    'Content-Type: application/json', 'X-Duplicate: A\r\nx-duplicate: B', 'Expect: 100-continue'])
    assert.equal(await rawHttp(lab.port, base + '\r\n' + extra), 400);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${WEBHOOK_TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 32769`), 413);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${WEBHOOK_TOKEN}\r\nX-Padding: ${'x'.repeat(4096)}`), 413);
  assert.equal(lab.calls.length, 0);
});

test('JSON and body limits reject duplicate keys, invalid UTF-8, oversize streaming and unknown routes', async t => {
  const lab = await laboratory(t);
  for (const bytes of [Buffer.from('{"event":"messages.upsert","event":"messages.update"}'),
    Buffer.from('{"data":{"key":{"remoteJid":"1@lid","remote\\u004aid":"2@lid"}}}'),
    Buffer.from([0xc0, 0xaf]), Buffer.from('{} trailing')])
    assert.equal((await request(lab.port, null, { bytes })).status, 400);
  assert.equal((await request(lab.port, null, { bytes: Buffer.alloc(32769, 32), headers: { 'transfer-encoding': 'chunked' } })).status, 413);
  assert.equal((await request(lab.port, human(), { path: '/wrong' })).status, 400);
  assert.equal((await request(lab.port, human(), { method: 'PUT' })).status, 400);
  assert.equal((await request(lab.port, human(), { headers: { 'content-type': 'text/plain' } })).status, 400);
  assert.equal(lab.calls.length, 0);
});

test('incomplete bodies occupy only two readers; abort before a complete body produces zero POST', async t => {
  const lab = await laboratory(t);
  let requests = 0; lab.server.on('request', () => { requests++; });
  const sockets = [];
  for (let index = 0; index < 2; index++) {
    const socket = connect(lab.port, '127.0.0.1'); sockets.push(socket);
    socket.on('error', () => {}); await once(socket, 'connect');
    socket.write(`POST /evolution/webhook HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ${WEBHOOK_TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{`);
  }
  await until(() => requests === 2);
  assert.equal((await request(lab.port, human())).status, 503);
  assert.equal((await request(lab.port, human(), { auth: false })).status, 401);
  for (const socket of sockets) socket.destroy();
  await until(() => lab.server.snapshot().invalid >= 2);
  assert.equal(lab.calls.length, 0);
});

test('window is closed at both boundaries and can never be extended by changing source configuration', async t => {
  let clock = NOW;
  const raw = rawConfig(); const lab = await laboratory(t, { raw, now: () => clock });
  raw.endsAt = '2026-11-02T12:00:00Z';
  for (const time of [lab.config.start - 1, lab.config.end, lab.config.end + 1]) {
    clock = time; assert.equal((await request(lab.port, human())).status, 204);
  }
  assert.equal(lab.calls.length, 0);
  clock = lab.config.start;
  const event = human(); event.date_time = lab.config.startsAt; event.data.messageTimestamp = clock / 1000;
  assert.equal((await request(lab.port, event)).status, 202);
});

test('quota is at most eight human attempts per contact, sixteen total, sixty-four status and sixteen connection', async t => {
  const lab = await laboratory(t);
  for (const contact of ['A', 'B']) {
    for (let index = 0; index < 8; index++) {
      const event = human(); event.data.key.id += contact + index;
      if (contact === 'B') { event.data.key.remoteJid = '10000000002@s.whatsapp.net'; event.data.key.remoteJidAlt = '20000000002@lid'; }
      assert.equal((await request(lab.port, event)).status, 202);
    }
    const event = human(); event.data.key.remoteJid = contact === 'A' ? '10000000001@s.whatsapp.net' : '10000000002@s.whatsapp.net';
    delete event.data.key.remoteJidAlt; assert.equal((await request(lab.port, event)).status, 204);
  }
  for (let index = 0; index < 64; index++) assert.equal((await request(lab.port, status())).status, 202);
  assert.equal((await request(lab.port, status())).status, 204);
  for (let index = 0; index < 16; index++) assert.equal((await request(lab.port, connection())).status, 202);
  assert.equal((await request(lab.port, connection())).status, 204);
  assert.equal(lab.calls.length, 96);
  const counts = lab.server.snapshot();
  assert.equal(counts.human, 16); assert.equal(counts.contactA, 8); assert.equal(counts.contactB, 8);
  assert.equal(counts.status, 64); assert.equal(counts.connection, 16);
});

test('one human and one callback lane; busy has no POST and client abort cannot open a late continuation', async t => {
  const pending = [], signals = [];
  const lab = await laboratory(t, { fetchImpl: async (_url, input) => {
    signals.push(input.signal); return new Promise(resolve => pending.push(resolve));
  } });
  const first = request(lab.port, human()); await until(() => pending.length === 1);
  const second = request(lab.port, status()); await until(() => pending.length === 2);
  assert.equal((await request(lab.port, human())).status, 503);
  assert.equal((await request(lab.port, connection())).status, 503);
  assert.equal(pending.length, 2);
  pending[0](response()); pending[1](response());
  assert.equal((await first).status, 202); assert.equal((await second).status, 202);
  const controller = new AbortController(); controller.abort(); let calls = 0;
  const noPost = startPost({ config: lab.config, envelope: human(), signal: controller.signal, now: () => NOW,
    fetchImpl: async () => { calls++; return response(); } });
  assert.equal((await noPost.result).ok, false); await noPost.settled; assert.equal(calls, 0);
  assert.equal(signals.length, 2);
});

test('timeout and abort invoke exactly one POST, reject late receipt and keep the actual operation bounded by its lane', async t => {
  const config = validateConfig(rawConfig()); let calls = 0, release, observed;
  const operation = startPost({ config, envelope: human(), now: () => NOW, timeoutMs: 20,
    fetchImpl: async (_url, input) => { calls++; observed = input.signal; return new Promise(resolve => { release = resolve; }); } });
  assert.equal((await operation.result).ok, false); assert.equal(calls, 1); assert.equal(observed.aborted, true);
  release(response()); assert.equal((await operation.settled).ok, false); assert.equal(calls, 1);
  const abort = new AbortController(); let releaseAbort;
  const aborted = startPost({ config, envelope: human(), now: () => NOW, signal: abort.signal,
    fetchImpl: async () => { calls++; return new Promise(resolve => { releaseAbort = resolve; }); } });
  abort.abort(); assert.equal((await aborted.result).ok, false);
  releaseAbort(response()); assert.equal((await aborted.settled).ok, false); assert.equal(calls, 2);
  const labPending = [];
  const lab = await laboratory(t, { fetchImpl: async () => new Promise(resolve => labPending.push(resolve)) });
  const timedOut = request(lab.port, human()); await until(() => labPending.length === 1);
  assert.equal((await timedOut).status, 503); // Production timeout is five seconds.
  assert.equal((await request(lab.port, human())).status, 503); assert.equal(labPending.length, 1);
  labPending[0](response()); await until(() => lab.server.snapshot().unavailable === 1);
  assert.equal(lab.logs.filter(value => value.code === 'FORWARDED').length, 0);
});

test('disconnect after invoking the upstream aborts once, keeps its lane and cannot accept a late receipt', async t => {
  let calls = 0, release, observed;
  const lab = await laboratory(t, { fetchImpl: async (_url, input) => {
    calls++; observed = input.signal; return new Promise(resolve => { release = resolve; });
  } });
  const client = httpRequest({ hostname: '127.0.0.1', port: lab.port, path: '/evolution/webhook', method: 'POST', agent: false,
    headers: { Authorization: `Bearer ${WEBHOOK_TOKEN}`, 'content-type': 'application/json' } });
  client.on('error', () => {}); client.end(JSON.stringify(human()));
  await until(() => calls === 1); client.destroy(); await until(() => observed.aborted);
  assert.equal((await request(lab.port, human())).status, 503); assert.equal(calls, 1);
  release(response()); await until(() => lab.server.snapshot().unavailable === 1);
  assert.equal(lab.logs.filter(value => value.code === 'FORWARDED').length, 0);
});

test('only HTTP202 plus a complete real persisted/duplicate receipt can be acknowledged', async t => {
  const replies = [() => response({ ...RECEIPT, status: 'IGNORED' }), () => response(RECEIPT, 200),
    () => response({ accepted: true }), () => response({ ...RECEIPT, receiptId: 'invalid' }),
    () => response({ ...RECEIPT, extra: SECRET }), () => response({ ...RECEIPT, serverTime: 'invalid' }),
    () => new Response('', { status: 307, headers: { location: 'http://other-api:8080' } }),
    () => new Response('{"receiptId":"a","receiptId":"b"}', { status: 202, headers: { 'content-type': 'application/json' } }),
    () => response({ ...RECEIPT, status: 'DUPLICATE' })];
  let calls = 0;
  const lab = await laboratory(t, { fetchImpl: async () => { calls++; return replies.shift()(); } });
  // Callbacks have their own budget; malformed acknowledgements are never converted to persisted receipts.
  for (let index = 0; index < 8; index++) assert.equal((await request(lab.port, status())).status, 503);
  const duplicate = await request(lab.port, status());
  assert.equal(duplicate.status, 202); assert.equal(JSON.parse(duplicate.body).status, 'DUPLICATE'); assert.equal(calls, 9);
  assert.equal(JSON.stringify(lab.logs).includes(SECRET), false);
});

test('actual loopback HTTP upstream receives exactly one sanitized request and no redirect is followed', async t => {
  const received = [];
  const upstream = createServer((req, res) => {
    const chunks = []; req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
      received.push({ path: req.url, method: req.method, authorization: req.headers.authorization, body: Buffer.concat(chunks).toString('utf8') });
      if (received.length === 1) { res.writeHead(202, { 'content-type': 'application/json' }); res.end(JSON.stringify(RECEIPT)); }
      else { res.writeHead(307, { location: `http://127.0.0.1:${upstream.address().port}/must-never-follow` }); res.end(); }
    });
  });
  upstream.listen(0, '127.0.0.1'); await once(upstream, 'listening');
  t.after(() => { upstream.close(); upstream.closeAllConnections(); });
  const raw = rawConfig(); raw.apiOrigin = `http://127.0.0.1:${upstream.address().port}`;
  const lab = await laboratory(t, { raw, fetchImpl: fetch });
  const event = human(); event.apikey = SECRET;
  assert.equal((await request(lab.port, event)).status, 202);
  assert.equal((await request(lab.port, status())).status, 503);
  assert.equal(received.length, 2); assert.equal(received[0].path, '/integrations/whatsapp/v1/events');
  assert.equal(received[0].authorization, `Bearer ${TOKEN}`); assert.equal(received[0].body.includes(SECRET), false);
});

test('late source message and malformed extended text never create identity candidates', () => {
  const config = validateConfig(rawConfig());
  const noLid = rawConfig(); delete noLid.contacts[0].lid;
  assert.equal(filterEvent(human(), validateConfig(noLid), NOW), null);
  const event = human(); event.data.messageType = 'extendedTextMessage';
  event.data.message = { extendedTextMessage: { text: 'plain', jpegThumbnail: SECRET } };
  assert.equal(filterEvent(event, config, NOW), null);
  event.data.message = { extendedTextMessage: { text: '🚐'.repeat(2000) } };
  assert.ok(filterEvent(event, config, NOW));
});
