import test from 'node:test';
import assert from 'node:assert/strict';
import { request as httpRequest, createServer } from 'node:http';
import { connect } from 'node:net';
import { once } from 'node:events';
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, statSync, existsSync, chmodSync, symlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { strictJson } from './strict-json.mjs';
import { createChallenge, validateChallenge, writeChallenge, readPrivateJson, claimChallenge,
  createBootstrapSession, createBootstrapServer, writePendingCandidate, confirmCandidate } from './bootstrap.mjs';

const NOW = Date.parse('2026-11-01T12:00:00Z');
const TOKEN = Buffer.alloc(32, 11).toString('base64url');
const PN = '10000000001@s.whatsapp.net', LID = '20000000001@lid';
const SECRET = 'SECRET_PROFILE_SENTINEL_NEVER_OUTPUT';
function spec(directory = '/synthetic-private') {
  return { instance: 'synthetic-bootstrap', webhookToken: TOKEN, authorizedPn: PN, listenHost: '127.0.0.1',
    listenPort: 7181, candidateFile: join(directory, 'candidate.json') };
}
const challenge = () => createChallenge(spec(), { now: () => NOW });
function event(config, at = NOW) {
  return { event: 'messages.upsert', instance: config.instance, date_time: new Date(at).toISOString(),
    apikey: SECRET, sender: SECRET, profile: SECRET, data: { pushName: SECRET, contextInfo: { quotedMessage: SECRET },
      messageType: 'conversation', messageTimestamp: Math.floor(at / 1000),
      key: { id: 'synthetic-message', fromMe: false, remoteJid: LID }, message: { conversation: config.nonce } } };
}
function privateDirectory(t) {
  const directory = mkdtempSync(join(tmpdir(), 'wa120-bootstrap-test-'));
  t.after(() => rmSync(directory, { recursive: true, force: true })); return directory;
}
async function until(predicate) {
  for (let count = 0; count < 400; count++) { if (predicate()) return; await new Promise(resolve => setTimeout(resolve, 5)); }
  assert.fail('TEST_CONDITION_TIMEOUT');
}
function request(port, value, { headers = {}, bytes, auth = TOKEN } = {}) {
  return new Promise((resolve, reject) => {
    const req = httpRequest({ hostname: '127.0.0.1', port, path: '/evolution/webhook', method: 'POST', agent: false,
      headers: { Authorization: `Bearer ${auth}`, 'content-type': 'application/json', ...headers } }, res => {
      const chunks = []; res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('error', reject); req.end(bytes ?? JSON.stringify(value));
  });
}
function rawHttp(port, headers, payload = '') {
  return new Promise((resolve, reject) => {
    const socket = connect(port, '127.0.0.1'); let output = '';
    socket.on('error', reject); socket.on('data', value => { output += value.toString('utf8'); });
    socket.on('end', () => resolve(Number(/^HTTP\/1\.1 ([0-9]{3})/.exec(output)?.[1])));
    socket.on('connect', () => socket.write(`POST /evolution/webhook HTTP/1.1\r\nHost: localhost\r\n${headers}\r\n\r\n${payload}`));
    socket.setTimeout(2000, () => { socket.destroy(); reject(new Error('TEST_SOCKET_TIMEOUT')); });
  });
}
async function laboratory(t, config = challenge(), options = {}) {
  const logs = [], server = createBootstrapServer({ config, now: options.now ?? (() => NOW), log: value => logs.push(value) });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); t.after(() => server.shutdown());
  return { server, config, logs, port: server.address().port };
}

test('challenge uses 256 random bits, a fixed private PN and a bounded TTL; sensitive fields do not serialize', () => {
  const first = challenge(), second = challenge();
  assert.equal(Buffer.from(first.nonce, 'base64url').length, 32); assert.notEqual(first.nonce, second.nonce);
  assert.notEqual(first.challengeId, second.challengeId); assert.ok(Object.isFrozen(first));
  assert.equal(first.expires - first.issued, 300_000); assert.equal(first.authorizedPn, PN);
  for (const secret of [PN, TOKEN, first.nonce]) assert.equal(JSON.stringify(first).includes(secret), false);
  for (const ttlMs of [0, 300001, 1.5]) assert.throws(() => createChallenge(spec(), { ttlMs, now: () => NOW }), /BOOTSTRAP_INVALID/);
  const extra = spec(); extra.apiOrigin = 'http://backend:8080'; assert.throws(() => createChallenge(extra), /BOOTSTRAP_INVALID/);
  const bad = spec(); bad.authorizedPn = 'lid'; assert.throws(() => createChallenge(bad), /BOOTSTRAP_INVALID/);
});

test('one LID-only nonce creates only a candidate after close; confirmation requires an explicit matching human marker', () => {
  const config = challenge(), session = createBootstrapSession(config, { now: () => NOW });
  assert.deepEqual(session.snapshot(), { state: 'WAITING' });
  assert.equal(session.receive(event(config)), 'PENDING'); assert.deepEqual(session.snapshot(), { state: 'PENDING' });
  const candidate = session.close();
  assert.equal(candidate.state, 'PENDING_HUMAN_CONFIRMATION'); assert.equal(candidate.nonceInvalidated, true);
  assert.equal(candidate.authorizedPn, PN); assert.equal(candidate.candidateLid, LID);
  for (const secret of [TOKEN, config.nonce, SECRET, 'synthetic-message']) assert.equal(JSON.stringify(candidate).includes(secret), false);
  assert.equal(session.receive(event(config)), 'DROP'); assert.equal(session.close(), candidate);
  assert.throws(() => confirmCandidate(candidate), /HUMAN_CONFIRMATION_REQUIRED/);
  const marker = { stage: 'OPERATOR_CONFIRMED_AUTHORIZED_CONTACT', challengeId: candidate.challengeId,
    authorizedPn: PN, candidateLid: LID, confirmedAt: new Date(NOW).toISOString() };
  for (const mutate of [value => { value.stage = 'AUTO'; }, value => { value.challengeId = 'wrong'; },
    value => { value.authorizedPn = '999@s.whatsapp.net'; }, value => { value.candidateLid = '999@lid'; },
    value => { value.confirmedAt = new Date(NOW - 1).toISOString(); }, value => { value.confirmedAt = new Date(NOW + 30001).toISOString(); },
    value => { value.inferredFromWebhook = true; }]) {
    const bad = structuredClone(marker); mutate(bad); assert.throws(() => confirmCandidate(candidate, bad, { now: () => NOW }), /HUMAN_CONFIRMATION_REQUIRED/);
  }
  const confirmed = confirmCandidate(candidate, marker, { now: () => NOW });
  assert.equal(confirmed.state, 'OPERATOR_CONFIRMED'); assert.equal(candidate.state, 'PENDING_HUMAN_CONFIRMATION');
  assert.throws(() => writePendingCandidate('/never-write', confirmed), /BOOTSTRAP_INVALID/);
});

test('an alleged PN can only equal the independent preregistered PN, regardless of primary/alternate order', () => {
  for (const reverse of [false, true]) {
    const config = challenge(), session = createBootstrapSession(config, { now: () => NOW }), value = event(config);
    value.data.key.remoteJid = reverse ? PN : LID; value.data.key.remoteJidAlt = reverse ? LID : PN;
    assert.equal(session.receive(value), 'PENDING'); assert.equal(session.close().authorizedPn, PN);
  }
  for (const alias of ['999@s.whatsapp.net', null, '', 'lid', '999@g.us', '999@lid', LID]) {
    const config = challenge(), session = createBootstrapSession(config, { now: () => NOW }), value = event(config);
    value.data.key.remoteJidAlt = alias;
    assert.equal(session.receive(value), 'FAILED'); assert.equal(session.close(), null);
  }
});

test('official addressingMode metadata accepts only lid/pn, cannot authorize another PN and is omitted from candidates', () => {
  for (const addressingMode of ['lid', 'pn']) {
    const config = challenge(), session = createBootstrapSession(config, { now: () => NOW }), value = event(config);
    value.data.key.addressingMode = addressingMode;
    assert.equal(session.receive(value), 'PENDING'); assert.equal(JSON.stringify(session.close()).includes('addressingMode'), false);
    const mismatch = createBootstrapSession(config, { now: () => NOW });
    value.data.key.remoteJidAlt = '10000000002@s.whatsapp.net'; assert.equal(mismatch.receive(value), 'FAILED');
    assert.equal(mismatch.close(), null);
  }
  for (const addressingMode of [null, true, 1, {}, 'LID', 'unknown']) {
    const config = challenge(), session = createBootstrapSession(config, { now: () => NOW }), value = event(config);
    value.data.key.addressingMode = addressingMode; assert.equal(session.receive(value), 'FAILED'); assert.equal(session.close(), null);
  }
});

test('nonce-bearing groups, echoes, media, edits, extra aliases and escaped/prototype keys burn the challenge', () => {
  const cases = [value => { value.data.key.remoteJid = '1@g.us'; }, value => { value.data.key.remoteJid = 'status@broadcast'; },
    value => { value.data.key.fromMe = true; }, value => { value.event = 'messages.edited'; },
    value => { value.data.key.participant = PN; }, value => { value.data.senderPn = PN; },
    value => { value.data.aliases = [PN, LID]; }, value => { value.aliases = [PN, LID]; },
    value => { value.data.key.remoteJid = PN; }, value => { value.data.messageType = 'imageMessage'; },
    value => { value.data.messageTimestamp = 'invalid'; }, value => { value.date_time = 'invalid'; },
    value => { value.data.key.id = 'invalid id'; }, value => { value.data.key.fromMe = 'false'; },
    value => { value.data.message.extendedTextMessage = { text: value.data.message.conversation }; },
    value => { value.data.key = Object.create(value.data.key); }];
  for (const field of ['imageMessage', 'videoMessage', 'audioMessage', 'locationMessage', 'liveLocationMessage', 'protocolMessage', 'editedMessage'])
    cases.push(value => { value.data.message[field] = { secret: SECRET }; });
  for (const mutate of cases) {
    const config = challenge(), session = createBootstrapSession(config, { now: () => NOW }), value = event(config); mutate(value);
    assert.equal(session.receive(value), 'FAILED'); assert.equal(session.close(), null);
  }
  const config = challenge(), session = createBootstrapSession(config, { now: () => NOW });
  const value = strictJson(Buffer.from(JSON.stringify(event(config)).replace('"fromMe":false', '"fromMe":false,"__proto__":{"fromMe":false}')));
  assert.equal(session.receive(value), 'FAILED'); assert.equal(session.close(), null);
  const safe = challenge(), plain = createBootstrapSession(safe, { now: () => NOW }), extended = event(safe);
  extended.data.messageType = 'extendedTextMessage';
  extended.data.message = { extendedTextMessage: { text: safe.nonce, contextInfo: { quotedMessage: SECRET } } };
  assert.equal(plain.receive(extended), 'PENDING'); assert.equal(JSON.stringify(plain.close()).includes(SECRET), false);
});

test('wrong nonce, whitespace, stale instance and expiry never produce a candidate; replay does not reuse the challenge', () => {
  let clock = NOW; const config = challenge(), session = createBootstrapSession(config, { now: () => clock });
  for (const nonce of ['wrong', config.nonce + ' ', '\n' + config.nonce]) {
    const value = event(config); value.data.message.conversation = nonce; assert.equal(session.receive(value), 'DROP');
  }
  const other = event(config); other.instance = 'other-instance'; assert.equal(session.receive(other), 'DROP');
  clock = config.expires; assert.equal(session.receive(event(config)), 'DROP'); assert.equal(session.close(), null);
  const fresh = challenge(), twice = createBootstrapSession(fresh, { now: () => NOW });
  assert.equal(twice.receive(event(fresh)), 'PENDING'); assert.equal(twice.receive(event(fresh)), 'REPLAY');
  const repeated = event(fresh); repeated.data.key.id = 'new-id-same-lid'; assert.equal(twice.receive(repeated), 'REPLAY');
  assert.equal(twice.close().candidateLid, LID);
  const collision = createBootstrapSession(fresh, { now: () => NOW });
  assert.equal(collision.receive(event(fresh)), 'PENDING'); const different = event(fresh); different.data.key.remoteJid = '20000000002@lid';
  assert.equal(collision.receive(different), 'FAILED'); assert.equal(collision.receive(event(fresh)), 'DROP'); assert.equal(collision.close(), null);
});

test('private writer is 0600/exclusive, duplicate-key safe, rejects symlinks and claims a challenge only once', t => {
  const directory = privateDirectory(t), file = join(directory, 'challenge.json');
  const config = createChallenge(spec(directory), { now: () => NOW }); writeChallenge(file, config);
  assert.equal(statSync(file).mode & 0o777, 0o600); assert.equal(readPrivateJson(file).nonce, config.nonce);
  assert.throws(() => writeChallenge(file, config), /PRIVATE_WRITE_FAILED/);
  const claimed = claimChallenge(file, { now: () => NOW }); assert.equal(claimed.challengeId, config.challengeId);
  assert.equal(statSync(file + '.claimed').mode & 0o777, 0o600);
  for (const secret of [TOKEN, config.nonce, PN]) assert.equal(readFileSync(file + '.claimed', 'utf8').includes(secret), false);
  assert.throws(() => claimChallenge(file, { now: () => NOW }), /PRIVATE_WRITE_FAILED/);
  const session = createBootstrapSession(config, { now: () => NOW }); session.receive(event(config));
  const candidate = session.close(); writePendingCandidate(config.candidateFile, candidate);
  assert.equal(statSync(config.candidateFile).mode & 0o777, 0o600);
  assert.throws(() => writePendingCandidate(config.candidateFile, candidate), /PRIVATE_WRITE_FAILED/);
  assert.equal(readPrivateJson(config.candidateFile).state, 'PENDING_HUMAN_CONFIRMATION');
  const symlink = join(directory, 'symlink.json'); symlinkSync(file, symlink);
  assert.throws(() => readPrivateJson(symlink), /PRIVATE_INPUT_INVALID/); assert.throws(() => writePendingCandidate(symlink, candidate), /PRIVATE_WRITE_FAILED/);
  chmodSync(file, 0o644); assert.throws(() => readPrivateJson(file), /PRIVATE_INPUT_INVALID/); chmodSync(file, 0o600);
  writeFileSync(file, '{"nonce":"a","\\u006eonce":"b"}'); assert.throws(() => readPrivateJson(file), /PRIVATE_INPUT_INVALID/);
  assert.throws(() => readPrivateJson('relative.json'), /PRIVATE_INPUT_INVALID/);
  const raw = { ...readPrivateJson(config.candidateFile), extra: SECRET }; assert.throws(() => writePendingCandidate(join(directory, 'extra.json'), raw));
});

test('inbound HTTP rejects auth/header/body/JSON violations and silently drops unrelated text with zero outbound calls', async t => {
  let outbound = 0; t.mock.method(globalThis, 'fetch', () => { outbound++; throw new Error('FORBIDDEN_OUTBOUND'); });
  const lab = await laboratory(t), original = event(lab.config);
  assert.equal(await rawHttp(lab.port, 'Authorization: Bearer wrong\r\nContent-Length: 999999'), 401);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${TOKEN}\r\nauthorization: Bearer ${TOKEN}\r\nContent-Length: 999999`), 401);
  const headers = `Authorization: Bearer ${TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 100`;
  for (const extra of ['Origin: https://example.test', 'Content-Encoding: identity', 'Content-Type: application/json',
    'X-Duplicate: a\r\nx-duplicate: b', 'Expect: 100-continue']) assert.equal(await rawHttp(lab.port, headers + '\r\n' + extra), 400);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 32769`), 413);
  assert.equal(await rawHttp(lab.port, `Authorization: Bearer ${TOKEN}\r\nX-Padding: ${'x'.repeat(4096)}`), 413);
  for (const bytes of [Buffer.from('{"data":{"key":{"remoteJid":"1@lid","remote\\u004aid":"2@lid"}}}'), Buffer.from([0xc0, 0xaf]), Buffer.from('{}x')])
    assert.equal((await request(lab.port, null, { bytes })).status, 400);
  assert.equal((await request(lab.port, null, { bytes: Buffer.alloc(32769, 32), headers: { 'transfer-encoding': 'chunked' } })).status, 413);
  original.data.message.conversation = SECRET;
  const result = await request(lab.port, original); assert.deepEqual(result, { status: 204, body: '' });
  assert.equal(lab.logs.length, 0); assert.equal(outbound, 0); assert.equal(lab.server.shutdown(), null);
});

