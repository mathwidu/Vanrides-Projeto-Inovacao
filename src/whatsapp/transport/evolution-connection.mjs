import { Buffer } from 'node:buffer';

const MAX_BODY_BYTES = 16 * 1024;
const UNKNOWN = Object.freeze({ status: 'UNKNOWN' });

/** A bounded read only: never connects an instance, submits a message, retries or logs. */
export async function readEvolutionConnection(options = {}) {
  let input;
  try { input = validate(options); } catch { return { ...UNKNOWN }; }
  const controller = new AbortController();
  const abort = () => controller.abort();
  input.signal?.addEventListener('abort', abort, { once: true });
  if (input.signal?.aborted) controller.abort();
  let timer;
  try {
    const timeout = new Promise(resolve => {
      timer = setTimeout(() => { controller.abort(); resolve({ ...UNKNOWN }); }, input.timeoutMs);
    });
    const operation = async () => {
      if (controller.signal.aborted || input.now() >= input.deadline) return { ...UNKNOWN };
      const response = await input.fetchImpl(input.url, {
        method: 'GET', headers: { apikey: input.apiKey }, redirect: 'manual', signal: controller.signal,
      });
      if (controller.signal.aborted || input.now() >= input.deadline || response.status !== 200) {
        cancel(response.body); return { ...UNKNOWN };
      }
      const value = await readBody(response, controller.signal);
      if (controller.signal.aborted || input.now() >= input.deadline
          || !exact(value, ['instance']) || !exact(value.instance, ['instanceName', 'state'])
          || value.instance.instanceName !== input.instance) return { ...UNKNOWN };
      if (value.instance.state === 'open') return { status: 'OPEN' };
      if (['close', 'connecting', 'refused'].includes(value.instance.state)) return { status: 'CLOSED' };
      return { ...UNKNOWN };
    };
    return await Promise.race([operation(), timeout]);
  } catch { return { ...UNKNOWN }; }
  finally {
    clearTimeout(timer);
    input.signal?.removeEventListener('abort', abort);
  }
}

function validate(options) {
  const { baseUrl, instance, apiKey, signal } = options;
  if (typeof baseUrl !== 'string' || baseUrl.length > 2048) throw Error();
  const authority = /^(https?):\/\/(\[[^\]]+\]|[^:/?#\\\s@]+)(?::([0-9]+))?\/?$/.exec(baseUrl);
  if (!authority) throw Error();
  const url = new URL(baseUrl);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/'
      || authority[3] !== undefined && Number(authority[3]) < 1
      || url.protocol !== 'https:' && !(url.protocol === 'http:'
        && ['127.0.0.1', 'localhost', '[::1]'].includes(authority[2].toLowerCase()))) throw Error();
  if (typeof instance !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(instance)
      || typeof apiKey !== 'string' || !/^[\x21-\x7e]{1,4096}$/.test(apiKey)) throw Error();
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  const now = options.now ?? Date.now;
  if (typeof fetchImpl !== 'function' || typeof now !== 'function'
      || signal !== undefined && !(signal instanceof AbortSignal)) throw Error();
  const started = now();
  const deadline = options.deadline ?? started + 2000;
  if (!Number.isSafeInteger(started) || !Number.isSafeInteger(deadline) || deadline <= started) throw Error();
  return { url: `${url.origin}/instance/connectionState/${encodeURIComponent(instance)}`, instance, apiKey,
    fetchImpl, now, signal, deadline: Math.min(deadline, started + 2000), timeoutMs: Math.min(deadline - started, 2000) };
}

function exact(value, keys) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}

function cancel(body) {
  try { Promise.resolve(body?.cancel()).catch(() => {}); } catch { /* No provider content in errors. */ }
}

async function readBody(response, signal) {
  const size = response.headers?.get('content-length');
  if (size && /^\d+$/.test(size) && Number(size) > MAX_BODY_BYTES) { cancel(response.body); throw Error(); }
  const reader = response.body.getReader();
  const abort = () => cancel(reader);
  signal.addEventListener('abort', abort, { once: true });
  const chunks = [];
  let total = 0;
  try {
    while (!signal.aborted) {
      const part = await reader.read();
      if (part.done) break;
      if (!(part.value instanceof Uint8Array)) throw Error();
      total += part.value.length;
      if (total > MAX_BODY_BYTES) { cancel(reader); throw Error(); }
      chunks.push(part.value);
    }
    if (signal.aborted) throw Error();
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, total)));
  } finally {
    signal.removeEventListener('abort', abort);
    try { reader.releaseLock(); } catch { /* A canceled stream may still be settling. */ }
  }
}
