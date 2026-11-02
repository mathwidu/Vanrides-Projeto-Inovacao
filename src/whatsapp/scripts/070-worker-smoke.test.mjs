import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { EventEmitter } from 'node:events';
import { loadInputs, backend } from './070-worker-preflight.mjs';
import { SmokeFailure, localJson, properties, summary } from './070-worker-common.mjs';
import { startProvider } from './070-worker-runtime.mjs';
import { createInterruption } from './070-worker-lifecycle.mjs';
import { provision } from './070-worker-scenarios.mjs';

const script = fileURLToPath(new URL('./070-worker-smoke.mjs', import.meta.url));
const sha = 'a'.repeat(40), sentinel = 'PRIVATE_CONTENT_MUST_NEVER_REACH_OUTPUT';
const token = Buffer.alloc(32, 9).toString('base64url');

function fixture() {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'wa070-preflight-'));
  const base = path.join(home, '.local/share/vanrides/local/wa070-backend-' + sha.slice(0, 8));
  fs.mkdirSync(base, { recursive: true, mode: 0o700 }); fs.chmodSync(base, 0o700);
  const state = { repository: backend, source_sha: sha, jar_sha256: 'b'.repeat(64), api_pid: 123456789,
    api_base_url: 'http://127.0.0.1:18083', container: 'vanrides-wa070-aaaaaaaa-postgres', db_port: 51222 };
  const operator = { environment: 'wa070-local', operatorCredential: token };
  const config = [
    'spring.datasource.url=jdbc:postgresql://127.0.0.1:51222/vanrides_wa070',
    'spring.datasource.username=vanrides_wa070', 'spring.datasource.password=' + token,
    'server.address=127.0.0.1', 'server.port=18083', 'vanrides.wa.environment=wa070-local',
    'vanrides.wa.enabled=true', 'vanrides.wa.test-cohort-only=true',
  ].join('\n') + '\n';
  const write = (name, value, mode = 0o600) => {
    const file = path.join(base, name);
    fs.writeFileSync(file, typeof value === 'string' ? value : JSON.stringify(value), { mode }); fs.chmodSync(file, mode);
  };
  write('state.json', state); write('operator-private.json', operator); write('application-wa070.properties', config);
  return { home, base, state, operator, config, write };
}

const scenarios = [
  ['missing-state', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'state.json'))],
  ['missing-operator', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'operator-private.json'))],
  ['missing-config', 'PRIVATE_INPUT_INVALID', f => fs.unlinkSync(path.join(f.base, 'application-wa070.properties'))],
  ['malformed-state', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', '{"private":"' + sentinel + token)],
  ['state-array', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', JSON.stringify([sentinel, token]))],
  ['malformed-operator', 'PRIVATE_INPUT_INVALID', f => f.write('operator-private.json', '{"private":"' + sentinel + token)],
  ['oversized-private-state', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', sentinel.repeat(70000))],
  ['state-mode', 'PRIVATE_INPUT_INVALID', f => f.write('state.json', { ...f.state, private: sentinel }, 0o644)],
  ['operator-mode', 'PRIVATE_INPUT_INVALID', f => f.write('operator-private.json', f.operator, 0o644)],
  ['config-mode', 'PRIVATE_INPUT_INVALID', f => f.write('application-wa070.properties', f.config, 0o644)],
  ['directory-mode', 'RUNTIME_DIRECTORY_INVALID', f => fs.chmodSync(f.base, 0o755)],
  ['state-symlink', 'PRIVATE_INPUT_INVALID', f => {
    fs.unlinkSync(path.join(f.base, 'state.json'));
    const target = path.join(f.home, sentinel); fs.writeFileSync(target, token, { mode: 0o600 });
    fs.symlinkSync(target, path.join(f.base, 'state.json'));
  }],
  ['foreign-repository', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, repository: '/' + sentinel })],
  ['foreign-sha', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, source_sha: sentinel + token })],
  ['bad-jar-hash', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, jar_sha256: sentinel })],
  ['remote-api-url', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, api_base_url: 'https://' + sentinel + '.invalid' })],
  ['foreign-container', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, container: sentinel + token })],
  ['foreign-db-port', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, db_port: 5432 })],
  ['invalid-api-pid', 'RUNTIME_STATE_INVALID', f => f.write('state.json', { ...f.state, api_pid: sentinel + token })],
  ['foreign-operator-environment', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, environment: sentinel })],
  ['bad-operator-token', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, operatorCredential: sentinel + token })],
  ['operator-extra-fields', 'OPERATOR_INPUT_INVALID', f => f.write('operator-private.json', { ...f.operator, private: sentinel + token })],
  ['malformed-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config + sentinel + token)],
  ['remote-db-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config.replace('127.0.0.1:51222', sentinel + '.invalid:51222'))],
  ['wrong-db-user', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config.replace('username=vanrides_wa070', 'username=' + sentinel))],
  ['wildcard-api-config', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config.replace('address=127.0.0.1', 'address=0.0.0.0'))],
  ['wrong-api-port', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config.replace('port=18083', 'port=8080'))],
  ['cohort-disabled', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config.replace('test-cohort-only=true', 'test-cohort-only=false'))],
  ['duplicate-config-key', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config + 'server.address=' + sentinel)],
  ['escaped-config-key', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config + 'server\\u002eaddress=' + sentinel)],
  ['extra-config-import', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config + 'spring.config.import=https://' + sentinel)],
  ['env-config-placeholder', 'LOCAL_CONFIG_INVALID', f => f.write('application-wa070.properties', f.config + 'foreign=${' + sentinel + '}')],
  ['wrong-runtime-directory', 'RUNTIME_SCOPE_INVALID', f => { f.directory = path.join(f.home, sentinel + token); }],
  ['extra-argument', 'EXPECTED_SOURCE_REQUIRED', f => { f.extra = [sentinel + token]; }],
  ['not-frozen-source', 'SOURCE_NOT_FROZEN', () => {}],
];

