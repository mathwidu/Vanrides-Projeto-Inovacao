// Phase contract. Runtime ownership, private receipt validation and guards are supplied by the phase runner.
import { isDeepStrictEqual } from 'node:util';
import { provision } from './070-worker-scenarios.mjs';
import { SmokeFailure } from './070-worker-common.mjs';

const hex = /^[a-f0-9]{64}$/, uuid = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/;
const demand = (ok, code) => { if (!ok) throw new SmokeFailure(code); };
const clone = value => structuredClone(value);
const exact = (value, keys) => value && Object.keys(value).sort().join() === [...keys].sort().join();
const token = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').toString('base64url') === value;

export function serialProvisionPlan({ binding, context, rawTools, gates, clock = Date.now, checkpoint = null }) {
  demand(/^[a-f0-9]{40}$/.test(binding.sourceSha) && /^[a-f0-9]{40}$/.test(binding.backendTree), 'BAD_SOURCE_BINDING');
  for (const key of ['jarSha256', 'assetsSha256', 'configSha256', 'operatorInputSha256', 'apkSha256', 'caSha256'])
    demand(hex.test(binding[key]), 'BAD_ARTIFACT_BINDING');
  demand(context.attendance === true && (checkpoint ? context.credentials.length <= 4 : context.credentials.length === 0) && context.peers.length === 2
    && uuid.test(context.batch) && uuid.test(context.actor)
    && new Set([context.actor, ...context.peers.map(peer => peer.actor)]).size === 3
    && context.instance === 'wa070_synthetic_' + context.batch.replaceAll('-', ''), 'BAD_NATIVE_COHORT');
  const expected = clone(binding), journal = [];
  const cohort = clone({ batch: context.batch, actor: context.actor, instance: context.instance,
    peers: context.peers.map(peer => peer.actor) });
  let phase = 'NEW', invitation = null, consumed = false;
  if (checkpoint) {
    demand(['PREPARED', 'FAILED'].includes(checkpoint.phase) && Array.isArray(checkpoint.journal), 'BAD_CHECKPOINT');
    phase = checkpoint.phase; journal.push(...checkpoint.journal); invitation = clone(checkpoint.invitation);
    if (phase === 'PREPARED') demand(journal.length === 6 && invitation?.identityId === cohort.actor
      && token(invitation.driverInviteCode) && context.credentials.length === 4, 'BAD_PREPARED_CHECKPOINT');
  }

  function unchanged(observed = binding) {
    demand(isDeepStrictEqual(expected, observed), 'PHASE_BINDING_CHANGED');
    demand(isDeepStrictEqual(cohort, { batch: context.batch, actor: context.actor, instance: context.instance,
      peers: context.peers.map(peer => peer.actor) }), 'PHASE_COHORT_CHANGED');
    gates.assertFrozen(expected);
  }
  function stopped() {
    unchanged();
    gates.assertApiStopped(); // Future adapter: owned child exited AND localhost:18083 closed.
    demand(gates.pressure() === 1, 'HOST_MEMORY_PRESSURE_NOT_NORMAL');
  }
  function operate(operation, fields = {}) {
    demand(phase === 'PROVISIONING', 'OPERATOR_PHASE_INVALID');
    stopped();
    const response = rawTools.operate(operation, fields); // Original CLI/auth/audit/transaction boundary.
    demand(response.status === 'COMMITTED' && response.operation === operation
      && response.batchId === context.batch && response.environment === 'wa070-local', 'OPERATOR_NOT_COMMITTED');
    journal.push(operation);
    return response;
  }
  return {
    async prepare() {
      demand(phase === 'NEW', 'PLAN_NOT_FRESH'); stopped(); phase = 'PROVISIONING';
      try {
        // Execute the current, unmodified provision() with real CLI in a future adapter.
        await provision({ ...context, tools: { ...rawTools, operate } });
        demand(journal.join() === ['create-test-cohort', ...Array(4).fill('provision-service-credential')].join(), 'PROVISION_SEQUENCE_CHANGED');
        const expiresAt = new Date(clock() + 1_800_000).toISOString();
        invitation = operate('driver-bootstrap', { identityId: context.actor, expiresAt });
        demand(uuid.test(invitation.resourceId) && invitation.identityId === context.actor
          && invitation.expiresAt === expiresAt && token(invitation.driverInviteCode), 'BOOTSTRAP_NOT_BOUND');
        stopped(); phase = 'PREPARED';
      } catch (error) { phase = 'FAILED'; throw error; }
    },
    enterFunctional(observedBinding, apiPid) {
      demand(phase === 'PREPARED', 'PROVISION_NOT_COMPLETE'); unchanged(observedBinding);
      demand(Number.isSafeInteger(apiPid) && apiPid > 0, 'BAD_API_PID');
      demand(Date.parse(invitation.expiresAt) > clock(), 'PREPARED_BOOTSTRAP_EXPIRED');
      gates.assertOwnedApi(apiPid, expected); phase = 'FUNCTIONAL';
    },
    liveOperate(operation, fields) {
      demand(phase === 'FUNCTIONAL', 'API_PHASE_INVALID'); unchanged();
      demand(operation === 'driver-bootstrap', 'OPERATOR_WHILE_API_LIVE');
      demand(!consumed && exact(fields, ['identityId', 'expiresAt']) && fields.identityId === context.actor,
        'BOOTSTRAP_CONSUMPTION_NOT_BOUND');
      const requested = Date.parse(fields.expiresAt), expires = Date.parse(invitation.expiresAt), now = clock();
      demand(expires > now && requested >= expires && requested > now && requested <= now + 1_800_000, 'BOOTSTRAP_EXPIRY_INVALID');
      // The unchanged passengerConversation.bootstrap sends this SAME real code through CLAIM_DRIVER_INVITE.
      const response = clone(invitation); delete invitation.driverInviteCode; consumed = true;
      return response;
    },
    enterCleanup() {
      demand(['FUNCTIONAL', 'PREPARED', 'FAILED'].includes(phase), 'CLEANUP_PHASE_INVALID');
      stopped(); phase = 'CLEANUP';
    },
    cleanupOperate(operation, fields = {}) {
      demand(phase === 'CLEANUP', 'CLEANUP_PHASE_INVALID'); stopped();
      const reset = operation === 'reset-test-data' && exact(fields, []);
      const revoke = operation === 'revoke-service-credential' && exact(fields, ['credentialId', 'reasonCode'])
        && uuid.test(fields.credentialId)
        && fields.reasonCode === 'SYNTHETIC_SMOKE_COMPLETED';
      demand(reset || revoke, 'CLEANUP_OUT_OF_SCOPE');
      if (revoke) gates.assertScopedTechnicalCredential(fields.credentialId,
        { batchId: context.batch, instance: context.instance, environment: 'wa070-local' });
      return rawTools.operate(operation, fields);
    },
    privateCheckpoint() { return { phase: phase === 'PREPARED' ? 'PREPARED' : 'FAILED', journal: [...journal], invitation: clone(invitation) }; },
    audit() { return { profile: 'NATIVE_SERIAL_PROVISIONING_V1', phase, sourceSha: expected.sourceSha, jarSha256: expected.jarSha256,
      initialCliCommitted: journal.length, bootstrapConsumedOnce: consumed, operationNames: [...journal] }; },
    dispose() { if (invitation) delete invitation.driverInviteCode; },
  };
}
