import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { root } from './070-worker-preflight.mjs';
import { SmokeFailure, demand, exact, localJson, privateWrite, safeCode } from './070-worker-common.mjs';

const listen = server => new Promise((resolve, reject) => {
  server.once('error', reject); server.listen(0, '127.0.0.1', resolve);
});
const close = server => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

/** The provider only binds loopback. Each expected message names its own synthetic recipient. */
export async function startProvider(instance, address, apiKey, peerAddresses = []) {
  demand(/^wa070_synthetic_[0-9a-f]{32}$/.test(instance) && /^0{15}@s\.whatsapp\.net$/.test(address), 'SYNTHETIC_PROVIDER_SCOPE_INVALID');
  demand(Array.isArray(peerAddresses) && peerAddresses.length <= 2
    && peerAddresses.every(peer => /^0{13,14}@s\.whatsapp\.net$/.test(peer))
    && new Set(peerAddresses).size === peerAddresses.length, 'SYNTHETIC_PROVIDER_SCOPE_INVALID');
  const destinations = new Set([address, ...peerAddresses]);
  const expected = [], failures = [], seen = new Set();
  const observations = [];
  let nextEffect = 'ACCEPTED';
  let requests = 0, accepted = 0, connectionReads = 0;
  const server = http.createServer({ requestTimeout: 2000, headersTimeout: 2000, maxHeaderSize: 4096,
    connectionsCheckingInterval: 500 }, async (request, response) => {
    // Read-only health traffic is separate from the submission counts used by smoke receipts.
    if (request.method === 'GET' && request.url === '/instance/connectionState/' + encodeURIComponent(instance)
        && request.headers.apikey === apiKey) {
      connectionReads++;
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ instance: { instanceName: instance, state: observations.shift() ?? 'open' } })); return;
    }
    requests++;
    try {
      demand(request.method === 'POST' && ['/message/sendText/', '/message/sendLocation/']
        .some(route => request.url === route + encodeURIComponent(instance))
        && request.headers.apikey === apiKey && request.headers['content-type'] === 'application/json', 'PROVIDER_REQUEST_INVALID');
      let size = 0; const chunks = [];
      for await (const chunk of request) {
        size += chunk.length; demand(size <= 16384, 'PROVIDER_BODY_TOO_LARGE'); chunks.push(chunk);
      }
      const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, size)));
      const next = expected.shift();
      demand(next && body.number === next.address && !seen.has(next.outboxId), 'PROVIDER_CONTENT_INVALID');
      if (typeof next.pinMatches === 'function') {
        demand(request.url === '/message/sendLocation/' + encodeURIComponent(instance)
          && exact(body, ['number', 'latitude', 'longitude', 'name', 'address'])
          && Number.isFinite(body.latitude) && Math.abs(body.latitude) <= 90
          && Number.isFinite(body.longitude) && Math.abs(body.longitude) <= 180
          && body.name === 'Localização atual do veículo' && typeof body.address === 'string'
          && next.pinMatches(body) === true, 'PROVIDER_PIN_INVALID');
      } else {
        demand(request.url === '/message/sendText/' + encodeURIComponent(instance)
          && exact(body, ['number', 'text', 'linkPreview']) && body.linkPreview === false
          && typeof body.text === 'string' && body.text.length > 0 && body.text.length <= 2000
          && (typeof next.text === 'string' ? body.text === next.text : next.matches?.(body.text) === true), 'PROVIDER_CONTENT_INVALID');
      }
      seen.add(next.outboxId); accepted++;
      const effect = nextEffect; nextEffect = 'ACCEPTED';
      response.writeHead(effect === 'AMBIGUOUS_AFTER_EFFECT' ? 400 : 201, { 'Content-Type': 'application/json' });
      response.end(effect === 'AMBIGUOUS_AFTER_EFFECT' ? '{"status":"UNKNOWN"}'
        : JSON.stringify({ key: { id: 'wa070_provider_' + accepted } }));
    } catch (error) {
      failures.push(safeCode(error, 'PROVIDER_REQUEST_INVALID'));
      if (!response.headersSent) response.writeHead(400, { 'Content-Type': 'application/json' });
      response.end('{"status":"REJECTED"}');
    }
  });
  server.on('clientError', (_error, socket) => socket.destroy());
  await listen(server);
  const bound = server.address();
  demand(bound?.address === '127.0.0.1', 'PROVIDER_NOT_LOOPBACK');
  return { url: 'http://127.0.0.1:' + bound.port,
    enqueue(item) {
      const recipient = item.address ?? address;
      demand(destinations.has(recipient), 'PROVIDER_EXPECTATION_OUTSIDE_COHORT');
      expected.push({ ...item, address: recipient });
    },
    counts: () => ({ requests, accepted, pending: expected.length, failures: failures.length }),
    observeConnections(states) {
      demand(Array.isArray(states) && states.length <= 4 && states.every(state => ['open', 'close'].includes(state)), 'PROVIDER_FAULT_INVALID');
      demand(observations.length === 0, 'PROVIDER_FAULT_PENDING'); observations.push(...states);
    },
    nextPostEffect(effect) {
      demand(['ACCEPTED', 'AMBIGUOUS_AFTER_EFFECT'].includes(effect), 'PROVIDER_FAULT_INVALID'); nextEffect = effect;
    },
    connectionReadCount: () => connectionReads,
    stop: () => close(server) };
}