test('concurrent identical nonces produce one pending candidate; concurrent different LIDs burn it with sanitized output', async t => {
  let outbound = 0; t.mock.method(globalThis, 'fetch', () => { outbound++; throw new Error('FORBIDDEN_OUTBOUND'); });
  const identical = await laboratory(t);
  const same = await Promise.all([request(identical.port, event(identical.config)), request(identical.port, event(identical.config))]);
  assert.ok(same.every(value => value.status === 204 && value.body === ''));
  assert.equal(identical.logs.filter(value => value.code === 'CANDIDATE_PENDING').length, 1);
  assert.equal(identical.server.shutdown().state, 'PENDING_HUMAN_CONFIRMATION');
  const conflict = await laboratory(t), different = event(conflict.config); different.data.key.remoteJid = '20000000002@lid';
  const results = await Promise.all([request(conflict.port, event(conflict.config)), request(conflict.port, different)]);
  assert.ok(results.every(value => value.status === 204 && value.body === ''));
  assert.equal(conflict.server.snapshot().state, 'FAILED'); assert.equal(conflict.server.shutdown(), null);
  for (const secret of [TOKEN, PN, LID, SECRET, conflict.config.nonce, 'synthetic-message']) assert.equal(JSON.stringify(conflict.logs).includes(secret), false);
  assert.equal(outbound, 0);
});

