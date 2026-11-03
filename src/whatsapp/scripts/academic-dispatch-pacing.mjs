import { demand, uuid, summary } from './070-worker-common.mjs';

/** Test-harness admission only. The API's quota and final SUBMITTED result remain authoritative. */
export function createPacedDispatch({ worker, provider, scopeFor, inspect, wait, now = Date.now,
  assertActive, onWait = () => {}, maxWaitMs = 65000 }) {
  demand(Number.isSafeInteger(maxWaitMs) && maxWaitMs > 0 && maxWaitMs <= 65000, 'QUOTA_WAIT_BOUND_INVALID');
  const pending = [];
  let windowWaits = 0, waitedMs = 0;
  async function ready(target) {
    const deadline = now() + maxWaitMs;
    while (true) {
      assertActive();
      const observed = await inspect(target);
      demand(now() <= deadline, 'QUOTA_WAIT_TIMEOUT');
      demand(observed?.scopeValid === true, 'EXPECTED_RESPONSE_SCOPE_MISSING');
      demand(observed.state === 'PENDING', 'EXPECTED_RESPONSE_NOT_PENDING');
      demand(Number.isSafeInteger(observed.expiresInMs) && observed.expiresInMs > 0, 'EXPECTED_RESPONSE_EXPIRED');
      demand(Number.isSafeInteger(observed.delayMs) && observed.delayMs >= 0 && observed.delayMs <= 60000,
        'QUOTA_SLOT_INVALID');
      if (observed.delayMs === 0) return;
      const delay = observed.delayMs + 1;
      demand(delay < observed.expiresInMs, 'EXPECTED_RESPONSE_EXPIRES_BEFORE_QUOTA');
      demand(now() + delay <= deadline, 'QUOTA_WAIT_TIMEOUT');
      onWait({ waitMs: delay });
      const started = now();
      await wait(delay);
      assertActive();
      windowWaits++;
      waitedMs += Math.max(0, now() - started);
    }
  }
  return {
    provider: { ...provider, enqueue(item) {
      const target = scopeFor(item);
      demand(target && uuid(target.outboxId) && uuid(target.recipient), 'EXPECTED_RESPONSE_SCOPE_INVALID');
      demand(item.beforeDispatch === undefined || typeof item.beforeDispatch === 'function', 'EXPECTED_RESPONSE_PREPARATION_INVALID');
      provider.enqueue(item);
      pending.push(Object.freeze({ target: Object.freeze({ outboxId: target.outboxId, recipient: target.recipient }),
        beforeDispatch: item.beforeDispatch }));
    } },
    worker: { ...worker, async request(route, authenticated = true) {
      if (route !== '/v1/dispatch-one' || !authenticated || pending.length === 0)
        return worker.request(route, authenticated);
      await ready(pending[0].target);
      // Synthetic app telemetry is refreshed only after quota admission; the API
      // still authorizes the acquisition and the provider verifies its freshness.
      await pending[0].beforeDispatch?.();
      assertActive();
      const response = await worker.request(route, authenticated);
      demand(response.status === 200, 'EXPECTED_RESPONSE_CONTROL_HTTP_FAILED');
      // No IDLE retry, uncertain-attempt retry or lenient success classification.
      summary(response.data, 'dispatch', 'SUBMITTED', response.status);
      pending.shift();
      return response;
    } },
    stats: () => ({ windowWaits, waitedMs }),
  };
}
