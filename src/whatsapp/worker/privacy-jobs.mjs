import { WorkerError } from './errors.mjs';
import { safeDeliverySummary } from './delivery-jobs.mjs';

export const PRIVACY_FAMILIES = Object.freeze([
  'TERMINAL_7D', 'TECHNICAL_7D', 'PROFILE_30D', 'HISTORY_PROJECTION_90D', 'HISTORY_90D',
]);
const path = '/integrations/whatsapp/v1/privacy/tick';
const counts = ['examined', 'erased', 'deleted', 'deferred', 'wouldChange'];
const keys = ['status', 'family', ...counts, 'complete', 'nextCursor', 'serverTime', 'reasonCodes'];
const cursorPattern = /^privacycursor_[A-Za-z0-9_-]{40,2200}$/u;
const empty = status => ({ status, ...Object.fromEntries(counts.map(key => [key, 0])) });

function validPage(response, family, previousCursor) {
  const body = response?.body;
  return response?.status === 200 && body && typeof body === 'object' && !Array.isArray(body)
    && Object.keys(body).length === keys.length && keys.every(key => Object.hasOwn(body, key))
    && ['DISABLED', 'DRY_RUN', 'APPLIED', 'DEFERRED'].includes(body.status) && body.family === family
    && counts.every(key => Number.isSafeInteger(body[key]) && body[key] >= 0) && body.examined <= 20
    && typeof body.complete === 'boolean'
    && (body.nextCursor === null || typeof body.nextCursor === 'string'
      && cursorPattern.test(body.nextCursor) && body.nextCursor !== previousCursor)
    && (!body.complete || body.nextCursor === null)
    && typeof body.serverTime === 'string' && Number.isFinite(Date.parse(body.serverTime))
    && Array.isArray(body.reasonCodes) && body.reasonCodes.length <= 30
    && body.reasonCodes.every(code => typeof code === 'string' && /^[A-Z][A-Z_]{0,59}$/u.test(code))
    && (body.status !== 'DRY_RUN' || body.erased === 0 && body.deleted === 0)
    && (body.status !== 'DISABLED' || counts.every(key => body[key] === 0) && body.nextCursor === null);
}

/** Private bounded maintenance. Only the existing W08 schedule invokes it; no extra timer/cron. */
export function createPrivacyTicker({ api, enabled = false, now = Date.now, report = () => {}, operationTimeoutMs = 5000 }) {
  if (typeof enabled !== 'boolean' || enabled && typeof api?.request !== 'function' || typeof now !== 'function'
      || typeof report !== 'function' || !Number.isInteger(operationTimeoutMs) || operationTimeoutMs < 1 || operationTimeoutMs > 5000)
    throw new WorkerError('CONFIG_INVALID');
  let active = false, lastStarted, lastReport;
  const continuations = new Map();
  return async () => {
    if (!enabled) return empty('DISABLED');
    const started = now();
    if (!Number.isSafeInteger(started)) return empty('UNAVAILABLE');
    if (active) return empty('BUSY');
    if (lastStarted !== undefined && started >= lastStarted && started - lastStarted < 60_000) return empty('IDLE');
    active = true;
    lastStarted = started;
    const deadline = started + operationTimeoutMs;
    const summary = empty('IDLE');
    let failed = false, deferred = false;
    try {
      for (const family of PRIVACY_FAMILIES) {
        if (now() >= deadline) { failed = true; break; }
        let work = continuations.get(family);
        if (work && (started < work.started || started - work.started >= 240_000)) {
          continuations.delete(family); work = undefined;
        }
        const body = { family, batchLimit: 20 };
        if (work) body.cursor = work.cursor;
        let timer;
        try {
          // One slow family cannot consume the budgets of the four independent families.
          const remaining = Math.min(1000, deadline - now());
          if (remaining < 1) throw new WorkerError('TIMEOUT');
          const requestDeadline = now() + remaining;
          const response = await Promise.race([
            Promise.resolve().then(() => api.request(path, { body, deadline: requestDeadline })),
            new Promise(resolve => { timer = setTimeout(() => resolve(null), remaining); }),
          ]);
          if (now() >= requestDeadline || !validPage(response, family, work?.cursor)) {
            if ([400, 401, 403, 404, 409, 410, 422].includes(response?.status)) continuations.delete(family);
            failed = true;
            continue;
          }
          if (!counts.every(key => Number.isSafeInteger(summary[key] + response.body[key]))) throw new WorkerError('INVALID_RESPONSE');
          for (const key of counts) summary[key] += response.body[key];
          deferred ||= response.body.status === 'DEFERRED' || response.body.deferred > 0;
          if (response.body.nextCursor === null) continuations.delete(family);
          else continuations.set(family, { cursor: response.body.nextCursor, started: work?.started ?? started });
        } catch { failed = true; }
        finally { clearTimeout(timer); }
      }
      summary.status = failed ? 'UNAVAILABLE' : deferred ? 'DEFERRED' : continuations.size ? 'MORE'
        : summary.examined ? 'PROCESSED' : 'IDLE';
      const signature = JSON.stringify(summary);
      if ((failed || counts.some(key => summary[key] !== 0)) && signature !== lastReport) {
        // Only these closed counters/status reach the log callback. No reasons, IDs, cursor or payload.
        try { report(Object.freeze({ ...summary })); } catch { /* Logging cannot interrupt maintenance. */ }
      }
      lastReport = signature;
      return summary;
    } finally { active = false; }
  };
}

/** Preserve W08's two-field public contract and its delivery counts. Total nominal budget: 18s + 5s. */
export function withPrivacyMaintenance(reconcileDelivery, tickPrivacy) {
  if (typeof reconcileDelivery !== 'function' || typeof tickPrivacy !== 'function') throw new WorkerError('CONFIG_INVALID');
  return async () => {
    let delivery;
    try { delivery = safeDeliverySummary(await reconcileDelivery()); }
    catch { delivery = { status: 'UNAVAILABLE', processed: 0 }; }
    try {
      const privacy = await tickPrivacy();
      if (!privacy || Object.keys(privacy).length !== counts.length + 1
          || !['DISABLED', 'IDLE', 'BUSY', 'DEFERRED', 'MORE', 'PROCESSED'].includes(privacy.status)
          || !counts.every(key => Number.isSafeInteger(privacy[key]) && privacy[key] >= 0))
        return { status: 'UNAVAILABLE', processed: delivery.processed };
    } catch { return { status: 'UNAVAILABLE', processed: delivery.processed }; }
    return delivery;
  };
}
