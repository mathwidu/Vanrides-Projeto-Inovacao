import { randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';
import { constants, openSync, fstatSync, readSync, writeFileSync, fchmodSync, fsyncSync, closeSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { pn, lid } from './config.mjs';
import { strictJson, object, exact, timestamp } from './strict-json.mjs';

const SPEC = ['instance', 'webhookToken', 'authorizedPn', 'listenHost', 'listenPort', 'candidateFile'];
const DOCUMENT = [...SPEC, 'schemaVersion', 'challengeId', 'nonce', 'issuedAt', 'expiresAt'];
const PENDING = ['schemaVersion', 'state', 'challengeId', 'instance', 'authorizedPn', 'candidateLid', 'observedAt', 'closedAt', 'nonceInvalidated'];
const branded = new WeakSet();
const invalid = (code = 'BOOTSTRAP_INVALID') => { throw new Error(code); };
const token = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)
  && Buffer.from(value, 'base64url').toString('base64url') === value;
const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);
const filePath = value => typeof value === 'string' && isAbsolute(value) && !value.includes('\0');
const id = value => typeof value === 'string' && /^[\x21-\x7e]{1,160}$/.test(value);

function validSpec(raw) {
  return typeof raw.instance === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(raw.instance)
    && token(raw.webhookToken) && pn(raw.authorizedPn) && filePath(raw.candidateFile)
    && ['127.0.0.1', '::1', '0.0.0.0'].includes(raw.listenHost)
    && Number.isInteger(raw.listenPort) && raw.listenPort >= 1 && raw.listenPort <= 65535;
}

export function validateChallenge(raw) {
  if (!exact(raw, DOCUMENT) || !validSpec(raw) || raw.schemaVersion !== 1 || !uuid(raw.challengeId)
      || !token(raw.nonce) || raw.nonce === raw.webhookToken) invalid();
  const issued = timestamp(raw.issuedAt), expires = timestamp(raw.expiresAt);
  if (issued === null || expires === null || expires <= issued || expires - issued > 300_000) invalid();
  const config = { schemaVersion: 1, challengeId: raw.challengeId, instance: raw.instance, issuedAt: raw.issuedAt,
    expiresAt: raw.expiresAt, issued, expires, listenHost: raw.listenHost, listenPort: raw.listenPort, candidateFile: raw.candidateFile };
  Object.defineProperties(config, { webhookToken: { value: raw.webhookToken }, authorizedPn: { value: raw.authorizedPn }, nonce: { value: raw.nonce } });
  Object.freeze(config); branded.add(config); return config;
}

/** Generate on the operator's Mac. Both nonce and webhook token remain in private files only. */
export function createChallenge(spec, { now = Date.now, ttlMs = 300_000 } = {}) {
  if (!exact(spec, SPEC) || !validSpec(spec) || !Number.isInteger(ttlMs) || ttlMs < 1 || ttlMs > 300_000) invalid();
  const current = now();
  if (!Number.isSafeInteger(current) || current < 0) invalid();
  return validateChallenge({ schemaVersion: 1, challengeId: randomUUID(), instance: spec.instance,
    webhookToken: spec.webhookToken, authorizedPn: spec.authorizedPn, listenHost: spec.listenHost, listenPort: spec.listenPort,
    candidateFile: spec.candidateFile, nonce: randomBytes(32).toString('base64url'),
    issuedAt: new Date(current).toISOString(), expiresAt: new Date(current + ttlMs).toISOString() });
}

