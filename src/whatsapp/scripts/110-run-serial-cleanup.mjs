/** Private parent/child shutdown barrier. No process termination occurs in Node. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { demand, digest, exact, token, uuid, privateBytes, readPrivate, privateWrite } from './070-worker-common.mjs';

export const protocol = 'WA110_SERIAL_CLEANUP_V1';
const common = ['schemaVersion', 'protocol', 'sourceSha', 'jarSha256', 'backendTree', 'attempt', 'profile',
  'parentPid', 'apiPid', 'parentNonce'];
const bindings = [...common, 'requesterPid', 'requestNonce', 'batchId', 'instance', 'runDirectory'];
const sha = value => typeof value === 'string' && /^[a-f0-9]{40}$/.test(value);
const hash = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const pid = value => Number.isSafeInteger(value) && value > 0;

function privateDirectory(directory) {
  const info = fs.lstatSync(directory);
  demand(info.isDirectory() && info.uid === process.getuid() && (info.mode & 0o777) === 0o700
    && fs.realpathSync(directory) === directory, 'SERIAL_DIRECTORY_NOT_PRIVATE');
}

export function atomicPrivateJson(file, value) {
  const pending = file + '.' + crypto.randomUUID() + '.pending';
  try {
    privateWrite(pending, JSON.stringify(value) + '\n');
    fs.linkSync(pending, file); // Atomic publication without replacing an existing request/ACK.
  } finally { if (fs.existsSync(pending)) fs.unlinkSync(pending); }
}

export function validateShutdownResponse(response, request, requestSha256) {
  demand(exact(response, [...bindings, 'requestSha256', 'status', 'shutdownMethod', 'apiExitCode', 'processExited', 'portClosed'])
    && bindings.every(key => response[key] === request[key]) && response.requestSha256 === requestSha256
    && response.status === 'API_STOPPED' && response.shutdownMethod === 'OWNING_PARENT_TERMINATE_GROUP'
    && Number.isSafeInteger(response.apiExitCode) && response.processExited === true && response.portClosed === true,
  'SERIAL_SHUTDOWN_ACK_INVALID');
}

export function serialCleanupConfig(input, cardId, environment = process.env,
  identity = { pid: process.pid, parentPid: process.ppid }) {
  if (environment.WA110_SERIAL_CLEANUP_DIR === undefined) return null;
  demand(cardId === 'VR-WA-110-RUN-TRACKING', 'SERIAL_CLEANUP_ONLY_110');
  const directory = environment.WA110_SERIAL_CLEANUP_DIR;
  demand(typeof directory === 'string' && path.isAbsolute(directory) && path.resolve(directory) === directory,
    'SERIAL_DIRECTORY_SCOPE_INVALID');
  const attempt = path.basename(path.dirname(directory));
  const expected = path.join(environment.HOME, '.local/share/vanrides/wa110/attempts', input.expectedSha, attempt, 'serial-cleanup');
  demand(/^[a-z0-9][a-z0-9-]{0,59}$/.test(attempt) && directory === expected, 'SERIAL_DIRECTORY_SCOPE_INVALID');
  privateDirectory(directory);
  const intentFile = path.join(directory, 'parent-intent-private.json'), intentHash = digest(privateBytes(intentFile));
  const intent = readPrivate(intentFile);
  const profile = environment.WA110_DEVICE_APK ? 'NATIVE_SERIAL_CLEANUP' : 'HOST_SERIAL_CLEANUP';
  demand(exact(intent, common) && intent.schemaVersion === 1 && intent.protocol === protocol
    && sha(intent.sourceSha) && intent.sourceSha === input.expectedSha
    && hash(intent.jarSha256) && intent.jarSha256 === input.state.jar_sha256 && sha(intent.backendTree)
    && intent.attempt === attempt && intent.profile === profile && token(intent.parentNonce)
    && pid(identity.pid) && pid(intent.parentPid) && intent.parentPid === identity.parentPid
    && pid(intent.apiPid) && intent.apiPid === input.state.api_pid, 'SERIAL_PARENT_INTENT_INVALID');
  let confirmed = false, audit = { protocol, profile, apiShutdown: 'UNCONFIRMED' };
  return {
    get confirmed() { return confirmed; },
    get audit() { return { ...audit }; },
    async shutdown({ frozen, work, batch, instance, checks, interruption },
      { timeout = 90000, pause = () => new Promise(resolve => setTimeout(resolve, 50)), now = Date.now } = {}) {
      demand(!confirmed && uuid(batch) && instance === 'wa070_synthetic_' + batch.replaceAll('-', '')
        && path.dirname(work) === input.base && checks.length > 0
        && checks.every(check => exact(check, ['check', 'status']) && /^[a-z][a-z0-9_]{0,159}$/.test(check.check) && check.status === 'PASS'),
      'SERIAL_FUNCTIONAL_SCOPE_INVALID');
      privateDirectory(work);
      privateDirectory(directory);
      demand(digest(privateBytes(intentFile)) === intentHash && frozen.backendTree === intent.backendTree, 'SERIAL_PARENT_INTENT_CHANGED');
      frozen.assertSource(); frozen.assertContainer(); // Last proof with the actual HTTP process alive.
      const requestFile = path.join(directory, 'shutdown-request-private.json');
      const responseFile = path.join(directory, 'shutdown-response-private.json');
      demand(!fs.existsSync(requestFile) && !fs.existsSync(responseFile), 'SERIAL_HANDSHAKE_ALREADY_USED');
      const request = { ...intent, requesterPid: identity.pid, requestNonce: crypto.randomBytes(32).toString('base64url'),
        batchId: batch, instance, runDirectory: work, functionalChecks: checks.map(check => check.check), assetSha256: frozen.assets };
      atomicPrivateJson(requestFile, request);
      const requestSha256 = digest(privateBytes(requestFile));
      const deadline = now() + timeout;
      while (!fs.existsSync(responseFile)) {
        interruption.assertActive();
        demand(now() < deadline, 'SERIAL_SHUTDOWN_ACK_TIMEOUT');
        await pause();
      }
      interruption.assertActive();
      privateDirectory(directory);
      demand(digest(privateBytes(intentFile)) === intentHash && digest(privateBytes(requestFile)) === requestSha256,
        'SERIAL_HANDSHAKE_CHANGED');
      validateShutdownResponse(readPrivate(responseFile), request, requestSha256);
      frozen.assertStoppedRuntime(); frozen.assertContainer(); // Independently reject a live/reused PID or listener.
      confirmed = true;
      audit = { protocol, profile, apiShutdown: 'PARENT_CONFIRMED', requestSha256,
        responseSha256: digest(privateBytes(responseFile)), backendTree: intent.backendTree };
      return { ...audit };
    },
  };
}

export function assertAfterSerialCleanup(frozen, serial) {
  if (serial) {
    demand(serial.confirmed, 'SERIAL_SHUTDOWN_UNCONFIRMED');
    frozen.assertStoppedRuntime();
  } else frozen.assertSource();
  frozen.assertContainer();
}
