import { createServer } from 'node:http';
import { Buffer } from 'node:buffer';
import { timingSafeEqual } from 'node:crypto';
import { isToken } from './config.mjs';
import { WorkerError } from './errors.mjs';
import { emptyAttendanceSummary, safeAttendanceSummary } from './attendance-jobs.mjs';
import { emptyDeliverySummary, safeDeliverySummary } from './delivery-jobs.mjs';

const DISPATCH = ['IDLE', 'BUSY', 'SUBMITTED', 'NOT_SUBMITTED', 'UNKNOWN', 'FAILED', 'UNAVAILABLE'];
const PROCESS = ['PROCESSED', 'IDLE', 'RETRY', 'PAUSED', 'UNAVAILABLE'];
const COUNTS = { dispatch: ['claimed', 'submitted', 'unknown', 'failed'], process: ['claimed', 'processed', 'retried', 'paused'] };

function unavailable(kind) {
  if (kind === 'attendance') return emptyAttendanceSummary('UNAVAILABLE');
  if (kind === 'delivery') return emptyDeliverySummary('UNAVAILABLE');
  return Object.fromEntries([['status', 'UNAVAILABLE'], ...COUNTS[kind].map(key => [key, 0])]);
}

function safeSummary(value, kind) {
  try {
    if (kind === 'attendance') return safeAttendanceSummary(value);
    if (kind === 'delivery') return safeDeliverySummary(value);
    const keys = COUNTS[kind];
    if (value === null || typeof value !== 'object' || Array.isArray(value)
        || Object.keys(value).length !== keys.length + 1 || !Object.hasOwn(value, 'status')
        || !(kind === 'dispatch' ? DISPATCH : PROCESS).includes(value.status)
        || keys.some(key => !Object.hasOwn(value, key) || ![0, 1].includes(value[key]))
        || keys.slice(1).reduce((sum, key) => sum + value[key], 0) > value.claimed) return unavailable(kind);
    const expected = { SUBMITTED: 'submitted', UNKNOWN: 'unknown', FAILED: 'failed', PROCESSED: 'processed', RETRY: 'retried', PAUSED: 'paused' };
    if (expected[value.status] && value[expected[value.status]] !== 1
        || ['IDLE', 'BUSY'].includes(value.status) && value.claimed !== 0
        || value.status === 'NOT_SUBMITTED' && (value.claimed !== 1 || keys.slice(1).some(key => value[key] !== 0))
        || value.status === 'UNAVAILABLE' && keys.slice(1).some(key => value[key] !== 0)) return unavailable(kind);
    return Object.fromEntries([['status', value.status], ...keys.map(key => [key, value[key]])]);
  } catch { return unavailable(kind); }
}

function authorized(request, token) {
  if (request.rawHeaders.filter((_, index) => index % 2 === 0 && request.rawHeaders[index].toLowerCase() === 'authorization').length !== 1) return false;
  const value = request.headers.authorization;
  if (typeof value !== 'string' || !value.startsWith('Bearer ')) return false;
  const supplied = value.slice(7);
  return isToken(supplied) && timingSafeEqual(Buffer.from(supplied), Buffer.from(token));
}

function reply(response, status, body, close = false) {
  if (response.destroyed || response.writableEnded) return;
  response.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store',
    'x-content-type-options': 'nosniff', ...(close ? { connection: 'close' } : {}) });
  response.end(JSON.stringify(body));
}

function emptyBody(request, timeoutMs) {
  return new Promise((resolve, reject) => {
    let bytes = 0;
    const chunks = [];
    const finish = error => {
      clearTimeout(timer);
      request.off('data', data); request.off('end', end); request.off('aborted', failed); request.off('error', failed);
      if (error) reject(new WorkerError('INVALID_REQUEST')); else resolve();
    };
    const failed = () => finish(true);
    const data = chunk => {
      bytes += chunk.length;
      if (bytes > 1024) finish(true); else chunks.push(chunk);
    };
    const end = () => {
      try {
        const value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, bytes)));
        finish(value === null || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== 0);
      } catch { finish(true); }
    };
    const timer = setTimeout(failed, timeoutMs);
    request.on('data', data); request.on('end', end); request.on('aborted', failed); request.on('error', failed);
  });
}