test('two incomplete bodies are bounded; busy and abort cannot generate a candidate', async t => {
  const lab = await laboratory(t), sockets = []; let requests = 0;
  lab.server.on('request', () => { requests++; });
  for (let index = 0; index < 2; index++) {
    const socket = connect(lab.port, '127.0.0.1'); sockets.push(socket); socket.on('error', () => {}); await once(socket, 'connect');
    socket.write(`POST /evolution/webhook HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ${TOKEN}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{`);
  }
  await until(() => requests === 2); assert.equal((await request(lab.port, event(lab.config))).status, 503);
  for (const socket of sockets) socket.destroy();
  assert.equal(lab.server.shutdown(), null); assert.equal(lab.logs.length, 0);
});

async function unusedPort() {
  const server = createServer(); server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const port = server.address().port; await new Promise(resolve => server.close(resolve)); return port;
}
function child(command, file, specFile) {
  const process = spawn(globalThis.process.execPath, ['--max-old-space-size=256', fileURLToPath(new URL('./bootstrap-main.mjs', import.meta.url)), command],
    { env: { WA120_BOOTSTRAP_CHALLENGE_FILE: file, ...(specFile ? { WA120_BOOTSTRAP_SPEC_FILE: specFile } : {}) }, stdio: ['ignore', 'pipe', 'pipe'] });
  const state = { process, output: '', error: '', code: null };
  process.stdout.on('data', chunk => { state.output += chunk.toString('utf8'); });
  process.stderr.on('data', chunk => { state.error += chunk.toString('utf8'); });
  state.done = once(process, 'exit').then(([code]) => { state.code = code; }); return state;
}

