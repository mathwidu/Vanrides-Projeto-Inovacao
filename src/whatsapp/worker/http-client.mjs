import { Buffer } from 'node:buffer';
import { isToken, trustedOrigin } from './config.mjs';
import { WorkerError } from './errors.mjs';

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const PREFIX = '/integrations/whatsapp/v1/';
const POST = new RegExp(`^${PREFIX}(inbox/claim|inbox/${UUID}/result|commands|outbox/claim|outbox/${UUID}/(authorize-submit|result)|jobs/tick)$`);
const CONTEXT = new RegExp(`^${PREFIX}context\\?inboxEventId=${UUID}$`);
const MAX_BYTES = 32 * 1024;

export { WorkerError } from './errors.mjs';

function cancel(value) {
  try { Promise.resolve(value?.cancel()).catch(() => {}); } catch { /* Closed errors only. */ }
}

async function readJson(response, signal) {
  const length = response.headers?.get('content-length');
  if (length && /^\d+$/.test(length) && Number(length) > MAX_BYTES) {
    cancel(response.body);
    throw new WorkerError('INVALID_RESPONSE');
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
      if (!(chunk.value instanceof Uint8Array)) throw new WorkerError('INVALID_RESPONSE');
      bytes += chunk.value.byteLength;
      if (bytes > MAX_BYTES) { cancel(reader); throw new WorkerError('INVALID_RESPONSE'); }
      chunks.push(chunk.value);
    }
    const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, bytes)));
    if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new WorkerError('INVALID_RESPONSE');
    return body;
  } finally {
    signal.removeEventListener('abort', abort);
    try { reader.releaseLock(); } catch { /* Cancellation may still be settling. */ }
  }
}

function requestHeaders(extra) {
  if (extra === null || typeof extra !== 'object' || Array.isArray(extra)) throw new WorkerError('INVALID_REQUEST');
  const result = {};
  for (const [key, value] of Object.entries(extra)) {
    if (key === 'X-Inbox-Lease' ? !isToken(value)
      : key === 'Idempotency-Key' ? typeof value !== 'string' || !/^[A-Za-z0-9_-]{16,128}$/.test(value)
        : key === 'X-Correlation-Id' ? typeof value !== 'string' || !/^[A-Za-z0-9_-]{8,80}$/.test(value) : true)
      throw new WorkerError('INVALID_REQUEST');
    result[key] = value;
  }
  return result;
}

/** Root URL and credential are immutable configuration; callers can select only closed API paths. */
export function createApiClient({ baseUrl, token, localMode = false, allowedHttpHosts = [], fetchImpl = fetch, now = Date.now }) {
  const origin = trustedOrigin(baseUrl, { localMode, allowedHttpHosts });
  if (!isToken(token) || typeof fetchImpl !== 'function' || typeof now !== 'function') throw new WorkerError('CONFIG_INVALID');
  async function request(path, options = {}) {
    let input;
    try {
      const method = options.method ?? 'POST';
      if (typeof path !== 'string' || !(method === 'POST' && POST.test(path) || method === 'GET' && CONTEXT.test(path)))
        throw new WorkerError('INVALID_REQUEST');
      const currentTime = now(), deadline = options.deadline ?? currentTime + 5000;
      if (!Number.isSafeInteger(currentTime) || !Number.isSafeInteger(deadline) || deadline <= currentTime) throw new WorkerError('TIMEOUT');
      const headers = { ...requestHeaders(options.headers ?? {}), Authorization: `Bearer ${token}`, 'content-type': 'application/json' };
      let body;
      if (method === 'POST') {
        if (options.body === null || typeof options.body !== 'object' || Array.isArray(options.body)) throw new WorkerError('INVALID_REQUEST');
        body = JSON.stringify(options.body);
        if (Buffer.byteLength(body) > MAX_BYTES) throw new WorkerError('INVALID_REQUEST');
      } else if (options.body !== undefined) throw new WorkerError('INVALID_REQUEST');
      input = { method, headers, body, timeoutMs: Math.min(10_000, deadline - currentTime) };
    } catch (error) {
      throw new WorkerError(error instanceof WorkerError ? error.code : 'INVALID_REQUEST');
    }
    const controller = new AbortController();
    let timedOut = false;
    let timer;
    try {
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => { timedOut = true; controller.abort(); reject(new WorkerError('TIMEOUT')); }, input.timeoutMs);
      });
      const operation = async () => {
        const response = await fetchImpl(origin + path, { method: input.method, headers: input.headers, body: input.body,
          redirect: 'manual', signal: controller.signal });
        if (controller.signal.aborted) { cancel(response.body); throw new WorkerError('TIMEOUT'); }
        if (!Number.isInteger(response.status) || response.status < 200 || response.status >= 600) throw new WorkerError('INVALID_RESPONSE');
        if (response.status >= 300 && response.status < 400) { cancel(response.body); throw new WorkerError('UPSTREAM_UNAVAILABLE'); }
        return { status: response.status, body: await readJson(response, controller.signal) };
      };
      return await Promise.race([operation(), timeout]);
    } catch (error) {
      throw new WorkerError(timedOut ? 'TIMEOUT' : error instanceof WorkerError ? error.code : 'UPSTREAM_UNAVAILABLE');
    } finally { clearTimeout(timer); }
  }
  return Object.freeze({ request });
}
