import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import http from 'node:http';
import https from 'node:https';
import { execFileSync } from 'node:child_process';
import { root } from './070-worker-preflight.mjs';
import { demand, digest, privateBytes, privateWrite, SmokeFailure } from './070-worker-common.mjs';

export const origin = 'https://localhost:18443';
const routes = new Map([
  ['/driver-app/v1/pairings/begin', 'POST'], ['/driver-app/v1/pairings/redeem', 'POST'],
  ['/driver-app/v1/session/refresh', 'POST'], ['/driver-app/v1/session/revoke', 'POST'],
  ['/driver-app/v1/status', 'GET'],
]);
const runPath = /^\/driver-app\/v1\/(start-intents|runs|tracking-sessions)\/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\/(activate|resume|samples|state)$/u;
export function allowedRunRoute(method, route) {
  if (method === 'GET') return route === '/driver-app/v1/tracking-status';
  const hit = method === 'POST' && runPath.exec(route);
  return !!hit && ({'start-intents':['activate'],runs:['resume'],'tracking-sessions':['samples','state']})[hit[1]].includes(hit[2]);
}
export const allowedRoute = (method, route, runLab = false) => runLab && allowedRunRoute(method, route) || routes.get(route) === method
  || method === 'GET' && /^\/driver-app\/v1\/pairings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/status$/.test(route);

export function privateDirectory(directory) {
  demand(typeof directory === 'string' && path.isAbsolute(directory), 'LAB_DIRECTORY_INVALID');
  const resolved = fs.realpathSync(directory), stat = fs.lstatSync(directory);
  demand(resolved === path.resolve(directory) && stat.isDirectory() && stat.uid === process.getuid()
    && (stat.mode & 0o777) === 0o700 && resolved !== root && !resolved.startsWith(root + path.sep), 'LAB_DIRECTORY_INVALID');
  return resolved;
}

/** Creates a new private directory only. Never replaces an existing CA or runtime config. */
export function prepareTls(directory) {
  demand(typeof directory === 'string' && path.isAbsolute(directory), 'LAB_DIRECTORY_INVALID');
  const parent = fs.realpathSync(path.dirname(directory));
  demand(parent === path.resolve(path.dirname(directory)) && directory === path.join(parent, path.basename(directory))
    && directory !== root && !directory.startsWith(root + path.sep), 'LAB_DIRECTORY_INVALID');
  fs.mkdirSync(directory, { mode: 0o700 }); privateDirectory(directory);
  const config = `[req]\nprompt=no\ndistinguished_name=dn\n[dn]\nCN=VanRides local pairing lab\n[ca]\nbasicConstraints=critical,CA:TRUE,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign\nsubjectKeyIdentifier=hash\n[leaf]\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost\n`;
  privateWrite(path.join(directory, 'openssl.cnf'), config);
  privateWrite(path.join(directory, 'leaf-request.cnf'), '[req]\nprompt=no\ndistinguished_name=dn\n[dn]\nCN=localhost\n');
  const run = args => {
    try { execFileSync('/usr/bin/openssl', args, { cwd: directory, stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000,
      env: { PATH: '/usr/bin:/bin' } }); }
    catch { throw new SmokeFailure('LAB_TLS_PREPARATION_FAILED'); }
  };
  run(['genpkey', '-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-pkeyopt', 'ec_param_enc:named_curve', '-out', 'ca-key.pem']);
  run(['req', '-new', '-x509', '-sha256', '-days', '7', '-key', 'ca-key.pem', '-out', 'ca.pem', '-config', 'openssl.cnf', '-extensions', 'ca']);
  run(['genpkey', '-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-pkeyopt', 'ec_param_enc:named_curve', '-out', 'leaf-key.pem']);
  run(['req', '-new', '-sha256', '-key', 'leaf-key.pem', '-out', 'leaf.csr', '-config', 'leaf-request.cnf']);
  run(['x509', '-req', '-sha256', '-days', '7', '-in', 'leaf.csr', '-CA', 'ca.pem', '-CAkey', 'ca-key.pem',
    '-set_serial', '0x' + crypto.randomBytes(16).toString('hex'), '-out', 'leaf.pem', '-extfile', 'openssl.cnf', '-extensions', 'leaf']);
  run(['x509', '-in', 'ca.pem', '-outform', 'DER', '-out', 'ca.der']);
  for (const name of fs.readdirSync(directory)) fs.chmodSync(path.join(directory, name), 0o600);
  return loadTls(directory);
}

export function loadTls(directory) {
  privateDirectory(directory);
  const files = ['ca.pem', 'ca.der', 'leaf.pem', 'leaf-key.pem'];
  const values = Object.fromEntries(files.map(name => [name, privateBytes(path.join(directory, name))]));
  try {
    const ca = new crypto.X509Certificate(values['ca.pem']), leaf = new crypto.X509Certificate(values['leaf.pem']);
    demand(ca.ca && !leaf.ca && leaf.subjectAltName === 'DNS:localhost' && leaf.verify(ca.publicKey)
      && leaf.checkPrivateKey(crypto.createPrivateKey(values['leaf-key.pem']))
      && ca.raw.equals(values['ca.der']) && Date.parse(leaf.validFrom) <= Date.now()
      && Date.parse(leaf.validTo) > Date.now() + 60000 && Date.parse(ca.validTo) > Date.now() + 60000,
    'LAB_TLS_CERTIFICATE_INVALID');
  } catch { throw new SmokeFailure('LAB_TLS_CERTIFICATE_INVALID'); }
  return { ca: values['ca.pem'], cert: values['leaf.pem'], key: values['leaf-key.pem'],
    publicCa: path.join(directory, 'ca.der'), hashes: Object.fromEntries(files.filter(name => !name.includes('key'))
      .map(name => [name, digest(values[name])])) };
}

