import fs from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';
import { SmokeFailure, demand, digest, object, exact, token, privateBytes, readPrivate, properties, privateWrite } from './070-worker-common.mjs';

export const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
function backendRoot() {
  const selected = process.env.WA110_PHASE_BACKEND_ROOT;
  if (selected === undefined) return path.join(root, 'src/backend');
  demand(process.env.WA110_PHASE_PROFILE === 'NATIVE_SERIAL_PROVISIONING_V1'
    && root.startsWith(path.join(process.env.HOME, '.local/share/vanrides/wa110') + path.sep)
    && path.isAbsolute(selected) && path.resolve(selected) === selected && fs.realpathSync(selected) === selected,
  'PHASE_BACKEND_ROOT_INVALID');
  return selected;
}
export const backend = backendRoot();
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java')
  : '/usr/bin/java';

// The same JAR is checked repeatedly during a conversation. Keep only one chunk in memory.
export function jarDigest(file) {
  const fd = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    demand(fs.fstatSync(fd).isFile(), 'JAR_CHANGED');
    const digest = createHash('sha256');
    const buffer = Buffer.allocUnsafe(256 * 1024);
    for (let count; (count = fs.readSync(fd, buffer, 0, buffer.length, null)) > 0;) {
      digest.update(buffer.subarray(0, count));
    }
    return digest.digest('hex');
  } finally {
    fs.closeSync(fd);
  }
}

/** Read-only exit proof; a reused PID or an occupied listener must fail closed. */
export function assertApiStopped(pid, { probePid = value => process.kill(value, 0), inspectPort = () =>
  spawnSync('/usr/sbin/lsof', ['-nP', '-iTCP:18083', '-sTCP:LISTEN', '-Fpn'],
    { encoding: 'utf8', timeout: 5000, env: { PATH: '/usr/bin:/bin' } }) } = {}) {
  demand(Number.isSafeInteger(pid) && pid > 0, 'SERIAL_API_PID_INVALID');
  let absent = false;
  try { probePid(pid); } catch (error) { absent = error.code === 'ESRCH'; }
  demand(absent, 'SERIAL_API_PROCESS_STILL_PRESENT');
  const port = inspectPort();
  demand(!port.error && port.status === 1 && port.stdout === '' && port.stderr === '', 'SERIAL_API_PORT_NOT_CLOSED');
}

function loadInputFiles(directory, expectedSha, extra, requireApiPid) {
  demand(extra.length === 0 && typeof directory === 'string' && /^[a-f0-9]{40}$/.test(expectedSha ?? ''), 'EXPECTED_SOURCE_REQUIRED');
  demand(typeof process.env.HOME === 'string' && path.isAbsolute(process.env.HOME), 'RUNTIME_SCOPE_INVALID');
  const base = path.resolve(directory);
  demand(base === path.join(process.env.HOME, '.local/share/vanrides/local/wa070-backend-' + expectedSha.slice(0, 8)), 'RUNTIME_SCOPE_INVALID');
  let stat;
  try { stat = fs.lstatSync(base); } catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
  demand(stat.isDirectory() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o700, 'RUNTIME_DIRECTORY_INVALID');
  const stateFile = path.join(base, 'state.json'), operatorFile = path.join(base, 'operator-private.json');
  const state = readPrivate(stateFile), operator = readPrivate(operatorFile);
  const container = 'vanrides-wa070-' + expectedSha.slice(0, 8) + '-postgres';
  demand(state.repository === backend && state.source_sha === expectedSha && /^[0-9a-f]{64}$/.test(state.jar_sha256 ?? '')
    && (Number.isSafeInteger(state.api_pid) && state.api_pid > 0 || !requireApiPid && state.api_pid == null) && state.api_base_url === 'http://127.0.0.1:18083'
    && state.container === container && state.db_port === 51222, 'RUNTIME_STATE_INVALID');
  demand(exact(operator, ['environment', 'operatorCredential']) && operator.environment === 'wa070-local'
    && token(operator.operatorCredential), 'OPERATOR_INPUT_INVALID');
  const configFile = path.join(base, 'application-wa070.properties'), configBytes = privateBytes(configFile);
  let config;
  try { config = properties(configBytes); } catch { throw new SmokeFailure('LOCAL_CONFIG_INVALID'); }
  const required = { 'spring.datasource.url': 'jdbc:postgresql://127.0.0.1:51222/vanrides_wa070',
    'spring.datasource.username': 'vanrides_wa070', 'server.address': '127.0.0.1', 'server.port': '18083',
    'vanrides.wa.environment': 'wa070-local', 'vanrides.wa.enabled': 'true', 'vanrides.wa.test-cohort-only': 'true' };
  demand(Object.entries(required).every(([key, value]) => config.get(key) === value)
    && !!config.get('spring.datasource.password'), 'LOCAL_CONFIG_INVALID');
  return { base, expectedSha, state, operator, container, stateFile, operatorFile, configFile,
    inputHashes: [stateFile, operatorFile, configFile].map(file => digest(privateBytes(file))), jar: path.join(base, 'app.jar') };
}

