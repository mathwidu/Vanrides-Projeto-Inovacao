import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { SmokeFailure, digest, privateBytes } from './070-worker-common.mjs';
import { assertApiStopped } from './070-worker-preflight.mjs';
import { protocol, atomicPrivateJson, serialCleanupConfig, validateShutdownResponse, assertAfterSerialCleanup } from './110-run-serial-cleanup.mjs';

const card = 'VR-WA-110-RUN-TRACKING', sha = 'a'.repeat(40), tree = 'b'.repeat(40);
const batch = '00000000-0000-4000-8000-000000000110';

function fixture(t, profile = 'HOST_SERIAL_CLEANUP') {
  const home = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'wa110-serial-')));
  t.after(() => fs.rmSync(home, { force: true, recursive: true }));
  const base = path.join(home, '.local/share/vanrides/local/wa070-backend-' + sha.slice(0, 8));
  const directory = path.join(home, '.local/share/vanrides/wa110/attempts', sha, 'synthetic-a', 'serial-cleanup');
  fs.mkdirSync(base, { recursive: true, mode: 0o700 });
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const work = path.join(base, 'smoke-20261101T030000000Z-' + batch); fs.mkdirSync(work, { mode: 0o700 });
  const identity = { pid: 1234, parentPid: 1233 }, input = { base, expectedSha: sha, state: { api_pid: 1232, jar_sha256: 'c'.repeat(64) } };
  const intent = { schemaVersion: 1, protocol, sourceSha: sha, jarSha256: input.state.jar_sha256, backendTree: tree,
    attempt: 'synthetic-a', profile, parentPid: identity.parentPid, apiPid: input.state.api_pid, parentNonce: Buffer.alloc(32, 9).toString('base64url') };
  const intentFile = path.join(directory, 'parent-intent-private.json'); atomicPrivateJson(intentFile, intent);
  const environment = { HOME: home, WA110_SERIAL_CLEANUP_DIR: directory, ...(profile.startsWith('NATIVE') ? { WA110_DEVICE_APK: 'synthetic.apk' } : {}) };
  const calls = [], frozen = { backendTree: tree, assets: { 'src/whatsapp/agent/example.mjs': 'd'.repeat(64) },
    assertSource: () => calls.push('live'), assertStoppedRuntime: () => calls.push('stopped'), assertContainer: () => calls.push('database') };
  const context = { frozen, work, batch, instance: 'wa070_synthetic_' + batch.replaceAll('-', ''),
    checks: [{ check: 'synthetic_functional_check', status: 'PASS' }], interruption: { assertActive() {} } };
  const requestFile = path.join(directory, 'shutdown-request-private.json'), responseFile = path.join(directory, 'shutdown-response-private.json');
  function respond(change = value => value) {
    const request = JSON.parse(privateBytes(requestFile));
    const { functionalChecks, assetSha256, ...bindings } = request;
    const response = { ...bindings, requestSha256: digest(privateBytes(requestFile)), status: 'API_STOPPED',
      shutdownMethod: 'OWNING_PARENT_TERMINATE_GROUP', apiExitCode: 0, processExited: true, portClosed: true };
    atomicPrivateJson(responseFile, change(response));
  }
  return { home, input, identity, intent, intentFile, environment, directory, calls, frozen, context, requestFile, responseFile, respond };
}

test('historical cards do not opt in or load any protocol input by default', () => {
  assert.equal(serialCleanupConfig({}, 'VR-WA-070-AGENT-WORKFLOWS', {}), null);
  assert.throws(() => serialCleanupConfig({}, 'VR-WA-070-AGENT-WORKFLOWS', { WA110_SERIAL_CLEANUP_DIR: '/private' }), { code: 'SERIAL_CLEANUP_ONLY_110' });
});

for (const profile of ['HOST_SERIAL_CLEANUP', 'NATIVE_SERIAL_CLEANUP']) test(profile + ' proves live API before request and stopped API after ACK and cleanup', async t => {
  const f = fixture(t, profile), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  await serial.shutdown(f.context, { pause: async () => { assert.deepEqual(f.calls, ['live', 'database']); f.respond(); } });
  assert.equal(serial.confirmed, true); assert.equal(serial.audit.profile, profile);
  assert.deepEqual(f.calls, ['live', 'database', 'stopped', 'database']);
  assertAfterSerialCleanup(f.frozen, serial);
  assert.deepEqual(f.calls.slice(-2), ['stopped', 'database']);
  for (const forbidden of ['batchId', 'instance', 'apiPid', 'parentNonce', 'runDirectory']) assert.equal(forbidden in serial.audit, false);
});

for (const field of ['batchId', 'instance', 'requestNonce', 'parentNonce', 'apiPid', 'requesterPid', 'parentPid', 'sourceSha', 'jarSha256', 'backendTree', 'attempt', 'profile', 'requestSha256']) {
  test('an ACK for a different ' + field + ' cannot authorize cleanup success', async t => {
    const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
    await assert.rejects(serial.shutdown(f.context, { pause: async () => f.respond(response => ({ ...response, [field]: 'foreign' })) }), { code: 'SERIAL_SHUTDOWN_ACK_INVALID' });
    assert.equal(serial.confirmed, false); assert.equal(f.calls.includes('stopped'), false);
    assert.throws(() => assertAfterSerialCleanup(f.frozen, serial), { code: 'SERIAL_SHUTDOWN_UNCONFIRMED' });
  });
}

