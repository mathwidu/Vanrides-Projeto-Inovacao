import { Buffer } from 'node:buffer';
import { MODEL_INSTRUCTIONS, PROPOSAL_SCHEMA, validateProposal } from '../agent/model-policy.mjs';

const ENDPOINT = 'https://api.openai.com/v1/responses';
const MAX_REQUEST_BYTES = 3200;
const MAX_RESPONSE_BYTES = 32 * 1024;
const CODES = new Set(['CONFIG_INVALID', 'REQUEST_REJECTED', 'AUTH_FAILED', 'RATE_LIMITED',
  'UNAVAILABLE', 'TIMEOUT', 'REFUSED', 'INCOMPLETE', 'INVALID_OUTPUT']);

export class ModelError extends Error {
  constructor(code) {
    const safe = CODES.has(code) ? code : 'UNAVAILABLE';
    super(`WA070_MODEL_${safe}`);
    this.name = 'ModelError';
    this.code = safe;
  }
}

function cancel(body) {
  try { Promise.resolve(body?.cancel()).catch(() => {}); } catch { /* Never retain upstream data. */ }
}

export function createModelRequest(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)
      || Object.keys(input).length !== 3
      || !['text', 'actorState', 'registrationStep'].every(k => Object.hasOwn(input, k))
      || typeof input.text !== 'string' || !input.text.trim() || Buffer.byteLength(input.text) > 1200
      || !['UNREGISTERED', 'PASSENGER', 'DRIVER'].includes(input.actorState)
      || !['WELCOME', 'PAIRING', 'READY'].includes(input.registrationStep))
    throw new ModelError('REQUEST_REJECTED');
  const body = {
    model: 'gpt-6-luna', reasoning: { effort: 'low' }, max_output_tokens: 600,
    store: false, stream: false, truncation: 'disabled', tools: [],
    instructions: MODEL_INSTRUCTIONS,
    input: [{ role: 'user', content: [{ type: 'input_text', text: JSON.stringify(input) }] }],
    text: { format: { type: 'json_schema', name: 'vanrides_intent_v1', strict: true, schema: PROPOSAL_SCHEMA } },
  };
  // Conservative byte bound includes schema and framing, not only human text.
  // Provider usage is checked independently by the frozen live evaluation.
  const encoded = JSON.stringify(body);
  if (Buffer.byteLength(encoded) > MAX_REQUEST_BYTES) throw new ModelError('REQUEST_REJECTED');
  return encoded;
}

export function parseModelResponse(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ModelError('INVALID_OUTPUT');
  if (body.status === 'incomplete') throw new ModelError('INCOMPLETE');
  if (body.status !== 'completed' || body.error != null || body.incomplete_details != null
      || !Array.isArray(body.output) || body.output.length < 1 || body.output.length > 4)
    throw new ModelError('INVALID_OUTPUT');
  const messages = [];
  for (const item of body.output) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new ModelError('INVALID_OUTPUT');
    if (item.type === 'reasoning') continue;
    if (item.type !== 'message' || item.role !== 'assistant' || item.status !== 'completed'
        || !Array.isArray(item.content) || item.content.length !== 1) throw new ModelError('INVALID_OUTPUT');
    const content = item.content[0];
    if (content?.type === 'refusal') throw new ModelError('REFUSED');
    if (content?.type !== 'output_text' || typeof content.text !== 'string'
        || Buffer.byteLength(content.text) > 4096) throw new ModelError('INVALID_OUTPUT');
    messages.push(content.text);
  }
  if (messages.length !== 1) throw new ModelError('INVALID_OUTPUT');
  try {
    const proposal = JSON.parse(messages[0]);
    validateProposal(proposal);
    return proposal;
  } catch { throw new ModelError('INVALID_OUTPUT'); }
}

async function readResponse(response, signal) {
  const size = response.headers?.get('content-length');
  if (size && /^\d+$/u.test(size) && Number(size) > MAX_RESPONSE_BYTES) {
    cancel(response.body); throw new ModelError('INVALID_OUTPUT');
  }
  if (!response.body?.getReader) throw new ModelError('INVALID_OUTPUT');
  const reader = response.body.getReader();
  const abort = () => cancel(reader);
  signal.addEventListener('abort', abort, { once: true });
  const chunks = [];
  let length = 0;
  try {
    for (;;) {
      const next = await reader.read();
      if (signal.aborted) throw new ModelError('TIMEOUT');
      if (next.done) break;
      if (!(next.value instanceof Uint8Array)) throw new ModelError('INVALID_OUTPUT');
      length += next.value.byteLength;
      if (length > MAX_RESPONSE_BYTES) { cancel(reader); throw new ModelError('INVALID_OUTPUT'); }
      chunks.push(next.value);
    }
    const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, length)));
    return body;
  } catch (error) {
    throw error instanceof ModelError ? error : new ModelError('INVALID_OUTPUT');
  } finally {
    signal.removeEventListener('abort', abort);
    try { reader.releaseLock(); } catch { /* Read may be cancelling. */ }
  }
}