test('CLI prepare/collect prints only codes, writes pending only after stop, and refuses restart or automatic confirmation', async t => {
  const directory = privateDirectory(t), file = join(directory, 'challenge.json'), specFile = join(directory, 'spec.json');
  const raw = spec(directory); raw.listenPort = await unusedPort(); writeFileSync(specFile, JSON.stringify(raw), { mode: 0o600 });
  const prepare = child('prepare', file, specFile); await prepare.done;
  assert.equal(prepare.code, 0); assert.equal(prepare.output, '{"code":"CHALLENGE_CREATED"}\n'); assert.equal(prepare.error, '');
  const config = validateChallenge(readPrivateJson(file));
  const collect = child('collect', file); t.after(() => { if (collect.process.exitCode === null) collect.process.kill('SIGTERM'); });
  await until(() => collect.output.includes('COLLECTOR_READY'));
  assert.equal((await request(raw.listenPort, event(config, Date.now()))).status, 204);
  await until(() => collect.output.includes('CANDIDATE_PENDING')); assert.equal(existsSync(config.candidateFile), false);
  collect.process.kill('SIGTERM'); await collect.done; assert.equal(collect.code, 0);
  const candidate = readPrivateJson(config.candidateFile);
  assert.equal(candidate.state, 'PENDING_HUMAN_CONFIRMATION'); assert.equal(statSync(config.candidateFile).mode & 0o777, 0o600);
  const retry = child('collect', file); await retry.done; assert.equal(retry.code, 1); assert.equal(retry.error, '{"code":"BOOTSTRAP_INVALID"}\n');
  const auto = child('confirm', file); await auto.done; assert.equal(auto.code, 1);
  const transcript = prepare.output + prepare.error + collect.output + collect.error + retry.output + retry.error + auto.output + auto.error;
  for (const secret of [PN, LID, TOKEN, SECRET, config.nonce, directory]) assert.equal(transcript.includes(secret), false);
});
