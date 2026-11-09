import { Buffer } from 'node:buffer';
import { readEvolutionConnection } from './evolution-connection.mjs';

const MAX_RESPONSE_BYTES = 32 * 1024;
// Evolution can return 400 after sendMessage succeeded; only pre-handler rejections are definite.
const DEFINITE_REJECTIONS = new Set([401, 403, 404]);
const REJECTED = Object.freeze({ outcome: 'FAILED', failureCode: 'REJECTED' });

/** One reservation authorizes one HTTP attempt. This function never retries or logs. */
export async function submitEvolutionMessage(options = {}, expectedType) {
  let attemptToken;
  let config;
  try {
    const reservation = options?.reservation;
    const candidate = reservation?.attemptToken;
    if (!validToken(candidate)) return { ...REJECTED };
    attemptToken = candidate;
    config = validate(options, reservation, expectedType);
  } catch {
    return rejected(attemptToken);
  }

  const controller = new AbortController();
  let timer;
  let timedOut = false;
  let closed = false;
  let postInvoked = false;
  let resolveCanceled;
  const noEffect = () => ({ attemptToken, outcome: 'NOT_SUBMITTED', failureCode: 'CHANNEL_UNAVAILABLE' });
  const interrupted = () => postInvoked ? unknown(attemptToken, timedOut ? 'TIMEOUT' : 'CHANNEL_UNAVAILABLE') : noEffect();
  const abort = () => { closed = true; controller.abort(); resolveCanceled?.(interrupted()); };
  const expired = () => closed || controller.signal.aborted || config.now() >= config.deadline;
  try {
    const canceled = new Promise(resolve => { resolveCanceled = resolve; });
    config.signal?.addEventListener('abort', abort, { once: true });
    if (config.signal?.aborted) abort();
    const timeout = new Promise(resolve => {
      timer = setTimeout(() => {
        timedOut = true;
        abort();
        resolve(interrupted());
      }, config.timeoutMs);
    });
    const operation = async () => {
      if (expired()) return noEffect();
      const connection = await config.readConnection({ baseUrl: config.origin, instance: config.instance,
        apiKey: config.apiKey, fetchImpl: config.fetchImpl, now: config.now,
        deadline: Math.min(config.deadline, config.now() + 2000), signal: controller.signal });
      // A late probe cannot continue after any timeout/cancellation, even if it ignored AbortSignal.
      if (expired() || connection === null || typeof connection !== 'object' || Array.isArray(connection)
          || Object.keys(connection).length !== 1 || connection.status !== 'OPEN') return noEffect();
      // No await between the final fence and marking the invocation: a thrown fetch is already uncertain.
      postInvoked = true;
      const response = await config.fetchImpl(config.url, {
        method: 'POST',
        headers: { apikey: config.apiKey, 'content-type': 'application/json' },
        body: config.body,
        redirect: 'manual',
        signal: controller.signal,
      });
      if (expired()) {
        cancel(response.body);
        return unknown(attemptToken, 'TIMEOUT');
      }
      if (DEFINITE_REJECTIONS.has(response.status)) {
        cancel(response.body);
        return rejected(attemptToken);
      }
      if (response.status !== 201) {
        cancel(response.body);
        return unknown(attemptToken);
      }
      const body = await boundedJson(response, controller.signal);
      if (expired()) return unknown(attemptToken, 'TIMEOUT');
      const id = body?.key?.id;
      if (typeof id !== 'string' || !/^[A-Za-z0-9_-]{1,160}$/.test(id)) return unknown(attemptToken);
      return { attemptToken, outcome: 'SUBMITTED', externalMessageId: id };
    };
    // The race bounds even an injected fetch/stream that ignores AbortSignal.
    return await Promise.race([operation(), timeout, canceled]);
  } catch {
    return interrupted();
  } finally {
    closed = true;
    config.signal?.removeEventListener('abort', abort);
    controller.abort();
    clearTimeout(timer);
  }
}

