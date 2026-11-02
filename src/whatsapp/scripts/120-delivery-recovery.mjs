import { isDeepStrictEqual } from 'node:util';
import { submitEvolutionText } from '../transport/evolution-text.mjs';
import { demand, exact, quote, token, uuid } from './070-worker-common.mjs';
import { connectionEvent } from './070-worker-scenarios.mjs';

const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const time = value => typeof value === 'string' && Number.isFinite(Date.parse(value));
const pageFields = ['jobType', 'processed', 'nextCursor', 'serverTime'];
const caseFields = ['caseId', 'outboxId', 'state', 'reasonCode', 'correlationId', 'createdAt', 'attemptState', 'hasExternalMessageId'];

/** Same-cohort TTL fixtures only; states, receipts and callbacks use the real APIs. */
export async function runDeliveryRecovery(runtime, { uncertain, message, request, read, state, attempts }) {
  const { actor, instance, credentials, worker, provider, interruption, passed, tools, apiKey } = runtime;
  demand(typeof apiKey === 'string' && credentials[2]?.principal === 'DELIVERY_WORKER'
    && credentials[3]?.principal === 'SYSTEM_JOB', 'DELIVERY_RECOVERY_CREDENTIALS_MISSING');
  const actorSql = quote(actor), instanceSql = quote(instance);
  const cohort = read(`SELECT test_cohort_id FROM wa.identity WHERE id=${actorSql}`);
  demand(uuid(cohort), 'DELIVERY_RECOVERY_COHORT_INVALID');
  const count = sql => {
    const value = read(sql);
    demand(/^\d+$/u.test(value) && Number.isSafeInteger(Number(value)), 'DELIVERY_RECOVERY_COUNT_INVALID');
    return Number(value);
  };
  const attempt = id => JSON.parse(read(`SELECT json_build_object('state',state,'external',external_message_id,
    'receiptHash',result_request_hash,'receipt',result_json) FROM wa.message_attempt
    WHERE outbox_id=${quote(id)} AND instance_id=${instanceSql}`));
  const original = attempt(uncertain.id);
  demand(exact(original, ['state', 'external', 'receiptHash', 'receipt']) && original.state === 'UNKNOWN'
    && original.external === null && /^[0-9a-f]{64}$/u.test(original.receiptHash)
    && exact(original.receipt, ['outboxId', 'state', 'serverTime']) && original.receipt.outboxId === uncertain.id
    && original.receipt.state === 'UNKNOWN' && time(original.receipt.serverTime), 'DELIVERY_ORIGINAL_UNKNOWN_NOT_DURABLE');
  const originalUnchanged = () => demand(isDeepStrictEqual(attempt(uncertain.id), original), 'DELIVERY_ORIGINAL_UNKNOWN_RECEIPT_CHANGED');

  const abandoned = await message('AJUDA');
  const claimed = await request('/outbox/claim', { batchLimit: 1, leaseSeconds: 60 }, credentials[2]);
  demand(claimed.status === 200 && exact(claimed.data, ['items', 'serverTime']) && time(claimed.data.serverTime)
    && Array.isArray(claimed.data.items) && claimed.data.items.length === 1, 'DELIVERY_LOST_ACK_CLAIM_FAILED');
  const lease = claimed.data.items[0];
  demand(exact(lease, ['outboxId', 'purpose', 'leaseToken', 'leaseExpiresAt', 'expiresAt'])
    && lease.outboxId === abandoned.id && lease.purpose === 'COMMAND_RESPONSE' && token(lease.leaseToken), 'DELIVERY_LOST_ACK_LEASE_INVALID');
  const authorized = await request('/outbox/' + abandoned.id + '/authorize-submit', { leaseToken: lease.leaseToken }, credentials[2]);
  const reservation = authorized.data;
  demand(authorized.status === 200 && exact(reservation, ['outboxId', 'attemptToken', 'submitBefore', 'recipient', 'content', 'serverTime'])
    && reservation.outboxId === abandoned.id && token(reservation.attemptToken) && time(reservation.submitBefore)
    && time(reservation.serverTime) && Date.parse(reservation.submitBefore) > Date.parse(reservation.serverTime)
    && Date.parse(reservation.submitBefore) - Date.parse(reservation.serverTime) <= 10_000, 'DELIVERY_LOST_ACK_AUTHORIZATION_INVALID');
  provider.enqueue({ outboxId: abandoned.id, text: abandoned.text });
  const result = await interruption.wait(() => submitEvolutionText({ baseUrl: provider.url, instance, apiKey, reservation }));
  demand(exact(result, ['attemptToken', 'outcome', 'externalMessageId']) && result.attemptToken === reservation.attemptToken
    && result.outcome === 'SUBMITTED' && /^[A-Za-z0-9_-]{1,160}$/u.test(result.externalMessageId), 'DELIVERY_LOST_ACK_PROVIDER_EFFECT_MISSING');
  // Keep the actual adapter result in memory. Deliberately do not ACK it yet.
  demand(state(abandoned.id) === 'LEASED' && attempts(abandoned.id) === 1
    && isDeepStrictEqual(attempt(abandoned.id), { state: 'SUBMITTING', external: null, receiptHash: null, receipt: null }),
  'DELIVERY_LOST_ACK_WAS_ACKNOWLEDGED');
  const pending = await message('AJUDA');
  demand(state(pending.id) === 'PENDING' && attempts(pending.id) === 0, 'DELIVERY_FRESH_PENDING_INVALID');

  // Wait for the real persisted 10s submit budget; never shorten submit_before.
  const waitUntil = Date.now() + 12_000;
  while (true) {
    const remaining = count(`SELECT greatest(0,extract(epoch FROM submit_before-clock_timestamp())*1000)::bigint
      FROM wa.message_attempt WHERE outbox_id=${quote(abandoned.id)} AND instance_id=${instanceSql} AND state='SUBMITTING'`);
    if (!remaining) break;
    demand(Date.now() < waitUntil && remaining <= 10_000, 'DELIVERY_SUBMIT_BUDGET_DID_NOT_ELAPSE');
    await interruption.wait(() => sleep(Math.min(remaining + 20, 1000)));
  }
  const ids = [uncertain.id, abandoned.id, pending.id].map(quote).join(',');
  // Controlled integration fixture: only expires_at changes, for these three
  // existing outboxes of this synthetic cohort. Preserve every state/receipt.
  demand(count(`WITH cutoff AS MATERIALIZED (SELECT clock_timestamp()-interval '1 millisecond' AS at), changed AS (
      UPDATE wa.message_outbox o SET expires_at=cutoff.at FROM cutoff,wa.identity person,wa.test_cohort cohort
      WHERE o.recipient_identity_id=person.id AND person.id=${actorSql} AND person.test_cohort_id=cohort.id
        AND cohort.id=${quote(cohort)} AND cohort.environment='wa070-local' AND cohort.synthetic=true
        AND o.id IN (${ids}) AND o.state IN ('UNKNOWN','LEASED','PENDING')
        AND o.created_at<cutoff.at AND o.not_before<cutoff.at RETURNING o.id)
    SELECT count(*) FROM changed`) === 3, 'DELIVERY_CONTROLLED_TTL_FIXTURE_REJECTED');
  demand(count(`SELECT count(*) FROM wa.message_outbox WHERE id IN (${ids}) AND recipient_identity_id=${actorSql}
    AND created_at<expires_at AND not_before<expires_at AND expires_at<clock_timestamp()`) === 3, 'DELIVERY_CONTROLLED_TTL_FIXTURE_INVALID');
  await connectionEvent(runtime, 'close');
  const before = provider.counts(), reads = provider.connectionReadCount();
  demand(isDeepStrictEqual(before, { requests: 3, accepted: 3, pending: 0, failures: 0 }), 'DELIVERY_PROVIDER_TOTAL_BEFORE_RECOVERY_INVALID');
  const noProviderTraffic = () => demand(isDeepStrictEqual(provider.counts(), before) && provider.connectionReadCount() === reads,
    'DELIVERY_RECONCILIATION_CONTACTED_PROVIDER');
  const cases = () => count(`SELECT count(*) FROM wa.support_case WHERE identity_id=${actorSql} AND purpose='DELIVERY'
    AND environment='wa070-local' AND instance_id=${instanceSql} AND delivery_outbox_id IN (${quote(uncertain.id)},${quote(abandoned.id)})
    AND state='OPEN' AND reason_code='DELIVERY_UNKNOWN_EXPIRED' AND correlation_id=delivery_outbox_id::text
    AND deduplication_key='delivery-unknown:'||delivery_outbox_id::text`);
  const health = await interruption.wait(() => worker.request('/v1/reconcile-delivery'));
  demand(health.status === 200 && exact(health.data, ['status', 'processed'])
    && health.data.status === 'PROCESSED' && health.data.processed === 3, 'DELIVERY_CLOSED_HEALTH_FAILED');
  demand(count(`SELECT count(*) FROM wa.message_outbox WHERE id IN (${ids}) AND recipient_identity_id=${actorSql}
    AND state='EXPIRED' AND private_payload_ciphertext IS NULL AND start_payload_ciphertext IS NULL`) === 3
    && attempts(pending.id) === 0 && cases() === 2
    && isDeepStrictEqual(attempt(abandoned.id), { state: 'UNKNOWN', external: null, receiptHash: null, receipt: null }),
  'DELIVERY_HEALTH_EXPIRATION_NOT_DURABLE');
  originalUnchanged(); noProviderTraffic();
  const page = JSON.parse(read(`SELECT json_build_object('windowKey',window_key,'body',response_json)
    FROM wa.attendance_job_page WHERE environment='wa070-local' AND instance_id=${instanceSql}
      AND job_type='HEALTH_RECONCILE' AND page_key='ROOT' ORDER BY created_at DESC LIMIT 1`));
  demand(exact(page, ['windowKey', 'body']) && exact(page.body, pageFields) && page.body.jobType === 'HEALTH_RECONCILE'
    && page.body.processed === 3 && page.body.nextCursor === null && time(page.body.serverTime), 'DELIVERY_HEALTH_PAGE_NOT_DURABLE');
  const replayedPage = await request('/jobs/tick', { jobType: 'HEALTH_RECONCILE', windowKey: page.windowKey, batchLimit: 100 }, credentials[3]);
  demand(replayedPage.status === 200 && isDeepStrictEqual(replayedPage.data, page.body) && cases() === 2,
    'DELIVERY_HEALTH_PAGE_REPLAY_CHANGED');
  passed('closed_channel_health_expires_unknown_abandoned_and_pending_without_provider_traffic');

  async function list(afterCaseId) {
    const response = await interruption.wait(() => tools.operate('list-delivery-cases', {
      allowedInstanceId: instance, limit: 1, ...(afterCaseId ? { afterCaseId } : {}),
    }));
    demand(exact(response, ['status', 'operation', 'environment', 'batchId', 'serverTime', 'cases', 'nextAfterCaseId'])
      && response.status === 'COMMITTED' && response.operation === 'list-delivery-cases' && response.environment === 'wa070-local'
      && response.batchId === cohort && time(response.serverTime) && Array.isArray(response.cases) && response.cases.length === 1
      && (response.nextAfterCaseId === null || uuid(response.nextAfterCaseId)), 'DELIVERY_CASE_PRIVATE_PAGE_INVALID');
    return response;
  }
  function caseMetadata(item, attemptState, hasExternalMessageId) {
    demand(exact(item, caseFields) && uuid(item.caseId) && [uncertain.id, abandoned.id].includes(item.outboxId)
      && item.state === 'OPEN' && item.reasonCode === 'DELIVERY_UNKNOWN_EXPIRED' && item.correlationId === item.outboxId
      && time(item.createdAt) && item.attemptState === attemptState && item.hasExternalMessageId === hasExternalMessageId,
    'DELIVERY_CASE_METADATA_NOT_CLOSED');
  }
  const first = await list(); caseMetadata(first.cases[0], 'UNKNOWN', false);
  demand(first.nextAfterCaseId === first.cases[0].caseId, 'DELIVERY_CASE_CURSOR_INVALID');
  const second = await list(first.nextAfterCaseId); caseMetadata(second.cases[0], 'UNKNOWN', false);
  demand(second.nextAfterCaseId === null && second.cases[0].caseId > first.cases[0].caseId
    && new Set([first.cases[0].outboxId, second.cases[0].outboxId]).size === 2, 'DELIVERY_CASE_PAGINATION_NOT_EXACT');
  const afterCases = await message('AJUDA');
  demand(state(afterCases.id) === 'PENDING' && attempts(afterCases.id) === 0
    && count(`SELECT count(*) FROM wa.conversation_state WHERE identity_id=${actorSql} AND stage='READY'`) === 1
    && count(`SELECT count(*) FROM wa.support_case WHERE identity_id=${actorSql} AND purpose='CONVERSATION'`) === 0,
  'DELIVERY_CASE_PAUSED_CONVERSATION');
  noProviderTraffic();
  passed('delivery_cases_are_deduplicated_private_paginated_metadata_without_pausing_conversation');

  const resultRoute = '/outbox/' + abandoned.id + '/result';
  const late = await request(resultRoute, result, credentials[2]);
  demand(late.status === 200 && exact(late.data, ['outboxId', 'state', 'serverTime'])
    && late.data.outboxId === abandoned.id && late.data.state === 'EXPIRED' && time(late.data.serverTime)
    && state(abandoned.id) === 'EXPIRED' && attempt(abandoned.id).state === 'SUBMITTED'
    && attempt(abandoned.id).external === result.externalMessageId, 'DELIVERY_LATE_RESULT_REOPENED_OUTBOX');
  const replay = await request(resultRoute, result, credentials[2]);
  const altered = await request(resultRoute, { attemptToken: result.attemptToken, outcome: 'UNKNOWN', failureCode: 'TIMEOUT' }, credentials[2]);
  demand(replay.status === 200 && isDeepStrictEqual(replay.data, late.data) && altered.status === 409 && cases() === 2,
    'DELIVERY_LATE_RESULT_RECEIPT_MUTABLE');
  originalUnchanged(); noProviderTraffic();
  passed('late_first_submitted_preserves_expired_outbox_and_immutable_result_receipts');

  let callbacks = 0;
  async function callback(status, occurredAt = new Date().toISOString()) {
    const response = await request('/events', { event: 'messages.update', instance, date_time: occurredAt,
      data: { keyId: result.externalMessageId, fromMe: true, status } });
    demand(response.status === 202 && exact(response.data, ['receiptId', 'status', 'serverTime'])
      && uuid(response.data.receiptId) && response.data.status === 'PERSISTED' && time(response.data.serverTime),
    'DELIVERY_CORRELATED_CALLBACK_NOT_DURABLE');
    callbacks++;
    return response.data;
  }
  const readCallback = await callback('READ');
  // A cached ROOT cannot process a newly arrived callback. Pick an unused,
  // canonical current/previous UTC minute using the database clock and scope.
  const windowDeadline = Date.now() + 59_000;
  let windowKey;
  while (!windowKey) {
    windowKey = read(`WITH candidates AS (SELECT date_trunc('minute',clock_timestamp())-n*interval '1 minute' AS window_start
        FROM generate_series(0,1) AS offsets(n)), windows AS (SELECT window_start,
        to_char(window_start AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS"Z"') AS window_key FROM candidates)
      SELECT window_key FROM windows WHERE window_start<=clock_timestamp() AND clock_timestamp()<window_start+interval '120 seconds'
        AND NOT EXISTS(SELECT 1 FROM wa.attendance_job_page p WHERE p.environment='wa070-local'
          AND p.instance_id=${instanceSql} AND p.job_type='HEALTH_RECONCILE' AND p.window_key=windows.window_key)
      ORDER BY window_start DESC LIMIT 1`);
    if (!windowKey) {
      demand(Date.now() < windowDeadline, 'DELIVERY_UNUSED_HEALTH_MINUTE_UNAVAILABLE');
      await interruption.wait(() => sleep(Math.min(1000, Math.max(1, windowDeadline - Date.now()))));
    }
  }
  demand(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00Z$/u.test(windowKey) && windowKey !== page.windowKey,
    'DELIVERY_HEALTH_MINUTE_REUSED');
  const readHealth = await request('/jobs/tick', { jobType: 'HEALTH_RECONCILE', windowKey, batchLimit: 100 }, credentials[3]);
  demand(readHealth.status === 200 && exact(readHealth.data, pageFields) && readHealth.data.jobType === 'HEALTH_RECONCILE'
    && readHealth.data.processed === 1 && readHealth.data.nextCursor === null && time(readHealth.data.serverTime)
    && attempt(abandoned.id).state === 'READ' && state(abandoned.id) === 'EXPIRED', 'DELIVERY_CORRELATED_READ_NOT_RECONCILED');
  const olderAt = new Date(Date.parse(readCallback.serverTime) - 1000).toISOString();
  const delivered = await callback('DELIVERY_ACK', olderAt), error = await callback('ERROR', olderAt);
  // claim is authenticated and reconciliation precedes its closed-channel gate;
  // it is used only to drain late facts, never to dispatch the pending response.
  const closedClaim = await request('/outbox/claim', { batchLimit: 1, leaseSeconds: 60 }, credentials[2]);
  demand(closedClaim.status === 200 && exact(closedClaim.data, ['items', 'serverTime'])
    && Array.isArray(closedClaim.data.items) && closedClaim.data.items.length === 0 && time(closedClaim.data.serverTime)
    && attempt(abandoned.id).state === 'READ' && state(abandoned.id) === 'EXPIRED'
    && count(`SELECT count(*) FROM wa.integration_inbox WHERE id IN (${[readCallback.receiptId, delivered.receiptId, error.receiptId].map(quote).join(',')})
      AND instance_id=${instanceSql} AND category='STATUS' AND state='COMPLETED'`) === 3,
  'DELIVERY_OLDER_CALLBACK_REGRESSED_OR_RESERVED');
  const finalReplay = await request(resultRoute, result, credentials[2]);
  demand(finalReplay.status === 200 && isDeepStrictEqual(finalReplay.data, late.data) && cases() === 2
    && state(uncertain.id) === 'EXPIRED' && attempts(uncertain.id) === 1
    && state(pending.id) === 'EXPIRED' && attempts(pending.id) === 0 && state(afterCases.id) === 'PENDING'
    && attempts(afterCases.id) === 0 && attempts(abandoned.id) === 1, 'DELIVERY_FINAL_RECONCILIATION_CHANGED_TERMINALS');
  const abandonedAfter = first.cases[0].outboxId === abandoned.id ? undefined : first.cases[0].caseId;
  const updated = await list(abandonedAfter); caseMetadata(updated.cases[0], 'READ', true);
  demand(updated.cases[0].outboxId === abandoned.id, 'DELIVERY_CASE_LATE_METADATA_MISSING');
  originalUnchanged(); noProviderTraffic();
  passed('correlated_late_read_and_older_statuses_preserve_expired_outbox_without_resend');
  return { callbacks, attempts: attempts(abandoned.id) };
}