for (const [name, code, mutate] of scenarios) {
  test('closed preflight ' + name + ' stops before runtime writes', () => {
    const f = fixture();
    try {
      mutate(f);
      const result = spawnSync(process.execPath, [script, f.directory ?? f.base, sha, ...(f.extra ?? [])], {
        env: { HOME: f.home }, encoding: 'utf8', timeout: 5000, maxBuffer: 128 * 1024,
      });
      assert.equal(result.error, undefined); assert.equal(result.status, 1); assert.equal(result.stdout, '');
      assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code });
      for (const secret of [sentinel, token, f.home]) assert.equal((result.stdout + result.stderr).includes(secret), false);
      assert.equal(fs.readdirSync(f.base).some(entry => entry.startsWith('smoke-')), false);
    } finally { fs.rmSync(f.home, { recursive: true, force: true }); }
  });
}

test('missing CLI arguments return a fixed code', () => {
  const result = spawnSync(process.execPath, [script], { env: {}, encoding: 'utf8', timeout: 5000 });
  assert.equal(result.status, 1); assert.equal(result.stdout, '');
  assert.deepEqual(JSON.parse(result.stderr), { status: 'FAIL', code: 'EXPECTED_SOURCE_REQUIRED' });
});

test('valid dedicated inputs parse without starting any runtime', () => {
  const f = fixture(), previous = process.env.HOME;
  try {
    process.env.HOME = f.home;
    const input = loadInputs(f.base, sha);
    assert.equal(input.state.source_sha, sha); assert.equal(input.inputHashes.length, 3);
    assert.equal(fs.readdirSync(f.base).length, 3);
  } finally { process.env.HOME = previous; fs.rmSync(f.home, { recursive: true, force: true }); }
});

test('properties parser rejects hidden duplicate and escaped overrides', () => {
  assert.equal(properties(Buffer.from('x=y\n# ignored\n')).get('x'), 'y');
  for (const text of ['x=y\nx=z', 'x\\u002ey=z', 'x=${PRIVATE}', 'spring.config.import=/private'])
    assert.throws(() => properties(Buffer.from(text)), error => error instanceof SmokeFailure && error.code === 'LOCAL_CONFIG_INVALID');
});