export const loadInputs = (directory, expectedSha, extra = []) => loadInputFiles(directory, expectedSha, extra, true);
export const loadProvisionInputs = (directory, expectedSha, extra = []) => loadInputFiles(directory, expectedSha, extra, false);

function assetHashes({ includeRunLab, includePairingLab, includePassengerLab, includeAttendanceLab, includeRecoveryLab }) {
  const files = [];
  function add(directory) {
    demand(fs.lstatSync(directory).isDirectory(), 'ASSET_SYMLINK_DENIED');
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      demand(!entry.isSymbolicLink(), 'ASSET_SYMLINK_DENIED');
      if (entry.isDirectory()) add(file);
      else { demand(entry.isFile(), 'ASSET_TYPE_INVALID'); files.push(file); }
    }
  }
  add(path.join(root, 'src/whatsapp/worker'));
  add(path.join(root, 'src/whatsapp/agent'));
  files.push(path.join(root, 'src/whatsapp/transport/evolution-text.mjs'));
  files.push(path.join(root, 'src/whatsapp/transport/evolution-message.mjs'));
  files.push(path.join(root, 'src/whatsapp/transport/evolution-location.mjs'));
  files.push(path.join(root, 'src/whatsapp/transport/evolution-connection.mjs'));
  for (const name of fs.readdirSync(path.join(root, 'src/whatsapp/scripts')))
    if (/^(?:070-worker-|080-driver-).*\.mjs$/.test(name)
        || includePairingLab && /^060-.*\.mjs$/.test(name)
        || includePassengerLab && /^090-passenger-.*\.mjs$/.test(name)
        || includeRunLab && /^110-run-.*\.mjs$/.test(name)
        || includeRecoveryLab && /^120-delivery-.*\.mjs$/.test(name)
        || includeAttendanceLab && /^100-attendance-.*\.mjs$/.test(name)) files.push(path.join(root, 'src/whatsapp/scripts', name));
  return Object.fromEntries(files.sort().map(file => {
    demand(fs.lstatSync(file).isFile(), 'ASSET_TYPE_INVALID');
    return [path.relative(root, file), digest(fs.readFileSync(file))];
  }));
}