test('missing ACK cannot produce a passing barrier', async t => {
  const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  let clock = 0;
  await assert.rejects(serial.shutdown(f.context, { timeout: 1, now: () => clock++, pause: async () => {} }), { code: 'SERIAL_SHUTDOWN_ACK_TIMEOUT' });
  assert.equal(serial.confirmed, false);
});

test('interruption while awaiting the parent cannot confirm shutdown', async t => {
  const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  f.context.interruption.assertActive = () => { throw new SmokeFailure('SMOKE_INTERRUPTED'); };
  await assert.rejects(serial.shutdown(f.context), { code: 'SMOKE_INTERRUPTED' });
  assert.equal(serial.confirmed, false); assert.equal(fs.existsSync(f.responseFile), false);
});

test('private directory, symlink and foreign parent inputs fail before request creation', t => {
  const f = fixture(t);
  assert.throws(() => serialCleanupConfig(f.input, card, { ...f.environment, WA110_SERIAL_CLEANUP_DIR: f.home }, f.identity), { code: 'SERIAL_DIRECTORY_SCOPE_INVALID' });
  assert.throws(() => serialCleanupConfig(f.input, card, f.environment, { ...f.identity, parentPid: 9 }), { code: 'SERIAL_PARENT_INTENT_INVALID' });
  fs.chmodSync(f.directory, 0o755);
  assert.throws(() => serialCleanupConfig(f.input, card, f.environment, f.identity), { code: 'SERIAL_DIRECTORY_NOT_PRIVATE' });
  fs.chmodSync(f.directory, 0o700);
  const other = f.intentFile + '.other'; fs.renameSync(f.intentFile, other); fs.symlinkSync(other, f.intentFile);
  assert.throws(() => serialCleanupConfig(f.input, card, f.environment, f.identity), { code: 'PRIVATE_INPUT_INVALID' });
  assert.equal(fs.existsSync(f.requestFile), false);
});

test('stale response and changed intent are not accepted', async t => {
  const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  atomicPrivateJson(f.responseFile, { status: 'API_STOPPED' });
  await assert.rejects(serial.shutdown(f.context), { code: 'SERIAL_HANDSHAKE_ALREADY_USED' });
  fs.unlinkSync(f.responseFile);
  fs.writeFileSync(f.intentFile, JSON.stringify({ ...f.intent, attempt: 'other' }));
  await assert.rejects(serial.shutdown(f.context), { code: 'SERIAL_PARENT_INTENT_CHANGED' });
});

test('post-shutdown artifact validation remains mandatory', async t => {
  const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  f.frozen.assertStoppedRuntime = () => { throw new SmokeFailure('JAR_CHANGED'); };
  await assert.rejects(serial.shutdown(f.context, { pause: async () => f.respond() }), { code: 'JAR_CHANGED' });
  assert.equal(serial.confirmed, false);
});

test('final validation after cleanup rechecks artifacts even after a valid ACK', async t => {
  const f = fixture(t), serial = serialCleanupConfig(f.input, card, f.environment, f.identity);
  await serial.shutdown(f.context, { pause: async () => f.respond() });
  f.frozen.assertStoppedRuntime = () => { throw new SmokeFailure('JAR_CHANGED'); };
  assert.throws(() => assertAfterSerialCleanup(f.frozen, serial), { code: 'JAR_CHANGED' });
});

test('default final check still requires the live source and database', () => {
  const calls = [];
  assertAfterSerialCleanup({ assertSource: () => calls.push('live'), assertContainer: () => calls.push('database') }, null);
  assert.deepEqual(calls, ['live', 'database']);
});

test('atomic publication never replaces an existing ACK', t => {
  const f = fixture(t); atomicPrivateJson(f.responseFile, { status: 'first' });
  assert.throws(() => atomicPrivateJson(f.responseFile, { status: 'second' }), { code: 'EEXIST' });
  assert.equal(JSON.parse(privateBytes(f.responseFile)).status, 'first');
  assert.equal(fs.readdirSync(f.directory).some(name => name.endsWith('.pending')), false);
});

test('exit proof refuses PID reuse, permission failure, occupied port and probe failure', () => {
  const absent = () => { throw Object.assign(new Error(), { code: 'ESRCH' }); };
  const closed = () => ({ status: 1, stdout: '', stderr: '' });
  assertApiStopped(1234, { probePid: absent, inspectPort: closed });
  for (const probePid of [() => {}, () => { throw Object.assign(new Error(), { code: 'EPERM' }); }])
    assert.throws(() => assertApiStopped(1234, { probePid, inspectPort: closed }), { code: 'SERIAL_API_PROCESS_STILL_PRESENT' });
  for (const inspectPort of [() => ({ status: 0, stdout: 'listener', stderr: '' }), () => ({ error: new Error() })])
    assert.throws(() => assertApiStopped(1234, { probePid: absent, inspectPort }), { code: 'SERIAL_API_PORT_NOT_CLOSED' });
  assert.throws(() => assertApiStopped(0), { code: 'SERIAL_API_PID_INVALID' });
});