test('control summary accepts only closed exact public metadata', () => {
  summary({ status: 'PROCESSED', claimed: 1, processed: 1, retried: 0, paused: 0 }, 'process', 'PROCESSED');
  summary({ status: 'IDLE', claimed: 0, submitted: 0, unknown: 0, failed: 0 }, 'dispatch', 'IDLE');
  for (const extra of [{ private: sentinel }, { inboxEventId: cryptoUuid() }, { text: sentinel }, { credential: token }])
    assert.throws(() => summary({ status: 'IDLE', claimed: 0, processed: 0, retried: 0, paused: 0, ...extra }, 'process', 'IDLE'),
      { code: 'CONTROL_SUMMARY_INVALID' });
});
function cryptoUuid() { return '00000000-0000-4000-8000-000000000000'; }

async function fake(handler, action) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await action('http://127.0.0.1:' + server.address().port); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}

test('loopback HTTP helper reads bounded JSON', async () => {
  await fake((_req, res) => res.end('{"status":"UP"}'), async base => {
    assert.deepEqual(await localJson(base, '/healthz'), { status: 200, data: { status: 'UP' } });
  });
});

test('loopback helper forbids remote origin and client-selected query', async () => {
  await assert.rejects(localJson('https://example.invalid', '/healthz'), { code: 'LOCAL_HTTP_ORIGIN_INVALID' });
  await assert.rejects(localJson('http://127.0.0.1:12345', '//foreign.invalid'), { code: 'LOCAL_HTTP_ROUTE_INVALID' });
  await assert.rejects(localJson('http://127.0.0.1:12345', '/healthz?credential=' + token), { code: 'LOCAL_HTTP_ROUTE_INVALID' });
});

test('HTTP helper never retries or follows redirect', async () => {
  let calls = 0;
  await fake((_req, res) => { calls++; res.writeHead(302, { location: '/secret' }); res.end(); }, async base => {
    await assert.rejects(localJson(base, '/healthz'), { code: 'LOCAL_HTTP_FAILED' }); assert.equal(calls, 1);
  });
});

test('HTTP helper caps response and does not reflect raw malformed body', async () => {
  await fake((_req, res) => res.end(sentinel.repeat(2000)), async base => {
    await assert.rejects(localJson(base, '/healthz'), { code: 'LOCAL_HTTP_RESPONSE_TOO_LARGE' });
  });
  await fake((_req, res) => res.end(sentinel + token), async base => {
    await assert.rejects(localJson(base, '/healthz'), error => error.message === 'LOCAL_HTTP_FAILED' && !error.cause);
  });
});

test('HTTP helper deadline includes a stalled body', async () => {
  await fake((_req, res) => { res.writeHead(200); res.write('{'); }, async base => {
    await assert.rejects(localJson(base, '/healthz', { timeout: 20 }), { code: 'LOCAL_HTTP_TIMEOUT' });
  });
});

test('fake provider checks exact synthetic text and exposes counts only', async () => {
  const instance = 'wa070_synthetic_' + '0'.repeat(32), address = '000000000000000@s.whatsapp.net';
  const provider = await startProvider(instance, address, token);
  try {
    provider.enqueue({ outboxId: cryptoUuid(), text: 'Resposta sintética.' });
    const response = await localJson(provider.url, '/message/sendText/' + instance, { method: 'POST',
      headers: { apikey: token }, body: { number: address, text: 'Resposta sintética.', linkPreview: false } });
    assert.equal(response.status, 201); assert.equal(response.data.key.id, 'wa070_provider_1');
    assert.deepEqual(provider.counts(), { requests: 1, accepted: 1, pending: 0, failures: 0 });
    const duplicate = await localJson(provider.url, '/message/sendText/' + instance, { method: 'POST',
      headers: { apikey: token }, body: { number: address, text: 'Resposta sintética.', linkPreview: false } });
    assert.equal(duplicate.status, 400);
    assert.deepEqual(provider.counts(), { requests: 2, accepted: 1, pending: 0, failures: 1 });
  } finally { await provider.stop(); }
});

test('fake provider refuses a nonzero alias before opening a listener', async () => {
  await assert.rejects(startProvider('wa070_synthetic_' + '0'.repeat(32), '000000000000001@s.whatsapp.net', token),
    { code: 'SYNTHETIC_PROVIDER_SCOPE_INVALID' });
});

