#!/usr/bin/env node
/** Same-JAR local070; real Node worker/backend/PostgreSQL, fake loopback provider only. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { SmokeFailure, demand, safeCode, uuid, quote, privateWrite, localJson } from './070-worker-common.mjs';
import { loadInputs, freezeRuntime, createTools } from './070-worker-preflight.mjs';
import { startProvider, createWorkerProcess } from './070-worker-runtime.mjs';
import { provision, connectionEvent, runScenarios as foundationScenarios } from './070-worker-scenarios.mjs';
import { createInterruption } from './070-worker-lifecycle.mjs';

export async function runSmoke(runScenarios = foundationScenarios, cardId = 'VR-WA-070-AGENT-WORKFLOWS') {
  process.umask(0o077);
  const tracking = cardId === 'VR-WA-110-RUN-TRACKING';
  const recovery = cardId === 'VR-WA-120-DELIVERY-RECOVERY';
  const attendance = recovery || tracking || cardId === 'VR-WA-100-ATTENDANCE';
  const passengerScenario = attendance || cardId === 'VR-WA-090-PASSENGERS';
  demand((passengerScenario || ['VR-WA-060-DRIVER-APP', 'VR-WA-070-AGENT-WORKFLOWS', 'VR-WA-080-DRIVER-ROUTINES'].includes(cardId))
    && typeof runScenarios === 'function', 'SMOKE_SCENARIO_INVALID');
  const [directory, expectedSha, ...extra] = process.argv.slice(2);
  const input = loadInputs(directory, expectedSha, extra), frozen = freezeRuntime(input,
    { includeRecoveryLab: recovery, includeRunLab: tracking, includePairingLab: tracking || cardId === 'VR-WA-060-DRIVER-APP', includePassengerLab: passengerScenario, includeAttendanceLab: attendance });
  const serialModule = process.env.WA110_SERIAL_CLEANUP_DIR === undefined ? null : await import('./110-run-serial-cleanup.mjs');
  const serial = serialModule?.serialCleanupConfig(input, cardId) ?? null;
  const startedAt = new Date().toISOString(), started = process.hrtime.bigint();
  const work = path.join(input.base, 'smoke-' + startedAt.replace(/[^0-9TZ]/g, '') + '-' + crypto.randomUUID());
  fs.mkdirSync(work, { mode: 0o700 });
  const batch = crypto.randomUUID(), actor = crypto.randomUUID(), aliasId = crypto.randomUUID();
  const instance = 'wa070_synthetic_' + batch.replaceAll('-', ''), address = '000000000000000@s.whatsapp.net';
  const peers = passengerScenario ? [14, 13].map(length => ({
    actor: crypto.randomUUID(), aliasId: crypto.randomUUID(), address: '0'.repeat(length) + '@s.whatsapp.net',
  })) : [];
  const tools = createTools(input, frozen, work, batch), credentials = [], checks = [];
  const passed = check => { const item = { check, status: 'PASS' }; checks.push(item); console.log(JSON.stringify(item)); };
  privateWrite(path.join(work, 'run-private.json'), JSON.stringify({ batch, actor, aliasId, instance, ...(peers.length ? { peerIds: peers.map(peer => peer.actor) } : {}),
    environment: 'wa070-local', sourceSha: expectedSha, jarSha256: input.state.jar_sha256, startedAt }) + '\n');
  const interruption = createInterruption();
  let attempted = false, worker = null, provider = null, failure = null, totals = null;
  const cleanup = { status: 'NOT_STARTED', codes: [] };
  try {
    demand((await interruption.wait(() => localJson(input.state.api_base_url, '/actuator/health'))).status === 200, 'LOCAL_API_UNHEALTHY');
    attempted = true;
    await provision({ input, tools, batch, actor, aliasId, instance, address, peers, credentials, attendance, interruption });
    if (!recovery) await connectionEvent({ input, instance, credentials, interruption }, 'open');
    passed('same_clean_backend_jar_listener_database_and_synthetic_cohort');
    const apiKey = 'synthetic-provider-' + crypto.randomBytes(24).toString('hex');
    provider = await startProvider(instance, address, apiKey, peers.map(peer => peer.address));
    interruption.assertActive();
    worker = createWorkerProcess({ input, frozen, tools, work, instance, provider,
      conversation: credentials[1], delivery: credentials[2], job: credentials[3], apiKey, assertActive: interruption.assertActive });
    await interruption.wait(() => worker.start());
    totals = await runScenarios({ input, tools, work, actor, instance, address, peers, credentials, worker, provider, apiKey, passed, interruption });
  } catch (error) { failure = error; }
  finally {
    // Stop only processes created here before deleting their durable actor graph or revoking their credentials.
    for (const resource of [worker, provider]) if (resource) {
      try { await resource.stop(); } catch (error) { cleanup.codes.push(safeCode(error, 'OWNED_PROCESS_CLEANUP_FAILED')); }
    }
    if (serial && attempted && !failure && cleanup.codes.length === 0) {
      try { await serial.shutdown({ frozen, work, batch, instance, checks, interruption }); }
      catch (error) { failure = error; cleanup.codes.push(safeCode(error, 'SERIAL_SHUTDOWN_FAILED')); }
    }
    if (attempted) {
      const credentialIds = new Set(credentials.map(value => value.credentialId));
      let exists = false;
      try {
        exists = tools.count(`SELECT count(*) FROM wa.test_cohort WHERE id=${quote(batch)} AND environment='wa070-local' AND synthetic=true;`) === 1;
        const found = tools.sql(`SELECT id FROM wa.service_credential WHERE environment='wa070-local'
          AND allowed_instance_id=${quote(instance)} AND principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER','SYSTEM_JOB') ORDER BY id;`);
        for (const id of found ? found.split('\n') : []) {
          demand(uuid(id), 'CLEANUP_CREDENTIAL_ID_INVALID'); credentialIds.add(id);
        }
      } catch (error) { cleanup.codes.push(safeCode(error, 'CLEANUP_DISCOVERY_FAILED')); }
      if (exists) {
        try { tools.operate('reset-test-data'); } catch (error) { cleanup.codes.push(safeCode(error, 'SCOPED_RESET_FAILED')); }
      }
      for (const credentialId of credentialIds) {
        try { tools.operate('revoke-service-credential', { credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' }); }
        catch (error) { cleanup.codes.push(safeCode(error, 'SCOPED_REVOKE_FAILED')); }
      }
      try {
        tools.sql(`DELETE FROM wa.integration_inbox WHERE provider='EVOLUTION' AND instance_id=${quote(instance)} AND identity_id IS NULL;`);
        const identities = [actor, ...peers.map(peer => peer.actor)].map(quote).join(',');
        const queries = [
          `wa.identity WHERE test_cohort_id=${quote(batch)}`, `wa.profile WHERE identity_id IN (${identities})`,
          `wa.identity_alias WHERE instance_id=${quote(instance)}`, `wa.integration_inbox WHERE instance_id=${quote(instance)}`,
          `wa.command_receipt WHERE identity_id IN (${identities})`, `wa.support_case WHERE identity_id IN (${identities})`,
          `wa.conversation_state WHERE identity_id IN (${identities})`, `wa.message_outbox WHERE recipient_identity_id IN (${identities})`,
          `wa.message_attempt WHERE instance_id=${quote(instance)}`,
          `wa.delivery_connection WHERE environment='wa070-local' AND instance_id=${quote(instance)}`,
          `wa.routine WHERE driver_id IN (${identities})`, `wa.driver_invite WHERE driver_id IN (${identities})`,
          `wa.service_credential WHERE allowed_instance_id=${quote(instance)} AND revoked_at IS NULL`,
        ];
        if (passengerScenario) queries.push(
          `wa.driver_passenger_link WHERE driver_id IN (${identities}) OR passenger_id IN (${identities})`,
          `wa.pickup_point WHERE passenger_id IN (${identities})`, `wa.routine_member WHERE passenger_id IN (${identities})`,
          `wa.passenger_link_routine WHERE passenger_id IN (${identities})`, `wa.common_pickup_point WHERE driver_id IN (${identities})`,
        );
        if (attendance) queries.push(`wa.attendance_question WHERE instance_id=${quote(instance)}`,
          `wa.attendance WHERE passenger_id IN (${identities})`, `wa.routine_occurrence WHERE driver_id IN (${identities})`);
        demand(queries.every(query => tools.count('SELECT count(*) FROM ' + query + ';') === 0), 'SYNTHETIC_CLEANUP_INCOMPLETE');
      } catch (error) { cleanup.codes.push(safeCode(error, 'SCOPED_CLEANUP_FAILED')); }
    }
    cleanup.status = cleanup.codes.length ? 'FAIL' : 'PASS';
    privateWrite(path.join(work, 'cleanup.json'), JSON.stringify(cleanup) + '\n');
    if (cleanup.codes.length && !failure) failure = new SmokeFailure('SYNTHETIC_CLEANUP_FAILED');
  }
  try {
    interruption.assertActive();
    if (serialModule) serialModule.assertAfterSerialCleanup(frozen, serial);
    else { frozen.assertSource(); frozen.assertContainer(); }
    if (!failure) { passed('scoped_reset_and_credential_revocation'); passed('same_backend_jar_worker_agent_transport_and_smoke_hashes_after'); }
  } catch (error) { if (!failure) failure = error; }
  const receipt = { status: failure ? 'FAIL' : 'PASS', ...(failure ? { code: safeCode(failure, 'SMOKE_UNEXPECTED_FAILURE') } : {}),
    cardId, harnessEnvironment: 'wa070-local', sourceSha: expectedSha, jarSha256: input.state.jar_sha256, nodeVersion: process.versions.node, assetSha256: frozen.assets, startedAt,
    finishedAt: new Date().toISOString(), durationMillis: Number((process.hrtime.bigint() - started) / 1000000n),
    checks, totals, cleanup, ...(serial ? { serialCleanup: serial.audit } : {}),
    synthetic: true, realWhatsapp: false, nativeDevices: tracking && totals?.physicalAndroid === true, rawGps: false, openai: false };
  privateWrite(path.join(work, failure ? 'failure.json' : 'receipt.json'), JSON.stringify(receipt, null, 2) + '\n');
  if (failure) { console.error(JSON.stringify(receipt)); process.exitCode = 1; }
  else console.log(JSON.stringify(receipt));
  interruption.dispose();
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runSmoke().catch(error => {
    console.error(JSON.stringify({ status: 'FAIL', code: safeCode(error, 'SMOKE_PREFLIGHT_FAILURE') }));
    process.exitCode = 1;
  });
}
