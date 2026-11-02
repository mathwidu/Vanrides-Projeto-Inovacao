import crypto from 'node:crypto';
import { origin, tlsJson } from './060-pairing-tls.mjs';
import { demand, token, uuid } from './070-worker-common.mjs';

const hash = value => crypto.createHash('sha256').update(value).digest('base64url');
export function createConsumer(tls, transport = tlsJson) {
  let keys = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const publicJwk = keys.publicKey.export({ format: 'jwk' });
  let verifier = crypto.randomBytes(32).toString('base64url'), nonce = null;
  async function request(method, route, body, access, idempotency = crypto.randomUUID()) {
    const raw = body === undefined ? undefined : JSON.stringify(body);
    for (let attempt = 0; attempt < 2; attempt++) {
      const header = { typ: 'dpop+jwt', alg: 'ES256', jwk: publicJwk };
      const payload = { jti: crypto.randomBytes(24).toString('base64url'), iat: Math.floor(Date.now() / 1000), htm: method, htu: origin + route };
      if (nonce) payload.nonce = nonce;
      if (access) payload.ath = hash(access);
      if (method === 'POST') payload.vr_body_sha256 = hash(raw);
      const unsigned = Buffer.from(JSON.stringify(header)).toString('base64url') + '.' + Buffer.from(JSON.stringify(payload)).toString('base64url');
      const proof = unsigned + '.' + crypto.sign('sha256', Buffer.from(unsigned), { key: keys.privateKey,
        dsaEncoding: 'ieee-p1363' }).toString('base64url');
      const result = await transport(tls, method, route, raw, { DPoP: proof, 'Idempotency-Key': idempotency,
        ...(access ? { Authorization: 'DPoP ' + access } : {}) });
      if (result.nonce) nonce = result.nonce;
      if (attempt === 0 && result.status === 401 && result.data?.code === 'PROOF_NONCE_REQUIRED' && result.nonce) continue;
      return result;
    }
  }
  return { request, jkt: hash(JSON.stringify({ crv: publicJwk.crv, kty: publicJwk.kty, x: publicJwk.x, y: publicJwk.y })),
    begin: async () => {
      const body = { publicJwk, pkceChallenge: hash(verifier), pkceMethod: 'S256', platform: 'ANDROID', appVersion: 'synthetic-host-060' };
      const key = crypto.randomUUID(), result = await request('POST', '/driver-app/v1/pairings/begin', body, undefined, key);
      demand(result.status === 200 && uuid(result.data?.pairingId) && /^[BCDFGHJKMNPQRSTVWXYZ]{8}$/.test(result.data?.userCode), 'LAB_PAIR_BEGIN_FAILED');
      const repeat = await request('POST', '/driver-app/v1/pairings/begin', body, undefined, key);
      demand(repeat.status === 200 && repeat.data.pairingId === result.data.pairingId
        && repeat.data.userCode === result.data.userCode, 'LAB_PAIR_BEGIN_REPLAY_CHANGED');
      return result.data;
    },
    redeem: pairingId => request('POST', '/driver-app/v1/pairings/redeem', { pairingId, pkceVerifier: verifier }),
    dispose: () => { keys = null; verifier = null; nonce = null; },
  };
}

/** Keys, PKCE, code and session tokens exist only in this consumer's memory. */
export async function runHostConsumer(tls, confirm, passed, rememberPairing, rememberKey = () => {}, consumerFactory = createConsumer) {
  const first = consumerFactory(tls), stranger = consumerFactory(tls);
  rememberKey(first.jkt); rememberKey(stranger.jkt);
  let pending, session, rotated;
  try {
    pending = await first.begin(); rememberPairing(pending.pairingId);
    demand((await stranger.request('GET', '/driver-app/v1/pairings/' + pending.pairingId + '/status')).status === 404,
      'LAB_FOREIGN_KEY_READ_PAIRING');
    passed('host_tls_nonce_pkce_begin_replay_and_foreign_key_isolation');
    const pendingStatus = await first.request('GET', '/driver-app/v1/pairings/' + pending.pairingId + '/status');
    demand(pendingStatus.status === 200 && pendingStatus.data.state === 'PENDING', 'LAB_PAIR_NOT_PENDING');
    await confirm(pending.userCode); delete pending.userCode;
    const approved = await first.request('GET', '/driver-app/v1/pairings/' + pending.pairingId + '/status');
    demand(approved.status === 200 && approved.data.state === 'APPROVED', 'LAB_PAIR_NOT_APPROVED');
    const redeemed = await first.redeem(pending.pairingId); session = redeemed.data;
    demand(redeemed.status === 200 && session.tokenType === 'DPoP' && token(session.accessToken)
      && token(session.refreshToken), 'LAB_PAIR_REDEEM_FAILED');
    const status = await first.request('GET', '/driver-app/v1/status', undefined, session.accessToken);
    demand(status.status === 200 && status.data.readiness === 'READY', 'LAB_DEVICE_NOT_READY');
    passed('host_worker_approved_pairing_redeem_and_status');
    const body = { refreshToken: session.refreshToken }, key = crypto.randomUUID();
    // Discard the first successful response: the next request recovers it with a fresh proof.
    const lost = await first.request('POST', '/driver-app/v1/session/refresh', body, undefined, key);
    demand(lost.status === 200, 'LAB_REFRESH_FAILED');
    const replay = await first.request('POST', '/driver-app/v1/session/refresh', body, undefined, key); rotated = replay.data;
    demand(replay.status === 200 && rotated.refreshToken === lost.data.refreshToken
      && rotated.accessToken === lost.data.accessToken && rotated.refreshToken !== session.refreshToken,
    'LAB_REFRESH_RESPONSE_RECOVERY_FAILED');
    demand((await first.request('GET', '/driver-app/v1/status', undefined, session.accessToken)).status === 401,
      'LAB_OLD_ACCESS_VALID');
    demand((await first.request('GET', '/driver-app/v1/status', undefined, rotated.accessToken)).data?.readiness === 'READY',
      'LAB_ROTATED_ACCESS_INVALID');
    passed('host_refresh_rotation_and_lost_response_idempotent_recovery');
    const revoked = await first.request('POST', '/driver-app/v1/session/revoke', {}, rotated.accessToken);
    demand(revoked.status === 200 && revoked.data.state === 'REVOKED', 'LAB_REVOKE_FAILED');
    demand((await first.request('GET', '/driver-app/v1/status', undefined, rotated.accessToken)).status === 401,
      'LAB_REVOKED_ACCESS_VALID');
    demand((await first.request('POST', '/driver-app/v1/session/revoke', {}, rotated.accessToken)).status === 200,
      'LAB_REVOKE_RETRY_FAILED');
    passed('host_revoke_rejects_status_and_allows_own_retry');
    return { consumer: 'host-node', physicalDeviceProof: false, lostRefreshResponseSimulated: true };
  } finally { pending = session = rotated = null; first.dispose(); stranger.dispose(); }
}