async function boundedBody(request, limit = 32768) {
  let size = 0; const chunks = [];
  for await (const chunk of request) { size += chunk.length; demand(size <= limit, 'LAB_BODY_TOO_LARGE'); chunks.push(chunk); }
  return Buffer.concat(chunks, size);
}

/** Dedicated loopback listener. Does not forward Host, X-Forwarded-*, cookies or arbitrary routes. */
export async function startTlsProxy(tls, { port = 18443, upstreamPort = 18083, onPairingBegin = () => {}, dropFirstRefreshResponse = false, runLab = false } = {}) {
  let refreshRequests = 0, refreshResponsesDropped = 0, recoveredDroppedRefresh = 0, droppedOperation = null;
  const server = https.createServer({ key: tls.key, cert: tls.cert, minVersion: 'TLSv1.2', requestTimeout: 15000,
    headersTimeout: 5000, maxHeaderSize: 16384 }, async (request, response) => {
    let upstream;
    try {
      demand(request.headers.host === (port === 18443 ? 'localhost:18443' : 'localhost:' + server.address().port)
        && allowedRoute(request.method, request.url, runLab), 'LAB_ROUTE_DENIED');
      const body = await boundedBody(request);
      if (request.url === '/driver-app/v1/session/refresh') refreshRequests++;
      demand(request.method !== 'GET' || body.length === 0, 'LAB_ROUTE_DENIED');
      const headers = {};
      for (const name of ['authorization', 'dpop', 'idempotency-key', 'content-type'])
        if (request.headers[name] !== undefined) headers[name] = request.headers[name];
      if (body.length) headers['content-length'] = body.length;
      await new Promise((resolve, reject) => {
        upstream = http.request({ hostname: '127.0.0.1', port: upstreamPort, path: request.url,
          method: request.method, headers, timeout: 10000 }, async result => {
          try {
            const bytes = await boundedBody(result);
            const outgoing = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
            if (result.headers['dpop-nonce']) outgoing['DPoP-Nonce'] = result.headers['dpop-nonce'];
            demand(result.statusCode >= 200 && result.statusCode < 500, 'LAB_UPSTREAM_FAILED');
            if (request.url === '/driver-app/v1/pairings/begin' && result.statusCode === 200) {
              const begun = JSON.parse(bytes.toString('utf8')), jwk = JSON.parse(body.toString('utf8')).publicJwk;
              const thumbprint = digest(JSON.stringify({ crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y }));
              onPairingBegin({ pairingId: begun.pairingId, jkt: Buffer.from(thumbprint, 'hex').toString('base64url') });
            }
            if (request.url === '/driver-app/v1/session/refresh' && result.statusCode === 200) {
              const operation = digest(body.toString('utf8') + '\0' + (request.headers['idempotency-key'] ?? ''));
              if (dropFirstRefreshResponse && refreshResponsesDropped === 0) {
                droppedOperation = operation; refreshResponsesDropped++; response.destroy(); resolve(); return;
              }
              if (droppedOperation === operation) recoveredDroppedRefresh++;
            }
            response.writeHead(result.statusCode, outgoing); response.end(bytes); resolve();
          } catch (error) { reject(error); }
        });
        upstream.on('error', reject); upstream.on('timeout', () => upstream.destroy(new SmokeFailure('LAB_UPSTREAM_TIMEOUT')));
        upstream.end(body);
      });
    } catch {
      upstream?.destroy();
      if (!response.headersSent) response.writeHead(400, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end('{"code":"LAB_REQUEST_REJECTED"}');
    }
  });
  server.on('clientError', (_error, socket) => socket.destroy());
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  return { port: server.address().port, counts: () => ({ refreshRequests, refreshResponsesDropped, recoveredDroppedRefresh }),
    stop: () => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }) };
}

/** TLS validation is mandatory; connect to loopback while verifying the localhost name. */
export async function tlsJson(tls, method, route, raw, headers = {}, port = 18443, runLab = false) {
  demand(allowedRoute(method, route, runLab), 'LAB_CLIENT_ROUTE_INVALID');
  return await new Promise((resolve, reject) => {
    const request = https.request({ hostname: '127.0.0.1', servername: 'localhost', port, path: route, method,
      ca: tls.ca, rejectUnauthorized: true, agent: false, timeout: 12000,
      headers: { ...headers, Host: 'localhost:' + port, ...(raw === undefined ? {} : { 'Content-Type': 'application/json' }) } }, async response => {
      try {
        const bytes = await boundedBody(response);
        resolve({ status: response.statusCode, data: JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)),
          nonce: response.headers['dpop-nonce'] ?? null });
      } catch { reject(new SmokeFailure('LAB_RESPONSE_INVALID')); }
    });
    request.on('error', () => reject(new SmokeFailure('LAB_TLS_HTTP_FAILED')));
    request.on('timeout', () => request.destroy()); request.end(raw);
  });
}