function validate(options, reservation, expectedType) {
  const { baseUrl, instance, apiKey, fetchImpl: providedFetch, now: providedNow } = options;
  fields(reservation, ['outboxId', 'attemptToken', 'submitBefore', 'recipient', 'content', 'serverTime']);
  if (typeof reservation.outboxId !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(reservation.outboxId)) invalid();
  fields(reservation.recipient, ['addressType', 'address']);
  const { addressType, address } = reservation.recipient;
  if (typeof address !== 'string'
      || !(addressType === 'JID' && /^[0-9]{1,32}@s\.whatsapp\.net$/.test(address)
        || addressType === 'LID' && /^[0-9]{1,32}@lid$/.test(address))) invalid();
  const before = utc(reservation.submitBefore);
  const serverTime = utc(reservation.serverTime);
  const { type } = reservation.content ?? {};
  if (expectedType !== undefined && expectedType !== type) invalid();
  let providerBody;
  let route;
  if (type === 'TEXT') {
    fields(reservation.content, ['type', 'text', 'referenceTime']);
    const { text, referenceTime } = reservation.content;
    if (typeof text !== 'string' || [...text].length < 1 || [...text].length > 2000
        || utc(referenceTime) > serverTime) invalid();
    providerBody = { number: address, text, linkPreview: false };
    route = 'sendText';
  } else if (type === 'STATIC_PIN') {
    fields(reservation.content, ['type', 'latitude', 'longitude', 'capturedAt', 'label']);
    const { latitude, longitude, capturedAt, label } = reservation.content;
    const captured = utc(capturedAt);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180
        || label !== 'Localização atual do veículo'
        || captured > serverTime || before > captured + 20_000) invalid();
    // Evolution 2.3.7 sends a static pin. Its fixed address carries capture time;
    // no live location, map URL, passenger pickup or model-authored text is sent.
    providerBody = { number: address, latitude, longitude, name: label,
      address: `Capturada em ${capturedAt}` };
    route = 'sendLocation';
  } else invalid();
  if (before <= serverTime) invalid();
  const origin = baseOrigin(baseUrl);
  if (typeof instance !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(instance)) invalid();
  if (typeof apiKey !== 'string' || !/^[\x21-\x7e]{1,4096}$/.test(apiKey)) invalid();
  const fetchImpl = providedFetch === undefined ? globalThis.fetch : providedFetch;
  const now = providedNow === undefined ? () => Date.now() : providedNow;
  const readConnection = options.readConnection === undefined ? readEvolutionConnection : options.readConnection;
  if (typeof fetchImpl !== 'function' || typeof now !== 'function' || typeof readConnection !== 'function'
      || options.signal !== undefined && !(options.signal instanceof AbortSignal)) invalid();
  // Build the immutable provider payload before the final local deadline check.
  const body = JSON.stringify(providerBody);
  const currentTime = now();
  const deadline = Math.min(before, currentTime + 10_000, options.deadline ?? before);
  if (!Number.isSafeInteger(currentTime) || !Number.isSafeInteger(deadline) || deadline <= currentTime) invalid();
  return {
    origin, instance, now, readConnection, deadline, signal: options.signal,
    url: `${origin}/message/${route}/${encodeURIComponent(instance)}`,
    apiKey,
    body,
    fetchImpl,
    timeoutMs: deadline - currentTime,
  };
}

function baseOrigin(value) {
  if (typeof value !== 'string' || value.length > 2048) invalid();
  // Check the written authority too: WHATWG URL normalizes 127.1 and integer IPv4.
  const authority = /^(https?):\/\/(\[[^\]]+\]|[^:/?#\\\s@]+)(?::([0-9]+))?\/?$/.exec(value);
  if (!authority) invalid();
  const url = new URL(value);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/') invalid();
  if (authority[3] !== undefined && Number(authority[3]) < 1) invalid();
  if (url.protocol !== 'https:' && !(url.protocol === 'http:'
      && ['127.0.0.1', 'localhost', '[::1]'].includes(authority[2].toLowerCase()))) invalid();
  return url.origin;
}

function validToken(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
    && Buffer.from(value, 'base64url').toString('base64url') === value;
}

function fields(value, expected) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) invalid();
  const keys = Object.keys(value);
  if (keys.length !== expected.length || !expected.every(key => Object.hasOwn(value, key))) invalid();
}

function utc(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)) invalid();
  const milliseconds = Date.parse(value);
  if (!Number.isFinite(milliseconds) || new Date(milliseconds).toISOString().slice(0, 19) !== value.slice(0, 19)) invalid();
  return milliseconds;
}

async function boundedJson(response, signal) {
  const length = response.headers?.get('content-length');
  if (length && /^\d+$/.test(length) && Number(length) > MAX_RESPONSE_BYTES) {
    cancel(response.body);
    invalid();
  }
  const reader = response.body.getReader();
  const abort = () => cancel(reader);
  signal.addEventListener('abort', abort, { once: true });
  const chunks = [];
  let bytes = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      if (!(chunk.value instanceof Uint8Array)) invalid();
      bytes += chunk.value.byteLength;
      if (bytes > MAX_RESPONSE_BYTES) {
        cancel(reader);
        invalid();
      }
      chunks.push(chunk.value);
    }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, bytes)));
  } finally {
    signal.removeEventListener('abort', abort);
    try { reader.releaseLock(); } catch { /* The timeout may still be canceling a read. */ }
  }
}

function cancel(stream) {
  try { Promise.resolve(stream?.cancel()).catch(() => {}); } catch { /* Never expose provider errors. */ }
}

function invalid() { throw new Error('EVOLUTION_INPUT_OR_RESPONSE_INVALID'); }
function rejected(token) { return token === undefined ? { ...REJECTED } : { attemptToken: token, ...REJECTED }; }
function unknown(token, failureCode = 'CHANNEL_UNAVAILABLE') { return { attemptToken: token, outcome: 'UNKNOWN', failureCode }; }
