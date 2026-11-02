import crypto from 'node:crypto';
import { demand, exact, uuid, token, quote, privateWrite, privateBytes, properties, localJson, summary } from './070-worker-common.mjs';
import { extractConfirmationMarker, extractPassengerInviteCode, successfulReceipt } from './080-driver-evidence.mjs';

const prefix = '/integrations/whatsapp/v1';
const marker = text => {
  const result = extractConfirmationMarker(text);
  demand(result !== null, 'DRIVER_REVIEW_MARKER_MISSING_OR_AMBIGUOUS');
  return result;
};

/** All messages, locations and aliases are synthetic. Private values never enter the receipt. */
export async function runDriverScenarios({ input, tools, actor, instance, address, credentials, worker, provider, passed, interruption }) {
  const [channel, conversation] = credentials;
  const config = properties(privateBytes(input.configFile));
  const hashKey = Buffer.from(config.get('vanrides.wa.hash-key') ?? '', 'base64url');
  demand(hashKey.length === 32, 'LOCAL_HASH_KEY_INVALID');
  config.clear();
  let uniqueEvents = 0, duplicates = 0, submissions = 0, workerStarts = 1, lastEvent, lastReceipt;
  let privateInviteVerified = false;

  async function request(route, body, credential = channel) {
    const response = await interruption.wait(() => localJson(input.state.api_base_url, prefix + route,
      { method: 'POST', body, headers: { Authorization: 'Bearer ' + credential.credential } }));
    privateWrite(tools.next('driver-http-result.json'), JSON.stringify(response) + '\n');
    return response;
  }
  async function control(route, kind, expected) {
    const response = await interruption.wait(() => worker.request(route));
    privateWrite(tools.next('driver-control-result.json'), JSON.stringify({ route, kind, expected, response }) + '\n');
    demand(response.status === 200, 'DRIVER_CONTROL_HTTP_FAILED'); summary(response.data, kind, expected);
  }
  const event = value => ({ event: 'messages.upsert', instance, date_time: new Date().toISOString(),
    data: { key: { id: 'wa080_message_' + crypto.randomUUID().replaceAll('-', ''), fromMe: false, remoteJid: address },
      messageTimestamp: Math.floor(Date.now() / 1000), message: typeof value === 'string'
        ? { conversation: value } : { locationMessage: { degreesLatitude: 0, degreesLongitude: 0 } } } });
  const routineCount = () => tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)};`);
  const record = receipt => JSON.parse(tools.sql(`SELECT json_build_object(
    'command',r.command_type,'result',r.result_json,'outboxId',o.id,'payload',o.payload_json,
    'private',o.private_invite_id IS NOT NULL,'hasCipher',o.private_payload_ciphertext IS NOT NULL,
    'inviteHash',i.code_hash) FROM wa.command_receipt r JOIN wa.message_outbox o ON o.command_id=r.command_id
    LEFT JOIN wa.driver_invite i ON i.id=o.private_invite_id
    WHERE r.inbox_event_id=${quote(receipt)} AND r.identity_id=${quote(actor)};`));

  async function send(value, expectedCommand, expectedRejection = null) {
    demand(expectedRejection === null || expectedRejection === 'VERSION_CONFLICT', 'DRIVER_SMOKE_EXPECTATION_INVALID');
    const body = event(value), response = await request('/events', body);
    demand(response.status === 202 && exact(response.data, ['receiptId', 'status', 'serverTime'])
      && response.data.status === 'PERSISTED' && uuid(response.data.receiptId), 'DRIVER_EVENT_NOT_DURABLE');
    lastEvent = body; lastReceipt = response.data.receiptId; uniqueEvents++;
    await control('/v1/process-inbox', 'process', 'PROCESSED');
    const item = record(lastReceipt);
    const expectedResult = expectedRejection === null ? successfulReceipt(item.result)
      : exact(item.result, ['errorCode']) && item.result.errorCode === expectedRejection;
    demand(item.command === expectedCommand && uuid(item.outboxId) && expectedResult,
      'DRIVER_COMMAND_OR_OUTBOX_MISMATCH');
    if (item.private) {
      demand(expectedCommand === 'CREATE_INVITE' && item.hasCipher && /^[0-9a-f]{64}$/u.test(item.inviteHash),
        'INVITE_PRIVATE_DELIVERY_NOT_PREPARED');
      const stored = tools.sql(`SELECT json_build_object('outbox',o.payload_json,'receipt',to_jsonb(r),
        'context',c.context_json,'invite',to_jsonb(i)) FROM wa.message_outbox o
        JOIN wa.command_receipt r ON r.command_id=o.command_id
        JOIN wa.conversation_state c ON c.identity_id=r.identity_id
        JOIN wa.driver_invite i ON i.id=o.private_invite_id WHERE o.id=${quote(item.outboxId)};`);
      provider.enqueue({ outboxId: item.outboxId, matches: text => {
        const raw = extractPassengerInviteCode(text);
        if (raw === null) return false;
        const hash = crypto.createHmac('sha256', hashKey).update('passenger-invite\0' + raw).digest('hex');
        privateInviteVerified = hash === item.inviteHash && !stored.includes(raw);
        return privateInviteVerified;
      } });
    } else {
      demand(typeof item.payload?.text === 'string' && !item.hasCipher, 'DRIVER_RESPONSE_MISSING');
      provider.enqueue({ outboxId: item.outboxId, text: item.payload.text });
    }
    await control('/v1/dispatch-one', 'dispatch', 'SUBMITTED'); submissions++;
    demand(provider.counts().accepted === submissions && provider.counts().failures === 0
      && provider.counts().pending === 0, 'DRIVER_RESPONSE_NOT_SUBMITTED_ONCE');
    if (item.private) demand(privateInviteVerified && tools.count(`SELECT count(*) FROM wa.message_outbox
      WHERE id=${quote(item.outboxId)} AND state='SUBMITTED' AND private_payload_ciphertext IS NULL;`) === 1,
    'INVITE_CIPHERTEXT_NOT_PURGED_AFTER_SUBMISSION');
    return item.payload?.text ?? '';
  }
  const advance = text => send(text, 'ADVANCE_DRIVER_SETUP');
  async function confirm(text, command) { return send('CONFIRMAR ' + marker(text), command); }
  async function replayLast() {
    const response = await request('/events', lastEvent); duplicates++;
    demand(response.status === 202 && response.data.status === 'DUPLICATE' && response.data.receiptId === lastReceipt,
      'DRIVER_REPLAY_NOT_DEDUPLICATED');
    await control('/v1/process-inbox', 'process', 'IDLE');
    await control('/v1/dispatch-one', 'dispatch', 'IDLE');
  }

  try {
    const rejected = await request('/events', event('ROTINAS'), conversation);
    demand(rejected.status === 403, 'DRIVER_CHANNEL_PRINCIPAL_NOT_ENFORCED');
    await send('oi', 'START_ONBOARDING');
    await new Promise(resolve => setImmediate(resolve)); interruption.assertActive();
    const bootstrap = tools.operate('driver-bootstrap', { identityId: actor,
      expiresAt: new Date(Date.now() + 1800000).toISOString() });
    await new Promise(resolve => setImmediate(resolve)); interruption.assertActive();
    demand(token(bootstrap.driverInviteCode), 'DRIVER_BOOTSTRAP_NOT_ISSUED');
    await send(bootstrap.driverInviteCode, 'CLAIM_DRIVER_INVITE');
    delete bootstrap.driverInviteCode;
    await send('Alex Exemplo', 'SET_PROFILE_NAME');
    await send('ACEITO', 'ACCEPT_NOTICE');
    demand(tools.count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)} AND role='DRIVER' AND status='ACTIVE';`) === 1
      && tools.count(`SELECT count(*) FROM wa.driver_device WHERE driver_id=${quote(actor)};`) === 0, 'UNPAIRED_DRIVER_NOT_PREPARED');
    passed('driver_bootstrap_name_notice_without_company_or_paired_device');

    await advance('ROTINAS'); await advance('CRIAR');
    for (const text of ['Ida Sintética', 'IDA', 'SEGUNDA A SEXTA', '06:00', 'MANTER']) await advance(text);
    let review = await advance({ staticPin: true });
    const oldMarker = marker(review);
    demand(routineCount() === 0, 'ROUTINE_CREATED_BEFORE_CONFIRMATION');
    review = await advance('SIM');
    demand(marker(review) !== oldMarker && routineCount() === 0, 'PLAIN_YES_APPLIED_ROUTINE');
    review = await advance('CONFIRMAR ' + oldMarker);
    demand(routineCount() === 0, 'OLD_MARKER_APPLIED_NEW_REVISION');
    await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start()); workerStarts++;
    await confirm(review, 'CONFIGURE_ROUTINE');
    demand(routineCount() === 1 && tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)}
      AND direction='OUTBOUND' AND state='ACTIVE' AND local_time='06:00'
      AND timezone='America/Sao_Paulo' AND destination_latitude=0 AND destination_longitude=0;`) === 1,
    'CONFIRMED_ROUTINE_NOT_DURABLE');
    passed('routine_pin_review_old_marker_rejection_and_worker_restart');
    await replayLast();

    review = await advance('PAUSAR 1'); await confirm(review, 'SET_ROUTINE_STATE');
    demand(tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='PAUSED';`) === 1,
      'ROUTINE_NOT_PAUSED');
    review = await advance('RETOMAR 1'); await confirm(review, 'SET_ROUTINE_STATE');
    demand(tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='ACTIVE';`) === 1,
      'ROUTINE_NOT_RESUMED');
    passed('routine_pause_and_resume_require_bound_confirmation');

    review = await advance('PAUSAR 1');
    const conflictedMarker = marker(review);
    tools.sql(`UPDATE wa.routine SET version=version+1 WHERE driver_id=${quote(actor)};`);
    await send('CONFIRMAR ' + conflictedMarker, 'SET_ROUTINE_STATE', 'VERSION_CONFLICT');
    demand(tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='ACTIVE';`) === 1
      && tools.count(`SELECT count(*) FROM wa.integration_inbox WHERE id=${quote(lastReceipt)} AND state='COMPLETED';`) === 1,
      'TARGET_CONFLICT_MUTATED_ROUTINE_OR_BLOCKED_INBOX');
    await advance('ROTINAS');
    review = await advance('PAUSAR 1');
    demand(marker(review) !== conflictedMarker, 'TARGET_RECOVERY_REUSED_OLD_MARKER');
    review = await advance('CONFIRMAR ' + conflictedMarker);
    demand(tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='ACTIVE';`) === 1,
      'TARGET_RECOVERY_ACCEPTED_OLD_MARKER');
    await confirm(review, 'SET_ROUTINE_STATE');
    demand(tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='PAUSED';`) === 1,
      'TARGET_RECOVERY_DID_NOT_APPLY_NEW_PROPOSAL');
    review = await advance('RETOMAR 1'); await confirm(review, 'SET_ROUTINE_STATE');
    passed('target_version_conflict_is_durable_and_next_message_recovers_without_old_marker');

    const day = new Date(Date.now() + 86400000);
    while (day.getUTCDay() !== 1) day.setUTCDate(day.getUTCDate() + 1);
    const date = day.toISOString().slice(0, 10);
    await advance('CANCELAR 1'); review = await advance(date); await confirm(review, 'CANCEL_OCCURRENCE');
    demand(tools.count(`SELECT count(*) FROM wa.routine_occurrence c JOIN wa.routine r ON r.id=c.routine_id
      WHERE r.driver_id=${quote(actor)} AND c.local_date=${quote(date)} AND c.state='CANCELED';`) === 1
      && tools.count(`SELECT count(*) FROM wa.routine WHERE driver_id=${quote(actor)} AND state='ACTIVE';`) === 1,
    'SINGLE_DATE_CANCEL_CHANGED_ROUTINE');
    passed('cancel_one_future_date_preserves_recurring_routine');

    await advance('CONVIDAR'); review = await advance('PADRÃO'); await confirm(review, 'CREATE_INVITE');
    demand(tools.count(`SELECT count(*) FROM wa.driver_invite WHERE driver_id=${quote(actor)} AND max_uses=1
      AND use_count=0 AND revoked_at IS NULL;`) === 1 && privateInviteVerified, 'PRIVATE_INVITE_NOT_VERIFIED');
    await replayLast();
    review = await advance('REVOGAR 1'); await confirm(review, 'REVOKE_INVITE');
    demand(tools.count(`SELECT count(*) FROM wa.driver_invite WHERE driver_id=${quote(actor)} AND revoked_at IS NOT NULL;`) === 1,
      'DRIVER_INVITE_NOT_REVOKED');
    await send('RESUMO', 'GET_SUMMARY');
    passed('invite_hash_ciphertext_private_submit_purge_revoke_and_summary');

    await replayLast(); await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start()); workerStarts++;
    await control('/v1/process-inbox', 'process', 'IDLE'); await control('/v1/dispatch-one', 'dispatch', 'IDLE');
    demand(tools.count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)};`) === uniqueEvents
      && tools.count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)} AND result_json->>'errorCode'='VERSION_CONFLICT';`) === 1
      && tools.count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)} AND state='SUBMITTED';`) === uniqueEvents
      && provider.counts().requests === submissions && submissions === uniqueEvents, 'DRIVER_RESTART_REPEATED_EFFECT');
    passed('driver_replays_and_final_restart_do_not_repeat_mutations_or_messages');
    return { uniqueEvents, duplicateEvents: duplicates, commandReceipts: uniqueEvents, outbox: uniqueEvents,
      providerRequests: submissions, submittedAttempts: submissions, workerStarts, modelCalls: 0,
      controlledBusinessRejections: 1,
      privateInviteHashVerified: privateInviteVerified, rawInviteAbsentFromDurablePlaintext: privateInviteVerified };
  } finally { hashKey.fill(0); }
}
