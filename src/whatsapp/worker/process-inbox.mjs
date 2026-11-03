import { decideDeterministically, validateContext, validateMessage, validateDecision } from '../agent/deterministic.mjs';

const prefix = '/integrations/whatsapp/v1';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const summary = (status, claimed = 0, processed = 0, retried = 0, paused = 0) =>
  ({ status, claimed, processed, retried, paused });

export function createInboxProcessor({ api, now = Date.now, decide = decideDeterministically }) {
  return async function processInbox() {
    const end = now() + 25000;
    const deadline = () => Math.min(end, item ? Date.parse(item.leaseExpiresAt) : end, now() + 5000);
    let item;
    async function finish(outcome, failureCode) {
      const body = outcome === 'COMPLETED' ? { outcome }
        : outcome === 'RETRY' ? { outcome, failureCode, retryAfterSeconds: 5 } : { outcome, failureCode };
      const ack = await api.request(`${prefix}/inbox/${item.inboxEventId}/result`, {
        body, headers: { 'X-Inbox-Lease': item.inboxLease }, deadline: deadline(),
      });
      if (ack.status !== 200 || ack.body?.inboxEventId !== item.inboxEventId) return summary('UNAVAILABLE', 1);
      if (ack.body.state === 'PROCESSED') return summary('PROCESSED', 1, 1);
      if (ack.body.state === 'PENDING' && outcome === 'RETRY') return summary('RETRY', 1, 0, 1);
      if (ack.body.state === 'PAUSED' && outcome === 'PAUSED') return summary('PAUSED', 1, 0, 0, 1);
      return summary('UNAVAILABLE', 1);
    }
    try {
      const claim = await api.request(`${prefix}/inbox/claim`, {
        body: { batchLimit: 1, leaseSeconds: 60 }, deadline: deadline(),
      });
      if (claim.status !== 200 || !Array.isArray(claim.body?.items) || claim.body.items.length > 1)
        return summary('UNAVAILABLE');
      if (!claim.body.items.length) return summary('IDLE');
      const candidate = claim.body.items[0];
      if (!uuid.test(candidate?.inboxEventId) || !/^[A-Za-z0-9_-]{43}$/u.test(candidate?.inboxLease)
          || !Number.isFinite(Date.parse(candidate.leaseExpiresAt))
          || Date.parse(candidate.leaseExpiresAt) <= now()) return summary('UNAVAILABLE');
      item = candidate;
      validateMessage(item.message);
      const context = await api.request(`${prefix}/context?inboxEventId=${item.inboxEventId}`, {
        method: 'GET', headers: { 'X-Inbox-Lease': item.inboxLease }, deadline: deadline(),
      });
      if (context.status !== 200) return await finish('RETRY', 'DEPENDENCY_UNAVAILABLE');
      validateContext(context.body, item.inboxEventId);
      // Preserve time for the command and ACK. The model shares this deadline
      // across retries; a slow/expired proposal cannot reach the command API.
      const decisionDeadline = Math.min(now() + 20000, end - 10000, Date.parse(item.leaseExpiresAt) - 10000);
      if (decisionDeadline <= now()) return await finish('RETRY', 'DEPENDENCY_UNAVAILABLE');
      let decision;
      let decisionTimer;
      try {
        const timeout = new Promise((_, reject) => {
          decisionTimer = setTimeout(() => reject(new Error('DECISION_TIMEOUT')), decisionDeadline - now());
        });
        decision = await Promise.race([
          Promise.resolve().then(() => decide(item.message, context.body, { deadline: decisionDeadline })), timeout,
        ]);
        if (now() >= decisionDeadline) throw new Error('DECISION_TIMEOUT');
        validateDecision(decision, context.body);
      }
      catch { return await finish('PAUSED', 'INVALID_MODEL_OUTPUT'); }
      finally { clearTimeout(decisionTimer); }
      // Only validated decisions become commands. Actor, target and endpoint
      // are never supplied by the model; the API rechecks its own policy.
      const response = await api.request(`${prefix}/commands`, {
        headers: { 'X-Inbox-Lease': item.inboxLease, 'Idempotency-Key': `wa070_${item.inboxEventId}` },
        body: { commandType: decision.commandType, inboxEventId: item.inboxEventId,
          expectedVersion: context.body.entityVersion, payload: decision.payload }, deadline: deadline(),
      });
      if (response.status === 200 && uuid.test(response.body?.commandId)
          && ['APPLIED', 'NO_CHANGE'].includes(response.body?.outcome)) return await finish('COMPLETED');
      if (response.status >= 500 || response.status === 409 && response.body?.code === 'VERSION_CONFLICT')
        return await finish('RETRY', 'DEPENDENCY_UNAVAILABLE');
      // A committed rejection wins in the API and becomes PROCESSED. An uncommitted invalid
      // proposal pauses, instead of issuing a second command for the same human message.
      return await finish('PAUSED', 'INVALID_MODEL_OUTPUT');
    } catch {
      if (!item || now() >= end) return summary('UNAVAILABLE', item ? 1 : 0);
      try { return await finish('RETRY', 'DEPENDENCY_UNAVAILABLE'); }
      catch { return summary('UNAVAILABLE', 1); }
    }
  };
}
