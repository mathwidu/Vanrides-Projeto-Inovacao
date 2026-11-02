#!/usr/bin/env node
/** Physical-channel server; original business worker/conversation, no ADB/TLS keys. */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import net from 'node:net';
import crypto from 'node:crypto';
import readline from 'node:readline';
import { performance } from 'node:perf_hooks';
import { fileURLToPath } from 'node:url';
import { Bridge, PROFILE, binding, code, envelope, pairingMatch, cleanupCredentials } from './bridge.mjs';
import { createTools, watchMemory } from './tools.mjs';
import { demand, digest, exact, token, uuid, quote, privateBytes, privateWrite, properties } from './source/integrations/whatsapp/scripts/070-worker-common.mjs';
import { provision } from './source/integrations/whatsapp/scripts/070-worker-scenarios.mjs';
import { startProvider, createWorkerProcess } from './source/integrations/whatsapp/scripts/070-worker-runtime.mjs';
import { passengerConversation } from './source/integrations/whatsapp/scripts/090-passenger-conversation.mjs';
import { registerCohort } from './source/integrations/whatsapp/scripts/100-attendance-scenarios.mjs';
import { fixtureTimes, extractSyntheticIntent, menuOption } from './source/integrations/whatsapp/scripts/110-run-wire.mjs';

export const TABLES = ['identity','identity_alias','profile','driver_authorization','driver_device','pairing_challenge',
  'device_token','driver_bootstrap_invite','driver_invite','driver_passenger_link','pickup_point','routine','routine_member',
  'routine_occurrence','attendance','operation_run','run_stop','start_intent','tracking_session','tracking_batch_receipt',
  'tracking_sample_receipt','conversation_state','message_outbox','message_attempt','command_receipt','support_case',
  'passenger_link_routine','common_pickup_point','passenger_response_route','attendance_question','occurrence_roster_override'];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const send = value => process.stdout.write(JSON.stringify(value) + '\n'); // Closed IPC to owner; never a log sink.

export function shutdownBudget(clock = () => performance.now()) {
  const end = clock() + 280000;
  return { remainingMs: () => Math.max(0, end - clock()) };
}

