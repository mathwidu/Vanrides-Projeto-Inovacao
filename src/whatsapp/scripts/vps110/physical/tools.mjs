/** VPS-only adapter: exact sandbox containers, private official CLI, SQL in memory. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { demand, digest, privateBytes, privateWrite, readPrivate, token, uuid, quote } from './source/integrations/whatsapp/scripts/070-worker-common.mjs';

export function fileHash(file) {
  const value = crypto.createHash('sha256'), fd = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try { const bytes = Buffer.allocUnsafe(262144); let size;
    while ((size = fs.readSync(fd, bytes, 0, bytes.length, null))) value.update(bytes.subarray(0, size));
    return value.digest('hex');
  } finally { fs.closeSync(fd); }
}
const fingerprint = file => { const s = fs.lstatSync(file); demand(s.isFile() && !s.isSymbolicLink(), 'ARTIFACT_TYPE_INVALID');
  return [s.dev, s.ino, s.size, s.mtimeMs, s.ctimeMs].join(':'); };

export function watchMemory(deadline = Infinity) {
  const available = Number(/^MemAvailable:\s+(\d+)/m.exec(fs.readFileSync('/proc/meminfo', 'utf8'))?.[1]) / 1024;
  // Cgroup pressure/events and all global PSI admission gates belong to the Python owner.
  demand(Number.isFinite(available) && available >= 2048, 'VPS_HEADROOM_LOW');
  demand(Date.now() < deadline, 'VPS_SESSION_TTL_EXPIRED');
}

export function ownedListener(port, pid, executable, main) {
  demand(Number.isInteger(port) && Number.isInteger(pid) && pid > 0, 'WORKER_LISTENER_INVALID');
  const args = fs.readFileSync('/proc/' + pid + '/cmdline', 'utf8').split('\0').filter(Boolean);
  demand(args[0] === executable && args.includes(main) && args.some(arg => /^--wa070-smoke-owner=[a-f0-9-]{36}$/.test(arg)), 'WORKER_PROCESS_OWNERSHIP_MISMATCH');
  const rows = fs.readFileSync('/proc/net/tcp', 'utf8').trim().split('\n').slice(1).map(line => line.trim().split(/\s+/))
    .filter(row => row[3] === '0A' && Number.parseInt(row[1].split(':')[1], 16) === port);
  demand(rows.length === 1 && rows[0][1].split(':')[0] === '0100007F', 'WORKER_LISTENER_NOT_LOOPBACK');
  const expected = 'socket:[' + rows[0][9] + ']';
  demand(fs.readdirSync('/proc/' + pid + '/fd').some(name => {
    try { return fs.readlinkSync('/proc/' + pid + '/fd/' + name) === expected; } catch { return false; }
  }), 'WORKER_LISTENER_PID_MISMATCH');
}

export function createTools(base, context, assertActive, { run = spawnSync, remainingMs = () => Infinity } = {}) {
  const contract = readPrivate(path.join(base, 'contract.json')), seal = readPrivate(path.join(base, 'SHA256SUMS.json'));
  const manifestFile = path.join(base, 'source-manifest.json'), manifest = readPrivate(manifestFile);
  demand(fileHash(manifestFile) === contract.moduleSha256, 'MODULE_DIGEST_MISMATCH');
  const owner = readPrivate(path.join(base, 'runtime/owner.json'));
  demand(owner.attemptId === context.attemptId && owner.sourceSha === contract.backend.commit && token(owner.nonce), 'OWNER_CONTEXT_MISMATCH');
  const work = path.join(base, 'session'), configFile = path.join(base, 'runtime/application.properties');
  const operatorFile = path.join(base, 'runtime/operator-private.json'), operator = readPrivate(operatorFile);
  demand(operator.environment === 'wa070-local' && token(operator.operatorCredential), 'OPERATOR_INPUT_INVALID');
  const project = contract.destination.projectPrefix + context.attemptId;
  const environment = { PATH: '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin', HOME: process.env.HOME,
    LANG: 'C.UTF-8', DOCKER_CONFIG: path.join(base, 'runtime/docker-config'), WA110_OWNER: owner.nonce,
    WA110_API_UID: String(process.getuid()), WA110_API_GID: String(process.getgid()),
    WA110_JAVA_IMAGE: contract.images.java, WA110_POSTGRES_IMAGE: contract.images.postgres,
    WA070_MODEL_MODE: 'disabled' };
  const docker = ['compose', '--project-directory', base, '-f', path.join(base, 'compose.yaml'), '-p', project];
  function command(args, { stdin, timeout = 30000, guarded = true } = {}) {
    if (guarded) assertActive();
    timeout = Math.floor(Math.min(timeout, remainingMs())); demand(timeout > 0, 'SHUTDOWN_BUDGET_EXPIRED');
    const result = run('docker', args, { cwd: base, env: environment, input: stdin,
      encoding: 'utf8', timeout, maxBuffer: 1048576, stdio: [stdin === undefined ? 'ignore' : 'pipe', 'pipe', 'ignore'] });
    demand(result.status === 0 && !result.error, 'OWNED_DOCKER_COMMAND_FAILED');
    return result.stdout.trim();
  }
  const jar = path.join(base, 'inputs/backend.jar');
  demand(fileHash(jar) === contract.backend.jarSha256, 'JAR_DIGEST_MISMATCH');
  const jarFingerprint = fingerprint(jar), configHash = digest(privateBytes(configFile)), operatorHash = digest(privateBytes(operatorFile));
  function assertArtifacts(final = false) {
    demand(fingerprint(jar) === jarFingerprint && (!final || fileHash(jar) === contract.backend.jarSha256), 'JAR_CHANGED');
    for (const [name, hash] of Object.entries(manifest.files)) demand(fileHash(path.join(base, name)) === hash, 'MODULE_SOURCE_CHANGED');
    demand(fileHash(path.join(base, 'contract.json')) === seal['contract.json']
      && digest(privateBytes(configFile)) === configHash && digest(privateBytes(operatorFile)) === operatorHash, 'RUNTIME_ARTIFACT_CHANGED');
  }
  function assertContainer() {
    const ids = command(['ps', '-aq', '--filter', 'label=com.docker.compose.project=' + project], { guarded: false }).split(/\s+/).filter(Boolean);
    demand(ids.length === 2 && ids.every(id => /^[a-f0-9]{12,64}$/.test(id)), 'OWNED_CONTAINER_SET_CHANGED');
    const rows = JSON.parse(command(['inspect', ...ids], { guarded: false }));
    const services = new Set();
    for (const row of rows) {
      const labels = row.Config.Labels, service = labels['com.docker.compose.service'];
      demand(['api', 'postgres'].includes(service) && !services.has(service)
        && labels['com.docker.compose.project'] === project && labels['wa110.owner'] === owner.nonce
        && labels['wa110.backend'] === contract.backend.commit && labels['traefik.enable'] === 'false'
        && row.Config.Image === contract.images[service === 'api' ? 'java' : 'postgres']
        && row.Config.User === (service === 'api' ? process.getuid() + ':' + process.getgid() : '0')
        && row.HostConfig.LogConfig.Type === 'none' && row.State.Running === true
        && row.State.OOMKilled === false && row.RestartCount === 0, 'OWNED_CONTAINER_SCOPE_INVALID');
      const expectedNetworks = [project + '_private', ...(service === 'api' ? [project + '_edge'] : [])];
      demand(Object.keys(row.NetworkSettings.Networks).sort().join() === expectedNetworks.sort().join(), 'OWNED_CONTAINER_NETWORK_CHANGED');
      const ports = row.HostConfig.PortBindings ?? {};
      demand(JSON.stringify(ports) === JSON.stringify(service === 'api' ? { '18083/tcp': [{ HostIp: '127.0.0.1', HostPort: '28083' }] } : {}), 'OWNED_CONTAINER_EXPOSURE_CHANGED');
      const published = Object.fromEntries(Object.entries(row.NetworkSettings.Ports ?? {}).filter(([, value]) => value?.length));
      demand(JSON.stringify(published) === JSON.stringify(ports), 'OWNED_CONTAINER_RUNTIME_EXPOSURE_CHANGED');
      const expected = service === 'api' ? { '/opt/wa110/app.jar': ['inputs/backend.jar', false],
        '/run/wa110/application.properties': ['runtime/application.properties', false], '/run/wa110/session': ['session', true] }
        : { '/run/secrets/db-password': ['runtime/db-password', false], '/run/secrets/pgpass': ['runtime/pgpass', false] };
      const binds = Object.fromEntries(row.Mounts.filter(mount => mount.Type === 'bind').map(mount => [mount.Destination, mount]));
      demand(Object.keys(binds).sort().join() === Object.keys(expected).sort().join()
        && Object.entries(expected).every(([destination, [source, rw]]) => binds[destination].Source === path.join(base, source)
          && binds[destination].RW === rw)
        && row.Mounts.every(mount => ['bind', 'tmpfs'].includes(mount.Type)), 'OWNED_CONTAINER_MOUNT_CHANGED');
      services.add(service);
    }
  }
  let ordinal = 0;
  const next = suffix => { demand(/^[a-z][a-z0-9.-]{1,79}$/.test(suffix), 'PRIVATE_ARTIFACT_NAME_INVALID');
    return path.join(work, String(++ordinal).padStart(3, '0') + '-' + suffix); };
  function sql(statement) {
    assertActive(); assertContainer();
    demand(typeof statement === 'string' && statement.length <= 65536, 'SQL_STATEMENT_INVALID');
    return command([...docker, 'exec', '-T', '--user', '0', 'postgres', 'psql', '-XAtq', '-h', '/var/run/postgresql',
      '-U', 'vanrides_wa070', '-d', 'vanrides_wa070', '-v', 'ON_ERROR_STOP=1'],
    { stdin: "SET statement_timeout='10s';\n" + statement + '\n', timeout: 15000 });
  }
  const count = statement => { const value = sql(statement); demand(/^\d+$/.test(value), 'DATABASE_COUNT_INVALID'); return Number(value); };
  function operate(operation, fields = {}) {
    assertActive(); assertArtifacts(); assertContainer();
    demand(['create-test-cohort', 'provision-service-credential', 'driver-bootstrap', 'reset-test-data', 'revoke-service-credential'].includes(operation), 'OPERATOR_OPERATION_DENIED');
    if (operation === 'provision-service-credential') demand(['CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB'].includes(fields.principal)
      && fields.allowedInstanceId === context.instance, 'OPERATOR_CREDENTIAL_SCOPE_INVALID');
    if (operation === 'driver-bootstrap') demand(fields.identityId === context.actor, 'OPERATOR_DRIVER_SCOPE_INVALID');
    if (operation === 'revoke-service-credential') demand(uuid(fields.credentialId)
      && count(`SELECT count(*) FROM wa.service_credential c JOIN wa.test_cohort t ON t.id=${quote(context.batch)} WHERE c.id=${quote(fields.credentialId)} AND c.environment=t.environment AND t.environment='wa070-local' AND t.synthetic=true AND c.allowed_instance_id=${quote(context.instance)} AND c.principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER','SYSTEM_JOB');`) === 1, 'OPERATOR_REVOKE_SCOPE_INVALID');
    const request = next('operator-request.json'), output = next('operator-result.json');
    privateWrite(request, JSON.stringify({ ...operator, operation, batchId: context.batch, ...fields }));
    const timeout = Math.floor(Math.min(45000, remainingMs())); demand(timeout > 0, 'SHUTDOWN_BUDGET_EXPIRED');
    const result = run('docker', [...docker, 'exec', '-T', 'api', 'java', '-Xms24m', '-Xmx192m',
      '-XX:MaxMetaspaceSize=192m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', '/opt/wa110/app.jar',
      '--spring.main.web-application-type=none', '--vanrides.wa.operator.request-file=/run/wa110/session/' + path.basename(request),
      '--vanrides.wa.operator.output-file=/run/wa110/session/' + path.basename(output)],
    { cwd: base, env: environment, timeout, stdio: 'ignore' });
    demand(result.status === 0 && !result.error, 'OPERATOR_CLI_FAILED');
    const response = readPrivate(output);
    demand(response.status === 'COMMITTED' && response.operation === operation && response.batchId === context.batch
      && response.environment === 'wa070-local', 'OPERATOR_RESULT_NOT_COMMITTED');
    return response;
  }
  const frozen = { env: environment, assertArtifacts, assertContainer,
    assertSource() { assertActive(); assertArtifacts(); assertContainer(); },
    assertListener(port, pid) { assertActive(); ownedListener(port, pid, process.execPath, path.join(base, 'source/integrations/whatsapp/worker/main.mjs')); } };
  return { input: { base, expectedSha: contract.backend.commit, configFile, operatorFile, operator,
    state: { api_base_url: 'http://127.0.0.1:28083', jar_sha256: contract.backend.jarSha256 } },
    frozen, work, next, sql, count, operate, contract, finalProof: () => assertArtifacts(true) };
}