export function readPrivateJson(file) {
  let fd;
  try {
    if (!filePath(file) || typeof process.getuid !== 'function') invalid();
    fd = openSync(file, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    const stat = fstatSync(fd);
    if (!stat.isFile() || stat.uid !== process.getuid() || (stat.mode & 0o777) !== 0o600 || stat.size < 1 || stat.size > 8192) invalid();
    const bytes = Buffer.alloc(8193); let count = 0;
    while (count < bytes.length) {
      const size = readSync(fd, bytes, count, bytes.length - count, null);
      if (!size) break;
      count += size;
    }
    if (count < 1 || count > 8192) invalid();
    return strictJson(bytes.subarray(0, count));
  } catch { invalid('PRIVATE_INPUT_INVALID'); }
  finally { if (fd !== undefined) closeSync(fd); }
}

function privateWrite(file, value) {
  let fd;
  try {
    if (!filePath(file) || typeof process.getuid !== 'function') invalid();
    fd = openSync(file, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
    fchmodSync(fd, 0o600);
    writeFileSync(fd, JSON.stringify(value) + '\n'); fsyncSync(fd);
  } catch { invalid('PRIVATE_WRITE_FAILED'); }
  finally { if (fd !== undefined) closeSync(fd); }
}

export function writeChallenge(file, config) {
  if (!branded.has(config)) invalid();
  const document = Object.create(null);
  for (const field of DOCUMENT) document[field] = config[field];
  privateWrite(file, document);
}

/** Atomic exclusive claim survives restart. Failure burns this file; use a fresh challenge, never remove the marker to retry. */
export function claimChallenge(file, { now = Date.now } = {}) {
  const config = validateChallenge(readPrivateJson(file)), current = now();
  if (!Number.isSafeInteger(current) || current < config.issued || current >= config.expires || config.candidateFile === file
      || config.candidateFile === file + '.claimed') invalid();
  privateWrite(file + '.claimed', { schemaVersion: 1, challengeId: config.challengeId, state: 'CLAIMED_ONCE' });
  return config;
}

function pendingValid(value) {
  return exact(value, PENDING) && value.schemaVersion === 1 && value.state === 'PENDING_HUMAN_CONFIRMATION'
    && uuid(value.challengeId) && typeof value.instance === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(value.instance)
    && pn(value.authorizedPn) && lid(value.candidateLid) && value.nonceInvalidated === true
    && timestamp(value.observedAt) !== null && timestamp(value.closedAt) !== null
    && timestamp(value.closedAt) >= timestamp(value.observedAt);
}

export function writePendingCandidate(file, candidate) {
  if (!pendingValid(candidate)) invalid();
  const document = Object.create(null);
  for (const field of PENDING) document[field] = candidate[field];
  privateWrite(file, document);
}

/** The marker is an explicit assertion by the trusted operator AFTER a real human confirmation, never evidence inferred from a webhook. */
export function confirmCandidate(candidate, marker, { now = Date.now } = {}) {
  if (!pendingValid(candidate) || !exact(marker, ['stage', 'challengeId', 'authorizedPn', 'candidateLid', 'confirmedAt'])
      || marker.stage !== 'OPERATOR_CONFIRMED_AUTHORIZED_CONTACT' || marker.challengeId !== candidate.challengeId
      || marker.authorizedPn !== candidate.authorizedPn || marker.candidateLid !== candidate.candidateLid) invalid('HUMAN_CONFIRMATION_REQUIRED');
  const confirmed = timestamp(marker.confirmedAt), current = now();
  if (confirmed === null || !Number.isSafeInteger(current) || confirmed < timestamp(candidate.closedAt) || confirmed > current + 30_000)
    invalid('HUMAN_CONFIRMATION_REQUIRED');
  return Object.freeze({ state: 'OPERATOR_CONFIRMED', challengeId: candidate.challengeId,
    authorizedPn: candidate.authorizedPn, candidateLid: candidate.candidateLid });
}

function plainText(message) {
  if (exact(message, ['conversation']) && typeof message.conversation === 'string') return { text: message.conversation, type: 'conversation' };
  if (!exact(message, ['extendedTextMessage']) || !object(message.extendedTextMessage)) return null;
  const extended = message.extendedTextMessage;
  if (!(exact(extended, ['text']) || exact(extended, ['text', 'contextInfo'])) || typeof extended.text !== 'string') return null;
  return { text: extended.text, type: 'extendedTextMessage' };
}

function messageKey(key) {
  if (!object(key)) return false;
  const fields = ['id', 'fromMe', 'remoteJid'];
  if (Object.hasOwn(key, 'remoteJidAlt')) fields.push('remoteJidAlt');
  if (Object.hasOwn(key, 'addressingMode')) {
    if (!['lid', 'pn'].includes(key.addressingMode)) return false;
    fields.push('addressingMode');
  }
  return exact(key, fields);
}

/** No I/O or identity mapping. A match creates only an in-memory candidate, finalized after the collector closes. */
export function createBootstrapSession(config, { now = Date.now } = {}) {
  if (!branded.has(config) || typeof now !== 'function') invalid();
  let phase = 'WAITING', nonce = config.nonce, candidate = null, finalized = null;
  const snapshot = () => Object.freeze({ state: phase });
  const fail = () => { phase = 'FAILED'; nonce = ''; candidate = null; return 'FAILED'; };
  function receive(envelope) {
    if (phase === 'CLOSED' || phase === 'FAILED') return 'DROP';
    const current = now();
    if (!Number.isSafeInteger(current) || current < config.issued || current >= config.expires) return 'DROP';
    if (!object(envelope) || envelope.instance !== config.instance || !object(envelope.data) || !object(envelope.data.message)) return 'DROP';
    const data = envelope.data, message = data.message;
    // Ignore unrelated messages without copying, retaining or logging their content.
    if (message.conversation !== nonce && message.extendedTextMessage?.text !== nonce) return 'DROP';
    const content = plainText(message), key = data.key;
    if (envelope.event !== 'messages.upsert' || !object(key) || !Object.hasOwn(data, 'key') || !Object.hasOwn(data, 'message')
        || !content || content.text !== nonce || key.fromMe !== false || !id(key.id)
        || !messageKey(key)
        || Object.hasOwn(data, 'messageType') && data.messageType !== content.type) return fail();
    const occurred = timestamp(envelope.date_time);
    if (occurred === null || occurred < config.issued || occurred >= config.expires || occurred > current + 30_000) return fail();
    if (Object.hasOwn(data, 'messageTimestamp') && (!Number.isSafeInteger(data.messageTimestamp)
        || data.messageTimestamp < Math.floor(config.issued / 1000) || data.messageTimestamp * 1000 >= config.expires
        || data.messageTimestamp * 1000 > current + 30_000)) return fail();
    // Additional alias channels are never ignored or interpreted as another source of authority.
    if (Object.keys(data).some(field => !['key', 'message', 'messageType', 'messageTimestamp', 'pushName', 'status',
      'contextInfo', 'instanceId', 'source'].includes(field))) return fail();
    for (const field of ['remoteJid', 'remoteJidAlt', 'participant', 'participantAlt', 'senderPn', 'senderLid', 'participantPn', 'participantLid', 'aliases'])
      if (Object.hasOwn(envelope, field)) return fail();
    const aliases = [key.remoteJid];
    if (Object.hasOwn(key, 'remoteJidAlt')) aliases.push(key.remoteJidAlt);
    if (aliases.some(alias => !pn(alias) && !lid(alias))) return fail();
    const lids = aliases.filter(lid), pns = aliases.filter(pn);
    if (lids.length !== 1 || pns.length > 1 || pns.some(alias => alias !== config.authorizedPn)) return fail();
    if (candidate) return candidate.candidateLid === lids[0] ? 'REPLAY' : fail();
    candidate = { candidateLid: lids[0], observedAt: new Date(current).toISOString() };
    phase = 'PENDING'; return 'PENDING';
  }
  function close() {
    if (phase === 'CLOSED') return finalized;
    const current = now();
    nonce = '';
    if (phase === 'PENDING' && Number.isSafeInteger(current) && current >= timestamp(candidate.observedAt))
      finalized = Object.freeze({ schemaVersion: 1, state: 'PENDING_HUMAN_CONFIRMATION', challengeId: config.challengeId,
        instance: config.instance, authorizedPn: config.authorizedPn, candidateLid: candidate.candidateLid,
        observedAt: candidate.observedAt, closedAt: new Date(current).toISOString(), nonceInvalidated: true });
    candidate = null; phase = 'CLOSED'; return finalized;
  }
  return Object.freeze({ receive, snapshot, close });
}

function authorized(request, tokenValue) {
  if (request.rawHeaders.filter((name, index) => index % 2 === 0 && name.toLowerCase() === 'authorization').length !== 1) return false;
  const actual = request.headers.authorization, expected = `Bearer ${tokenValue}`;
  return typeof actual === 'string' && Buffer.byteLength(actual) === Buffer.byteLength(expected)
    && timingSafeEqual(Buffer.from(actual), Buffer.from(expected));
}

function headersValid(request) {
  const seen = new Set();
  for (let index = 0; index < request.rawHeaders.length; index += 2) {
    const name = request.rawHeaders[index].toLowerCase();
    if (seen.has(name)) return false;
    seen.add(name);
  }
  return request.headers.origin === undefined && request.headers['content-encoding'] === undefined && request.headers.expect === undefined
    && (request.headers['transfer-encoding'] === undefined || request.headers['transfer-encoding'] === 'chunked')
    && /^application\/json(?:;\s*charset=utf-8)?$/i.test(request.headers['content-type'] ?? '');
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0, finished = false; const chunks = [];
    const finish = (code, result) => {
      if (finished) return;
      finished = true; clearTimeout(timer);
      request.off('data', data); request.off('end', end); request.off('aborted', fail); request.off('error', fail);
      if (code) reject(code); else resolve(result);
    };
    const fail = () => finish(400);
    const data = chunk => { size += chunk.length; if (size > 32768) finish(413); else chunks.push(chunk); };
    const end = () => { try { finish(null, strictJson(Buffer.concat(chunks, size))); } catch { finish(400); } };
    const timer = setTimeout(fail, 3000);
    request.on('data', data); request.on('end', end); request.on('aborted', fail); request.on('error', fail);
  });
}

