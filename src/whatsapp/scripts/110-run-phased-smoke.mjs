#!/usr/bin/env node
/** New local native profile. Each invocation is exactly one parent-controlled phase. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import { demand, digest, privateBytes, readPrivate, privateWrite, safeCode, uuid, quote, localJson } from './070-worker-common.mjs';
import { root, jarDigest, loadInputs, loadProvisionInputs, freezeRuntime, freezeProvisionRuntime, createTools } from './070-worker-preflight.mjs';
import { startProvider, createWorkerProcess } from './070-worker-runtime.mjs';
import { createInterruption } from './070-worker-lifecycle.mjs';
import { runRedmiScenarios } from './110-run-redmi.mjs';
import { serialProvisionPlan } from './110-run-phase-contract.mjs';

export const phasedProfile = 'NATIVE_SERIAL_PROVISIONING_V1';
const options = { includeRunLab: true, includePairingLab: true, includePassengerLab: true, includeAttendanceLab: true };
function directory(value) {
  const info = fs.lstatSync(value);
  demand(info.isDirectory() && info.uid === process.getuid() && (info.mode & 0o777) === 0o700
    && fs.realpathSync(value) === value, 'PHASE_DIRECTORY_NOT_PRIVATE');
}
function atomic(file, value) {
  const temporary = file + '.' + crypto.randomUUID() + '.pending';
  try { privateWrite(temporary, JSON.stringify(value) + '\n'); fs.renameSync(temporary, file); }
  finally { if (fs.existsSync(temporary)) fs.unlinkSync(temporary); }
}
const withoutPid = state => Object.fromEntries(Object.entries(state).filter(([key]) => key !== 'api_pid'));
function binding(input, frozen, apk) {
  const qa = nativeExecutor();
  return { sourceSha: input.expectedSha, jarSha256: input.state.jar_sha256, backendTree: frozen.backendTree,
    assetsSha256: digest(JSON.stringify(frozen.assets)), configSha256: digest(privateBytes(input.configFile)),
    operatorInputSha256: digest(privateBytes(input.operatorFile)), apkSha256: jarDigest(apk),
    caSha256: digest(fs.readFileSync(path.join(process.env.HOME, '.local/share/vanrides/wa060/tls-v2/ca.der'))),
    qaExecutorConfigSha256: digest(privateBytes(process.env.WA110_QA_EXECUTOR_CONFIG)),
    qaRunnerSha256: qa.runnerSha256, qaSourceTreeSha256: qa.sourceTreeSha256, officialReaderJarSha256: qa.readerJarSha256 };
}
function nativeExecutor() {
  const config = readPrivate(process.env.WA110_QA_EXECUTOR_CONFIG);
  demand(['wa110-apk-laboratorio', 'wa110-automacao-redmi'].some(name => config.root === path.join(process.env.HOME, '.local/share/vanrides/qa-worktrees', name))
    && fs.realpathSync(config.root) === config.root && /^[a-f0-9]{64}$/.test(config.runnerSha256)
    && jarDigest(path.join(config.root, 'integration_test/driver_run/android_lab.py')) === config.runnerSha256
    && fs.realpathSync(config.readerJar) === config.readerJar && jarDigest(config.readerJar) === config.readerJarSha256,
  'PHASE_QA_EXECUTOR_CHANGED');
  const hashes = {};
  for (const relative of ['integration_test/driver_run', 'integration_test/driver_pairing']) {
    for (const name of fs.readdirSync(path.join(config.root, relative)).sort()) if (/\.(?:py|swift)$/.test(name)) {
      const file = path.join(config.root, relative, name);
      demand(fs.realpathSync(file) === file && fs.lstatSync(file).isFile(), 'PHASE_QA_SOURCE_SYMLINK');
      hashes[relative + '/' + name] = jarDigest(file);
    }
  }
  demand(digest(JSON.stringify(hashes)) === config.sourceTreeSha256
    && isDeepStrictEqual(hashes, config.sourceFiles), 'PHASE_QA_SOURCE_CHANGED');
  return config;
}
function maximumOrdinal(work) {
  return Math.max(0, ...fs.readdirSync(work).filter(name => /^\d{3,}-/.test(name)).map(name => Number(name.split('-')[0])));
}
function fileHashes(work) {
  return Object.fromEntries(fs.readdirSync(work).sort().filter(name => fs.lstatSync(path.join(work, name)).isFile())
    .map(name => [name, digest(privateBytes(path.join(work, name)))]));
}
function gates(input, frozen, expected, apk, tools, batch, instance) {
  return {
    assertFrozen(value) { demand(isDeepStrictEqual(value, binding(input, frozen, apk)), 'PHASE_ARTIFACT_CHANGED'); frozen.assertFrozenSource(); frozen.assertContainer(); },
    assertApiStopped() { frozen.assertPortClosed(); },
    pressure() { return Number(frozen.exec('/usr/sbin/sysctl', ['-n', 'kern.memorystatus_vm_pressure_level'])); },
    assertOwnedApi(pid) { demand(pid === input.state.api_pid, 'PHASE_API_PID_MISMATCH'); frozen.assertSource(); },
    assertScopedTechnicalCredential(id, scope) {
      demand(scope.batchId === batch && scope.instance === instance && scope.environment === 'wa070-local'
        && tools.count(`SELECT count(*) FROM wa.service_credential c JOIN wa.test_cohort t ON t.id=${quote(batch)}
          WHERE c.id=${quote(id)} AND c.environment=t.environment AND t.environment='wa070-local' AND t.synthetic=true
          AND c.allowed_instance_id=${quote(instance)} AND c.principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER','SYSTEM_JOB');`) === 1,
      'PHASE_CLEANUP_CREDENTIAL_OUT_OF_SCOPE');
    },
  };
}
function contextFrom(plan, input, interruption) {
  const c = plan.context;
  demand(uuid(plan.batch) && uuid(c.actor) && uuid(c.aliasId) && plan.instance === 'wa070_synthetic_' + plan.batch.replaceAll('-', '')
    && c.address === '000000000000000@s.whatsapp.net' && c.peers.length === 2
    && c.peers.every((peer, i) => uuid(peer.actor) && uuid(peer.aliasId) && peer.address === '0'.repeat(14 - i) + '@s.whatsapp.net'), 'PHASE_CONTEXT_INVALID');
  return { input, batch: plan.batch, instance: plan.instance, ...c, credentials: plan.credentials ?? [], attendance: true, interruption };
}
function loadPlan(file, input, frozen, apk) {
  const plan = readPrivate(file);
  demand(plan.schemaVersion === 1 && plan.profile === phasedProfile && plan.base === input.base
    && isDeepStrictEqual(plan.stateWithoutApiPid, withoutPid(input.state))
    && isDeepStrictEqual(plan.binding, binding(input, frozen, apk)), 'PHASE_PLAN_BINDING_MISMATCH');
  directory(plan.work);
  demand(path.dirname(plan.work) === input.base && /^smoke-[0-9TZ]+-[a-f0-9-]{36}$/.test(path.basename(plan.work)), 'PHASE_WORK_SCOPE_INVALID');
  for (const [name, hash] of Object.entries(plan.provisionFiles ?? {}))
    demand(path.basename(name) === name && digest(privateBytes(path.join(plan.work, name))) === hash, 'PHASE_PROVISION_RECEIPT_CHANGED');
  return plan;
}

export async function executePhase(mode, base, sha, attempt, apk) {
  process.umask(0o077);
  demand(process.env.WA110_PHASE_PROFILE === phasedProfile, 'PHASE_PARENT_PROFILE_REQUIRED');
  demand(['provision', 'functional', 'cleanup'].includes(mode), 'PHASE_MODE_INVALID');
  directory(attempt);
  demand(path.dirname(attempt) === path.join(process.env.HOME, '.local/share/vanrides/wa110/attempts', sha)
    && /^[a-z0-9][a-z0-9-]{0,59}$/.test(path.basename(attempt)), 'PHASE_ATTEMPT_SCOPE_INVALID');
  demand(path.dirname(apk) === path.join(process.env.HOME, '.local/share/vanrides/wa110/apk')
    && fs.realpathSync(apk) === apk && fs.lstatSync(apk).isFile(), 'PHASE_APK_SCOPE_INVALID');
  const input = (mode === 'functional' ? loadInputs : loadProvisionInputs)(base, sha);
  const frozen = (mode === 'functional' ? freezeRuntime : freezeProvisionRuntime)(input, options);
  const file = path.join(attempt, 'phase-plan-private.json'), interruption = createInterruption();
  let plan, contract, worker, provider, failure;
  try {
    if (mode === 'provision') {
      demand(!fs.existsSync(file), 'PHASE_PLAN_ALREADY_EXISTS');
      const batch = crypto.randomUUID(), work = path.join(base, 'smoke-' + new Date().toISOString().replace(/[^0-9TZ]/g, '') + '-' + crypto.randomUUID());
      fs.mkdirSync(work, { mode: 0o700 });
      plan = { schemaVersion: 1, profile: phasedProfile, base, work, batch,
        instance: 'wa070_synthetic_' + batch.replaceAll('-', ''), stateWithoutApiPid: withoutPid(input.state),
        binding: binding(input, frozen, apk), credentials: [], provisionFiles: {}, startedAt: new Date().toISOString(),
        checkpoint: { phase: 'FAILED', journal: [], invitation: null },
        context: { actor: crypto.randomUUID(), aliasId: crypto.randomUUID(), address: '000000000000000@s.whatsapp.net',
          peers: [14, 13].map(length => ({ actor: crypto.randomUUID(), aliasId: crypto.randomUUID(), address: '0'.repeat(length) + '@s.whatsapp.net' })) } };
      atomic(file, plan); // Publish scope before the first possible mutation, including a lost-ACK case.
      const ctx = contextFrom(plan, input, interruption), tools = createTools(input, frozen, work, batch);
      contract = serialProvisionPlan({ binding: plan.binding, context: ctx, rawTools: tools,
        gates: gates(input, frozen, plan.binding, apk, tools, batch, plan.instance) });
      try { await contract.prepare(); }
      finally { plan.credentials = ctx.credentials; plan.checkpoint = contract.privateCheckpoint(); plan.provisionFiles = fileHashes(work); atomic(file, plan); }
      privateWrite(path.join(attempt, 'provision.json'), JSON.stringify({ status: 'PASS', profile: phasedProfile,
        sourceSha: sha, jarSha256: input.state.jar_sha256, planSha256: digest(privateBytes(file)), initialCliCommitted: 6 }) + '\n');
      return;
    }
    plan = loadPlan(file, input, frozen, apk);
    const ctx = contextFrom(plan, input, interruption), tools = createTools(input, frozen, plan.work, plan.batch, maximumOrdinal(plan.work));
    contract = serialProvisionPlan({ binding: plan.binding, context: ctx, rawTools: tools,
      gates: gates(input, frozen, plan.binding, apk, tools, plan.batch, plan.instance), checkpoint: plan.checkpoint });
    if (mode === 'functional') {
      const prepared = readPrivate(path.join(attempt, 'provision.json'));
      demand(prepared.status === 'PASS' && prepared.profile === phasedProfile && prepared.sourceSha === sha
        && prepared.jarSha256 === input.state.jar_sha256 && prepared.planSha256 === digest(privateBytes(file))
        && prepared.initialCliCommitted === 6, 'PHASE_PROVISION_NOT_ATTESTED');
      contract.enterFunctional(plan.binding, input.state.api_pid);
      const passed = check => { checks.push({ check, status: 'PASS' }); console.log(JSON.stringify({ check, status: 'PASS' })); };
      const checks = []; let totals;
      try {
        demand((await interruption.wait(() => localJson(input.state.api_base_url, '/actuator/health'))).status === 200, 'LOCAL_API_UNHEALTHY');
        const apiKey = 'synthetic-provider-' + crypto.randomBytes(24).toString('hex');
        provider = await startProvider(plan.instance, ctx.address, apiKey, ctx.peers.map(peer => peer.address));
        worker = createWorkerProcess({ input, frozen, tools, work: plan.work, instance: plan.instance, provider,
          conversation: ctx.credentials[1], delivery: ctx.credentials[2], job: ctx.credentials[3], apiKey, assertActive: interruption.assertActive });
        await interruption.wait(() => worker.start());
        const liveTools = { ...tools, operate: (operation, fields) => contract.liveOperate(operation, fields) };
        totals = await runRedmiScenarios({ ...ctx, tools: liveTools, work: plan.work, worker, provider, passed, interruption,
          profile: phasedProfile, nativeExecutor: nativeExecutor(), phaseBinding: plan.binding });
      } catch (error) { failure = error; }
      finally {
        for (const resource of [worker, provider]) if (resource) {
          try { await resource.stop(); } catch (error) { failure ??= error; }
        }
        worker = provider = null;
        privateWrite(path.join(attempt, 'functional.json'), JSON.stringify({ status: failure ? 'FAIL' : 'PASS',
          profile: phasedProfile, sourceSha: sha, jarSha256: input.state.jar_sha256, checks, totals,
          bootstrapConsumedOnce: contract.audit().bootstrapConsumedOnce, ...(failure ? { code: safeCode(failure, 'PHASE_FUNCTIONAL_FAILED') } : {}) }) + '\n');
      }
      if (failure) throw failure;
      demand(totals?.physicalAndroid === true && contract.audit().bootstrapConsumedOnce, 'PHASE_REAL_DEVICE_NOT_PROVED');
    } else {
      contract.enterCleanup();
      const codes = [], credentialIds = new Set(ctx.credentials.map(value => value.credentialId));
      let exists = false;
      try {
        exists = tools.count(`SELECT count(*) FROM wa.test_cohort WHERE id=${quote(plan.batch)} AND environment='wa070-local' AND synthetic=true;`) === 1;
        const found = tools.sql(`SELECT id FROM wa.service_credential WHERE environment='wa070-local'
          AND allowed_instance_id=${quote(plan.instance)} AND principal IN ('CHANNEL','CONVERSATION_WORKER','DELIVERY_WORKER','SYSTEM_JOB') ORDER BY id;`);
        for (const id of found ? found.split('\n') : []) { demand(uuid(id), 'CLEANUP_CREDENTIAL_ID_INVALID'); credentialIds.add(id); }
      } catch (error) { codes.push(safeCode(error, 'CLEANUP_DISCOVERY_FAILED')); }
      if (exists) {
        try { contract.cleanupOperate('reset-test-data'); } catch (error) { codes.push(safeCode(error, 'SCOPED_RESET_FAILED')); }
      }
      for (const credentialId of credentialIds) {
        try { contract.cleanupOperate('revoke-service-credential', { credentialId, reasonCode: 'SYNTHETIC_SMOKE_COMPLETED' }); }
        catch (error) { codes.push(safeCode(error, 'SCOPED_REVOKE_FAILED')); }
      }
      try {
        tools.sql(`DELETE FROM wa.integration_inbox WHERE provider='EVOLUTION' AND instance_id=${quote(plan.instance)} AND identity_id IS NULL;`);
        const identities = [ctx.actor, ...ctx.peers.map(peer => peer.actor)].map(quote).join(',');
        const queries = [`wa.identity WHERE test_cohort_id=${quote(plan.batch)}`, `wa.profile WHERE identity_id IN (${identities})`,
          `wa.identity_alias WHERE instance_id=${quote(plan.instance)}`, `wa.integration_inbox WHERE instance_id=${quote(plan.instance)}`,
          `wa.command_receipt WHERE identity_id IN (${identities})`, `wa.message_outbox WHERE recipient_identity_id IN (${identities})`,
          `wa.support_case WHERE identity_id IN (${identities})`, `wa.driver_invite WHERE driver_id IN (${identities})`,
          `wa.message_attempt WHERE instance_id=${quote(plan.instance)}`, `wa.conversation_state WHERE identity_id IN (${identities})`,
          `wa.routine WHERE driver_id IN (${identities})`, `wa.driver_passenger_link WHERE driver_id IN (${identities}) OR passenger_id IN (${identities})`,
          `wa.pickup_point WHERE passenger_id IN (${identities})`, `wa.routine_member WHERE passenger_id IN (${identities})`,
          `wa.passenger_link_routine WHERE passenger_id IN (${identities})`, `wa.common_pickup_point WHERE driver_id IN (${identities})`,
          `wa.driver_device WHERE driver_id IN (${identities})`, `wa.attendance_question WHERE instance_id=${quote(plan.instance)}`,
          `wa.tracking_session WHERE driver_id IN (${identities})`, `wa.operation_run WHERE driver_id IN (${identities})`,
          `wa.start_intent WHERE driver_id IN (${identities})`, `wa.driver_bootstrap_invite WHERE identity_id IN (${identities})`,
          `wa.driver_authorization WHERE driver_id IN (${identities})`, `wa.occurrence_roster_override WHERE driver_id IN (${identities})`,
          `wa.run_stop WHERE driver_id IN (${identities})`,
          `wa.attendance WHERE passenger_id IN (${identities})`, `wa.routine_occurrence WHERE driver_id IN (${identities})`,
          `wa.service_credential WHERE allowed_instance_id=${quote(plan.instance)} AND revoked_at IS NULL`];
        demand(queries.every(query => tools.count('SELECT count(*) FROM ' + query + ';') === 0), 'SYNTHETIC_CLEANUP_INCOMPLETE');
      } catch (error) { codes.push(safeCode(error, 'SCOPED_CLEANUP_FAILED')); }
      privateWrite(path.join(attempt, 'cleanup-' + crypto.randomUUID() + '.json'), JSON.stringify({ status: codes.length ? 'FAIL' : 'PASS', codes,
        profile: phasedProfile, sourceSha: sha, jarSha256: input.state.jar_sha256, credentialsObserved: credentialIds.size,
        originalProtocolApproved: false }) + '\n');
      demand(codes.length === 0, 'PHASE_CLEANUP_FAILED');
    }
  } finally { contract?.dispose(); interruption.dispose(); }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  executePhase(...process.argv.slice(2)).catch(error => {
    console.error(JSON.stringify({ status: 'FAIL', profile: phasedProfile, code: safeCode(error, 'PHASE_EXECUTION_FAILED') }));
    process.exitCode = 1;
  });
}