/** Read-only process/container checks. No backend/container is started by this module. */
function freezeRuntimeState(input, options, requireLiveApi) {
  const env = Object.fromEntries(['HOME', 'USER', 'LOGNAME', 'TMPDIR', 'LANG', 'LC_ALL']
    .filter(key => process.env[key]).map(key => [key, process.env[key]]));
  env.PATH = '/opt/homebrew/opt/openjdk@17/bin:/usr/bin:/bin:/usr/sbin:/sbin';
  const exec = (command, args) => {
    try { return execFileSync(command, args, { env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 15000, maxBuffer: 512 * 1024 }).trim(); }
    catch { throw new SmokeFailure('RUNTIME_COMMAND_FAILED'); }
  };
  const git = (...args) => exec('/usr/bin/git', ['--no-optional-locks', '-C', backend, ...args]);
  const assertCheckout = () => demand(git('rev-parse', 'HEAD') === input.expectedSha
    && git('status', '--porcelain', '--untracked-files=all') === '', 'SOURCE_NOT_FROZEN');
  assertCheckout();
  const backendTree = git('rev-parse', 'HEAD^{tree}');
  const assets = assetHashes(options);
  function assertListener(port, pid) {
    const lines = exec('/usr/sbin/lsof', ['-nP', '-iTCP:' + port, '-sTCP:LISTEN', '-Fpn']).split('\n');
    const pids = [...new Set(lines.filter(line => line.startsWith('p')))];
    const addresses = lines.filter(line => line.startsWith('n'));
    demand(pids.length === 1 && pids[0] === 'p' + pid && addresses.length > 0
      && addresses.every(line => line === 'n127.0.0.1:' + port), 'LISTENER_OWNERSHIP_INVALID');
  }
  function assertArtifacts() {
    demand(jarDigest(input.jar) === input.state.jar_sha256, 'JAR_CHANGED');
    demand([input.stateFile, input.operatorFile, input.configFile].every((file, i) => digest(privateBytes(file)) === input.inputHashes[i]), 'RUNTIME_INPUT_CHANGED');
  }
  function assertOwnedRuntime() {
    assertListener(18083, input.state.api_pid);
    const command = exec('/bin/ps', ['-p', String(input.state.api_pid), '-o', 'command=']);
    // execve may deliberately set argv[0] to "java". Verify the mapped executable,
    // not a display-name substring, before trusting this listener and JAR argument.
    const executable = exec('/usr/sbin/lsof', ['-a', '-p', String(input.state.api_pid), '-d', 'txt', '-Fn']);
    demand(executable.split('\n').includes('n' + fs.realpathSync(java)) && command.includes(input.jar), 'JAR_PROCESS_MISMATCH');
  }
  function assertFrozenSource() {
    assertCheckout();
    demand(git('rev-parse', 'HEAD^{tree}') === backendTree, 'BACKEND_TREE_CHANGED');
    demand(JSON.stringify(assetHashes(options)) === JSON.stringify(assets), 'WORKER_AGENT_OR_SMOKE_CHANGED');
    assertArtifacts();
  }
  function assertSource() {
    assertFrozenSource();
    if (requireLiveApi) assertOwnedRuntime();
    else assertPortClosed();
  }
  function assertPortClosed() {
    const observed = spawnSync('/usr/sbin/lsof', ['-nP', '-iTCP:18083', '-sTCP:LISTEN', '-Fpn'],
      { encoding: 'utf8', timeout: 5000, env: { PATH: '/usr/bin:/bin' } });
    demand(!observed.error && observed.status === 1 && observed.stdout === '' && observed.stderr === '', 'PHASE_API_PORT_NOT_CLOSED');
  }
  function assertStoppedRuntime() {
    assertFrozenSource();
    assertApiStopped(input.state.api_pid);
  }
  assertSource();
  demand(/^openjdk 17\./.test(exec(java, ['--version'])), 'JAVA17_REQUIRED');
  const docker = [path.join(process.env.HOME, '.docker/bin/docker'), '/opt/homebrew/bin/docker', '/usr/local/bin/docker',
    '/Applications/Docker.app/Contents/Resources/bin/docker'].find(file => {
    try { fs.accessSync(file, fs.constants.X_OK); return true; } catch { return false; }
  });
  demand(docker, 'LOCAL_DOCKER_REQUIRED');
  let endpoint;
  try { endpoint = JSON.parse(exec(docker, ['context', 'inspect', '--format', '{{json .Endpoints.docker}}'])); }
  catch { throw new SmokeFailure('LOCAL_DOCKER_CONTEXT_INVALID'); }
  demand(object(endpoint) && typeof endpoint.Host === 'string' && /^unix:\/\/\/[^\0\r\n?#]+$/.test(endpoint.Host), 'LOCAL_DOCKER_SOCKET_REQUIRED');
  try {
    const url = new URL(endpoint.Host);
    demand(url.hostname === '' && fs.statSync(decodeURIComponent(url.pathname)).isSocket(), 'LOCAL_DOCKER_SOCKET_REQUIRED');
  } catch { throw new SmokeFailure('LOCAL_DOCKER_SOCKET_REQUIRED'); }
  const dockerArgs = ['--host', endpoint.Host];
  function assertContainer() {
    let data;
    try { data = JSON.parse(exec(docker, [...dockerArgs, 'inspect', '--format',
      '{"name":{{json .Name}},"image":{{json .Config.Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"ports":{{json .NetworkSettings.Ports}}}', input.container])); }
    catch { throw new SmokeFailure('LOCAL_DATABASE_SCOPE_INVALID'); }
    const ports = data?.ports?.['5432/tcp'];
    demand(data.name === '/' + input.container && data.image === 'postgres:16-alpine' && data.running === true
      && data.labels?.['vanrides.card'] === 'VR-WA-070-AGENT-WORKFLOWS' && data.labels?.['vanrides.source-sha'] === input.expectedSha
      && Array.isArray(ports) && ports.length === 1 && ports[0].HostIp === '127.0.0.1' && ports[0].HostPort === '51222', 'LOCAL_DATABASE_SCOPE_INVALID');
  }
  assertContainer();
  return { env, exec, assets, backendTree, assertSource, assertFrozenSource, assertStoppedRuntime,
    assertArtifacts, assertContainer, assertListener, assertPortClosed, docker, dockerArgs };
}

export const freezeRuntime = (input, options = {}) => freezeRuntimeState(input, options, true);
export const freezeProvisionRuntime = (input, options = {}) => freezeRuntimeState(input, options, false);

export function createTools(input, frozen, work, batch, startOrdinal = 0) {
  demand(Number.isSafeInteger(startOrdinal) && startOrdinal >= 0 && startOrdinal <= 100000, 'OPERATOR_ORDINAL_INVALID');
  let ordinal = startOrdinal;
  const next = suffix => path.join(work, String(++ordinal).padStart(3, '0') + '-' + suffix);
  const operate = (operation, fields = {}) => {
    // Cleanup must still work when the HTTP process has failed or the source checkout changed.
    frozen.assertArtifacts(); frozen.assertContainer();
    const request = next('operator-request.json'), output = next('operator-result.json');
    privateWrite(request, JSON.stringify({ ...input.operator, operation, batchId: batch, ...fields }));
    const result = spawnSync(java, ['-Xms24m', '-Xmx192m', '-XX:MaxMetaspaceSize=192m',
      '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', input.jar,
      '--spring.main.web-application-type=none', '--vanrides.wa.operator.request-file=' + request,
      '--vanrides.wa.operator.output-file=' + output], { cwd: work,
      env: { ...frozen.env, SPRING_CONFIG_IMPORT: 'file:' + input.configFile + '[.properties]' },
      timeout: 45000, maxBuffer: 512 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
    privateWrite(next('operator.log'), Buffer.concat([result.stdout ?? Buffer.alloc(0), result.stderr ?? Buffer.alloc(0)]));
    demand(result.status === 0 && !result.error, 'OPERATOR_CLI_FAILED');
    const response = readPrivate(output);
    demand(response.status === 'COMMITTED' && response.operation === operation && response.batchId === batch, 'OPERATOR_RESULT_NOT_COMMITTED');
    return response;
  };
  const sql = (statement, { recordOutput = true } = {}) => {
    frozen.assertContainer();
    const result = spawnSync(frozen.docker, [...frozen.dockerArgs, 'exec', '-i', input.container, 'psql', '-X', '-U',
      'vanrides_wa070', '-d', 'vanrides_wa070', '-v', 'ON_ERROR_STOP=1', '-Atq'], { env: frozen.env,
      input: "SET statement_timeout='10s';\n" + statement + '\n', timeout: 15000, maxBuffer: 512 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
    if (recordOutput) privateWrite(next('database.log'), Buffer.concat([result.stdout ?? Buffer.alloc(0), result.stderr ?? Buffer.alloc(0)]));
    demand(result.status === 0 && !result.error, 'LOCAL_DATABASE_CHECK_FAILED');
    return result.stdout.toString('utf8').trim();
  };
  const count = statement => { const result = sql(statement); demand(/^\d+$/.test(result), 'DATABASE_COUNT_INVALID'); return Number(result); };
  return { next, operate, sql, count };
}
