import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { demand, exact, SmokeFailure } from './070-worker-common.mjs';

/** Owner-only Unix socket. No code, response body, key or screen is persisted. */
export async function startConfirmationSocket(directory, confirm, { timeoutMillis = 900000, assertComplete = () => {} } = {}) {
  const stat = fs.lstatSync(directory);
  demand(stat.isDirectory() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o700
    && fs.realpathSync(directory) === path.resolve(directory), 'LAB_SOCKET_DIRECTORY_INVALID');
  const socketPath = path.join(directory, 'confirm.sock');
  demand(Buffer.byteLength(socketPath) <= 100 && !fs.existsSync(socketPath), 'LAB_SOCKET_PATH_INVALID');
  demand(Number.isInteger(timeoutMillis) && timeoutMillis >= 100 && timeoutMillis <= 900000, 'LAB_SOCKET_TIMEOUT_INVALID');
  let phase = 'WAITING_CODE', busy = false, terminal = false, resolveDone, rejectDone, currentAction = null;
  const done = new Promise((resolve, reject) => { resolveDone = resolve; rejectDone = reject; });
  // The caller may be inside a native operation when this expires; keep rejection observed.
  done.catch(() => {});
  const fail = error => { if (!terminal) { terminal = true; phase = 'FAILED'; rejectDone(error); } };
  const timer = setTimeout(() => fail(new SmokeFailure('LAB_EXTERNAL_CONSUMER_TIMEOUT')), timeoutMillis);
  const server = http.createServer({ requestTimeout: 5000, headersTimeout: 2000, maxHeaderSize: 2048 }, async (request, response) => {
    try {
      demand(!terminal && !busy && request.method === 'POST' && ['/confirm', '/complete'].includes(request.url)
        && request.headers['content-type'] === 'application/json', 'LAB_SOCKET_REQUEST_INVALID');
      busy = true; let length = 0; const chunks = [];
      for await (const chunk of request) { length += chunk.length; demand(length <= 128, 'LAB_SOCKET_REQUEST_INVALID'); chunks.push(chunk); }
      const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks, length)));
      if (request.url === '/confirm') {
        demand(phase === 'WAITING_CODE' && exact(body, ['userCode']) && /^[BCDFGHJKMNPQRSTVWXYZ]{8}$/.test(body.userCode),
          'LAB_SOCKET_REQUEST_INVALID');
        phase = 'CONFIRMING';
        try {
          currentAction = Promise.resolve().then(() => confirm(body.userCode)); await currentAction;
          demand(!terminal, 'LAB_SOCKET_CLOSED'); phase = 'WAITING_COMPLETION';
        }
        catch (error) { fail(error); throw error; }
        finally { delete body.userCode; currentAction = null; }
      } else {
        demand(phase === 'WAITING_COMPLETION' && exact(body, []), 'LAB_SOCKET_REQUEST_INVALID');
        await assertComplete(); phase = 'COMPLETED'; terminal = true; clearTimeout(timer);
      }
      response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end(JSON.stringify({ state: phase }), () => { if (phase === 'COMPLETED') resolveDone(); });
    } catch {
      response.writeHead(400, { 'Content-Type': 'application/json' }); response.end('{"state":"REJECTED"}');
    } finally { busy = false; }
  });
  server.on('clientError', (_error, socket) => socket.destroy());
  try {
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(socketPath, resolve); });
    fs.chmodSync(socketPath, 0o600);
  } catch (error) { clearTimeout(timer); throw error; }
  return { socketPath, done, phase: () => phase, stop: async () => {
    clearTimeout(timer); fail(new SmokeFailure('LAB_SOCKET_CLOSED'));
    await new Promise(resolve => { server.closeAllConnections(); server.close(resolve); });
    if (currentAction) await currentAction.catch(() => {});
    if (fs.existsSync(socketPath)) fs.unlinkSync(socketPath);
  } };
}

export async function socketRequest(socketPath, route, body) {
  const stat = fs.lstatSync(socketPath);
  demand(stat.isSocket() && stat.uid === process.getuid() && (stat.mode & 0o777) === 0o600, 'LAB_SOCKET_INVALID');
  return await new Promise((resolve, reject) => {
    const request = http.request({ socketPath, method: 'POST', path: route, timeout: 60000,
      headers: { 'Content-Type': 'application/json' } }, response => {
      let size = 0; const chunks = [];
      response.on('data', chunk => { size += chunk.length; if (size > 256) response.destroy(); else chunks.push(chunk); });
      response.on('error', () => reject(new SmokeFailure('LAB_SOCKET_RESPONSE_INVALID')));
      response.on('end', () => {
        try { demand(response.statusCode === 200, 'LAB_SOCKET_REQUEST_REJECTED'); resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
        catch { reject(new SmokeFailure('LAB_SOCKET_REQUEST_REJECTED')); }
      });
    });
    request.on('error', () => reject(new SmokeFailure('LAB_SOCKET_REQUEST_FAILED')));
    request.on('timeout', () => request.destroy()); request.end(JSON.stringify(body));
  });
}