/** Transport control only. Disconnecting a caller never cancels an authorized operation. */
export function createWorkerServer({ config, dispatchOne, processInbox, tickAttendance, reconcileDelivery, bodyTimeoutMs = 3000, operationTimeoutMs = 25_000 }) {
  if (!isToken(config?.controlToken) || typeof dispatchOne !== 'function'
      || !Number.isInteger(bodyTimeoutMs) || bodyTimeoutMs < 1 || bodyTimeoutMs > 3000
      || !Number.isInteger(operationTimeoutMs) || operationTimeoutMs < 1 || operationTimeoutMs > 25_000) throw new WorkerError('CONFIG_INVALID');
  const active = { dispatch: 0, process: 0, attendance: 0, delivery: 0 };
  const routes = new Map([['/v1/dispatch-one', ['dispatch', dispatchOne]], ['/v1/process-inbox', ['process', processInbox]],
    ['/v1/tick-attendance', ['attendance', tickAttendance]], ['/v1/reconcile-delivery', ['delivery', reconcileDelivery]]]);
  const server = createServer({ maxHeaderSize: 4096, requestTimeout: 3000, headersTimeout: 3000,
    keepAliveTimeout: 1000, connectionsCheckingInterval: 500 }, (request, response) => {
    handle(request, response).catch(() => reply(response, 503, { status: 'UNAVAILABLE' }, true));
  });
  server.maxRequestsPerSocket = 10;
  server.setTimeout(26_000);
  server.on('clientError', (_error, socket) => {
    if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 28\r\nContent-Type: application/json\r\n\r\n{"status":"INVALID_REQUEST"}');
  });

  async function handle(request, response) {
    request.on('error', () => {});
    if (request.method === 'GET' && request.url === '/healthz') return reply(response, 200, { status: 'UP', role: 'WA070_WORKER' });
    const route = routes.get(request.url);
    if (!route) return reply(response, 404, { status: 'NOT_FOUND' }, true);
    const [kind, action] = route;
    if (request.method !== 'POST') return reply(response, 405, { status: 'INVALID_REQUEST' }, true);
    if (!authorized(request, config.controlToken)) return reply(response, 401, { status: 'UNAUTHORIZED' }, true);
    if (request.headers.origin !== undefined || !/^application\/json(?:;\s*charset=utf-8)?$/i.test(request.headers['content-type'] ?? '')
        || request.headers['content-encoding'] !== undefined || Number(request.headers['content-length'] ?? 0) > 1024)
      return reply(response, 400, { status: 'INVALID_REQUEST' }, true);
    try { await emptyBody(request, bodyTimeoutMs); }
    catch { return reply(response, 400, { status: 'INVALID_REQUEST' }, true); }
    if (request.aborted) return;
    if (kind === 'attendance' && active[kind]) return reply(response, 200, emptyAttendanceSummary('BUSY'));
    if (kind === 'delivery' && active[kind]) return reply(response, 200, emptyDeliverySummary('BUSY'));
    if (active[kind] >= (kind === 'process' ? 2 : 1)) return reply(response, 200,
      kind === 'dispatch' ? { status: 'BUSY', claimed: 0, submitted: 0, unknown: 0, failed: 0 } : unavailable(kind));
    if (typeof action !== 'function') return reply(response, 503, unavailable(kind));
    active[kind]++;
    const operation = Promise.resolve().then(action).then(value => {
      active[kind]--; return safeSummary(value, kind);
    }, () => { active[kind]--; return unavailable(kind); });
    let timer;
    try {
      const result = await Promise.race([operation, new Promise(resolve => {
        timer = setTimeout(() => resolve(unavailable(kind)), operationTimeoutMs);
      })]);
      reply(response, ['attendance', 'delivery'].includes(kind) && result.status === 'UNAVAILABLE' ? 503 : 200, result);
    } finally { clearTimeout(timer); }
  }
  return server;
}
