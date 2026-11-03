import { submitEvolutionMessage } from '../transport/evolution-message.mjs';
import { readEvolutionConnection } from '../transport/evolution-connection.mjs';
import { isToken } from './config.mjs';
import { WorkerError } from './errors.mjs';

export const DISPATCH_STATUSES = Object.freeze(['IDLE', 'BUSY', 'SUBMITTED', 'NOT_SUBMITTED', 'UNKNOWN', 'FAILED', 'UNAVAILABLE']);
const PREFIX = '/integrations/whatsapp/v1/outbox/';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const exact = (value, keys) => object(value) && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));

function summary(status, claimed = 0) {
  return { status, claimed, submitted: status === 'SUBMITTED' ? 1 : 0,
    unknown: status === 'UNKNOWN' ? 1 : 0, failed: status === 'FAILED' ? 1 : 0 };
}

function item(value, now) {
  return exact(value, ['outboxId', 'purpose', 'leaseToken', 'leaseExpiresAt', 'expiresAt'])
    && UUID.test(value.outboxId) && ['COMMAND_RESPONSE', 'ATTENDANCE_REMINDER', 'DRIVER_LOCATION', 'APPROACH'].includes(value.purpose) && isToken(value.leaseToken)
    && Date.parse(value.leaseExpiresAt) > now && Date.parse(value.expiresAt) > now;
}

function resultBody(value, token) {
  if (value?.attemptToken !== token) return false;
  if (value.outcome === 'SUBMITTED') return exact(value, ['attemptToken', 'outcome', 'externalMessageId'])
    && typeof value.externalMessageId === 'string' && /^[A-Za-z0-9_-]{1,160}$/.test(value.externalMessageId);
  if (value.outcome === 'NOT_SUBMITTED') return exact(value, ['attemptToken', 'outcome', 'failureCode'])
    && value.failureCode === 'CHANNEL_UNAVAILABLE';
  return ['UNKNOWN', 'FAILED'].includes(value.outcome) && exact(value, ['attemptToken', 'outcome', 'failureCode'])
    && ['TIMEOUT', 'CHANNEL_UNAVAILABLE', 'REJECTED'].includes(value.failureCode)
    && !(value.outcome === 'FAILED' && value.failureCode === 'TIMEOUT');
}

function fallback(token) { return { attemptToken: token, outcome: 'UNKNOWN', failureCode: 'CHANNEL_UNAVAILABLE' }; }

async function boundedSubmit(action, milliseconds, token) {
  const controller = new AbortController();
  let timer;
  try {
    return await Promise.race([Promise.resolve().then(() => action(controller.signal)), new Promise(resolve => {
      timer = setTimeout(() => {
        controller.abort();
        resolve({ attemptToken: token, outcome: 'UNKNOWN', failureCode: 'TIMEOUT' });
      }, milliseconds);
    })]);
  } catch { return fallback(token); }
  finally { clearTimeout(timer); controller.abort(); }
}

async function connectionReady(readConnection, config, now, deadline) {
  const controller = new AbortController();
  let timer;
  try {
    const observation = await Promise.race([
      Promise.resolve().then(() => readConnection({ baseUrl: config.evolutionBaseUrl, instance: config.instance,
        apiKey: config.evolutionApiKey, now, deadline, signal: controller.signal })),
      new Promise(resolve => { timer = setTimeout(() => { controller.abort(); resolve(null); }, 2000); }),
    ]);
    return !controller.signal.aborted && now() < deadline && exact(observation, ['status']) && observation.status === 'OPEN';
  } catch { return false; }
  finally { clearTimeout(timer); controller.abort(); }
}

/** One local dispatcher; cross-process reservation and uncertain-attempt fencing remain in the API. */
export function createDispatcher({ api, config, submit = submitEvolutionMessage, readConnection = readEvolutionConnection, now = Date.now }) {
  if (typeof api?.request !== 'function' || typeof submit !== 'function' || typeof now !== 'function'
      || typeof readConnection !== 'function'
      || typeof config?.evolutionBaseUrl !== 'string' || typeof config.instance !== 'string'
      || typeof config.evolutionApiKey !== 'string') throw new WorkerError('CONFIG_INVALID');
  let busy = false;
  return async function dispatchOne() {
    if (busy) return summary('BUSY');
    busy = true;
    let claimed = 0;
    let reserved = false;
    try {
      const finishBefore = now() + 25_000;
      // A stale callback alone cannot reopen dispatch. This read consumes no outbox attempt.
      if (!await connectionReady(readConnection, config, now, finishBefore - 23_000)) return summary('UNAVAILABLE');
      const claim = await api.request(PREFIX + 'claim', { body: { batchLimit: 1, leaseSeconds: 60 },
        deadline: Math.min(finishBefore - 16_000, now() + 4000) });
      if (claim.status !== 200 || !exact(claim.body, ['items', 'serverTime']) || !Array.isArray(claim.body.items)
          || claim.body.items.length > 1) return summary('UNAVAILABLE');
      if (claim.body.items.length === 0) return summary('IDLE');
      claimed = 1;
      const message = claim.body.items[0];
      if (!item(message, now())) return summary('UNAVAILABLE', claimed);
      const authorization = await api.request(PREFIX + message.outboxId + '/authorize-submit', {
        body: { leaseToken: message.leaseToken }, deadline: Math.min(finishBefore - 15_000, now() + 4000) });
      if (authorization.status !== 200 || authorization.body?.outboxId !== message.outboxId
          || !isToken(authorization.body?.attemptToken)) return summary('UNAVAILABLE', claimed);
      const reservation = authorization.body;
      reserved = true;
      const remaining = Math.min(10_000, Date.parse(reservation.submitBefore) - now(), finishBefore - now() - 5000);
      let result;
      if (!Number.isFinite(remaining) || remaining <= 0) {
        result = { attemptToken: reservation.attemptToken, outcome: 'FAILED', failureCode: 'REJECTED' };
      } else {
        result = await boundedSubmit(signal => submit({ baseUrl: config.evolutionBaseUrl, instance: config.instance,
          apiKey: config.evolutionApiKey, reservation, now, readConnection, signal, deadline: now() + remaining }), remaining, reservation.attemptToken);
      }
      if (!resultBody(result, reservation.attemptToken)) result = fallback(reservation.attemptToken);
      // Never reconstruct a different outcome on receipt loss: post precisely the adapter's body once.
      const receipt = await api.request(PREFIX + message.outboxId + '/result', { body: result,
        deadline: Math.min(finishBefore, now() + 5000) });
      if (receipt.status !== 200 || !exact(receipt.body, ['outboxId', 'state', 'serverTime'])
          || receipt.body.outboxId !== message.outboxId
          || !['PENDING', 'SUBMITTED', 'UNKNOWN', 'FAILED', 'SENT', 'DELIVERED', 'READ', 'EXPIRED', 'CANCELED'].includes(receipt.body.state)
          || receipt.body.state === 'PENDING' && result.outcome !== 'NOT_SUBMITTED') return summary('UNKNOWN', claimed);
      return summary(result.outcome, claimed);
    } catch { return summary(reserved ? 'UNKNOWN' : 'UNAVAILABLE', claimed); }
    finally { busy = false; }
  };
}
