import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { origin, loadTls, startTlsProxy } from './060-pairing-tls.mjs';
import { runHostConsumer } from './060-pairing-consumer.mjs';
import { startConfirmationSocket } from './060-pairing-socket.mjs';
import { demand, exact, uuid, token, quote, privateBytes, properties, privateWrite, localJson, summary, digest } from './070-worker-common.mjs';

export const canonical = value => Array.isArray(value) ? '[' + value.map(canonical).join(',') + ']'
  : value !== null && typeof value === 'object' ? '{' + Object.keys(value).sort().map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}'
    : JSON.stringify(value);

export function requireLabConfig(config, caFile, tls) {
  demand(config.get('vanrides.wa.public-base-url') === origin && caFile === tls.publicCa,
    'LAB_CANONICAL_ORIGIN_OR_CA_INVALID');
}

export function pairingScenarios({ tlsDirectory, caFile, mode = 'host', dropFirstRefreshResponse = false }) {
  demand(['host', 'socket'].includes(mode), 'LAB_MODE_INVALID');
  demand(!dropFirstRefreshResponse || mode === 'socket', 'LAB_REFRESH_DROP_MODE_INVALID');
  const tls = loadTls(tlsDirectory);
  return async function runScenarios({ input, tools, work, actor, instance, address, credentials, worker, provider, passed, interruption }) {
    const config = properties(privateBytes(input.configFile)); requireLabConfig(config, caFile, tls);
    const hashKey = Buffer.from(config.get('vanrides.wa.hash-key') ?? '', 'base64url');
    demand(hashKey.length === 32, 'LAB_HASH_KEY_INVALID');
    let uniqueEvents = 0, duplicateEvents = 0, submissions = 0, pairingId = null, proxy = null, socket = null, socketDirectory = null;
    const pairingIds = new Set(), deviceKeys = new Set();
    const observePairing = observed => {
      demand(uuid(observed.pairingId) && token(observed.jkt), 'LAB_PAIRING_OBSERVATION_INVALID');
      pairingIds.add(observed.pairingId); deviceKeys.add(observed.jkt);
    };
    const [channel] = credentials;
    const request = body => interruption.wait(() => localJson(input.state.api_base_url, '/integrations/whatsapp/v1/events',
      { method: 'POST', body, headers: { Authorization: 'Bearer ' + channel.credential } }));
    const event = text => ({ event: 'messages.upsert', instance, date_time: new Date().toISOString(),
      data: { key: { id: 'wa060_message_' + crypto.randomUUID().replaceAll('-', ''), fromMe: false, remoteJid: address },
        messageTimestamp: Math.floor(Date.now() / 1000), message: { conversation: text } } });
    async function control(route, kind, state) {
      const response = await interruption.wait(() => worker.request(route));
      demand(response.status === 200, 'LAB_WORKER_CONTROL_FAILED'); summary(response.data, kind, state);
    }
    async function send(text, command, payload = {}) {
      const version = Number(tools.sql(`SELECT COALESCE((SELECT version FROM wa.profile WHERE identity_id=${quote(actor)}),0);`));
      const body = event(text), response = await request(body);
      demand(response.status === 202 && exact(response.data, ['receiptId', 'status', 'serverTime'])
        && response.data.status === 'PERSISTED' && uuid(response.data.receiptId), 'LAB_EVENT_NOT_DURABLE');
      uniqueEvents++;
      const id = response.data.receiptId;
      await control('/v1/process-inbox', 'process', 'PROCESSED');
      const item = JSON.parse(tools.sql(`SELECT json_build_object('command',r.command_type,'hash',r.request_hash,
        'result',r.result_json,'outboxId',o.id,'text',o.payload_json->>'text','inbox',i.payload_json,'state',i.state)
        FROM wa.command_receipt r JOIN wa.integration_inbox i ON i.id=r.inbox_event_id
        JOIN wa.message_outbox o ON o.command_id=r.command_id
        WHERE r.inbox_event_id=${quote(id)} AND r.identity_id=${quote(actor)};`));
      const expectedHash = digest(canonical({ commandType: command, inboxEventId: id, expectedVersion: version, payload }));
      demand(item.command === command, 'LAB_' + command + '_TYPE_MISMATCH');
      demand(item.hash === expectedHash, 'LAB_' + command + '_PAYLOAD_HASH_MISMATCH');
      demand(item.result.outcome === 'APPLIED', 'LAB_' + command + '_NOT_APPLIED');
      demand(item.state === 'COMPLETED' && uuid(item.outboxId) && typeof item.text === 'string',
        'LAB_' + command + '_DELIVERY_INVALID');
      provider.enqueue({ outboxId: item.outboxId, text: item.text });
      await control('/v1/dispatch-one', 'dispatch', 'SUBMITTED'); submissions++;
      demand(provider.counts().requests === submissions && provider.counts().accepted === submissions
        && provider.counts().failures === 0 && provider.counts().pending === 0, 'LAB_DELIVERY_NOT_ONCE');
      return { body, id, item };
    }
    const effects = () => tools.sql(`SELECT json_build_object(
      'commands',(SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)}),
      'outbox',(SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)}),
      'attempts',(SELECT count(*) FROM wa.message_attempt WHERE instance_id=${quote(instance)}),
      'generation',(SELECT generation FROM wa.driver_authorization WHERE driver_id=${quote(actor)}),
      'pairings',(SELECT count(*) FROM wa.pairing_challenge WHERE driver_id=${quote(actor)}),
      'devices',(SELECT count(*) FROM wa.driver_device WHERE driver_id=${quote(actor)}));`);
    async function confirm(code) {
      demand(pairingId === null || uuid(pairingId), 'LAB_PAIRING_STATE_INVALID');
      demand(/^[BCDFGHJKMNPQRSTVWXYZ]{8}$/.test(code), 'LAB_CODE_SHAPE_INVALID');
      const codeHash = crypto.createHmac('sha256', hashKey).update('pairing-code\0' + code).digest('hex');
      const found = tools.sql(`SELECT id FROM wa.pairing_challenge WHERE code_hash=${quote(codeHash)} AND state='PENDING'
        AND driver_id IS NULL AND environment='wa070-local' AND expires_at>CURRENT_TIMESTAMP;`);
      demand(uuid(found) && (pairingId === null || pairingId === found), 'LAB_CODE_NOT_FROM_PENDING_CONSUMER'); pairingId = found;
      const delivered = await send(code, 'CONFIRM_PAIRING');
      demand(delivered.item.inbox.pairingCodeHash === codeHash && !JSON.stringify(delivered.item.inbox).includes(code),
        'LAB_CODE_NOT_NORMALIZED_TO_HASH');
      demand(tools.count(`SELECT count(*) FROM wa.pairing_challenge WHERE id=${quote(pairingId)} AND driver_id=${quote(actor)}
        AND state='APPROVED';`) === 1, 'LAB_CHANNEL_PAIRING_NOT_APPROVED');
      const before = effects();
      const duplicate = await request(delivered.body); duplicateEvents++;
      demand(duplicate.status === 202 && duplicate.data.status === 'DUPLICATE' && duplicate.data.receiptId === delivered.id,
        'LAB_PAIR_EVENT_NOT_DEDUPLICATED');
      await control('/v1/process-inbox', 'process', 'IDLE'); await control('/v1/dispatch-one', 'dispatch', 'IDLE');
      await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start());
      await control('/v1/process-inbox', 'process', 'IDLE'); await control('/v1/dispatch-one', 'dispatch', 'IDLE');
      demand(effects() === before && provider.counts().requests === submissions, 'LAB_PAIRING_REPLAY_OR_RESTART_CHANGED_EFFECT');
      passed('consumer_code_as_evolution_text_real_worker_confirm_pairing_empty_payload');
      passed('pairing_event_duplicate_and_worker_restart_have_no_new_effect');
    }
    function assertComplete() {
      demand(uuid(pairingId) && tools.count(`SELECT count(*) FROM wa.pairing_challenge WHERE id=${quote(pairingId)}
        AND driver_id=${quote(actor)} AND state='REDEEMED';`) === 1
        && tools.count(`SELECT count(*) FROM wa.driver_device WHERE driver_id=${quote(actor)};`) === 1
        && tools.count(`SELECT count(*) FROM wa.driver_device WHERE driver_id=${quote(actor)} AND status='REVOKED';`) === 1
        && tools.count(`SELECT count(*) FROM wa.device_token t JOIN wa.driver_device d ON d.id=t.device_id
          WHERE d.driver_id=${quote(actor)} AND t.kind='REFRESH';`) === 2
        && tools.count(`SELECT count(*) FROM wa.device_token t JOIN wa.driver_device d ON d.id=t.device_id
          WHERE d.driver_id=${quote(actor)} AND t.revoked_at IS NULL;`) === 0, 'LAB_CONSUMER_NOT_REDEEMED_ROTATED_REVOKED_ONCE');
      if (dropFirstRefreshResponse) demand(proxy.counts().refreshResponsesDropped === 1
        && proxy.counts().recoveredDroppedRefresh >= 1, 'LAB_NATIVE_REFRESH_RESPONSE_NOT_RECOVERED');
    }
    try {
      await send('oi', 'START_ONBOARDING');
      await new Promise(resolve => setImmediate(resolve)); interruption.assertActive();
      const bootstrap = tools.operate('driver-bootstrap', { identityId: actor, expiresAt: new Date(Date.now() + 1800000).toISOString() });
      demand(token(bootstrap.driverInviteCode), 'LAB_DRIVER_BOOTSTRAP_INVALID');
      await send(bootstrap.driverInviteCode, 'CLAIM_DRIVER_INVITE'); delete bootstrap.driverInviteCode;
      await send('Alex Exemplo', 'SET_PROFILE_NAME', { name: 'Alex Exemplo' });
      await send('ACEITO', 'ACCEPT_NOTICE', { noticeVersion: 'wa-mvp-v1' });
      passed('synthetic_driver_bootstrap_and_consent_through_real_inbox_worker');
      proxy = await startTlsProxy(tls, { onPairingBegin: observePairing, dropFirstRefreshResponse });
      let consumer;
      if (mode === 'host') {
        consumer = await interruption.wait(() => runHostConsumer(tls, confirm, passed, id => { pairingId = id; }, jkt => deviceKeys.add(jkt)));
        assertComplete();
      } else {
        socketDirectory = fs.mkdtempSync(path.join(fs.realpathSync('/tmp'), 'vr060-')); fs.chmodSync(socketDirectory, 0o700);
        socket = await startConfirmationSocket(socketDirectory, confirm, { assertComplete });
        privateWrite(path.join(work, 'external-consumer.json'), JSON.stringify({ schemaVersion: 1, socketPath: socket.socketPath,
          origin, publicCa: tls.publicCa, tlsHashes: tls.hashes, timeoutSeconds: 900, dropFirstRefreshResponse,
          sourceSha: input.expectedSha, jarSha256: input.state.jar_sha256, phase: 'WAITING_CODE' }) + '\n');
        passed('external_consumer_socket_ready_private_manifest_no_code');
        await interruption.wait(() => socket.done);
        consumer = { consumer: 'external-unix-socket', physicalDeviceProof: false,
          externalConsumerAssertion: 'redeemed_refreshed_once_and_revoked_in_database', lostRefreshResponseSimulated: dropFirstRefreshResponse };
      }
      const currentTls = loadTls(tlsDirectory);
      demand(JSON.stringify(currentTls.hashes) === JSON.stringify(tls.hashes) && currentTls.key.equals(tls.key), 'LAB_TLS_CHANGED');
      passed('single_device_single_refresh_rotation_and_revocation_durable');
      return { ...consumer, uniqueEvents, duplicateEvents, commandReceipts: uniqueEvents,
        providerRequests: submissions, workerStarts: 2, modelCalls: 0, tlsHashes: tls.hashes,
        proxy: proxy.counts(),
        canonicalOrigin: origin, integrationTransport: 'synthetic-evolution-text-real-worker',
        injectedOperatorPairingCode: false, injectedOperatorLease: false };
    } finally {
      hashKey.fill(0);
      try { await socket?.stop(); } finally {
        if (socketDirectory) fs.rmSync(socketDirectory, { recursive: true, force: true });
        await proxy?.stop();
        // Only keys and challenges actually observed through this owned proxy are removed.
        if (uuid(pairingId)) pairingIds.add(pairingId);
        for (const id of pairingIds) tools.sql(`DELETE FROM wa.pairing_challenge WHERE id=${quote(id)} AND driver_id IS NULL
          AND environment='wa070-local';`);
        for (const jkt of deviceKeys) {
          const hex = digest('wa070-local\0' + jkt).slice(0, 32);
          const scope = hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
          tools.sql(`BEGIN; DELETE FROM wa.operation_replay WHERE operation_kind='PAIR_BEGIN' AND scope_id=${quote(scope)};
            DELETE FROM wa.proof_nonce WHERE jkt=${quote(jkt)} AND environment='wa070-local';
            DELETE FROM wa.proof_replay WHERE jkt=${quote(jkt)}; COMMIT;`);
        }
      }
    }
  };
}
