#!/usr/bin/env node
/** Same-JAR local050 smoke. No real WhatsApp, device, phone, provider credential or GPS input. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';

class SmokeFailure extends Error {
  constructor(code) { super(code); this.code = code; }
}
const demand = (condition, code) => { if (!condition) throw new SmokeFailure(code); };
const safeCode = (error, fallback) => error instanceof SmokeFailure ? error.code : fallback;
const digest = (value) => crypto.createHash('sha256').update(value).digest('hex');
const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const isUuid = (value) => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
const isToken = (value) => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').length === 32 && Buffer.from(value, 'base64url').toString('base64url') === value;
const exactKeys = (object, names) => isObject(object) && Object.keys(object).sort().join('\0') === [...names].sort().join('\0');
const canonical = (value) => Array.isArray(value) ? '[' + value.map(canonical).join(',') + ']'
  : isObject(value) ? '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}' : JSON.stringify(value);
const privateWrite = (file, value) => fs.writeFileSync(file, value, { flag: 'wx', mode: 0o600 });

function privateBytes(file) {
  try {
    const stat = fs.lstatSync(file);
    demand(stat.isFile() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600, 'PRIVATE_FILE_REQUIRED');
    return fs.readFileSync(file);
  } catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
}
function readPrivate(file) {
  try { const value = JSON.parse(privateBytes(file).toString('utf8')); demand(isObject(value), 'PRIVATE_OBJECT_REQUIRED'); return value; }
  catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
}
function properties(bytes) {
  const result = new Map();
  for (const line of bytes.toString('utf8').split(/\r?\n/)) {
    if (/^\s*(?:[#!]|$)/.test(line)) continue;
    const match = /^\s*([^\s=:]+)\s*[=:]\s*(.*?)\s*$/.exec(line);
    demand(match && !line.trimEnd().endsWith('\\'), 'LOCAL_CONFIG_INVALID');
    result.set(match[1], match[2]);
  }
  return result;
}

async function main() {
  process.umask(0o077);
  const [directory, expectedSha, ...extra] = process.argv.slice(2);
  demand(extra.length === 0 && typeof directory === 'string' && /^[a-f0-9]{40}$/.test(expectedSha ?? ''), 'EXPECTED_SOURCE_REQUIRED');
  demand(typeof process.env.HOME === 'string' && path.isAbsolute(process.env.HOME), 'RUNTIME_SCOPE_INVALID');
  const base = path.resolve(directory);
  demand(base === path.join(process.env.HOME, '.local/share/vanrides/local/wa050-backend-' + expectedSha.slice(0, 8)), 'RUNTIME_SCOPE_INVALID');
  let baseStat;
  try { baseStat = fs.lstatSync(base); } catch { throw new SmokeFailure('PRIVATE_INPUT_INVALID'); }
  demand(baseStat.isDirectory() && baseStat.uid === process.getuid() && (baseStat.mode & 0o777) === 0o700, 'RUNTIME_DIRECTORY_INVALID');
  const stateFile = path.join(base, 'state.json'), operatorFile = path.join(base, 'operator-private.json');
  const state = readPrivate(stateFile), operator = readPrivate(operatorFile);
  const repository = path.resolve(fileURLToPath(new URL('../../backend/', import.meta.url)));
  const container = 'vanrides-wa050-' + expectedSha.slice(0, 8) + '-postgres';
  demand(state.repository === repository && state.source_sha === expectedSha && /^[0-9a-f]{64}$/.test(state.jar_sha256 ?? '')
    && Number.isSafeInteger(state.api_pid) && state.api_pid > 0 && state.api_base_url === 'http://127.0.0.1:18082'
    && state.container === container && state.db_port === 51221, 'RUNTIME_STATE_INVALID');
  demand(exactKeys(operator, ['environment', 'operatorCredential']) && operator.environment === 'wa050-local'
    && isToken(operator.operatorCredential), 'OPERATOR_INPUT_INVALID');
  const configFile = path.join(base, 'application-wa050.properties');
  const configBytes = privateBytes(configFile), config = properties(configBytes);
  const requiredConfig = {
    'spring.datasource.url': 'jdbc:postgresql://127.0.0.1:51221/vanrides_wa050',
    'spring.datasource.username': 'vanrides_wa050',
    'server.address': '127.0.0.1', 'server.port': '18082',
    'vanrides.wa.environment': 'wa050-local', 'vanrides.wa.enabled': 'true', 'vanrides.wa.test-cohort-only': 'true',
  };
  demand(Object.entries(requiredConfig).every(([key, value]) => config.get(key) === value), 'LOCAL_CONFIG_INVALID');
  const jar = path.join(base, 'app.jar');
  const scriptFile = fileURLToPath(import.meta.url), scriptSha = digest(fs.readFileSync(scriptFile));
  const stateSha = digest(privateBytes(stateFile)), operatorSha = digest(privateBytes(operatorFile)), configSha = digest(configBytes);
  const executable = '/opt/homebrew/opt/openjdk@17/bin/java';
  const env = Object.fromEntries(['HOME', 'USER', 'LOGNAME', 'TMPDIR', 'LANG', 'LC_ALL'].filter(k => process.env[k]).map(k => [k, process.env[k]]));
  env.PATH = '/opt/homebrew/opt/openjdk@17/bin:/usr/bin:/bin:/usr/sbin:/sbin';
  const exec = (command, args) => execFileSync(command, args, { env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000, maxBuffer: 512 * 1024 }).trim();
  const git = (...args) => exec('/usr/bin/git', ['--no-optional-locks', '-C', repository, ...args]);
  const assertSource = () => {
    demand(git('rev-parse', 'HEAD') === expectedSha && git('status', '--porcelain', '--untracked-files=all') === '', 'SOURCE_NOT_FROZEN');
    demand(digest(fs.readFileSync(jar)) === state.jar_sha256, 'JAR_CHANGED');
    demand(digest(fs.readFileSync(scriptFile)) === scriptSha && digest(privateBytes(stateFile)) === stateSha
      && digest(privateBytes(operatorFile)) === operatorSha && digest(privateBytes(configFile)) === configSha, 'RUNTIME_INPUT_CHANGED');
    const listener = exec('/usr/sbin/lsof', ['-nP', '-iTCP:18082', '-sTCP:LISTEN', '-Fpn']).split('\n');
    const pids = [...new Set(listener.filter(line => line.startsWith('p')))];
    const addresses = listener.filter(line => line.startsWith('n'));
    demand(pids.length === 1 && pids[0] === 'p' + state.api_pid && addresses.length > 0
      && addresses.every(line => line === 'n127.0.0.1:18082'), 'LISTENER_NOT_OWNED_BY_EXPECTED_JAR');
    demand(exec('/bin/ps', ['-p', String(state.api_pid), '-o', 'command=']).includes(jar), 'JAR_PROCESS_MISMATCH');
  };
  assertSource();
  const docker = [path.join(process.env.HOME, '.docker/bin/docker'), '/opt/homebrew/bin/docker', '/usr/local/bin/docker', '/Applications/Docker.app/Contents/Resources/bin/docker']
    .find(file => { try { fs.accessSync(file, fs.constants.X_OK); return true; } catch { return false; } });
  demand(docker, 'LOCAL_DOCKER_REQUIRED');
  let endpoint;
  try { endpoint = JSON.parse(exec(docker, ['context', 'inspect', '--format', '{{json .Endpoints.docker}}'])); }
  catch { throw new SmokeFailure('LOCAL_DOCKER_CONTEXT_INVALID'); }
  demand(isObject(endpoint) && typeof endpoint.Host === 'string' && /^unix:\/\/\/[^\0\r\n?#]+$/.test(endpoint.Host), 'LOCAL_DOCKER_SOCKET_REQUIRED');
  let socket;
  try { socket = new URL(endpoint.Host); demand(socket.hostname === '' && fs.statSync(decodeURIComponent(socket.pathname)).isSocket(), 'LOCAL_DOCKER_SOCKET_REQUIRED'); }
  catch { throw new SmokeFailure('LOCAL_DOCKER_SOCKET_REQUIRED'); }
  const dockerArgs = ['--host', endpoint.Host];
  const assertContainer = () => {
    let inspected;
    try {
      inspected = JSON.parse(exec(docker, [...dockerArgs, 'inspect', '--format', '{"name":{{json .Name}},"image":{{json .Config.Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"ports":{{json .NetworkSettings.Ports}}}', container]));
    } catch { throw new SmokeFailure('LOCAL_DATABASE_SCOPE_INVALID'); }
    const bindings = inspected?.ports?.['5432/tcp'];
    demand(inspected.name === '/' + container && inspected.image === 'postgres:16-alpine' && inspected.running === true
      && inspected.labels?.['vanrides.card'] === 'VR-WA-050-WHATSAPP-TRANSPORT' && inspected.labels?.['vanrides.source-sha'] === expectedSha
      && Array.isArray(bindings) && bindings.length === 1 && bindings[0].HostIp === '127.0.0.1' && bindings[0].HostPort === '51221', 'LOCAL_DATABASE_SCOPE_INVALID');
  };
  assertContainer();
  const adapterFile = fileURLToPath(new URL('../transport/evolution-text.mjs', import.meta.url));
  let adapter, adapterSha;
  try { adapterSha = digest(fs.readFileSync(adapterFile)); adapter = await import(adapterFile); }
  catch { throw new SmokeFailure('LOCAL_ADAPTER_UNAVAILABLE'); }
  demand(typeof adapter.submitEvolutionText === 'function', 'LOCAL_ADAPTER_UNAVAILABLE');
  const startedAt = new Date().toISOString(), started = process.hrtime.bigint();
  const work = path.join(base, 'smoke-' + startedAt.replace(/[^0-9TZ]/g, '') + '-' + crypto.randomUUID());
  fs.mkdirSync(work, { mode: 0o700 });
  env.SPRING_CONFIG_IMPORT = 'file:' + configFile + '[.properties]';
  const batch = crypto.randomUUID(), actor = crypto.randomUUID(), aliasId = crypto.randomUUID();
  const instance = 'wa050_synthetic_' + batch.replaceAll('-', '');
  const address = '000000000000000@s.whatsapp.net';
  const credentials = [], checks = [];
  const after = (seconds) => new Date(Date.now() + seconds * 1000).toISOString();
  let ordinal = 0, cohortAttempted = false, provider = null, providerRequests = 0;
  const cleanup = { status: 'NOT_STARTED', codes: [] };
  const passed = (check) => { checks.push({ check, status: 'PASS' }); console.log(JSON.stringify({ check, status: 'PASS' })); };
  const next = (suffix) => path.join(work, String(++ordinal).padStart(3, '0') + '-' + suffix);
  privateWrite(path.join(work, 'run-private.json'), JSON.stringify({ batch, actor, aliasId, instance }, null, 2) + '\n');

  function operate(operation, fields = {}) {
    const input = next('operator-request.json'), output = next('operator-result.json');
    privateWrite(input, JSON.stringify({ ...operator, operation, batchId: batch, ...fields }));
    const result = spawnSync(executable, ['-Xmx384m', '-XX:MaxMetaspaceSize=256m', '-jar', jar,
      '--spring.main.web-application-type=none', '--vanrides.wa.operator.request-file=' + input,
      '--vanrides.wa.operator.output-file=' + output], { cwd: work, env, timeout: 45000, maxBuffer: 512 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
    privateWrite(next('operator.log'), Buffer.concat([result.stdout ?? Buffer.alloc(0), result.stderr ?? Buffer.alloc(0)]));
    demand(result.status === 0 && !result.error, 'OPERATOR_CLI_FAILED');
    const response = readPrivate(output);
    demand(response.status === 'COMMITTED' && response.operation === operation && response.batchId === batch, 'OPERATOR_RESULT_NOT_COMMITTED');
    return response;
  }
  function sql(statement) {
    assertContainer();
    const result = spawnSync(docker, [...dockerArgs, 'exec', '-i', container, 'psql', '-X', '-U', 'vanrides_wa050', '-d', 'vanrides_wa050', '-v', 'ON_ERROR_STOP=1', '-Atq'],
      { env, input: "SET statement_timeout='10s';\n" + statement + '\n', timeout: 15000, maxBuffer: 512 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
    privateWrite(next('database.log'), Buffer.concat([result.stdout ?? Buffer.alloc(0), result.stderr ?? Buffer.alloc(0)]));
    demand(result.status === 0 && !result.error, 'LOCAL_DATABASE_CHECK_FAILED');
    return result.stdout.toString('utf8').trim();
  }
  const quote = (value) => "'" + String(value).replaceAll("'", "''") + "'";
  const count = (statement) => { const result = sql(statement); demand(/^\d+$/.test(result), 'DATABASE_COUNT_INVALID'); return Number(result); };
  const auth = (credential, more = {}) => ({ Authorization: 'Bearer ' + credential.credential, ...more });
  async function request(method, route, body, headers = {}) {
    demand(route.startsWith('/') && !route.startsWith('//'), 'LOCAL_ROUTE_INVALID');
    const response = await fetch(state.api_base_url + route, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      redirect: 'error', signal: AbortSignal.timeout(15000), ...(body === undefined ? {} : { body: typeof body === 'string' ? body : JSON.stringify(body) }) });
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { throw new SmokeFailure('RESPONSE_NOT_SINGLE_JSON'); }
    privateWrite(next('http-result.json'), JSON.stringify({ status: response.status, data }) + '\n');
    return { status: response.status, data };
  }
  const route = '/integrations/whatsapp/v1';
  const eventBody = (messageId, message = { conversation: 'Quero começar meu cadastro sintético.' }) => ({ event: 'messages.upsert', instance,
    date_time: new Date().toISOString(), data: { key: { id: messageId, fromMe: false, remoteJid: address }, message } });
  function accepted(response) {
    demand(response.status === 202 && exactKeys(response.data, ['receiptId', 'status', 'serverTime']) && isUuid(response.data.receiptId), 'EVENT_RECEIPT_INVALID');
    return response.data;
  }
  function privateProjection(value) {
    const forbidden = new Set(['latitude', 'longitude', 'degreesLatitude', 'degreesLongitude', 'coordinates', 'pin', 'recipient', 'address', 'remoteJid', 'remoteJidAlt', 'senderAliasId', 'apikey']);
    const inspect = (node) => {
      if (Array.isArray(node)) return node.forEach(inspect);
      if (!isObject(node)) return;
      for (const [key, child] of Object.entries(node)) { demand(!forbidden.has(key), 'WORKER_PRIVACY_BOUNDARY_FAILED'); inspect(child); }
    };
    inspect(value);
    demand(!JSON.stringify(value).includes(address), 'WORKER_PRIVACY_BOUNDARY_FAILED');
  }
  async function claimInbox(worker, expectedId, expectedType) {
    const claimed = await request('POST', route + '/inbox/claim', { batchLimit: 10, leaseSeconds: 60 }, auth(worker));
    demand(claimed.status === 200 && Array.isArray(claimed.data?.items) && claimed.data.items.length === 1, 'INBOX_CLAIM_INVALID');
    privateProjection(claimed.data);
    const item = claimed.data.items[0];
    demand(item.inboxEventId === expectedId && isToken(item.inboxLease) && item.message?.type === expectedType, 'INBOX_ITEM_INVALID');
    return item;
  }
  async function completeInbox(worker, item) {
    const body = { outcome: 'COMPLETED' }, headers = auth(worker, { 'X-Inbox-Lease': item.inboxLease });
    const first = await request('POST', route + '/inbox/' + item.inboxEventId + '/result', body, headers);
    const again = await request('POST', route + '/inbox/' + item.inboxEventId + '/result', body, headers);
    demand(first.status === 200 && first.data.state === 'PROCESSED' && again.status === 200 && canonical(first.data) === canonical(again.data), 'INBOX_RESULT_REPLAY_INVALID');
  }
  async function command(worker, item, type, payload) {
    const headers = auth(worker, { 'X-Inbox-Lease': item.inboxLease, 'Idempotency-Key': crypto.randomUUID() });
    const context = await request('GET', route + '/context?inboxEventId=' + item.inboxEventId, undefined, headers);
    demand(context.status === 200 && context.data.allowedCommands?.includes(type), 'CONTEXT_ACTION_NOT_ALLOWED');
    privateProjection(context.data);
    const body = { commandType: type, inboxEventId: item.inboxEventId, expectedVersion: context.data.entityVersion, payload };
    const first = await request('POST', route + '/commands', body, headers), again = await request('POST', route + '/commands', body, headers);
    demand(first.status === 200 && first.data.outcome === 'APPLIED' && isUuid(first.data.commandId)
      && Array.isArray(first.data.responseIds) && first.data.responseIds.length === 1 && isUuid(first.data.responseIds[0])
      && again.status === 200 && first.data.commandId === again.data.commandId && first.data.entityVersion === again.data.entityVersion, 'COMMAND_REPLAY_INVALID');
    return first.data;
  }
  async function reserve(delivery, outboxId) {
    const claimed = await request('POST', route + '/outbox/claim', { batchLimit: 10, leaseSeconds: 60 }, auth(delivery));
    demand(claimed.status === 200 && Array.isArray(claimed.data?.items) && claimed.data.items.length === 1, 'OUTBOX_CLAIM_INVALID');
    privateProjection(claimed.data);
    const item = claimed.data.items[0];
    demand(item.outboxId === outboxId && isToken(item.leaseToken) && !('content' in item), 'OUTBOX_CLAIM_DISCLOSED_CONTENT');
    const authorized = await request('POST', route + '/outbox/' + outboxId + '/authorize-submit', { leaseToken: item.leaseToken }, auth(delivery));
    demand(authorized.status === 200 && authorized.data.outboxId === outboxId && isToken(authorized.data.attemptToken)
      && authorized.data.recipient?.address === address && authorized.data.recipient.addressType === 'JID'
      && authorized.data.content?.type === 'TEXT' && typeof authorized.data.content.text === 'string', 'OUTBOX_RESERVATION_INVALID');
    demand(count(`SELECT count(*) FROM wa.message_attempt WHERE outbox_id=${quote(outboxId)} AND instance_id=${quote(instance)} AND state='SUBMITTING';`) === 1, 'SUBMISSION_NOT_DURABLY_RESERVED');
    return authorized.data;
  }

  let failure = null;
  try {
    demand((await request('GET', '/actuator/health')).status === 200, 'LOCAL_API_UNHEALTHY');
    cohortAttempted = true;
    operate('create-test-cohort', { synthetic: true });
    for (const principal of ['CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER']) {
      const credential = operate('provision-service-credential', { principal, allowedInstanceId: instance, expiresAt: after(3600) });
      demand(isUuid(credential.credentialId) && isToken(credential.credential) && credential.allowedInstanceId === instance && credential.principal === principal, 'SERVICE_CREDENTIAL_INVALID');
      credentials.push(credential);
    }
    const [channel, conversation, delivery] = credentials;
    sql(`BEGIN; INSERT INTO wa.identity(id,test_cohort_id) VALUES (${quote(actor)},${quote(batch)}); INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (${quote(aliasId)},${quote(actor)},'EVOLUTION',${quote(instance)},'PN',${quote(address)}); COMMIT;`);
    demand(count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)};`) === 0, 'PROFILE_CREATED_BEFORE_HUMAN_COMMAND');
    passed('operator_cli_nonweb_and_scoped_synthetic_identity');

    const firstBody = eventBody('wa050_message_' + crypto.randomUUID().replaceAll('-', ''));
    const first = accepted(await request('POST', route + '/events', firstBody, auth(channel)));
    demand(first.status === 'PERSISTED' && count(`SELECT count(*) FROM wa.integration_inbox WHERE id=${quote(first.receiptId)} AND instance_id=${quote(instance)} AND identity_id=${quote(actor)} AND category='MESSAGE' AND state='PENDING';`) === 1, 'ACCEPTED_EVENT_NOT_DURABLE');
    const duplicate = accepted(await request('POST', route + '/events', firstBody, auth(channel)));
    demand(duplicate.status === 'DUPLICATE' && duplicate.receiptId === first.receiptId, 'EVENT_DUPLICATE_CHANGED_RECEIPT');
    demand((await request('POST', route + '/events', firstBody, auth(conversation))).status === 403, 'WRONG_PRINCIPAL_ACCEPTED');
    demand((await request('POST', route + '/events', { ...firstBody, instance: 'wa050_forged_' + batch.replaceAll('-', '') }, auth(channel))).status === 403, 'FORGED_INSTANCE_ACCEPTED');
    demand((await request('POST', route + '/events', { ...firstBody, unsupportedExtension: 'x'.repeat(33000) }, auth(channel))).status === 400, 'OVERSIZE_BODY_ACCEPTED');
    demand(count(`SELECT count(*) FROM wa.integration_inbox WHERE instance_id=${quote(instance)};`) === 1, 'REJECTED_EVENT_LEFT_RECEIPT');
    passed('durable_202_duplicate_auth_instance_and_body_limit');

    const firstItem = await claimInbox(conversation, first.receiptId, 'TEXT');
    const onboarding = await command(conversation, firstItem, 'START_ONBOARDING', {});
    demand(count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)};`) === 1
      && count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)};`) === 1
      && count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)};`) === 1, 'HUMAN_COMMAND_NOT_EXACTLY_ONCE');
    await completeInbox(conversation, firstItem);
    passed('private_inbox_context_command_and_ack_replays');

    const fakeKey = 'synthetic-provider-' + crypto.randomBytes(16).toString('hex');
    const externalId = 'wa050_provider_' + crypto.randomUUID().replaceAll('-', '');
    let expectedPost = null;
    const providerFailures = [];
    provider = http.createServer(async (req, res) => {
      providerRequests++;
      try {
        demand(req.method === 'POST' && req.url === '/message/sendText/' + encodeURIComponent(instance)
          && req.headers.apikey === fakeKey && req.headers['content-type']?.startsWith('application/json'), 'FAKE_PROVIDER_REQUEST_INVALID');
        const chunks = []; let bytes = 0;
        for await (const chunk of req) { bytes += chunk.length; demand(bytes <= 16384, 'FAKE_PROVIDER_BODY_INVALID'); chunks.push(chunk); }
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        demand(expectedPost && exactKeys(body, ['number', 'text', 'linkPreview']) && body.number === expectedPost.number
          && body.text === expectedPost.text && body.linkPreview === false, 'FAKE_PROVIDER_CONTENT_INVALID');
        res.writeHead(201, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(expectedPost.missingId ? { key: {} } : { key: { id: externalId } }));
      } catch (error) {
        providerFailures.push(safeCode(error, 'FAKE_PROVIDER_REQUEST_INVALID'));
        if (!res.headersSent) res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end('{"status":"rejected"}');
      }
    });
    await new Promise((resolve, reject) => { provider.once('error', reject); provider.listen(0, '127.0.0.1', resolve); });
    const providerAddress = provider.address();
    demand(providerAddress && typeof providerAddress !== 'string' && providerAddress.address === '127.0.0.1', 'FAKE_PROVIDER_NOT_LOOPBACK');
    const providerUrl = 'http://127.0.0.1:' + providerAddress.port;
    const firstReservation = await reserve(delivery, onboarding.responseIds[0]);
    expectedPost = { number: firstReservation.recipient.address, text: firstReservation.content.text, missingId: false };
    const submitted = await adapter.submitEvolutionText({ baseUrl: providerUrl, instance, apiKey: fakeKey, reservation: firstReservation });
    demand(submitted?.outcome === 'SUBMITTED' && submitted.attemptToken === firstReservation.attemptToken && submitted.externalMessageId === externalId
      && providerRequests === 1 && providerFailures.length === 0, 'LOCAL_PROVIDER_SUBMISSION_INVALID');
    const statusEvent = (status) => ({ event: 'messages.update', instance, date_time: new Date().toISOString(), data: { keyId: externalId, fromMe: true, status } });
    demand(accepted(await request('POST', route + '/events', statusEvent('READ'), auth(channel))).status === 'PERSISTED', 'EARLY_READ_CALLBACK_NOT_PERSISTED');
    const result = await request('POST', route + '/outbox/' + onboarding.responseIds[0] + '/result', submitted, auth(delivery));
    demand(result.status === 200 && result.data.state === 'READ', 'EARLY_CALLBACK_NOT_RECONCILED');
    const resultReplay = await request('POST', route + '/outbox/' + onboarding.responseIds[0] + '/result', submitted, auth(delivery));
    demand(resultReplay.status === 200 && canonical(resultReplay.data) === canonical(result.data), 'OUTBOX_RESULT_REPLAY_INVALID');
    accepted(await request('POST', route + '/events', statusEvent('SERVER_ACK'), auth(channel)));
    const emptyAfterRead = await request('POST', route + '/outbox/claim', { batchLimit: 10, leaseSeconds: 60 }, auth(delivery));
    demand(emptyAfterRead.status === 200 && emptyAfterRead.data.items?.length === 0
      && sql(`SELECT state FROM wa.message_outbox WHERE id=${quote(onboarding.responseIds[0])};`) === 'READ'
      && providerRequests === 1, 'READ_STATE_REGRESSED_OR_RESUBMITTED');
    passed('reserved_text_single_submit_early_read_and_monotonic_status');

    const nameEvent = accepted(await request('POST', route + '/events', eventBody('wa050_name_' + crypto.randomUUID().replaceAll('-', ''), { conversation: 'Pode me chamar de Pessoa Sintética.' }), auth(channel)));
    const nameItem = await claimInbox(conversation, nameEvent.receiptId, 'TEXT');
    const named = await command(conversation, nameItem, 'SET_PROFILE_NAME', { name: 'Pessoa Sintética WA050' });
    await completeInbox(conversation, nameItem);
    const secondReservation = await reserve(delivery, named.responseIds[0]);
    expectedPost = { number: secondReservation.recipient.address, text: secondReservation.content.text, missingId: true };
    const unknown = await adapter.submitEvolutionText({ baseUrl: providerUrl, instance, apiKey: fakeKey, reservation: secondReservation });
    demand(unknown?.outcome === 'UNKNOWN' && unknown.attemptToken === secondReservation.attemptToken
      && providerRequests === 2 && providerFailures.length === 0, 'ACCEPTED_WITHOUT_ID_NOT_UNKNOWN');
    const unknownResult = await request('POST', route + '/outbox/' + named.responseIds[0] + '/result', unknown, auth(delivery));
    demand(unknownResult.status === 200 && unknownResult.data.state === 'UNKNOWN', 'UNKNOWN_RESULT_NOT_PERSISTED');
    const emptyAfterUnknown = await request('POST', route + '/outbox/claim', { batchLimit: 10, leaseSeconds: 60 }, auth(delivery));
    demand(emptyAfterUnknown.status === 200 && emptyAfterUnknown.data.items?.length === 0 && providerRequests === 2
      && sql(`SELECT state FROM wa.message_outbox WHERE id=${quote(named.responseIds[0])};`) === 'UNKNOWN', 'UNKNOWN_AUTOMATICALLY_RETRIED');
    passed('accepted_without_external_id_is_unknown_without_retry');

    const pin = accepted(await request('POST', route + '/events', eventBody('wa050_pin_' + crypto.randomUUID().replaceAll('-', ''), { locationMessage: { degreesLatitude: 0, degreesLongitude: 0 } }), auth(channel)));
    const pinItem = await claimInbox(conversation, pin.receiptId, 'STATIC_PIN_REFERENCE');
    demand(exactKeys(pinItem.message, ['type', 'sourceMessageId']), 'PIN_COORDINATES_REACHED_WORKER');
    await completeInbox(conversation, pinItem);
    const effects = count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)};`);
    const ignoredEvents = [eventBody('wa050_echo_' + crypto.randomUUID().replaceAll('-', '')), eventBody('wa050_group_' + crypto.randomUUID().replaceAll('-', '')),
      { ...eventBody('wa050_edit_' + crypto.randomUUID().replaceAll('-', '')), event: 'messages.edited' }];
    ignoredEvents[0].data.key.fromMe = true;
    ignoredEvents[1].data.key.remoteJid = '000000000000000@g.us';
    for (const event of ignoredEvents) demand(accepted(await request('POST', route + '/events', event, auth(channel))).status === 'IGNORED', 'NONHUMAN_EVENT_NOT_IGNORED');
    const noHumanItems = await request('POST', route + '/inbox/claim', { batchLimit: 10, leaseSeconds: 60 }, auth(conversation));
    demand(noHumanItems.status === 200 && noHumanItems.data.items?.length === 0
      && count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)};`) === effects && effects === 2 && providerRequests === 2, 'NONHUMAN_EVENT_CAUSED_HUMAN_EFFECT');
    passed('pin_reference_and_nonhuman_event_isolation');
  } catch (error) { failure = error; }
  finally {
    if (provider) {
      try { provider.closeAllConnections(); await new Promise(resolve => provider.close(resolve)); }
      catch { if (!failure) failure = new SmokeFailure('LOCAL_PROVIDER_CLEANUP_FAILED'); }
    }
    const cleanupErrors = [];
    if (cohortAttempted) {
      // A CLI process can commit and lose its output. Discover only this unique instance's IDs for cleanup.
      let cohortExists = false;
      const credentialIds = new Set(credentials.map(credential => credential.credentialId));
      try {
        cohortExists = count(`SELECT count(*) FROM wa.test_cohort WHERE id=${quote(batch)} AND environment='wa050-local' AND synthetic=true;`) === 1;
        const found = sql(`SELECT id FROM wa.service_credential WHERE environment='wa050-local' AND allowed_instance_id=${quote(instance)} AND principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER') ORDER BY id;`);
        for (const id of found ? found.split('\n') : []) { demand(isUuid(id), 'CLEANUP_CREDENTIAL_ID_INVALID'); credentialIds.add(id); }
      } catch (error) { cleanupErrors.push(safeCode(error, 'SYNTHETIC_CLEANUP_DISCOVERY_FAILED')); }
      if (cohortExists) {
        try { operate('reset-test-data'); } catch (error) { cleanupErrors.push(safeCode(error, 'SYNTHETIC_RESET_FAILED')); }
      }
      for (const credentialId of credentialIds) {
        try { operate('revoke-service-credential', { credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' }); }
        catch (error) { cleanupErrors.push(safeCode(error, 'SYNTHETIC_CREDENTIAL_REVOKE_FAILED')); }
      }
      try {
        sql(`DELETE FROM wa.integration_inbox WHERE provider='EVOLUTION' AND instance_id=${quote(instance)} AND identity_id IS NULL;`);
        demand(count(`SELECT count(*) FROM wa.identity WHERE test_cohort_id=${quote(batch)};`) === 0
          && count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)};`) === 0
          && count(`SELECT count(*) FROM wa.identity_alias WHERE instance_id=${quote(instance)};`) === 0
          && count(`SELECT count(*) FROM wa.integration_inbox WHERE instance_id=${quote(instance)};`) === 0
          && count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)};`) === 0
          && count(`SELECT count(*) FROM wa.message_attempt WHERE instance_id=${quote(instance)};`) === 0
          && count(`SELECT count(*) FROM wa.service_credential WHERE allowed_instance_id=${quote(instance)} AND revoked_at IS NULL;`) === 0, 'SYNTHETIC_CLEANUP_INCOMPLETE');
      } catch (error) { cleanupErrors.push(safeCode(error, 'SYNTHETIC_CLEANUP_FAILED')); }
      if (cleanupErrors.length === 0) passed('scoped_reset_orphan_receipts_and_credential_revocation');
      cleanup.status = cleanupErrors.length ? 'FAIL' : 'PASS';
      cleanup.codes = cleanupErrors;
    }
    privateWrite(path.join(work, 'cleanup.json'), JSON.stringify(cleanup) + '\n');
    if (cleanupErrors.length && !failure) failure = new SmokeFailure('SYNTHETIC_CLEANUP_FAILED');
  }
  try {
    assertSource(); assertContainer();
    demand(digest(fs.readFileSync(adapterFile)) === adapterSha, 'LOCAL_ADAPTER_CHANGED');
    if (!failure) passed('same_clean_source_jar_listener_and_adapter_after_smoke');
  } catch (error) { if (!failure) failure = error; }
  const receipt = { status: failure ? 'FAIL' : 'PASS', ...(failure ? { code: safeCode(failure, 'SMOKE_UNEXPECTED_FAILURE') } : {}),
    sourceSha: expectedSha, jarSha256: state.jar_sha256, smokeScriptSha256: scriptSha, adapterScriptSha256: adapterSha,
    startedAt, finishedAt: new Date().toISOString(), durationMillis: Number((process.hrtime.bigint() - started) / 1000000n), checks,
    providerRequests, cleanup, synthetic: true, realWhatsapp: false, nativeDevices: false, rawGps: false };
  privateWrite(path.join(work, failure ? 'failure.json' : 'receipt.json'), JSON.stringify(receipt, null, 2) + '\n');
  if (failure) { console.error(JSON.stringify(receipt)); process.exitCode = 1; }
  else console.log(JSON.stringify(receipt));
}

main().catch(error => {
  console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'SMOKE_PREFLIGHT_FAILURE') }));
  process.exitCode = 1;
});