export function createWorkerProcess({ input, frozen, tools, work, instance, provider, conversation, delivery, job, apiKey, assertActive }) {
  const control = crypto.randomBytes(32).toString('base64url');
  const secretFiles = {};
  for (const [name, value] of Object.entries({ CONVERSATION_TOKEN: conversation.credential,
    DELIVERY_TOKEN: delivery.credential, CONTROL_TOKEN: control, EVOLUTION_API_KEY: apiKey })) {
    const file = path.join(work, name.toLowerCase() + '.secret');
    privateWrite(file, value + '\n'); secretFiles['WA070_' + name + '_FILE'] = file;
  }
  if (job) {
    const file = path.join(work, 'attendance-job.secret');
    privateWrite(file, job.credential + '\n');
    secretFiles.WA100_JOB_TOKEN_FILE = file;
  }
  let child = null, base = null, exited = false;
  async function start() {
    assertActive(); demand(child === null, 'WORKER_ALREADY_STARTED'); frozen.assertSource();
    const reservation = http.createServer(); await listen(reservation);
    const port = reservation.address().port;
    await close(reservation);
    assertActive();
    const owner = crypto.randomUUID(), main = path.join(root, 'src/whatsapp/worker/main.mjs');
    // Intent survives a lost spawn ACK. Recover only this nonce + cwd + executable, never arbitrary Node PIDs.
    const intent = { owner, port, main, cwd: work, executable: process.execPath, startedAt: new Date().toISOString() };
    privateWrite(tools.next('worker-intent-private.json'), JSON.stringify(intent) + '\n');
    const stdout = fs.openSync(tools.next('worker.stdout.log'), 'wx', 0o600);
    const stderr = fs.openSync(tools.next('worker.stderr.log'), 'wx', 0o600);
    try {
      child = spawn(process.execPath, [main, '--wa070-smoke-owner=' + owner], {
        cwd: work, env: { ...frozen.env, ...secretFiles, WA070_ENVIRONMENT: 'wa070-local', WA070_LOCAL_MODE: 'true',
          WA070_HTTP_HOSTS: '127.0.0.1', WA070_BACKEND_BASE_URL: input.state.api_base_url,
          WA070_EVOLUTION_BASE_URL: provider.url, WA070_INSTANCE: instance, WA070_HOST: '127.0.0.1', WA070_PORT: String(port) },
        stdio: ['ignore', stdout, stderr],
      });
    } finally { fs.closeSync(stdout); fs.closeSync(stderr); }
    exited = false;
    child.on('error', () => { exited = true; }); child.on('exit', () => { exited = true; });
    privateWrite(tools.next('worker-process-private.json'), JSON.stringify({ ...intent, pid: child.pid ?? null }) + '\n');
    base = 'http://127.0.0.1:' + port;
    const deadline = Date.now() + 10000;
    while (Date.now() < deadline && !exited) {
      assertActive();
      try {
        const health = await localJson(base, '/healthz', { timeout: 500 });
        if (health.status === 200 && exact(health.data, ['status', 'role'])
            && health.data.status === 'UP' && health.data.role === 'WA070_WORKER') {
          frozen.assertListener(port, child.pid); return;
        }
      } catch { /* Startup polling never logs an exception or trusts an unrelated listener. */ }
      await pause(50);
    }
    throw new SmokeFailure('WORKER_START_FAILED');
  }
  async function stop() {
    if (!child) return;
    const owned = child;
    if (!exited) {
      owned.kill('SIGTERM');
      const deadline = Date.now() + 29000;
      while (!exited && Date.now() < deadline) await pause(50);
      if (!exited) {
        owned.kill('SIGKILL');
        await new Promise(resolve => owned.once('exit', resolve));
        child = null; throw new SmokeFailure('WORKER_FORCED_STOP');
      }
    }
    child = null;
    demand(owned.exitCode === 0, 'WORKER_EXIT_FAILED');
  }
  return { start, stop, request: (route, authenticated = true) => {
    assertActive();
    demand(child && !exited && base, 'WORKER_NOT_RUNNING');
    frozen.assertListener(Number(new URL(base).port), child.pid);
    return localJson(base, route, { method: 'POST', body: {}, timeout: 29000,
      headers: authenticated ? { Authorization: 'Bearer ' + control } : {} });
  } };
}
