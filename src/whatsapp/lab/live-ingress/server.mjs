import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { strictJson } from './strict-json.mjs';
import { filterEvent } from './policy.mjs';
import { startPost } from './post.mjs';

const MAX_BODY = 32768;
const LIMITS = Object.freeze({ human: 16, status: 64, connection: 16 });
function authorized(request, token) {
  const headers = request.rawHeaders;
  if (headers.filter((name, index) => index % 2 === 0 && name.toLowerCase() === 'authorization').length !== 1) return false;
  const actual = request.headers.authorization, expected = `Bearer ${token}`;
  return typeof actual === 'string' && Buffer.byteLength(actual) === Buffer.byteLength(expected)
    && timingSafeEqual(Buffer.from(actual), Buffer.from(expected));
}

function validHeaders(request) {
  const names = new Set();
  for (let i = 0; i < request.rawHeaders.length; i += 2) {
    const name = request.rawHeaders[i].toLowerCase();
    if (names.has(name)) return false;
    names.add(name);
  }
  return request.headers.origin === undefined && request.headers['content-encoding'] === undefined
    && request.headers.expect === undefined
    && (request.headers['transfer-encoding'] === undefined || request.headers['transfer-encoding'] === 'chunked')
    && /^application\/json(?:;\s*charset=utf-8)?$/i.test(request.headers['content-type'] ?? '');
}

function body(request) {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0, finished = false;
    const finish = (code, result) => {
      if (finished) return;
      finished = true; clearTimeout(timer);
      request.off('data', data); request.off('end', end); request.off('aborted', fail); request.off('error', fail);
      if (code) reject(code); else resolve(result);
    };
    const fail = () => finish(400);
    const data = chunk => {
      size += chunk.length;
      if (size > MAX_BODY) finish(413); else chunks.push(chunk);
    };
    const end = () => {
      try { finish(null, strictJson(Buffer.concat(chunks, size))); }
      catch { finish(400); }
    };
    const timer = setTimeout(fail, 3000);
    request.on('data', data); request.on('end', end); request.on('aborted', fail); request.on('error', fail);
  });
}

/** Disposable WA120 lab filter only. All mutable state consists of bounded counters and two occupied lanes. */
export function createLiveIngressServer({ config, fetchImpl = fetch, now = Date.now, log = () => {} }) {
  if (!Object.isFrozen(config) || !Object.isFrozen(config.contacts) || typeof fetchImpl !== 'function'
      || typeof now !== 'function' || typeof log !== 'function') throw new Error('CONFIG_INVALID');
  const counters = { human: 0, status: 0, connection: 0, contactA: 0, contactB: 0,
    forwarded: 0, dropped: 0, unauthorized: 0, invalid: 0, oversized: 0, busy: 0, unavailable: 0 };
  const active = { human: 0, callback: 0 };
  const controllers = new Set();
  let reading = 0, stopped = false;
  const snapshot = () => Object.freeze({ ...counters });
  const record = (code, counter) => {
    if (counter) counters[counter]++;
    try { log(Object.freeze({ code, counters: snapshot() })); } catch { /* Log failure cannot expose the request. */ }
  };
  function reply(response, status, result, code, counter, close = false) {
    record(code, counter);
    if (response.destroyed || response.writableEnded) return;
    response.writeHead(status, { 'cache-control': 'no-store', 'x-content-type-options': 'nosniff',
      connection: close ? 'close' : 'keep-alive', ...(status === 204 ? {} : { 'content-type': 'application/json' }) });
    response.end(status === 204 ? undefined : JSON.stringify(result));
  }
  const invalid = (response, status = 400) => reply(response, status, { code: status === 413 ? 'BODY_TOO_LARGE' : 'INVALID_REQUEST' },
    status === 413 ? 'BODY_TOO_LARGE' : 'INVALID_REQUEST', status === 413 ? 'oversized' : 'invalid', true);
  const busy = response => reply(response, 503, { code: 'BUSY' }, 'BUSY', 'busy', true);
  async function handle(request, response) {
    request.on('error', () => {});
    if (!authorized(request, config.webhookToken))
      return reply(response, 401, { code: 'UNAUTHORIZED' }, 'UNAUTHORIZED', 'unauthorized', true);
    if (request.method !== 'POST' || request.url !== '/evolution/webhook' || !validHeaders(request)) return invalid(response);
    if (Number(request.headers['content-length'] ?? 0) > MAX_BODY) return invalid(response, 413);
    if (stopped || reading >= 2) return busy(response);
    reading++;
    let envelope;
    try { envelope = await body(request); }
    catch (status) { return invalid(response, status === 413 ? 413 : 400); }
    finally { reading--; }
    if (request.aborted || response.destroyed) return;
    const event = filterEvent(envelope, config, now());
    if (!event || counters[event.kind] >= LIMITS[event.kind]
        || event.kind === 'human' && counters[`contact${event.contact}`] >= 8)
      return reply(response, 204, null, 'POLICY_DROP', 'dropped');
    if (stopped || active[event.lane]) return busy(response);
    counters[event.kind]++;
    if (event.kind === 'human') counters[`contact${event.contact}`]++;
    active[event.lane]++;
    const controller = new AbortController();
    controllers.add(controller);
    const disconnect = () => { if (!response.writableEnded) controller.abort(); };
    response.on('close', disconnect);
    const operation = startPost({ config, envelope: event.envelope, signal: controller.signal, fetchImpl, now });
    operation.settled.then(() => { active[event.lane]--; controllers.delete(controller); });
    const result = await operation.result;
    response.off('close', disconnect);
    if (result.ok) return reply(response, 202, result.receipt, 'FORWARDED', 'forwarded');
    return reply(response, 503, { code: 'UPSTREAM_UNAVAILABLE' }, 'UPSTREAM_UNAVAILABLE', 'unavailable', true);
  }
  const server = createServer({ maxHeaderSize: 4096, requestTimeout: 4000, headersTimeout: 3000,
    keepAliveTimeout: 1000, connectionsCheckingInterval: 500 }, (request, response) => {
    handle(request, response).catch(() => reply(response, 503, { code: 'UNAVAILABLE' }, 'UNAVAILABLE', 'unavailable', true));
  });
  const expected = (request, response) => { handle(request, response).catch(() => invalid(response)); };
  server.on('checkContinue', expected);
  server.on('checkExpectation', expected);
  server.on('clientError', (error, socket) => {
    const status = error.code === 'HPE_HEADER_OVERFLOW' ? 413 : 400;
    record(status === 413 ? 'HEADER_TOO_LARGE' : 'INVALID_HTTP', status === 413 ? 'oversized' : 'invalid');
    if (socket.writable) socket.end(`HTTP/1.1 ${status} ${status === 413 ? 'Payload Too Large' : 'Bad Request'}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  });
  server.maxRequestsPerSocket = 8;
  server.maxConnections = 16;
  server.setTimeout(9000, socket => socket.destroy());
  Object.defineProperties(server, {
    snapshot: { value: snapshot },
    shutdown: { value: () => {
      stopped = true;
      for (const controller of controllers) controller.abort();
      server.close(); server.closeAllConnections();
    } },
  });
  return server;
}
