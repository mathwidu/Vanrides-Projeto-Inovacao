import { strictJson, exact, timestamp } from './strict-json.mjs';
import { isHml } from './hml-config.mjs';

const unavailable = () => ({ ok: false });
function cancel(body) {
  try { Promise.resolve(body?.cancel()).catch(() => {}); } catch { /* Never expose upstream errors. */ }
}

async function receipt(response, signal, config) {
  if (isHml(config) && response.headers?.get('x-vanrides-environment') !== config.environment) {
    cancel(response.body); return unavailable();
  }
  if (config.admissionMode === 'API' && response.headers?.get('x-vanrides-admission-policy') !== 'TEAM_V1') {
    cancel(response.body); return unavailable();
  }
  if (response.status !== 202 || !/^application\/json(?:;\s*charset=utf-8)?$/i.test(response.headers?.get('content-type') ?? '')
      || response.headers?.get('content-encoding') !== null
      || Number(response.headers?.get('content-length') ?? 0) > 4096) {
    cancel(response.body); return unavailable();
  }
  const reader = response.body?.getReader();
  if (!reader) return unavailable();
  const abort = () => cancel(reader);
  signal.addEventListener('abort', abort, { once: true });
  let size = 0; const chunks = [];
  try {
    while (!signal.aborted) {
      const chunk = await reader.read();
      if (signal.aborted) return unavailable();
      if (chunk.done) break;
      if (!(chunk.value instanceof Uint8Array) || (size += chunk.value.byteLength) > 4096) return unavailable();
      chunks.push(chunk.value);
    }
    if (signal.aborted) return unavailable();
    const body = strictJson(Buffer.concat(chunks, size));
    if (!exact(body, ['receiptId', 'status', 'serverTime'])
        || typeof body.receiptId !== 'string' || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(body.receiptId)
        || !(isHml(config) ? ['PERSISTED', 'DUPLICATE', 'IGNORED'] : ['PERSISTED', 'DUPLICATE']).includes(body.status) || timestamp(body.serverTime) === null) return unavailable();
    return { ok: true, receipt: { receiptId: body.receiptId, status: body.status, serverTime: body.serverTime } };
  } finally {
    signal.removeEventListener('abort', abort);
    cancel(reader);
    try { reader.releaseLock(); } catch { /* Abort may still be settling. */ }
  }
}

/** One invocation, no retry/redirect. A timed-out continuation can never start another POST or confirm a receipt. */
export function startPost({ config, envelope, signal, fetchImpl = fetch, now = Date.now, timeoutMs = 5000 }) {
  const controller = new AbortController();
  let closed = false, timer, resolveCancel;
  const abort = () => { closed = true; controller.abort(); resolveCancel?.(unavailable()); };
  const canceled = new Promise(resolve => { resolveCancel = resolve; });
  signal?.addEventListener('abort', abort, { once: true });
  if (signal?.aborted) abort();
  const current = now();
  const remaining = Math.min(timeoutMs, config.end - current);
  const deadline = current + remaining;
  const expired = () => closed || controller.signal.aborted || now() >= deadline;
  const operation = (async () => {
    try {
      if (!Number.isSafeInteger(current) || current < config.start || remaining <= 0 || expired()
          || !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 5000) return unavailable();
      // Serialize before the last fence; nothing asynchronous precedes the single HTTP invocation.
      const body = JSON.stringify(envelope);
      if (expired()) return unavailable();
      const response = await fetchImpl(config.apiOrigin + '/integrations/whatsapp/v1/' + (config.admissionMode === 'API' ? 'team-events' : 'events'), {
        method: 'POST', redirect: 'manual', signal: controller.signal,
        headers: { Authorization: `Bearer ${config.channelToken}`, 'content-type': 'application/json',
          ...(config.admissionMode === 'API' ? { 'X-Vanrides-Admission-Policy': 'TEAM_V1' } : {}) }, body,
      });
      if (expired()) { cancel(response.body); return unavailable(); }
      const result = await receipt(response, controller.signal, config);
      return expired() ? unavailable() : result;
    } catch { return unavailable(); }
  })();
  timer = setTimeout(abort, Math.max(1, remaining));
  const result = Promise.race([operation, canceled]).finally(() => {
    closed = true; controller.abort(); clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  });
  // Keep the lane reserved until the actual operation settles, even when a faulty injected fetch ignores abort.
  return Object.freeze({ result, settled: operation });
}