export async function runSession(base, attemptId) {
  process.umask(0o077);
  demand(path.isAbsolute(base) && fs.realpathSync(base) === base, 'SESSION_BASE_INVALID');
  const started = Date.now(), deadline = started + 900000;
  let interrupted = null, cleaning = false, cleanupDeadline = 0, worker, provider, chat, server, socketServer, finishPromise;
  let committed, scope = null, pair = null, pendingAck = null, closePromise = null, shutdown = null;
  const checks = [], codes = [], passed = check => checks.push({ check, status: 'PASS' });
  const context = { attemptId, batch: crypto.randomUUID(), actor: crypto.randomUUID(), aliasId: crypto.randomUUID(),
    address: '000000000000000@s.whatsapp.net', peers: [14, 13].map(length => ({ actor: crypto.randomUUID(), aliasId: crypto.randomUUID(), address: '0'.repeat(length) + '@s.whatsapp.net' })),
    credentials: [], attendance: true };
  context.instance = 'wa070_synthetic_' + context.batch.replaceAll('-', '');
  privateWrite(path.join(base, 'session/cohort-scope-private.json'), JSON.stringify({ batch: context.batch, instance: context.instance,
    actor: context.actor, peers: context.peers.map(person => person.actor), environment: 'wa070-local', startedAt: new Date(started).toISOString() }));
  const memoryAbort = () => interrupted && (/^(?:VPS_(?:HEADROOM_LOW|CRITICAL_MEMORY_PRESSURE|SUSTAINED_MEMORY_PRESSURE|PRESSURE_OBSERVATION_GAP|MEMORY_MEASUREMENT_INVALID|SUPERVISOR_TTL_EXPIRED))$/.test(interrupted)
    || /^(?:OWNED_CGROUP_|OWNED_CONTAINER_|CGROUP_V2_)/.test(interrupted));
  const assertActive = () => {
    if (!cleaning || memoryAbort()) demand(!interrupted, interrupted ?? 'SESSION_INTERRUPTED');
    if (shutdown) demand(shutdown.remainingMs() > 0, 'SHUTDOWN_BUDGET_EXPIRED');
    watchMemory(cleaning ? cleanupDeadline : deadline);
  };
  const interruption = { assertActive, wait: async action => { assertActive(); const result = await action(); assertActive(); return result; } };
  const tools = createTools(base, context, assertActive, { remainingMs: () => shutdown ? shutdown.remainingMs() : Infinity }), { input, frozen, work, contract } = tools;
  const bridge = new Bridge({ attemptId, sourceSha: contract.backend.commit, jarSha256: contract.backend.jarSha256, moduleSha256: contract.moduleSha256 });
  const propertiesMap = properties(privateBytes(input.configFile)), hashKey = Buffer.from(propertiesMap.get('vanrides.wa.hash-key'), 'base64url');
  propertiesMap.clear(); demand(hashKey.length === 32, 'SESSION_HASH_KEY_INVALID');
  const committedPromise = new Promise(resolve => { committed = resolve; });
  const inputLines = readline.createInterface({ input: process.stdin, terminal: false });
  inputLines.on('line', line => {
    try {
      demand(line.length <= 8192, 'OWNER_IPC_TOO_LARGE'); const value = JSON.parse(line);
      if (exact(value, ['operation', 'code']) && value.operation === 'ABORT' && /^[A-Z][A-Z0-9_]{2,119}$/.test(value.code)) {
        interrupted = value.code; void finish(false, value.code).then(closeServers).catch(() => { process.exitCode = 1; });
      } else if (exact(value, ['operation', 'receiptSha256', 'cleanupConfirmed']) && value.operation === 'INFRA_STOPPED'
          && /^[a-f0-9]{64}$/.test(value.receiptSha256) && typeof value.cleanupConfirmed === 'boolean') committed(value);
      else throw new Error();
    } catch { interrupted = 'OWNER_IPC_INVALID'; void finish(false, interrupted).then(closeServers).catch(() => { process.exitCode = 1; }); }
  });
  inputLines.on('close', () => { if (!finishPromise) { interrupted = 'OWNER_IPC_CLOSED'; void finish(false, interrupted).then(closeServers).catch(() => { process.exitCode = 1; }); } });
  for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) process.on(signal, () => {
    interrupted = 'OWNER_SIGNAL_INTERRUPTED'; void finish(false, interrupted).then(closeServers).catch(() => { process.exitCode = 1; });
  });

  const waitUntil = async (test, timeout, failure) => {
    const end = Date.now() + timeout;
    do { assertActive(); if (await test()) return; await sleep(400); } while (Date.now() < end);
    demand(false, failure);
  };
  const scopedDriver = quote(context.actor), scopedCohort = quote(context.batch);
  function assertCohort() {
    demand(tools.count(`SELECT count(*) FROM wa.test_cohort WHERE id=${scopedCohort} AND synthetic=true AND environment='wa070-local';`) === 1
      && tools.count('SELECT count(*) FROM wa.test_cohort;') === 1, 'SESSION_COHORT_NOT_EXCLUSIVE');
  }
  function challenges() {
    assertCohort();
    return JSON.parse(tools.sql(`SELECT coalesce(json_agg(json_build_object('id',id,'jkt',jkt,'codeHash',code_hash,'pkce',pkce_challenge,
      'platform',platform,'state',state,'environment',environment,'driverId',driver_id,'createdAt',created_at,'expiresAt',expires_at)),'[]')
      FROM wa.pairing_challenge WHERE environment='wa070-local';`));
  }
  async function phase(route, body) {
    assertActive(); tools.frozen.assertSource(); assertCohort();
    if (route === '/pair') {
      demand(bridge.scope && scope && !pair, 'PAIRING_SCOPE_NOT_READY');
      pair = pairingMatch(challenges(), body, hashKey, Date.now(), scope.notBefore);
      await chat.send(chat.driver, body.code, 'CONFIRM_PAIRING');
      demand(tools.count(`SELECT count(*) FROM wa.pairing_challenge p JOIN wa.identity i ON i.id=p.driver_id WHERE p.id=${quote(pair.id)}
        AND p.jkt=${quote(pair.jkt)} AND p.driver_id=${scopedDriver} AND i.test_cohort_id=${scopedCohort} AND p.state='APPROVED' AND p.environment='wa070-local';`) === 1,
      'PAIRING_NOT_APPROVED_FOR_COHORT');
      passed('mac_observed_pairing_challenge_and_jkt_approved_for_synthetic_driver'); return {};
    }
    demand(pair, 'PAIRING_REQUIRED');
    if (route === '/intent') {
      demand(tools.count(`SELECT count(*) FROM wa.driver_device d JOIN wa.pairing_challenge p ON p.device_id=d.id
        JOIN wa.identity i ON i.id=d.driver_id WHERE d.driver_id=${scopedDriver} AND d.jkt=${quote(pair.jkt)}
        AND d.status='ACTIVE' AND d.environment='wa070-local' AND p.id=${quote(pair.id)} AND p.state='REDEEMED'
        AND p.jkt=d.jkt AND i.test_cohort_id=${scopedCohort};`) === 1
        && tools.count('SELECT count(*) FROM wa.driver_device;') === 1, 'PKCE_DEVICE_REDEEM_NOT_PROVED');
      const menu = await chat.send(chat.driver, 'INICIAR SAÍDA', 'ADVANCE_RUN_SETUP'), choice = menuOption(menu.reply, ', ida');
      demand(choice, 'RUN_START_DATE_MISSING');
      const reply = await chat.confirm(chat.driver, await chat.send(chat.driver, choice, 'ADVANCE_RUN_SETUP'), 'REQUEST_START', {
        verifyRunLink: (text, stored) => !!extractSyntheticIntent(text) && !stored?.includes('#v1.') });
      const intent = extractSyntheticIntent(reply.reply); demand(intent, 'RUN_REAL_LINK_REQUIRED');
      demand(tools.count(`SELECT count(*) FROM wa.tracking_session WHERE driver_id=${scopedDriver};`) === 0, 'RUN_ACTIVATED_BEFORE_DEVICE');
      passed('same_jkt_redeemed_by_native_pkce_and_reviewed_link_did_not_start_sensor');
      return { uri: 'vanridesdriver://run/start#v1.' + intent.id + '.' + intent.expectedVersion + '.' + intent.intentToken };
    }
    if (route === '/started') {
      await waitUntil(() => tools.count(`SELECT count(*) FROM wa.operation_run r JOIN wa.tracking_session s ON s.run_id=r.id
        JOIN wa.driver_device d ON d.id=s.device_id WHERE r.driver_id=${scopedDriver} AND r.state='TRACKING' AND d.jkt=${quote(pair.jkt)}
        AND s.last_captured_at>CURRENT_TIMESTAMP-interval '20 seconds' AND EXISTS (SELECT 1 FROM wa.tracking_sample_receipt x WHERE x.session_id=s.id AND x.outcome='ACCEPTED');`) === 1,
      35000, 'RUN_REAL_GPS_NOT_RECEIVED');
      const boxes = JSON.parse(tools.sql(`SELECT coalesce(json_agg(json_build_object('id',id,'text',payload_json->>'text')),'[]')
        FROM wa.message_outbox WHERE recipient_identity_id=${scopedDriver} AND response_part='run-gps-confirmed';`));
      demand(boxes.length === 1, 'RUN_REAL_GPS_CONFIRMATION_NOT_UNIQUE');
      await chat.deliverRunNotice(chat.driver, boxes[0], /saída iniciada/iu);
      demand(tools.count(`SELECT count(*) FROM wa.message_attempt a JOIN wa.message_outbox o ON o.id=a.outbox_id
        WHERE o.recipient_identity_id=${scopedDriver} AND o.response_part='run-gps-confirmed' AND a.state='SUBMITTED';`) === 1, 'RUN_CONFIRMATION_NOT_SUBMITTED_ONCE');
      passed('fresh_native_acquisition_confirmed_once_and_delivered_only_to_loopback_driver'); return {};
    }
    await waitUntil(() => tools.count(`SELECT count(*) FROM wa.tracking_session s JOIN wa.driver_device d ON d.id=s.device_id
      WHERE s.driver_id=${scopedDriver} AND d.jkt=${quote(pair.jkt)} AND s.state='STOPPED' AND s.stopped_at IS NOT NULL;`) === 1,
    25000, 'RUN_REAL_STOP_NOT_ACKNOWLEDGED');
    passed('native_stop_acknowledged_in_same_device_bound_session'); return {};
  }

  async function cleanup() {
    cleaning = true; cleanupDeadline = Date.now() + 180000;
    for (const resource of [worker, provider]) if (resource) {
      try { await resource.stop(); } catch (error) { codes.push(code(error)); }
    }
    const totals = chat?.totals() ?? null;
    chat?.dispose(); worker = provider = chat = null;
    let issued = 0, revoked = 0, reset = 0, graph = null;
    try {
      const exists = tools.count(`SELECT count(*) FROM wa.test_cohort WHERE id=${scopedCohort} AND environment='wa070-local' AND synthetic=true;`);
      demand(exists <= 1 && tools.count('SELECT count(*) FROM wa.test_cohort;') === exists, 'CLEANUP_COHORT_SCOPE_CHANGED');
      const rows = JSON.parse(tools.sql(`SELECT coalesce(json_agg(json_build_object('id',c.id,'principal',c.principal,'environment',c.environment,
        'instance',c.allowed_instance_id,'cohortId',t.id,'synthetic',t.synthetic)),'[]') FROM wa.service_credential c
        JOIN wa.test_cohort t ON t.id=${scopedCohort} WHERE c.allowed_instance_id=${quote(context.instance)};`));
      const ids = cleanupCredentials(rows, context); issued = ids.length;
      // Recover even if begin succeeded but /pair or its ACK was lost. This DB is fresh and exclusive.
      if (!pair && exists) {
        const rows = challenges();
        demand(rows.length <= 1, 'CLEANUP_PAIRING_NOT_UNIQUE');
        if (rows.length) {
          const row = rows[0];
          demand(uuid(row.id) && token(row.jkt) && row.platform === 'ANDROID' && row.environment === 'wa070-local'
            && Date.parse(row.createdAt) >= started && (!row.driverId || row.driverId === context.actor), 'CLEANUP_PAIRING_OUT_OF_SCOPE');
          pair = { id: row.id, jkt: row.jkt };
        }
      }
      const checkpoint = async () => { await new Promise(resolve => setImmediate(resolve)); assertActive(); };
      if (exists) { await checkpoint(); tools.operate('reset-test-data'); reset = 1; }
      for (const credentialId of ids) { await checkpoint(); tools.operate('revoke-service-credential', { credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' }); }
      if (pair) {
        await checkpoint();
        const seq = digest('wa070-local\0' + pair.jkt).slice(0, 32).replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/u, '$1-$2-$3-$4-$5');
        tools.sql(`BEGIN; DELETE FROM wa.pairing_challenge WHERE id=${quote(pair.id)} AND driver_id IS NULL AND environment='wa070-local';
          DELETE FROM wa.operation_replay WHERE operation_kind='PAIR_BEGIN' AND scope_id=${quote(seq)};
          DELETE FROM wa.proof_nonce WHERE environment='wa070-local' AND jkt=${quote(pair.jkt)};
          DELETE FROM wa.proof_replay WHERE jkt=${quote(pair.jkt)}; COMMIT;`);
      }
      await checkpoint(); tools.sql(`DELETE FROM wa.integration_inbox WHERE provider='EVOLUTION' AND instance_id=${quote(context.instance)} AND identity_id IS NULL;`);
      revoked = tools.count(`SELECT count(*) FROM wa.service_credential WHERE environment='wa070-local'
        AND allowed_instance_id=${quote(context.instance)} AND principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER','SYSTEM_JOB') AND revoked_at IS NOT NULL;`);
      demand(revoked === issued && tools.count(`SELECT count(*) FROM wa.service_credential WHERE principal<>'OPERATOR' AND revoked_at IS NULL;`) === 0,
        'CLEANUP_REVOKE_INCOMPLETE');
      const entries = TABLES.map(table => quote(table) + ',(SELECT count(*) FROM wa.' + table + ')').join(',');
      graph = JSON.parse(tools.sql('SELECT json_build_object(' + entries + ');'));
      demand(Object.values(graph).every(value => value === 0)
        && tools.count('SELECT count(*) FROM wa.integration_inbox;') === 0
        && tools.count('SELECT count(*) FROM wa.proof_nonce;') === 0
        && tools.count('SELECT count(*) FROM wa.proof_replay;') === 0
        && tools.count('SELECT count(*) FROM wa.operation_replay;') === 0, 'CLEANUP_GRAPH_NOT_EMPTY');
      if (reset) demand(tools.count(`SELECT count(*) FROM wa.audit_event WHERE action='RESET_TEST_DATA' AND actor_kind='OPERATOR' AND resource_id=${scopedCohort};`) === 1,
        'CLEANUP_RESET_AUDIT_MISSING');
      tools.finalProof();
    } catch (error) { codes.push(code(error)); }
    hashKey.fill(0); pair = null; context.credentials.length = 0;
    return { cleanupConfirmed: codes.length === 0, cohortReset: reset, issuedCredentials: issued, revokedCredentials: revoked,
      graph, totals, checks, codes: [...new Set(codes)], originalNineCheckProfile: false, hardwareVerified: false,
      sensorEvidence: 'BACKEND_ACCEPTED_NATIVE_CHANNEL_ONLY_MAC_EVIDENCE_REQUIRED', externalBusinessSubmissions: 0 };
  }

  async function finish(success, reason = null) {
    if (finishPromise) return finishPromise;
    shutdown = shutdownBudget();
    send({ event: 'CLEANUP_BEGIN', binding: bridge.scope, budgetSeconds: 280 });
    finishPromise = (async () => {
      const result = await cleanup();
      result.functionalCode = reason;
      result.functionalComplete = success && result.cleanupConfirmed && bridge.step === 4 && !bridge.failed && !interrupted;
      if (result.functionalComplete) demand(result.issuedCredentials === 4 && result.revokedCredentials === 4
        && result.cohortReset === 1 && result.totals?.providerRequests > 0, 'PHYSICAL_COMPLETE_CLEANUP_INVALID');
      send({ event: 'CLEANUP_READY', result });
      let timeout;
      try {
        const proof = await Promise.race([committedPromise, new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error()), shutdown.remainingMs());
        })]);
        demand(shutdown.remainingMs() > 0, 'SHUTDOWN_BUDGET_EXPIRED');
        demand(proof.cleanupConfirmed && result.cleanupConfirmed, 'INFRASTRUCTURE_CLEANUP_NOT_CONFIRMED');
        const receipt = { status: 'SHUTDOWN_ACK', backendStopped: true, databaseStopped: true, cleanupConfirmed: true, receiptSha256: proof.receiptSha256 };
        pendingAck = receipt; return receipt;
      } finally { clearTimeout(timeout); }
    })();
    return finishPromise;
  }
  async function closeServers() {
    if (closePromise) return closePromise;
    closePromise = (async () => {
      bridge.close(); clearInterval(liveness);
      for (const item of [server, socketServer]) if (item) {
        item.closeAllConnections?.(); await new Promise(resolve => item.close(resolve));
      }
      inputLines.close(); process.stdin.destroy();
      send({ event: 'CLOSED', cleanupAcknowledged: !!pendingAck });
    })();
    return closePromise;
  }
  async function readBody(request) {
    demand(request.headers['content-type'] === 'application/json' && !request.headers['transfer-encoding'], 'BRIDGE_CONTENT_TYPE_INVALID');
    const length = Number(request.headers['content-length']); demand(Number.isInteger(length) && length >= 2 && length <= 1024, 'BRIDGE_BODY_SIZE_INVALID');
    const chunks = []; let total = 0;
    for await (const chunk of request) { total += chunk.length; demand(total <= 1024, 'BRIDGE_BODY_SIZE_INVALID'); chunks.push(chunk); }
    demand(total === length, 'BRIDGE_BODY_SIZE_INVALID'); return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  }
  const liveness = setInterval(() => {
    if (finishPromise) return;
    try { assertActive(); } catch (error) {
      interrupted = code(error); void finish(false, interrupted).then(closeServers).catch(() => { process.exitCode = 1; void closeServers(); });
    }
  }, 500); liveness.unref();
  try {
    tools.frozen.assertSource();
    demand(tools.count('SELECT count(*) FROM wa.service_credential;') === 0 && tools.count('SELECT count(*) FROM wa.pairing_challenge;') === 0,
      'SESSION_DATABASE_NOT_FRESH');
    await provision({ ...context, input, tools, interruption }); assertCohort();
    const apiKey = crypto.randomBytes(32).toString('base64url');
    provider = await startProvider(context.instance, context.address, apiKey, context.peers.map(person => person.address));
    worker = createWorkerProcess({ input, frozen, tools, work, instance: context.instance, provider, conversation: context.credentials[1],
      delivery: context.credentials[2], job: context.credentials[3], apiKey, assertActive });
    await worker.start();
    chat = passengerConversation({ ...context, input, tools, worker, provider, interruption });
    const fixture = fixtureTimes(); await registerCohort(chat, fixture.schedules, fixture.zone, { remindersForFirst: false });
    const menu = await chat.send(chat.driver, 'ROTINAS', 'ADVANCE_DRIVER_SETUP'), n = /Rotina ([1-9][0-9]*): Ida escolar;/u.exec(menu.reply)?.[1];
    demand(n, 'RUN_ROUTINE_NOT_LISTED'); await chat.send(chat.driver, 'ORDEM ' + n, 'ADVANCE_DRIVER_SETUP');
    await chat.confirm(chat.driver, await chat.send(chat.driver, '1,2', 'ADVANCE_DRIVER_SETUP'), 'SET_STOP_ORDER');
    await interruption.wait(() => sleep(60500 - Date.now() % 60000));
    demand((await worker.request('/v1/tick-attendance')).status === 200, 'RUN_OCCURRENCE_TICK');
    passed('original_worker_conversation_registered_driver_routines_and_two_passengers');
    const socketPath = path.join(base, 'control.sock'); demand(Buffer.byteLength(socketPath) < 104 && !fs.existsSync(socketPath), 'CONTROL_SOCKET_SCOPE_INVALID');
    socketServer = net.createServer(client => {
      let size = 0, chunks = [];
      client.setTimeout(15000, () => client.destroy());
      client.on('error', () => {});
      client.on('data', data => {
        size += data.length; if (size > 2048) { client.destroy(); return; }
        chunks.push(data); if (!Buffer.concat(chunks).includes(10)) return;
        client.pause();
        (async () => {
          let request;
          try {
            request = JSON.parse(Buffer.concat(chunks).toString('utf8'));
            demand(exact(request, ['operation','attemptId','sourceSha','nonce']) && ['bridge-bootstrap','stop'].includes(request.operation)
              && request.attemptId === attemptId && request.sourceSha === contract.backend.commit, 'CONTROL_SOCKET_REQUEST_INVALID');
            let result;
            if (request.operation === 'bridge-bootstrap') {
              demand(!finishPromise && !cleaning, 'BRIDGE_SESSION_CLOSING');
              assertActive(); result = bridge.bootstrap(request.nonce);
              scope = { notBefore: Date.now() }; send({ event: 'BOUND', binding: bridge.scope });
            } else {
              const bound = bridge.recovery(request.nonce); send({ event: 'BOUND', binding: bound });
              result = envelope(bound, { status: 'STOP_REQUESTED' });
              setImmediate(() => { void finish(false, 'REMOTE_STOP_REQUESTED').then(closeServers).catch(() => { process.exitCode = 1; void closeServers(); }); });
            }
            client.end(JSON.stringify(result) + '\n');
          } catch (error) {
            let bound; try { bound = binding(attemptId, contract.backend.commit, request?.nonce); } catch { client.destroy(); return; }
            client.end(JSON.stringify(envelope(bound, { status: 'BLOCKED', code: code(error) })) + '\n');
          }
        })();
      });
    });
    await new Promise((resolve, reject) => { socketServer.once('error', reject); socketServer.listen(socketPath, resolve); }); fs.chmodSync(socketPath, 0o600);
    server = http.createServer({ requestTimeout: 55000, headersTimeout: 5000, maxHeaderSize: 4096 }, async (request, response) => {
      try {
        demand(request.method === 'POST' && ['/pair','/intent','/started','/stopped','/shutdown'].includes(request.url), 'BRIDGE_ROUTE_DENIED');
        bridge.authorize(request.headers);
        demand(!finishPromise && !cleaning, 'BRIDGE_SESSION_CLOSING');
        demand(request.rawHeaders.filter((value, index) => index % 2 === 0 && value.toLowerCase() === 'x-wa110-bridge-token').length === 1, 'BRIDGE_DUPLICATE_AUTH');
        const body = await readBody(request); let result;
        if (request.url === '/shutdown') {
          demand(exact(body, []) && bridge.step === 4 && !bridge.failed && !bridge.busy, 'SHUTDOWN_FUNCTIONAL_INCOMPLETE');
          result = envelope(bridge.scope, await finish(true));
        } else result = await bridge.exchange(request.url, body, () => phase(request.url, body));
        response.writeHead(200, { 'Content-Type': 'application/json', Connection: 'close' });
        response.end(JSON.stringify(result), () => { if (request.url === '/shutdown') void closeServers(); });
      } catch (error) {
        const bound = bridge.scope;
        response.writeHead(409, { 'Content-Type': 'application/json', Connection: 'close' });
        response.end(JSON.stringify(bound ? envelope(bound, { status: 'BLOCKED', code: code(error) }) : { result: { status: 'BLOCKED', code: code(error) } }));
        if (bridge.failed || finishPromise) void finish(false, code(error)).then(closeServers).catch(() => { process.exitCode = 1; void closeServers(); });
      }
    });
    server.on('clientError', (_error, socket) => socket.destroy());
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(28444, '127.0.0.1', resolve); });
    demand(server.address().address === '127.0.0.1', 'SIDECAR_NOT_LOOPBACK');
    send({ event: 'READY', profile: PROFILE, jarSha256: contract.backend.jarSha256, moduleSha256: contract.moduleSha256 });
  } catch (error) {
    interrupted = code(error);
    await finish(false, interrupted).catch(() => { process.exitCode = 1; });
    await closeServers();
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runSession(...process.argv.slice(2)).catch(() => { send({ event: 'FATAL', code: 'PHYSICAL_SESSION_INITIALIZATION_FAILED' }); process.exitCode = 1; });
}
