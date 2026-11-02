import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { allowedRoute, prepareTls, loadTls, startTlsProxy, origin, tlsJson } from './060-pairing-tls.mjs';
import { prepare } from './060-pairing-prepare.mjs';
import { runHostConsumer } from './060-pairing-consumer.mjs';
import { canonical, requireLabConfig } from './060-pairing-scenarios.mjs';
import { startConfirmationSocket, socketRequest } from './060-pairing-socket.mjs';
import { properties } from './070-worker-common.mjs';

const temporary = () => fs.mkdtempSync(path.join(fs.realpathSync('/tmp'), 'vr060-test-'));
const dispose = directory => fs.rmSync(directory, { recursive: true, force: true });
const hash = raw => crypto.createHash('sha256').update(raw).digest('base64url');
const id = '00000000-0000-4000-8000-000000000060';
const code = 'BCDFGHJK';

test('allowlist rejects integration, operator, tracking, query, encoding and method overrides', () => {
  for (const [method, route] of [['POST', '/driver-app/v1/pairings/begin'], ['GET', '/driver-app/v1/status'],
    ['GET', '/driver-app/v1/pairings/' + id + '/status']]) assert.equal(allowedRoute(method, route), true);
  for (const [method, route] of [['POST', '/integrations/whatsapp/v1/events'], ['GET', '/actuator/health'],
    ['POST', '/driver-app/v1/tracking-sessions/' + id + '/samples'], ['GET', '/driver-app/v1/status?admin=1'],
    ['GET', '/driver-app/v1/%73tatus'], ['POST', '/driver-app/v1/status'], ['GET', '/driver-app/v1/pairings/begin'],
    ['GET', '//driver-app/v1/status'], ['GET', '/driver-app/v1/status/../status']]) assert.equal(allowedRoute(method, route), false);
});

test('TLS preparation creates private keys outside Git, DER CA and canonical config without replacing source', () => {
  const directory = temporary(), target = path.join(directory, 'tls');
  try {
    const source = path.join(directory, 'source.properties');
    const config = 'vanrides.wa.environment=wa070-local\nserver.address=127.0.0.1\nserver.port=18083\n'
      + 'spring.datasource.url=jdbc:postgresql://127.0.0.1:51222/vanrides_wa070\nvanrides.wa.test-cohort-only=true\n'
      + 'spring.datasource.password=PRIVATE_SENTINEL\nvanrides.wa.public-base-url=http://127.0.0.1:18083\n';
    fs.writeFileSync(source, config, { mode: 0o600 });
    const result = prepare(target, source), tls = loadTls(target);
    assert.equal(result.origin, origin); assert.equal(fs.readFileSync(source, 'utf8'), config);
    requireLabConfig(properties(fs.readFileSync(result.configFile)), result.publicCa, tls);
    assert.throws(() => requireLabConfig(properties(Buffer.from(config)), result.publicCa, tls), { code: 'LAB_CANONICAL_ORIGIN_OR_CA_INVALID' });
    assert.equal(new crypto.X509Certificate(fs.readFileSync(tls.publicCa)).ca, true);
    assert.equal(new crypto.X509Certificate(tls.cert).subjectAltName, 'DNS:localhost');
    for (const name of fs.readdirSync(target)) assert.equal(fs.statSync(path.join(target, name)).mode & 0o777, 0o600);
    assert.throws(() => prepareTls(target));
    fs.chmodSync(path.join(target, 'leaf-key.pem'), 0o644);
    assert.throws(() => loadTls(target), { code: 'PRIVATE_INPUT_INVALID' });
    assert.equal(JSON.stringify(result).includes('PRIVATE_SENTINEL'), false);
  } finally { dispose(directory); }
});

async function rawTls(tls, port, route, { trust = true, servername = 'localhost', headers = {} } = {}) {
  return await new Promise((resolve, reject) => {
    const request = https.request({ hostname: '127.0.0.1', servername, port, path: route, agent: false,
      ...(trust ? { ca: tls.ca } : {}), headers: { Host: 'localhost:' + port, ...headers } }, response => {
      const chunks = []; response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => resolve({ status: response.statusCode, data: JSON.parse(Buffer.concat(chunks)) }));
    });
    request.on('error', reject); request.end();
  });
}

