/** Closed private bridge. No I/O on import; no sensor data belongs here. */
import crypto from 'node:crypto';
import { demand, exact, token, uuid, SmokeFailure } from './source/integrations/whatsapp/scripts/070-worker-common.mjs';

export const PROFILE = 'VPS_PHYSICAL_JOURNEY_V1';
export const ROUTES = ['/pair', '/intent', '/started', '/stopped'];
export const code = error => error instanceof SmokeFailure && /^[A-Z][A-Z0-9_]{2,119}$/.test(error.code)
  ? error.code : 'PHYSICAL_SESSION_FAILED';

export function binding(attemptId, sourceSha, nonce) {
  demand(/^[a-z0-9][a-z0-9-]{0,39}$/.test(attemptId) && /^[a-f0-9]{40}$/.test(sourceSha) && token(nonce), 'BRIDGE_CONTEXT_INVALID');
  return { attemptId, sourceSha, nonce };
}
export const envelope = (scope, result) => ({ ...scope, result });

export function phaseBody(route, body, step) {
  demand(step < 4 && ROUTES[step] === route, 'BRIDGE_PHASE_ORDER');
  if (route === '/pair') demand(exact(body, ['code', 'pairingId', 'jkt'])
    && typeof body.code === 'string' && /^[BCDFGHJKMNPQRSTVWXYZ]{8}$/.test(body.code)
    && uuid(body.pairingId) && token(body.jkt), 'BRIDGE_PAIR_BODY_INVALID');
  else demand(exact(body, []), 'BRIDGE_EMPTY_BODY_REQUIRED');
}

export class Bridge {
  constructor({ attemptId, sourceSha, jarSha256, moduleSha256, clock = Date.now }) {
    demand(/^[a-f0-9]{64}$/.test(jarSha256) && /^[a-f0-9]{64}$/.test(moduleSha256), 'BRIDGE_PINS_INVALID');
    Object.assign(this, { attemptId, sourceSha, jarSha256, moduleSha256, clock });
    this.scope = null; this.secret = null; this.expiry = 0; this.step = 0; this.busy = false;
    this.failed = false; this.closed = false;
  }
  bootstrap(nonce) {
    demand(!this.scope && !this.closed, 'BRIDGE_BOOTSTRAP_ALREADY_USED');
    this.scope = binding(this.attemptId, this.sourceSha, nonce);
    this.secret = crypto.randomBytes(32).toString('base64url'); this.expiry = this.clock() + 900000;
    return envelope(this.scope, { bridgeToken: this.secret, expiresAt: new Date(this.expiry).toISOString(),
      apiPort: 28083, sidecarPort: 28444, profile: PROFILE, jarSha256: this.jarSha256, moduleSha256: this.moduleSha256 });
  }
  recovery(nonce) {
    const scope = binding(this.attemptId, this.sourceSha, nonce);
    demand(!this.scope || JSON.stringify(scope) === JSON.stringify(this.scope), 'BRIDGE_CONTEXT_MISMATCH');
    if (!this.scope) this.scope = scope; // Cleanup only; never creates a bridge credential.
    return this.scope;
  }
  authorize(headers) {
    demand(this.scope && this.secret && !this.closed && this.clock() < this.expiry, 'BRIDGE_EXPIRED_OR_CLOSED');
    demand(headers['x-wa110-attempt'] === this.scope.attemptId && headers['x-wa110-source-sha'] === this.scope.sourceSha
      && headers['x-wa110-nonce'] === this.scope.nonce, 'BRIDGE_CONTEXT_MISMATCH');
    const supplied = headers['x-wa110-bridge-token'];
    demand(token(supplied) && supplied.length === this.secret.length
      && crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(this.secret)), 'BRIDGE_AUTH_FAILED');
  }
  async exchange(route, body, action) {
    demand(!this.busy && !this.failed && !this.closed, 'BRIDGE_CONCURRENT_OR_FAILED');
    try { phaseBody(route, body, this.step); }
    catch (error) { this.failed = true; throw error; }
    this.busy = true;
    try {
      const result = await action();
      demand(exact(result, route === '/intent' ? ['uri'] : []), 'BRIDGE_RESULT_INVALID');
      this.step++; return envelope(this.scope, result);
    } catch (error) { this.failed = true; throw error; }
    finally { this.busy = false; }
  }
  close() { this.closed = true; this.secret = null; }
}

export function pairingMatch(rows, body, hashKey, now, notBefore) {
  demand(Array.isArray(rows) && rows.length === 1, 'PAIRING_CHALLENGE_NOT_UNIQUE');
  const row = rows[0], codeHash = crypto.createHmac('sha256', hashKey).update('pairing-code\0' + body.code).digest('hex');
  demand(exact(row, ['id', 'jkt', 'codeHash', 'pkce', 'platform', 'state', 'environment', 'driverId', 'createdAt', 'expiresAt'])
    && row.id === body.pairingId && row.jkt === body.jkt && token(row.jkt) && token(row.pkce)
    && row.codeHash === codeHash && row.platform === 'ANDROID' && row.state === 'PENDING'
    && row.environment === 'wa070-local' && row.driverId === null
    && Number.isFinite(Date.parse(row.createdAt)) && Date.parse(row.createdAt) >= notBefore
    && Date.parse(row.createdAt) <= now + 1000 && Date.parse(row.expiresAt) > now, 'PAIRING_BINDING_MISMATCH');
  return { id: row.id, jkt: row.jkt };
}

export function cleanupCredentials(rows, scope) {
  const roles = new Set(['CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB']);
  demand(Array.isArray(rows) && rows.length <= 4 && new Set(rows.map(row => row.id)).size === rows.length
    && new Set(rows.map(row => row.principal)).size === rows.length, 'CLEANUP_CREDENTIAL_SET_INVALID');
  demand(rows.every(row => uuid(row.id) && roles.has(row.principal) && row.environment === 'wa070-local'
    && row.instance === scope.instance && row.cohortId === scope.batch && row.synthetic === true), 'CLEANUP_CREDENTIAL_OUT_OF_SCOPE');
  return rows.map(row => row.id);
}