/** Inbound HTTP only; there is deliberately no API/worker/Evolution client or forwarding callback. */
export function createBootstrapServer({ config, now = Date.now, log = () => {} }) {
  const session = createBootstrapSession(config, { now });
  if (typeof log !== 'function') invalid();
  let reading = 0, stopped = false, final = null;
  const emit = code => { try { log(Object.freeze({ code })); } catch { /* No provider errors are logged. */ } };
  const reply = (response, status) => {
    if (response.destroyed || response.writableEnded) return;
    response.writeHead(status, { 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', connection: 'close', 'content-length': '0' });
    response.end();
  };
  async function handle(request, response) {
    request.on('error', () => {});
    if (!authorized(request, config.webhookToken)) return reply(response, 401);
    if (request.method !== 'POST' || request.url !== '/evolution/webhook' || !headersValid(request)) return reply(response, 400);
    if (Number(request.headers['content-length'] ?? 0) > 32768) return reply(response, 413);
    if (stopped || reading >= 2) return reply(response, 503);
    reading++;
    let envelope;
    try { envelope = await readBody(request); }
    catch (status) { return reply(response, status === 413 ? 413 : 400); }
    finally { reading--; }
    if (stopped || request.aborted || response.destroyed) return;
    const result = session.receive(envelope);
    if (result === 'PENDING') emit('CANDIDATE_PENDING');
    if (result === 'FAILED') emit('CHALLENGE_FAILED');
    reply(response, 204);
  }
  const server = createServer({ maxHeaderSize: 4096, requestTimeout: 4000, headersTimeout: 3000,
    keepAliveTimeout: 1000, connectionsCheckingInterval: 500 }, (request, response) => {
    handle(request, response).catch(() => reply(response, 400));
  });
  const expected = (request, response) => { handle(request, response).catch(() => reply(response, 400)); };
  server.on('checkContinue', expected); server.on('checkExpectation', expected);
  server.on('clientError', (error, socket) => {
    const status = error.code === 'HPE_HEADER_OVERFLOW' ? 413 : 400;
    if (socket.writable) socket.end(`HTTP/1.1 ${status} ${status === 413 ? 'Payload Too Large' : 'Bad Request'}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  });
  server.maxConnections = 8; server.maxRequestsPerSocket = 1; server.setTimeout(4000, socket => socket.destroy());
  Object.defineProperties(server, {
    snapshot: { value: session.snapshot },
    shutdown: { value: () => {
      if (stopped) return final;
      stopped = true; server.close(); server.closeAllConnections(); final = session.close(); return final;
    } },
  });
  return server;
}
