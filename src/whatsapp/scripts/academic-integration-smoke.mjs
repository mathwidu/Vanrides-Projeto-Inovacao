#!/usr/bin/env node
/** Academic checkout: same JAR + real PostgreSQL/Node worker, fake loopback Evolution only. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createTools } from './070-worker-preflight.mjs';
import { provision, connectionEvent, runScenarios } from './070-worker-scenarios.mjs';
import { runDriverScenarios } from './080-driver-scenarios.mjs';
import { runAttendanceScenarios } from './100-attendance-scenarios.mjs';
import { runTrackingScenarios } from './110-run-scenarios.mjs';
import { startProvider, createWorkerProcess } from './070-worker-runtime.mjs';
import { createInterruption } from './070-worker-lifecycle.mjs';
import { expectedApiExit } from './academic-process-exit.mjs';
import { SmokeFailure, demand, digest, quote, privateWrite, localJson, safeCode } from './070-worker-common.mjs';

const root = fs.realpathSync(fileURLToPath(new URL('../../../', import.meta.url)));
const backend = path.join(root, 'src/backend');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const minimal = Object.fromEntries(['PATH', 'HOME', 'JAVA_HOME', 'TMPDIR', 'LANG', 'LC_ALL']
  .filter(key => process.env[key]).map(key => [key, process.env[key]]));
const gitEnv = { PATH: process.env.PATH, HOME: '/nonexistent', XDG_CONFIG_HOME: '/nonexistent',
  LC_ALL: 'C', GIT_CONFIG_GLOBAL: '/dev/null', GIT_CONFIG_SYSTEM: '/dev/null',
  GIT_CONFIG_NOSYSTEM: '1', GIT_NO_REPLACE_OBJECTS: '1' };
function command(binary, args, { input, env = minimal, timeout = 60000 } = {}) {
  try { return execFileSync(binary, args, { input, env, encoding: 'utf8', timeout,
    stdio: ['pipe', 'pipe', 'pipe'], maxBuffer: 1024 * 1024 }).trim(); }
  catch { throw Object.assign(new Error('SYNTHETIC_RUNTIME_COMMAND_FAILED'), { code: 'SYNTHETIC_RUNTIME_COMMAND_FAILED' }); }
}
const git = (...args) => command('git', ['--no-replace-objects', '-c', 'core.fsmonitor=false',
  '-c', 'core.untrackedCache=false', '-C', root, ...args], { env: gitEnv });

async function reserveApiPort() {
  const probe = http.createServer();
  await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(18083, '127.0.0.1', resolve); });
  await new Promise(resolve => probe.close(resolve));
}
async function run() {
  process.umask(0o077);
  demand(process.argv.length === 2, 'SMOKE_ARGUMENTS_INVALID');
  demand(['linux', 'darwin'].includes(process.platform), 'SMOKE_POSIX_REQUIRED');
  for (const [key, value] of Object.entries(process.env)) {
    if (value && (key.startsWith('GIT_') && key !== 'GIT_PAGER'
      || ['DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'].includes(key)))
      demand(false, 'SMOKE_RUNTIME_OVERRIDE_REJECTED');
  }
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java')
    : command('/usr/bin/which', ['java']);
  demand(fs.realpathSync(git('rev-parse', '--show-toplevel')) === root, 'SMOKE_GIT_ROOT_MISMATCH');
  const sourceSha = git('rev-parse', 'HEAD'), sourceTree = git('rev-parse', 'HEAD^{tree}');
  if (process.env.VANRIDES_EXPECTED_SOURCE_SHA !== undefined)
    demand(/^[a-f0-9]{40}$/.test(process.env.VANRIDES_EXPECTED_SOURCE_SHA)
      && process.env.VANRIDES_EXPECTED_SOURCE_SHA === sourceSha, 'SMOKE_SOURCE_SHA_MISMATCH');
  demand(git('status', '--porcelain', '--untracked-files=all') === '', 'SMOKE_CLEAN_CHECKOUT_REQUIRED');
  const jars = fs.readdirSync(path.join(backend, 'target')).filter(name => name.endsWith('.jar'));
  demand(jars.length === 1, 'SMOKE_PACKAGED_JAR_REQUIRED');
  const jar = path.join(backend, 'target', jars[0]);
  demand(fs.lstatSync(jar).isFile() && !fs.lstatSync(jar).isSymbolicLink(), 'SMOKE_JAR_TYPE_INVALID');
  const jarHash = digest(fs.readFileSync(jar));
  const assertSource = () => demand(git('rev-parse', 'HEAD') === sourceSha
    && git('rev-parse', 'HEAD^{tree}') === sourceTree
    && git('status', '--porcelain', '--untracked-files=all') === ''
    && digest(fs.readFileSync(jar)) === jarHash, 'SMOKE_SOURCE_OR_JAR_CHANGED');
  const context = command('docker', ['context', 'show']);
  const endpoint = command('docker', ['context', 'inspect', context, '--format', '{{.Endpoints.docker.Host}}']);
  demand(/^unix:\/\/\//.test(endpoint), 'SMOKE_LOCAL_DOCKER_REQUIRED');
  const dockerArgs = ['--host', endpoint];
  const docker = (...args) => command('docker', [...dockerArgs, ...args]);
  docker('info', '--format', '{{.ServerVersion}}');
  await reserveApiPort();
  const work = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'vanrides-academic-smoke-')));
  const interruption = createInterruption();
  const owner = crypto.randomUUID(), containerName = 'vanrides-academic-' + owner;
  const cidfile = path.join(work, 'container.id');
  const databasePassword = crypto.randomBytes(32).toString('base64url');
  let container = null, api = null, apiExited = false, apiExit = null, apiSignal = null, apiStopRequested = false, failure = null;
  const cleanupErrors = [];
  const checks = [], totals = [];
  const assertContainer = () => {
    demand(container && docker('inspect', '--format', '{{index .Config.Labels "vanrides.academic.owner"}}', container) === owner,
      'SMOKE_CONTAINER_OWNERSHIP_CHANGED');
  };
  const assertListener = (port, pid) => {
    demand(Number.isSafeInteger(pid) && pid > 0, 'SMOKE_PROCESS_OWNER_INVALID');
    process.kill(pid, 0);
    const lines = command('lsof', ['-nP', '-a', '-p', String(pid), '-iTCP:' + port, '-sTCP:LISTEN', '-Fpn']).split('\n');
    demand(lines.includes('p' + pid) && lines.filter(line => line.startsWith('n')).length === 1
      && lines.includes('n127.0.0.1:' + port), 'SMOKE_LISTENER_OWNERSHIP_CHANGED');
  };
  try {
    const dbEnv = path.join(work, 'postgres.env');
    privateWrite(dbEnv, `POSTGRES_DB=vanrides_wa070\nPOSTGRES_USER=vanrides_wa070\nPOSTGRES_PASSWORD=${databasePassword}\n`);
    docker('run', '--detach', '--cidfile', cidfile, '--name', containerName,
      '--label', 'vanrides.academic.owner=' + owner, '--env-file', dbEnv,
      '--publish', '127.0.0.1::5432', 'postgres:16-alpine');
    container = fs.readFileSync(cidfile, 'utf8').trim();
    demand(/^[a-f0-9]{64}$/.test(container), 'SMOKE_CONTAINER_ID_INVALID');
    assertContainer();
    const binding = docker('port', container, '5432/tcp');
    demand(/^127\.0\.0\.1:[1-9][0-9]{0,4}$/.test(binding), 'SMOKE_DATABASE_NOT_LOOPBACK');
    const dbPort = Number(binding.split(':')[1]);
    const readyDeadline = Date.now() + 60000;
    let ready = false;
    while (Date.now() < readyDeadline && !ready) {
      interruption.assertActive();
      try { docker('exec', container, 'pg_isready', '-h', '127.0.0.1', '-U', 'vanrides_wa070', '-d', 'vanrides_wa070'); ready = true; }
      catch { await pause(500); }
    }
    demand(ready, 'SMOKE_DATABASE_START_FAILED');
    const configFile = path.join(work, 'application.properties');
    const operatorToken = crypto.randomBytes(32).toString('base64url');
    const properties = {
      'spring.profiles.active': 'local', 'server.address': '127.0.0.1', 'server.port': '18083',
      'spring.datasource.url': `jdbc:postgresql://127.0.0.1:${dbPort}/vanrides_wa070`,
      'spring.datasource.username': 'vanrides_wa070', 'spring.datasource.password': databasePassword,
      'spring.jpa.hibernate.ddl-auto': 'validate', 'spring.jpa.show-sql': 'false',
      'spring.datasource.hikari.maximum-pool-size': '6', 'spring.main.banner-mode': 'off',
      'vanrides.wa.enabled': 'true', 'vanrides.wa.test-cohort-only': 'true',
      'vanrides.wa.environment': 'wa070-local', 'vanrides.wa.public-base-url': 'https://localhost:18443',
      'vanrides.wa.start-link-base-url': 'https://vanrides.invalid',
      'vanrides.wa.hash-key': crypto.randomBytes(32).toString('base64url'),
      'vanrides.wa.response-key': crypto.randomBytes(32).toString('base64url'),
      'vanrides.wa.operator-credential': operatorToken,
      'vanrides.wa.operator-expires-at': new Date(Date.now() + 3600_000).toISOString(),
      'vanrides.rate-limit.sensitive-limit': '10000',
    };
    privateWrite(configFile, Object.entries(properties).map(([key, value]) => key + '=' + value).join('\n') + '\n');
    const apiStdout = fs.openSync(path.join(work, 'api.log'), 'wx', 0o600);
    try {
      api = spawn(java, ['-Xms24m', '-Xmx512m', '-XX:MaxMetaspaceSize=256m', '-XX:+UseSerialGC',
        '-XX:ActiveProcessorCount=2', '-jar', jar], { cwd: work,
        env: { ...minimal, SPRING_CONFIG_IMPORT: 'file:' + configFile + '[.properties]' },
        stdio: ['ignore', apiStdout, apiStdout] });
    } finally { fs.closeSync(apiStdout); }
    api.on('exit', (code, signal) => { apiExited = true; apiExit = code; apiSignal = signal; });
    api.on('error', () => { apiExited = true; });
    let healthy = false;
    const deadline = Date.now() + 90000;
    while (Date.now() < deadline && !healthy && !apiExited) {
      interruption.assertActive();
      try { const result = await localJson('http://127.0.0.1:18083', '/actuator/health', { timeout: 1000 });
        healthy = result.status === 200 && result.data.status === 'UP'; }
      catch { await pause(250); }
    }
    demand(healthy, 'SMOKE_API_START_FAILED'); assertListener(18083, api.pid);
    const input = { jar, configFile, container, expectedSha: sourceSha,
      state: { api_base_url: 'http://127.0.0.1:18083', jar_sha256: jarHash },
      operator: { environment: 'wa070-local', operatorCredential: operatorToken } };
    const frozen = { env: minimal, docker: 'docker', dockerArgs, assertContainer, assertSource, assertListener,
      assertArtifacts: assertSource };
    for (const [scenario, execute] of [['070', runScenarios], ['080', runDriverScenarios],
      ['100', runAttendanceScenarios], ['110', runTrackingScenarios]]) {
      interruption.assertActive(); assertSource();
      const scenarioWork = path.join(work, scenario); fs.mkdirSync(scenarioWork, { mode: 0o700 });
      const batch = crypto.randomUUID(), actor = crypto.randomUUID(), aliasId = crypto.randomUUID();
      const instance = 'wa070_synthetic_' + batch.replaceAll('-', ''), address = '000000000000000@s.whatsapp.net';
      const peers = [14, 13].map(length => ({ actor: crypto.randomUUID(), aliasId: crypto.randomUUID(),
        address: '0'.repeat(length) + '@s.whatsapp.net' }));
      const tools = createTools(input, frozen, scenarioWork, batch), credentials = [];
      let worker = null, provider = null, scenarioFailure = null;
      try {
        await provision({ input, tools, batch, actor, aliasId, instance, address, peers, credentials, attendance: true, interruption });
        await connectionEvent({ input, instance, credentials, interruption }, 'open');
        const apiKey = 'synthetic-provider-' + crypto.randomBytes(24).toString('hex');
        provider = await startProvider(instance, address, apiKey, peers.map(peer => peer.address));
        worker = createWorkerProcess({ input, frozen, tools, work: scenarioWork, instance, provider,
          conversation: credentials[1], delivery: credentials[2], job: credentials[3], apiKey,
          assertActive: interruption.assertActive });
        await worker.start();
        const result = await execute({ input, tools, work: scenarioWork, actor, instance, address, peers,
          credentials, worker, provider, apiKey, interruption,
          passed: check => { checks.push({ scenario, check, status: 'PASS' });
            console.log(JSON.stringify({ scenario, check, status: 'PASS' })); } });
        totals.push({ scenario, ...result });
      } catch (error) { scenarioFailure = error; }
      finally {
        for (const resource of [worker, provider]) if (resource) {
          try { await resource.stop(); } catch { cleanupErrors.push('SCENARIO_OWNED_PROCESS_CLEANUP_FAILED'); }
        }
        try {
          if (tools.count(`SELECT count(*) FROM wa.test_cohort WHERE id=${quote(batch)}`) === 1)
            tools.operate('reset-test-data');
          for (const item of credentials) tools.operate('revoke-service-credential',
            { credentialId: item.credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' });
          demand(tools.count(`SELECT count(*) FROM wa.identity WHERE test_cohort_id=${quote(batch)}`) === 0,
            'SMOKE_SCOPED_RESET_FAILED');
        } catch { cleanupErrors.push('SCENARIO_SCOPED_RESET_FAILED'); }
      }
      if (scenarioFailure) throw scenarioFailure;
      demand(cleanupErrors.length === 0, 'SMOKE_SCENARIO_CLEANUP_FAILED');
    }
    assertSource();
  } catch (error) { failure = error; }
  finally {
    if (api && !apiExited) {
      apiStopRequested = api.kill('SIGTERM');
      const deadline = Date.now() + 30000;
      while (!apiExited && Date.now() < deadline) await pause(100);
      if (!apiExited) { api.kill('SIGKILL'); cleanupErrors.push('API_FORCED_STOP'); }
    }
    if (apiExited && !failure && !expectedApiExit({ requested: apiStopRequested, code: apiExit, signal: apiSignal }))
      cleanupErrors.push('API_EXIT_FAILED');
    try {
      if (!container && fs.existsSync(cidfile)) {
        const candidate = fs.readFileSync(cidfile, 'utf8').trim();
        demand(/^[a-f0-9]{64}$/.test(candidate), 'SMOKE_CONTAINER_ID_INVALID'); container = candidate;
      }
      if (!container) {
        try { container = docker('inspect', '--format', '{{.Id}}', containerName); } catch { /* No created container. */ }
      }
      if (container) { assertContainer(); docker('rm', '--force', '--volumes', container); }
    } catch { cleanupErrors.push('OWNED_CONTAINER_CLEANUP_FAILED'); }
    interruption.dispose();
    fs.rmSync(work, { recursive: true, force: true });
  }
  if (failure) throw failure instanceof SmokeFailure ? failure : new SmokeFailure('SMOKE_SCENARIO_FAILED');
  demand(cleanupErrors.length === 0, 'SMOKE_CLEANUP_FAILED');
  console.log(JSON.stringify({ status: 'PASS', sourceSha, sourceTree, jarSha256: jarHash,
    checks: checks.length, scenarios: totals, realWhatsApp: false, physicalDevices: false,
    provider: 'FAKE_LOOPBACK', cleanup: 'OWNED_RESOURCES_REMOVED' }));
}
run().catch(error => {
  const result = { status: 'FAIL', code: safeCode(error, 'SMOKE_PREFLIGHT_FAILED') };
  if (error instanceof SmokeFailure && error.code === 'CONTROL_SUMMARY_INVALID') result.detail = error.controlSummary;
  console.error(JSON.stringify(result)); process.exitCode = 1;
});
