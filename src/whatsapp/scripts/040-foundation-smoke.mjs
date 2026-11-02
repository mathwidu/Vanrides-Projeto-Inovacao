#!/usr/bin/env node
/** Same-JAR, loopback-only smoke. Private inputs/results stay outside Git; no real transport or GPS. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';

async function main() {
process.umask(0o077);
const [directory, expectedSha] = process.argv.slice(2);
const demand = (condition, code) => { if (!condition) throw new Error(code); };
const readPrivate = (file) => {
  try {
    const stat = fs.lstatSync(file);
    demand(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600, 'PRIVATE_FILE_REQUIRED');
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    throw new Error('PRIVATE_INPUT_INVALID');
  }
};
const digest = (value) => crypto.createHash('sha256').update(value).digest('hex');
const b64hash = (value) => crypto.createHash('sha256').update(value).digest('base64url');
const privateWrite = (file, value) => fs.writeFileSync(file, value, { flag: 'wx', mode: 0o600 });
const repository = fileURLToPath(new URL('../../backend/', import.meta.url));
const executable = '/opt/homebrew/opt/openjdk@17/bin/java';
const env = Object.fromEntries(['HOME', 'USER', 'LOGNAME', 'TMPDIR', 'LANG', 'LC_ALL'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
env.PATH = '/opt/homebrew/opt/openjdk@17/bin:/usr/bin:/bin:/usr/sbin:/sbin';
const git = (...args) => execFileSync('/usr/bin/git', ['--no-optional-locks', '-C', repository, ...args], { env, encoding: 'utf8' }).trim();
demand(typeof directory === 'string' && /^[a-f0-9]{40}$/.test(expectedSha ?? ''), 'EXPECTED_SOURCE_REQUIRED');
const base = path.resolve(directory);
demand(base === path.join(process.env.HOME, '.local/share/vanrides/local/wa040-backend-' + expectedSha.slice(0, 8)), 'RUNTIME_SCOPE_INVALID');
const state = readPrivate(path.join(base, 'state.json'));
const operator = readPrivate(path.join(base, 'operator-private.json'));
demand(state.source_sha === expectedSha && state.api_base_url === 'http://127.0.0.1:18082', 'RUNTIME_SOURCE_OR_URL_INVALID');
demand(operator.environment === 'wa040-local', 'OPERATOR_ENVIRONMENT_INVALID');
const jar = path.join(base, 'app.jar');
const assertSource = () => {
  demand(git('rev-parse', 'HEAD') === expectedSha && git('status', '--porcelain', '--untracked-files=all') === '', 'SOURCE_NOT_FROZEN');
  demand(digest(fs.readFileSync(jar)) === state.jar_sha256, 'JAR_CHANGED');
  const listener = execFileSync('/usr/sbin/lsof', ['-nP', '-iTCP:18082', '-sTCP:LISTEN', '-Fp'], { env, encoding: 'utf8' }).trim();
  demand(listener.split('\n').includes('p' + state.api_pid), 'LISTENER_NOT_OWNED_BY_EXPECTED_JAR');
  demand(execFileSync('/bin/ps', ['-p', String(state.api_pid), '-o', 'command='], { env, encoding: 'utf8' }).includes(jar), 'JAR_PROCESS_MISMATCH');
};
assertSource();
const runId = new Date().toISOString().replace(/[^0-9TZ]/g, '') + '-' + crypto.randomUUID();
const work = path.join(base, 'smoke-' + runId);
fs.mkdirSync(work, { mode: 0o700 });
env.SPRING_CONFIG_IMPORT = 'optional:file:' + path.join(base, 'application-wa040.properties') + '[.properties]';
const batch = crypto.randomUUID();
const stages = [];
const startedAt = new Date().toISOString();
let ordinal = 0;
const passed = (check) => { stages.push({ check, status: 'PASS' }); console.log(JSON.stringify({ check, status: 'PASS' })); };
const after = (seconds) => new Date(Date.now() + seconds * 1000).toISOString();

function operate(operation, fields = {}) {
  const index = String(++ordinal).padStart(3, '0');
  const input = path.join(work, index + '-request.json'), output = path.join(work, index + '-result.json');
  privateWrite(input, JSON.stringify({ ...operator, operation, batchId: batch, ...fields }));
  const result = spawnSync(executable, ['-Xmx384m', '-XX:MaxMetaspaceSize=256m', '-jar', jar,
    '--spring.main.web-application-type=none', '--vanrides.wa.operator.request-file=' + input,
    '--vanrides.wa.operator.output-file=' + output], { cwd: work, env, timeout: 45000, maxBuffer: 512 * 1024 });
  privateWrite(path.join(work, index + '-operator.log'), Buffer.concat([result.stdout ?? Buffer.alloc(0), result.stderr ?? Buffer.alloc(0)]));
  demand(result.status === 0 && !result.error, 'OPERATOR_CLI_FAILED_' + operation.toUpperCase().replaceAll('-', '_'));
  const response = readPrivate(output);
  demand(response.status === 'COMMITTED' && response.operation === operation && response.batchId === batch, 'OPERATOR_RESULT_NOT_COMMITTED');
  return response;
}

async function request(method, route, body, headers = {}) {
  demand(route.startsWith('/') && !route.startsWith('//'), 'ROUTE_INVALID');
  const response = await fetch(state.api_base_url + route, {
    method, headers, redirect: 'error', signal: AbortSignal.timeout(15000),
    ...(body === undefined ? {} : { body: typeof body === 'string' ? body : JSON.stringify(body) }),
  });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { throw new Error('RESPONSE_NOT_SINGLE_JSON'); }
  return { status: response.status, data, nonce: response.headers.get('DPoP-Nonce') };
}

function device() {
  const keys = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  return { keys, jwk: keys.publicKey.export({ format: 'jwk' }), verifier: crypto.randomBytes(32).toString('base64url'), nonce: null };
}
function sign(device, method, route, raw, access) {
  const header = { typ: 'dpop+jwt', alg: 'ES256', jwk: device.jwk };
  const payload = { jti: crypto.randomBytes(24).toString('base64url'), iat: Math.floor(Date.now() / 1000), htm: method, htu: state.api_base_url + route };
  if (device.nonce) payload.nonce = device.nonce;
  if (access) payload.ath = b64hash(access);
  if (method === 'POST') payload.vr_body_sha256 = b64hash(raw);
  const unsigned = Buffer.from(JSON.stringify(header)).toString('base64url') + '.' + Buffer.from(JSON.stringify(payload)).toString('base64url');
  return unsigned + '.' + crypto.sign('sha256', Buffer.from(unsigned), { key: device.keys.privateKey, dsaEncoding: 'ieee-p1363' }).toString('base64url');
}
async function dpop(device, method, route, body, access, idempotency = crypto.randomUUID()) {
  const raw = body === undefined ? undefined : JSON.stringify(body);
  for (let attempt = 0; attempt < 2; attempt++) {
    const headers = { DPoP: sign(device, method, route, raw, access), 'Idempotency-Key': idempotency };
    if (raw !== undefined) headers['Content-Type'] = 'application/json';
    if (access) headers.Authorization = 'DPoP ' + access;
    const result = await request(method, route, raw, headers);
    if (result.nonce) device.nonce = result.nonce;
    if (attempt === 0 && result.status === 401 && result.data?.code === 'PROOF_NONCE_REQUIRED' && result.nonce) continue;
    return result;
  }
  throw new Error('NONCE_NEGOTIATION_FAILED');
}

try {
  const health = await request('GET', '/actuator/health'); demand(health.status === 200, 'LOCAL_API_UNHEALTHY');
  operate('create-test-cohort', { synthetic: true }); passed('operator_cli_without_http_server');
  const worker = operate('provision-service-credential', { principal: 'CONVERSATION_WORKER', allowedInstanceId: 'wa040_synthetic', expiresAt: after(3600) });
  const driver = crypto.randomUUID();
  let version = 0;
  async function command(type, payload, metadata = {}, verifyReplay = false) {
    const event = operate('seed-test-data', { identityId: driver, inboxEventId: crypto.randomUUID(), workerCredentialId: worker.credentialId, leaseExpiresAt: after(55), ...metadata });
    const headers = { Authorization: 'Bearer ' + worker.credential, 'X-Inbox-Lease': event.leaseToken, 'Idempotency-Key': crypto.randomUUID(), 'Content-Type': 'application/json' };
    const context = await request('GET', '/integrations/whatsapp/v1/context?inboxEventId=' + event.inboxEventId, undefined, headers);
    demand(context.status === 200 && context.data.allowedCommands.includes(type), 'CONTEXT_ACTION_NOT_ALLOWED');
    const envelope = { commandType: type, inboxEventId: event.inboxEventId, expectedVersion: version, payload };
    const result = await request('POST', '/integrations/whatsapp/v1/commands', envelope, headers);
    demand(result.status === 200 && result.data.outcome === 'APPLIED', 'HUMAN_COMMAND_NOT_APPLIED');
    if (verifyReplay) {
      const repeat = await request('POST', '/integrations/whatsapp/v1/commands', envelope, headers);
      demand(repeat.status === 200 && repeat.data.commandId === result.data.commandId && repeat.data.entityVersion === result.data.entityVersion, 'COMMAND_REPLAY_CHANGED_RESULT');
    }
    version = result.data.entityVersion;
    operate('complete-test-inbox', { inboxEventId: event.inboxEventId });
  }
  await command('START_ONBOARDING', {}, {}, true); passed('onboarding_and_message_idempotency');
  const invite = operate('driver-bootstrap', { identityId: driver, expiresAt: after(300) });
  await command('CLAIM_DRIVER_INVITE', { inviteCode: invite.driverInviteCode }, { driverInviteCode: invite.driverInviteCode });
  await command('SET_PROFILE_NAME', { name: 'Motorista sintético WA040' }, { messageText: 'Motorista sintético WA040' });
  const contract = JSON.parse(fs.readFileSync(path.join(repository, 'contracts/whatsapp-mvp/openapi.yaml'), 'utf8'));
  await command('ACCEPT_NOTICE', { noticeVersion: contract.components.examples.AcceptNotice.value.payload.noticeVersion }, { messageText: 'ACEITO' });
  passed('operator_bound_driver_bootstrap_and_notice');
  const first = device(), stranger = device();
  const beginBody = { publicJwk: first.jwk, pkceChallenge: b64hash(first.verifier), pkceMethod: 'S256', platform: 'ANDROID', appVersion: 'synthetic-smoke-040' };
  const beginKey = crypto.randomUUID();
  const begin = await dpop(first, 'POST', '/driver-app/v1/pairings/begin', beginBody, undefined, beginKey);
  demand(begin.status === 200, 'PAIR_BEGIN_FAILED');
  const again = await dpop(first, 'POST', '/driver-app/v1/pairings/begin', beginBody, undefined, beginKey);
  demand(again.status === 200 && again.data.pairingId === begin.data.pairingId, 'PAIR_BEGIN_REPLAY_FAILED');
  const foreign = await dpop(stranger, 'GET', '/driver-app/v1/pairings/' + begin.data.pairingId + '/status');
  demand(foreign.status === 404, 'FOREIGN_KEY_COULD_READ_PAIRING');
  await command('CONFIRM_PAIRING', { userCode: begin.data.userCode }, { pairingCode: begin.data.userCode });
  const redeemed = await dpop(first, 'POST', '/driver-app/v1/pairings/redeem', { pairingId: begin.data.pairingId, pkceVerifier: first.verifier });
  demand(redeemed.status === 200 && redeemed.data.tokenType === 'DPoP', 'PAIR_REDEEM_FAILED');
  const session = redeemed.data;
  const status = await dpop(first, 'GET', '/driver-app/v1/status', undefined, session.accessToken);
  demand(status.status === 200 && status.data.driverId === driver && status.data.readiness === 'READY', 'DEVICE_STATUS_INVALID');
  passed('nonce_key_isolation_pkce_and_device_status');
  const refreshKey = crypto.randomUUID();
  const refreshed = await dpop(first, 'POST', '/driver-app/v1/session/refresh', { refreshToken: session.refreshToken }, undefined, refreshKey);
  demand(refreshed.status === 200 && refreshed.data.refreshToken !== session.refreshToken, 'TOKEN_NOT_ROTATED');
  const replay = await dpop(first, 'POST', '/driver-app/v1/session/refresh', { refreshToken: session.refreshToken }, undefined, refreshKey);
  demand(replay.status === 200 && replay.data.refreshToken === refreshed.data.refreshToken, 'REFRESH_REPLAY_CHANGED_TOKEN');
  demand((await dpop(first, 'GET', '/driver-app/v1/status', undefined, session.accessToken)).status === 401, 'OLD_ACCESS_STILL_VALID');
  const rotatedStatus = await dpop(first, 'GET', '/driver-app/v1/status', undefined, refreshed.data.accessToken);
  demand(rotatedStatus.status === 200 && rotatedStatus.data.driverId === driver && rotatedStatus.data.readiness === 'READY', 'ROTATED_ACCESS_NOT_USABLE');
  const revoked = await dpop(first, 'POST', '/driver-app/v1/session/revoke', {}, refreshed.data.accessToken);
  demand(revoked.status === 200 && revoked.data.state === 'REVOKED', 'REVOKE_FAILED');
  demand((await dpop(first, 'GET', '/driver-app/v1/status', undefined, refreshed.data.accessToken)).status === 401, 'REVOKED_ACCESS_ACCEPTED');
  demand((await dpop(first, 'POST', '/driver-app/v1/session/revoke', {}, refreshed.data.accessToken)).status === 200, 'OWN_REVOKE_RETRY_FAILED');
  passed('refresh_rotation_idempotency_and_revocation');
  operate('reset-test-data');
  operate('revoke-service-credential', { credentialId: worker.credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' });
  passed('scoped_synthetic_cleanup');
  assertSource();
  const receipt = { status: 'PASS', sourceSha: expectedSha, jarSha256: state.jar_sha256, smokeScriptSha256: digest(fs.readFileSync(fileURLToPath(import.meta.url))),
    startedAt, finishedAt: new Date().toISOString(), stages, realWhatsapp: false, nativeDevices: false, rawGps: false };
  privateWrite(path.join(work, 'receipt.json'), JSON.stringify(receipt, null, 2) + '\n');
  console.log(JSON.stringify(receipt));
} catch (error) {
  const safe = /^[A-Z0-9_]+$/.test(error?.message ?? '') ? error.message : 'SMOKE_UNEXPECTED_FAILURE';
  privateWrite(path.join(work, 'failure.json'), JSON.stringify({ status: 'FAIL', code: safe, sourceSha: expectedSha, stages, startedAt, finishedAt: new Date().toISOString() }, null, 2) + '\n');
  console.error(JSON.stringify({ status: 'FAIL', code: safe, completedChecks: stages.length }));
  process.exitCode = 1;
}

}
main().catch((error) => {
  const code = /^[A-Z0-9_]+$/.test(error?.message ?? '') ? error.message : 'SMOKE_PREFLIGHT_FAILURE';
  console.error(JSON.stringify({ status: 'FAIL', code }));
  process.exitCode = 1;
});