test('fake connection reads are authenticated and do not count as message submissions', async () => {
  const instance = 'wa070_synthetic_' + '0'.repeat(32);
  const provider = await startProvider(instance, '000000000000000@s.whatsapp.net', token);
  try {
    const result = await localJson(provider.url, '/instance/connectionState/' + instance, { headers: { apikey: token } });
    assert.equal(result.status, 200);
    assert.deepEqual(result.data, { instance: { instanceName: instance, state: 'open' } });
    assert.equal(provider.connectionReadCount(), 1);
    assert.deepEqual(provider.counts(), { requests: 0, accepted: 0, pending: 0, failures: 0 });
    const denied = await localJson(provider.url, '/instance/connectionState/' + instance, { headers: { apikey: 'wrong' } });
    assert.equal(denied.status, 400);
    assert.equal(provider.connectionReadCount(), 1);
    assert.equal(provider.counts().accepted, 0);
  } finally { await provider.stop(); }
});

test('controlled outage consumes no queued message and an ambiguous response can follow a recorded effect', async () => {
  const instance = 'wa070_synthetic_' + '0'.repeat(32), address = '000000000000000@s.whatsapp.net';
  const provider = await startProvider(instance, address, token);
  try {
    provider.enqueue({ outboxId: cryptoUuid(), text: 'Resposta sintética.' });
    provider.observeConnections(['open', 'close']);
    for (const expected of ['open', 'close', 'open']) {
      const value = await localJson(provider.url, '/instance/connectionState/' + instance, { headers: { apikey: token } });
      assert.equal(value.data.instance.state, expected);
    }
    assert.deepEqual(provider.counts(), { requests: 0, accepted: 0, pending: 1, failures: 0 });
    provider.nextPostEffect('AMBIGUOUS_AFTER_EFFECT');
    const result = await localJson(provider.url, '/message/sendText/' + instance, { method: 'POST',
      headers: { apikey: token }, body: { number: address, text: 'Resposta sintética.', linkPreview: false } });
    assert.equal(result.status, 400);
    assert.deepEqual(provider.counts(), { requests: 1, accepted: 1, pending: 0, failures: 0 });
    provider.enqueue({ outboxId: '00000000-0000-4000-8000-000000000001', text: 'Outra resposta sintética.' });
    assert.equal((await localJson(provider.url, '/message/sendText/' + instance, { method: 'POST',
      headers: { apikey: token }, body: { number: address, text: 'Outra resposta sintética.', linkPreview: false } })).status, 201);
    assert.equal(provider.counts().accepted, 2);
  } finally { await provider.stop(); }
});

test('SIGINT fences new work and removes only its own handlers', async () => {
  const emitter = new EventEmitter(), existing = () => {};
  emitter.on('SIGINT', existing);
  const interruption = createInterruption(emitter);
  let calls = 0;
  try {
    emitter.emit('SIGINT');
    await assert.rejects(interruption.wait(() => { calls++; }), { code: 'SMOKE_INTERRUPTED' });
    assert.equal(calls, 0);
  } finally { interruption.dispose(); }
  assert.deepEqual(emitter.listeners('SIGINT'), [existing]); assert.equal(emitter.listenerCount('SIGTERM'), 0);
});

test('SIGTERM stops waiting without aborting an already authorized operation', async () => {
  const emitter = new EventEmitter(), interruption = createInterruption(emitter);
  let complete;
  const authorized = new Promise(resolve => { complete = resolve; });
  try {
    const waiting = interruption.wait(() => authorized);
    await new Promise(resolve => setImmediate(resolve));
    emitter.emit('SIGTERM');
    await assert.rejects(waiting, { code: 'SMOKE_INTERRUPTED' });
    complete('receipt committed'); assert.equal(await authorized, 'receipt committed');
  } finally { interruption.dispose(); }
});

test('interruption during the first provisioning fences later credential mutations', async () => {
  const emitter = new EventEmitter(), interruption = createInterruption(emitter), calls = [];
  const tools = { operate(operation) { calls.push(operation); emitter.emit('SIGTERM'); } };
  try {
    await assert.rejects(provision({ tools, credentials: [], interruption }), { code: 'SMOKE_INTERRUPTED' });
    assert.deepEqual(calls, ['create-test-cohort']);
  } finally { interruption.dispose(); }
});
