import crypto from 'node:crypto';
import { demand, exact, localJson, quote, summary, uuid } from './070-worker-common.mjs';
import { connectionEvent } from './070-worker-scenarios.mjs';
import { runDeliveryRecovery } from './120-delivery-recovery.mjs';

const prefix = '/integrations/whatsapp/v1';
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** Integrated synthetic delivery faults. The only provider is the owned loopback listener. */
export async function runDeliveryScenarios(runtime) {
  const { input, tools, actor, address, instance, credentials, worker, provider, interruption, passed } = runtime;
  const read = sql => tools.sql(sql, { recordOutput: false });
  let events = 0;
  const actorSql = quote(actor);
  async function request(route, body, credential = credentials[0]) {
    return interruption.wait(() => localJson(input.state.api_base_url, prefix + route,
      { method: 'POST', headers: { Authorization: 'Bearer ' + credential.credential }, body }));
  }
  async function dispatch(status) {
    const response = await interruption.wait(() => worker.request('/v1/dispatch-one'));
    demand(response.status === 200, 'DELIVERY_CONTROL_HTTP_FAILED'); summary(response.data, 'dispatch', status);
  }
  const state = id => read(`SELECT state FROM wa.message_outbox WHERE id=${quote(id)} AND recipient_identity_id=${actorSql}`);
  const attempts = id => Number(read(`SELECT count(*) FROM wa.message_attempt WHERE outbox_id=${quote(id)}`));
  async function message(text) {
    const event = { event: 'messages.upsert', instance, date_time: new Date().toISOString(), data: {
      key: { id: 'wa120_' + crypto.randomUUID().replaceAll('-', ''), fromMe: false, remoteJid: address },
      messageTimestamp: Math.floor(Date.now() / 1000), message: { conversation: text },
    } };
    const response = await request('/events', event); events++;
    demand(response.status === 202 && response.data.status === 'PERSISTED' && uuid(response.data.receiptId), 'DELIVERY_EVENT_NOT_DURABLE');
    const processed = await interruption.wait(() => worker.request('/v1/process-inbox'));
    demand(processed.status === 200, 'DELIVERY_PROCESS_FAILED'); summary(processed.data, 'process', 'PROCESSED');
    const raw = read(`SELECT json_build_object('id',o.id,'text',o.payload_json->>'text')
      FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id
      WHERE r.inbox_event_id=${quote(response.data.receiptId)} AND o.recipient_identity_id=${actorSql}`);
    demand(raw && !raw.includes('\n'), 'DELIVERY_SINGLE_RESPONSE_REQUIRED');
    const box = JSON.parse(raw);
    demand(exact(box, ['id', 'text']) && uuid(box.id) && typeof box.text === 'string' && box.text.length > 0, 'DELIVERY_CONTENT_INVALID');
    return box;
  }
  async function waitRetry(id, maximumMs) {
    const deadline = Date.now() + maximumMs;
    while (Date.now() < deadline) {
      const remaining = Number(read(`SELECT greatest(0,extract(epoch FROM not_before-clock_timestamp())*1000)::bigint
        FROM wa.message_outbox WHERE id=${quote(id)} AND recipient_identity_id=${actorSql} AND state='PENDING'`));
      demand(Number.isSafeInteger(remaining) && remaining >= 0 && remaining <= maximumMs, 'DELIVERY_BACKOFF_INVALID');
      if (!remaining) return;
      await interruption.wait(() => sleep(Math.min(remaining + 20, 1000)));
    }
    demand(false, 'DELIVERY_BACKOFF_NOT_COMPLETED');
  }
  async function safeFailure(box, expectedAttempts) {
    provider.observeConnections(['open', 'close']);
    await dispatch('NOT_SUBMITTED');
    demand(attempts(box.id) === expectedAttempts && provider.counts().accepted === 0,
      'DELIVERY_SAFE_ATTEMPT_NOT_VERIFIED');
    demand(Number(read(`SELECT count(*) FROM wa.message_attempt WHERE outbox_id=${quote(box.id)}
      AND state='NOT_SUBMITTED' AND external_message_id IS NULL AND result_request_hash IS NOT NULL
      AND result_json->>'state' IN ('PENDING','FAILED')`)) === expectedAttempts,
    'DELIVERY_SAFE_RESULT_NOT_DURABLE');
    if (expectedAttempts < 3) {
      const delay = Number(read(`SELECT extract(epoch FROM o.not_before-a.completed_at)*1000
        FROM wa.message_outbox o JOIN wa.message_attempt a ON a.outbox_id=o.id
        WHERE o.id=${quote(box.id)} AND o.state='PENDING' AND a.attempt_number=${expectedAttempts}`));
      const expectedMs = expectedAttempts === 1 ? 5000 : 15000;
      // Production reads the clock again when recording completion. Reject shortened
      // backoff while tolerating at most 500ms between those two timestamp writes.
      demand(Number.isFinite(delay) && delay >= expectedMs - 500 && delay <= expectedMs, 'DELIVERY_BACKOFF_DURATION_MISMATCH');
    }
  }

  const exhausted = await message('oi');
  await dispatch('IDLE');
  demand(attempts(exhausted.id) === 0 && state(exhausted.id) === 'PENDING' && provider.counts().requests === 0,
    'DELIVERY_UNKNOWN_CONNECTION_DID_NOT_SUSPEND');
  await connectionEvent(runtime, 'open');
  passed('unknown_connection_blocks_reservation_until_authenticated_open');

  await safeFailure(exhausted, 1);
  demand(state(exhausted.id) === 'PENDING', 'DELIVERY_RETRY_NOT_PENDING');
  await dispatch('IDLE');
  await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start());
  await dispatch('IDLE');
  await waitRetry(exhausted.id, 5500);
  await safeFailure(exhausted, 2);
  await dispatch('IDLE');
  await waitRetry(exhausted.id, 15500);
  await safeFailure(exhausted, 3);
  demand(state(exhausted.id) === 'FAILED', 'DELIVERY_MAX_ATTEMPTS_NOT_TERMINAL');
  await dispatch('IDLE');
  demand(Number(read(`SELECT count(DISTINCT submit_token_hash) FROM wa.message_attempt WHERE outbox_id=${quote(exhausted.id)}`)) === 3
    && Number(read(`SELECT count(DISTINCT lease_generation) FROM wa.message_attempt WHERE outbox_id=${quote(exhausted.id)}`)) === 3
    && attempts(exhausted.id) === 3 && provider.counts().requests === 0, 'DELIVERY_SAFE_RETRY_NOT_FENCED');
  passed('three_safe_attempts_zero_posts_backoff_restart_and_distinct_generations');

  const recovered = await message('Pessoa Sintética');
  await safeFailure(recovered, 1);
  await connectionEvent(runtime, 'close');
  await waitRetry(recovered.id, 5500);
  await dispatch('IDLE');
  demand(attempts(recovered.id) === 1, 'DELIVERY_CLOSED_CHANNEL_CONSUMED_ATTEMPT');
  await connectionEvent(runtime, 'open');
  provider.enqueue({ outboxId: recovered.id, text: recovered.text });
  await dispatch('SUBMITTED');
  demand(attempts(recovered.id) === 2 && state(recovered.id) === 'SUBMITTED'
    && provider.counts().accepted === 1 && provider.counts().requests === 1, 'DELIVERY_RECOVERY_NOT_EXACTLY_ONCE');
  await dispatch('IDLE');
  passed('reconnection_reauthorizes_same_outbox_and_submits_once');

  const uncertain = await message('ACEITO');
  provider.enqueue({ outboxId: uncertain.id, text: uncertain.text });
  provider.nextPostEffect('AMBIGUOUS_AFTER_EFFECT');
  await dispatch('UNKNOWN');
  await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start());
  await dispatch('IDLE');
  demand(state(uncertain.id) === 'UNKNOWN' && attempts(uncertain.id) === 1 && provider.counts().accepted === 2
    && provider.counts().requests === 2 && provider.counts().failures === 0, 'DELIVERY_UNKNOWN_RESUBMITTED');
  passed('provider_effect_before_400_stays_unknown_after_worker_restart_without_resend');

  const recovery = await runDeliveryRecovery(runtime, { uncertain, message, request, read, state, attempts });
  // events counts the six conversation inputs and three status callbacks;
  // authenticated CONNECTION observations are kept out of this existing counter.
  events += recovery.callbacks;
  demand(events === 9 && attempts(exhausted.id) + attempts(recovered.id) + attempts(uncertain.id) + recovery.attempts === 7
    && provider.counts().requests === 3 && provider.counts().accepted === 3 && provider.counts().pending === 0
    && provider.counts().failures === 0 && provider.connectionReadCount() === 21, 'DELIVERY_FINAL_TOTALS_NOT_EXACT');
  return { events, attempts: attempts(exhausted.id) + attempts(recovered.id) + attempts(uncertain.id) + recovery.attempts,
    providerSubmissions: provider.counts().accepted, connectionReads: provider.connectionReadCount(),
    syntheticFixtures: true, realWhatsApp: false, physicalDevices: false };
}