test('real TLS proxy enforces trust/name/route and strips forwarding, cookies and arbitrary headers', async () => {
  const directory = temporary(); let proxy, seen = 0;
  const upstream = http.createServer((request, response) => {
    seen++; assert.equal(request.headers.cookie, undefined); assert.equal(request.headers['x-forwarded-host'], undefined);
    assert.equal(request.headers['x-extra'], undefined); assert.match(request.headers.host, /^127\.0\.0\.1:/);
    response.writeHead(200, { 'Content-Type': 'application/json', 'DPoP-Nonce': 'public-nonce' }); response.end('{"readiness":"READY"}');
  });
  try {
    const tls = prepareTls(path.join(directory, 'tls'));
    await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve));
    proxy = await startTlsProxy(tls, { port: 0, upstreamPort: upstream.address().port });
    assert.equal((await rawTls(tls, proxy.port, '/driver-app/v1/status', { headers: { Cookie: 'PRIVATE',
      'X-Forwarded-Host': 'remote.invalid', 'X-Extra': 'PRIVATE' } })).status, 200);
    assert.equal((await tlsJson(tls, 'GET', '/driver-app/v1/status', undefined, {}, proxy.port)).nonce, 'public-nonce');
    assert.equal((await rawTls(tls, proxy.port, '/actuator/health')).status, 400);
    assert.equal((await rawTls(tls, proxy.port, '/driver-app/v1/status?x=1')).status, 400);
    await assert.rejects(rawTls(tls, proxy.port, '/driver-app/v1/status', { trust: false }));
    await assert.rejects(rawTls(tls, proxy.port, '/driver-app/v1/status', { servername: 'other.invalid' }));
    assert.equal(seen, 2);
  } finally { await proxy?.stop(); upstream.closeAllConnections(); await new Promise(resolve => upstream.close(resolve)); dispose(directory); }
});

test('fault hook drops only first committed refresh and observes recovery of same exact operation', async () => {
  const directory = temporary(); let proxy, requests = 0;
  const upstream = http.createServer(async (request, response) => {
    for await (const _chunk of request) { /* bounded test payload */ }
    requests++; response.writeHead(200, { 'Content-Type': 'application/json' }); response.end('{"tokenType":"DPoP"}');
  });
  try {
    const tls = prepareTls(path.join(directory, 'tls'));
    await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve));
    proxy = await startTlsProxy(tls, { port: 0, upstreamPort: upstream.address().port, dropFirstRefreshResponse: true });
    const call = () => tlsJson(tls, 'POST', '/driver-app/v1/session/refresh', '{"refreshToken":"IN_MEMORY"}',
      { 'Idempotency-Key': 'same-operation' }, proxy.port);
    await assert.rejects(call(), { code: 'LAB_TLS_HTTP_FAILED' });
    assert.equal((await call()).status, 200);
    assert.deepEqual(proxy.counts(), { refreshRequests: 2, refreshResponsesDropped: 1, recoveredDroppedRefresh: 1 });
    assert.equal(requests, 2);
  } finally { await proxy?.stop(); upstream.closeAllConnections(); await new Promise(resolve => upstream.close(resolve)); dispose(directory); }
});

test('socket enforces owner mode, closed phases, transient code, completion and cleanup', async () => {
  const directory = temporary(); let socket, confirmations = 0, completions = 0;
  try {
    socket = await startConfirmationSocket(directory, received => { assert.equal(received, code); confirmations++; },
      { assertComplete: () => { completions++; } });
    assert.equal(fs.statSync(socket.socketPath).mode & 0o777, 0o600);
    await assert.rejects(socketRequest(socket.socketPath, '/complete', {}));
    await assert.rejects(socketRequest(socket.socketPath, '/confirm', { userCode: code + 'Z' }));
    await assert.rejects(socketRequest(socket.socketPath, '/confirm', { userCode: code, credential: 'PRIVATE' }));
    assert.deepEqual(await socketRequest(socket.socketPath, '/confirm', { userCode: code }), { state: 'WAITING_COMPLETION' });
    await assert.rejects(socketRequest(socket.socketPath, '/confirm', { userCode: code }));
    assert.deepEqual(await socketRequest(socket.socketPath, '/complete', {}), { state: 'COMPLETED' });
    await socket.done; assert.equal(confirmations, 1); assert.equal(completions, 1);
    assert.deepEqual(fs.readdirSync(directory), ['confirm.sock']);
    await socket.stop(); socket = null; assert.deepEqual(fs.readdirSync(directory), []);
  } finally { await socket?.stop(); dispose(directory); }
});

test('socket timeout is explicit and cleanup waits for in-flight confirmation', async () => {
  const directory = temporary(); let socket;
  try {
    socket = await startConfirmationSocket(directory, () => {}, { timeoutMillis: 100 });
    await assert.rejects(socket.done, { code: 'LAB_EXTERNAL_CONSUMER_TIMEOUT' });
    await assert.rejects(socketRequest(socket.socketPath, '/confirm', { userCode: code }));
  } finally { await socket?.stop(); dispose(directory); }
});

