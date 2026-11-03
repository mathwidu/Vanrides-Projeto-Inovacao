import { WorkerError } from './errors.mjs';

const path = '/integrations/whatsapp/v1/jobs/tick';
const jobType = 'HEALTH_RECONCILE';
const limit = 100;
const cursorPattern = /^jobcursor_[0-9a-f]{32}$/u;
const utcMinute = now => new Date(Math.floor(now / 60000) * 60000).toISOString().replace('.000Z', 'Z');

export const emptyDeliverySummary = status => ({ status, processed: 0 });

export function safeDeliverySummary(value) {
  try {
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== 2
        || !Object.hasOwn(value, 'status') || !Object.hasOwn(value, 'processed')
        || !['IDLE', 'BUSY', 'PROCESSED', 'MORE', 'UNAVAILABLE'].includes(value.status)
        || !Number.isInteger(value.processed) || value.processed < 0 || value.processed > 2 * limit
        || ['IDLE', 'BUSY'].includes(value.status) && value.processed !== 0
        || value.status === 'PROCESSED' && value.processed === 0) return emptyDeliverySummary('UNAVAILABLE');
    return { status: value.status, processed: value.processed };
  } catch { return emptyDeliverySummary('UNAVAILABLE'); }
}

function validPage(response, previousCursor) {
  const body = response?.body;
  return response?.status === 200 && body && typeof body === 'object' && !Array.isArray(body)
    && Object.keys(body).length === 4 && ['jobType', 'processed', 'nextCursor', 'serverTime'].every(key => Object.hasOwn(body, key))
    && body.jobType === jobType && Number.isInteger(body.processed) && body.processed >= 0 && body.processed <= limit
    && (body.nextCursor === null || typeof body.nextCursor === 'string' && cursorPattern.test(body.nextCursor)
      && body.nextCursor !== previousCursor)
    && typeof body.serverTime === 'string' && Number.isFinite(Date.parse(body.serverTime));
}

// Reconciliation uses only SYSTEM_JOB. Evolution state cannot suspend it.
// The backend owns idempotency; this private continuation is safe to discard.
export function createDeliveryTicker({ api, now = Date.now, operationTimeoutMs = 18_000 }) {
  if (typeof api?.request !== 'function' || typeof now !== 'function'
      || !Number.isInteger(operationTimeoutMs) || operationTimeoutMs < 1 || operationTimeoutMs > 18_000)
    throw new WorkerError('CONFIG_INVALID');
  let continuation;
  return async () => {
    const started = now(), deadline = started + operationTimeoutMs;
    if (continuation && started - continuation.started >= 4 * 60000) continuation = undefined;
    const work = continuation ?? { windowKey: utcMinute(started), started };
    const summary = emptyDeliverySummary('IDLE');
    let failed = false, expired = false, timer;
    const timeout = new Promise(resolve => {
      timer = setTimeout(() => { expired = true; resolve(null); }, operationTimeoutMs);
    });
    try {
      for (let page = 0; page < 2; page++) {
        if (now() >= deadline) { failed = true; break; }
        const body = { jobType, windowKey: work.windowKey, batchLimit: limit };
        if (work.cursor) body.cursor = work.cursor;
        const response = await Promise.race([api.request(path, { body, deadline }), timeout]);
        if (expired || now() >= deadline || !validPage(response, work.cursor)) {
          if ([400, 404, 409, 410, 422].includes(response?.status)) continuation = undefined;
          failed = true;
          break;
        }
        summary.processed += response.body.processed;
        if (response.body.nextCursor === null) { continuation = undefined; break; }
        work.cursor = response.body.nextCursor;
        continuation = work;
      }
    } catch { failed = true; }
    finally { clearTimeout(timer); }
    summary.status = failed ? 'UNAVAILABLE' : continuation ? 'MORE' : summary.processed ? 'PROCESSED' : 'IDLE';
    return summary;
  };
}