/** Closed Responses-only transport. At most two requests share one total deadline. */
export function createOpenAIClassifier({ config, fetchImpl = fetch, now = Date.now,
  sleep = ms => new Promise(resolve => setTimeout(resolve, ms)), onUsage = () => {} }) {
  if (config?.mode !== 'openai' || config.model !== 'gpt-6-luna' || config.reasoningEffort !== 'low'
      || config.maxOutputTokens !== 600 || !/^sk-[A-Za-z0-9_-]{20,4093}$/u.test(config.apiKey ?? '')
      || ![fetchImpl, now, sleep, onUsage].every(x => typeof x === 'function')) throw new ModelError('CONFIG_INVALID');
  return async function classify(input, { deadline } = {}) {
    const started = now();
    if (!Number.isSafeInteger(started) || !Number.isSafeInteger(deadline) || deadline <= started)
      throw new ModelError('TIMEOUT');
    const end = Math.min(deadline, started + 20000);
    const body = createModelRequest(input);
    for (let attempt = 0; attempt < 2; attempt++) {
      const remaining = end - now();
      if (remaining <= 0) throw new ModelError('TIMEOUT');
      const controller = new AbortController();
      let timer, delay = 250;
      try {
        const timeout = new Promise((_, reject) => {
          timer = setTimeout(() => { controller.abort(); reject(new ModelError('TIMEOUT')); }, remaining);
        });
        const operation = async () => {
          let response;
          try {
            response = await fetchImpl(ENDPOINT, { method: 'POST', redirect: 'manual',
              headers: { Authorization: `Bearer ${config.apiKey}`, 'content-type': 'application/json' },
              body, signal: controller.signal });
          } catch { throw new ModelError(controller.signal.aborted ? 'TIMEOUT' : 'UNAVAILABLE'); }
          if (controller.signal.aborted || now() >= end) { cancel(response?.body); throw new ModelError('TIMEOUT'); }
          if (response.status !== 200) {
            cancel(response.body);
            const retryAfter = response.headers?.get('retry-after');
            if (typeof retryAfter === 'string' && /^\d+(?:\.\d+)?$/u.test(retryAfter) && retryAfter.length < 10)
              delay = Math.max(250, Math.ceil(Number(retryAfter) * 1000));
            else if (typeof retryAfter === 'string' && retryAfter.length < 80 && Number.isFinite(Date.parse(retryAfter)))
              delay = Math.max(250, Date.parse(retryAfter) - now());
            if ([401, 403].includes(response.status)) throw new ModelError('AUTH_FAILED');
            if (response.status === 429) throw new ModelError('RATE_LIMITED');
            if ([500, 502, 503, 504].includes(response.status)) throw new ModelError('UNAVAILABLE');
            throw new ModelError('REQUEST_REJECTED');
          }
          const result = await readResponse(response, controller.signal);
          if (controller.signal.aborted || now() >= end) throw new ModelError('TIMEOUT');
          const proposal = parseModelResponse(result);
          const usage = result.usage;
          if (usage && ['input_tokens', 'output_tokens', 'total_tokens'].every(k => Number.isSafeInteger(usage[k]) && usage[k] >= 0)) {
            if (usage.input_tokens > 4000 || usage.output_tokens > 600) throw new ModelError('INVALID_OUTPUT');
            // Only counters, never IDs, text, headers or raw provider data.
            try { onUsage({ inputTokens: usage.input_tokens, outputTokens: usage.output_tokens,
              totalTokens: usage.total_tokens, attempts: attempt + 1 }); } catch { /* Diagnostics cannot affect action. */ }
          }
          return proposal;
        };
        return await Promise.race([operation(), timeout]);
      } catch (error) {
        const safe = error instanceof ModelError ? error : new ModelError('UNAVAILABLE');
        if (attempt !== 0 || !['UNAVAILABLE', 'RATE_LIMITED'].includes(safe.code) || end - now() < delay + 750) throw safe;
      } finally { clearTimeout(timer); }
      await sleep(delay);
      if (now() >= end) throw new ModelError('TIMEOUT');
    }
    throw new ModelError('UNAVAILABLE');
  };
}