test('host consumer signs exact bodies, fresh nonce proofs and retries refresh with identical key/body', async () => {
  const nonces = new Map(), seen = new Set(), operations = [], checks = [], keys = [];
  let owner, challenge, approved = false, rotated = false, revoked = false, confirmations = 0;
  const access0 = Buffer.alloc(32, 1).toString('base64url'), access1 = Buffer.alloc(32, 2).toString('base64url');
  const refresh0 = Buffer.alloc(32, 3).toString('base64url'), refresh1 = Buffer.alloc(32, 4).toString('base64url');
  const transport = async (_tls, method, route, raw, headers) => {
    const [a, b, signature] = headers.DPoP.split('.'), header = JSON.parse(Buffer.from(a, 'base64url')),
      claims = JSON.parse(Buffer.from(b, 'base64url')), publicKey = crypto.createPublicKey({ key: header.jwk, format: 'jwk' });
    assert.equal(crypto.verify('sha256', Buffer.from(a + '.' + b), { key: publicKey, dsaEncoding: 'ieee-p1363' }, Buffer.from(signature, 'base64url')), true);
    assert.equal(header.typ, 'dpop+jwt'); assert.equal(header.alg, 'ES256'); assert.equal(header.jwk.d, undefined);
    assert.equal(claims.htm, method); assert.equal(claims.htu, origin + route); assert.equal(seen.has(claims.jti), false); seen.add(claims.jti);
    if (method === 'POST') assert.equal(claims.vr_body_sha256, hash(raw));
    if (headers.Authorization) assert.equal(claims.ath, hash(headers.Authorization.slice(5)));
    const jkt = hash(canonical(header.jwk)), nonce = crypto.randomBytes(32).toString('base64url');
    if (!claims.nonce || claims.nonce !== nonces.get(jkt)) {
      nonces.set(jkt, nonce); return { status: 401, data: { code: 'PROOF_NONCE_REQUIRED' }, nonce };
    }
    nonces.set(jkt, nonce);
    const reply = (status, data) => ({ status, data, nonce });
    if (route.endsWith('/begin')) {
      const body = JSON.parse(raw); assert.equal(body.pkceMethod, 'S256'); owner ??= jkt;
      assert.equal(body.pkceChallenge.length, 43); challenge = body.pkceChallenge; return reply(200, { pairingId: id, userCode: code });
    }
    if (route.includes('/pairings/') && method === 'GET') return jkt === owner
      ? reply(200, { state: approved ? 'APPROVED' : 'PENDING' }) : reply(404, {});
    if (route.endsWith('/redeem')) {
      assert.equal(approved, true); assert.equal(hash(JSON.parse(raw).pkceVerifier), challenge);
      return reply(200, { tokenType: 'DPoP', accessToken: access0, refreshToken: refresh0 });
    }
    if (route.endsWith('/refresh')) {
      operations.push({ raw, key: headers['Idempotency-Key'] }); rotated = true;
      return reply(200, { tokenType: 'DPoP', accessToken: access1, refreshToken: refresh1 });
    }
    if (route.endsWith('/revoke')) { revoked = true; return reply(200, { state: 'REVOKED' }); }
    if (route.endsWith('/status')) return revoked || rotated && headers.Authorization === 'DPoP ' + access0
      ? reply(401, {}) : reply(200, { readiness: 'READY' });
    assert.fail('unexpected route');
  };
  // Inject transport only; exercise the complete host journey with its real P-256/PKCE consumer.
  const { createConsumer } = await import('./060-pairing-consumer.mjs');
  const result = await runHostConsumer({}, async received => { assert.equal(received, code); approved = true; confirmations++; },
    check => checks.push(check), pairingId => assert.equal(pairingId, id), jkt => keys.push(jkt), tls => createConsumer(tls, transport));
  assert.equal(result.consumer, 'host-node'); assert.equal(result.physicalDeviceProof, false);
  assert.deepEqual(operations[0], operations[1]); assert.equal(operations.length, 2);
  assert.equal(confirmations, 1); assert.notEqual(keys[0], keys[1]); assert.equal(checks.length, 4);
});

test('invalid CLI paths and malformed code fail without reflecting input or invoking runtime', () => {
  for (const name of ['060-pairing-smoke.mjs', '060-pairing-prepare.mjs', '060-pairing-bridge.mjs']) {
    const result = spawnSync(process.execPath, [new URL('./' + name, import.meta.url).pathname, 'PRIVATE_SENTINEL'],
      { env: {}, encoding: 'utf8', timeout: 5000 });
    assert.equal(result.status, 1); assert.equal(result.stdout, '');
    assert.equal(result.stderr.includes('PRIVATE_SENTINEL'), false); assert.equal(JSON.parse(result.stderr).status, 'FAIL');
  }
});
