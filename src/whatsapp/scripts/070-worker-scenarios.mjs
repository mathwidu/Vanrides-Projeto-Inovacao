import crypto from 'node:crypto';
import { demand, exact, uuid, token, quote, privateWrite, localJson, summary } from './070-worker-common.mjs';

const prefix = '/integrations/whatsapp/v1';
const after = seconds => new Date(Date.now() + seconds * 1000).toISOString();

export async function provision({ input, tools, batch, actor, aliasId, instance, address, peers = [], credentials, attendance = false, interruption }) {
  // spawnSync cannot dispatch Node signal handlers; yield before every new mutation.
  const checkpoint = async () => { await new Promise(resolve => setImmediate(resolve)); interruption.assertActive(); };
  await checkpoint();
  tools.operate('create-test-cohort', { synthetic: true });
  const roles = ['CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER'];
  if (attendance) roles.push('SYSTEM_JOB');
  for (const principal of roles) {
    await checkpoint();
    const credential = tools.operate('provision-service-credential', { principal,
      allowedInstanceId: instance, expiresAt: after(3600) });
    demand(uuid(credential.credentialId) && token(credential.credential) && credential.allowedInstanceId === instance
      && credential.principal === principal, 'SERVICE_CREDENTIAL_INVALID');
    credentials.push(credential);
  }
  await checkpoint();
  demand(new Set(credentials.map(value => value.credential)).size === roles.length, 'SERVICE_CREDENTIAL_ROLES_NOT_DISTINCT');
  for (const person of [{ actor, aliasId, address }, ...peers]) {
    await checkpoint();
    tools.sql(`BEGIN; INSERT INTO wa.identity(id,test_cohort_id) VALUES (${quote(person.actor)},${quote(batch)});
      INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
      VALUES (${quote(person.aliasId)},${quote(person.actor)},'EVOLUTION',${quote(instance)},'PN',${quote(person.address)}); COMMIT;`);
    demand(tools.count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(person.actor)};`) === 0, 'PROFILE_CREATED_BEFORE_EVENT');
  }
  demand(input.operator.environment === 'wa070-local', 'SYNTHETIC_SCOPE_INVALID');
}

export async function connectionEvent({ input, instance, credentials, interruption }, state) {
  demand(['open', 'close'].includes(state), 'SYNTHETIC_CONNECTION_INVALID');
  const response = await interruption.wait(() => localJson(input.state.api_base_url, '/integrations/whatsapp/v1/events', {
    method: 'POST', headers: { Authorization: 'Bearer ' + credentials[0].credential },
    body: { event: 'connection.update', instance, date_time: new Date().toISOString(), data: { state } },
  }));
  demand(response.status === 202 && exact(response.data, ['receiptId', 'status', 'serverTime'])
    && response.data.status === 'PERSISTED' && uuid(response.data.receiptId), 'SYNTHETIC_CONNECTION_NOT_DURABLE');
}

export async function runScenarios({ input, tools, actor, instance, address, credentials, worker, provider, passed, interruption }) {
  const [channel, conversation] = credentials;
  async function backendRequest(route, body, credential = channel) {
    const response = await interruption.wait(() => localJson(input.state.api_base_url, prefix + route,
      { method: 'POST', body, headers: { Authorization: 'Bearer ' + credential.credential } }));
    privateWrite(tools.next('backend-http-result.json'), JSON.stringify(response) + '\n');
    return response;
  }
  async function control(route, kind, status) {
    const response = await interruption.wait(() => worker.request(route));
    demand(response.status === 200, 'CONTROL_HTTP_FAILED'); summary(response.data, kind, status);
    return response.data;
  }
  const event = text => ({ event: 'messages.upsert', instance, date_time: new Date().toISOString(),
    data: { key: { id: 'wa070_message_' + crypto.randomUUID().replaceAll('-', ''), fromMe: false, remoteJid: address },
      messageTimestamp: Math.floor(Date.now() / 1000), message: { conversation: text } } });
  function receipt(response, status, id) {
    demand(response.status === 202 && exact(response.data, ['receiptId', 'status', 'serverTime'])
      && uuid(response.data.receiptId) && response.data.status === status
      && (!id || response.data.receiptId === id), 'EVENT_RECEIPT_INVALID');
    return response.data.receiptId;
  }
  const counts = () => JSON.stringify(JSON.parse(tools.sql(`SELECT json_build_object(
    'inbox',(SELECT count(*) FROM wa.integration_inbox WHERE instance_id=${quote(instance)}),
    'commands',(SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)}),
    'outbox',(SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)}),
    'support',(SELECT count(*) FROM wa.support_case WHERE identity_id=${quote(actor)}),
    'attempts',(SELECT count(*) FROM wa.message_attempt WHERE instance_id=${quote(instance)}));`)));
  for (const route of ['/v1/process-inbox', '/v1/dispatch-one']) {
    const denied = await interruption.wait(() => worker.request(route, false));
    demand(denied.status === 401 && exact(denied.data, ['status']) && denied.data.status === 'UNAUTHORIZED', 'CONTROL_AUTH_NOT_ENFORCED');
  }
  const forbidden = await backendRequest('/events', event('oi'), conversation);
  demand(forbidden.status === 403 && counts() === '{"inbox":1,"commands":0,"outbox":0,"support":0,"attempts":0}', 'CHANNEL_AUTH_NOT_ENFORCED');
  passed('real_worker_control_and_channel_auth_denied_without_effects');

  const steps = [
    ['oi', 'START_ONBOARDING', 'ASK_NAME'],
    ['Pessoa Sintética WA', 'SET_PROFILE_NAME', 'ASK_NOTICE'],
    ['ACEITO', 'ACCEPT_NOTICE', 'READY'],
    ['xyzzy sintético', 'RESPOND_TO_MESSAGE', 'READY'],
    ['AJUDA', 'RESPOND_TO_MESSAGE', 'READY'],
    ['FALAR\u00a0COM SUPORTE', 'REQUEST_SUPPORT', 'PAUSED'],
  ];
  let lastBody, lastId;
  for (let i = 0; i < steps.length; i++) {
    const [text, command, stage] = steps[i];
    const body = event(text), id = receipt(await backendRequest('/events', body), 'PERSISTED');
    await control('/v1/process-inbox', 'process', 'PROCESSED');
    demand(tools.count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)}
      AND inbox_event_id=${quote(id)} AND command_type=${quote(command)};`) === 1
      && tools.count(`SELECT count(*) FROM wa.integration_inbox WHERE id=${quote(id)} AND state='COMPLETED';`) === 1
      && tools.count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id=${quote(actor)};`) === i + 1
      && tools.count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)} AND state='PENDING';`) === i + 1
      && tools.sql(`SELECT stage FROM wa.conversation_state WHERE identity_id=${quote(actor)};`) === stage, 'REAL_CONTEXT_COMMAND_EFFECT_INVALID');
    if (i === 1) demand(tools.count(`SELECT count(*) FROM wa.profile p JOIN wa.conversation_state c ON c.identity_id=p.identity_id
      WHERE p.identity_id=${quote(actor)} AND p.display_name=${quote(text)} AND p.version=2 AND c.stage='ASK_NOTICE'
      AND c.question_ref IS NOT NULL AND c.question_expires_at>CURRENT_TIMESTAMP
      AND c.context_json->>'noticeVersion'='wa-mvp-v1' AND c.context_json ? 'noticeEmittedAt';`) === 1, 'REAL_NOTICE_CONTEXT_NOT_ISSUED');
    if (i === 2) demand(tools.count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)}
      AND notice_version='wa-mvp-v1' AND notice_accepted_at IS NOT NULL AND status='ACTIVE' AND version=3;`) === 1, 'CONSENT_NOT_DURABLE');
    if (i === 3 || i === 4) {
      const response = i === 3
        ? 'Não consegui identificar o que você deseja. Escreva AJUDA para ver a próxima etapa ou FALAR COM SUPORTE para atendimento.'
        : 'Seu cadastro está preparado. As demais funções serão liberadas nas próximas etapas do teste. Para atendimento, escreva FALAR COM SUPORTE.';
      demand(tools.count(`SELECT count(*) FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id
        WHERE r.inbox_event_id=${quote(id)} AND o.payload_json->>'text'=${quote(response)};`) === 1, 'FIXED_ORIENTATION_NOT_GENERATED');
    }
    if (i === 5) demand(tools.count(`SELECT count(*) FROM wa.support_case s JOIN wa.conversation_state c ON c.identity_id=s.identity_id
      WHERE s.identity_id=${quote(actor)} AND s.state='OPEN' AND c.stage='PAUSED' AND c.last_event_id=${quote(id)}
      AND c.context_json->>'supportCaseId'=s.id::text AND c.question_ref IS NULL;`) === 1, 'SUPPORT_CASE_PAUSE_NOT_ATOMIC');
    const before = counts();
    receipt(await backendRequest('/events', body), 'DUPLICATE', id);
    await control('/v1/process-inbox', 'process', 'IDLE');
    demand(counts() === before, 'EVENT_REPLAY_DUPLICATED_EFFECT');
    passed(['human_greeting_starts_onboarding', 'human_name_emits_real_notice_context', 'explicit_consent_uses_real_context',
      'unknown_message_generates_fixed_orientation', 'help_generates_fixed_orientation', 'nbsp_support_creates_case_pauses_and_queues_reply'][i]);
    lastBody = body; lastId = id;
  }
  demand(tools.count(`SELECT count(*) FROM wa.support_case WHERE identity_id=${quote(actor)} AND state='OPEN';`) === 1
    && tools.count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(actor)} AND version=3;`) === 1, 'SUPPORT_OR_INFORMATIONAL_EFFECT_INVALID');
  const pending = JSON.parse(tools.sql(`SELECT COALESCE(json_agg(json_build_object('outboxId',id,'text',text) ORDER BY created_at,id),'[]'::json)
    FROM (SELECT id,payload_json->>'text' AS text,created_at FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)} AND state='PENDING') o;`));
  demand(Array.isArray(pending) && pending.length === 6 && pending.every(item => exact(item, ['outboxId', 'text'])
    && uuid(item.outboxId) && typeof item.text === 'string' && item.text.length > 0 && item.text.length <= 2000), 'OUTBOX_FIXED_CONTENT_INVALID');
  for (const item of pending) provider.enqueue(item);
  for (let i = 0; i < pending.length; i++) {
    await control('/v1/dispatch-one', 'dispatch', 'SUBMITTED');
    demand(exact(provider.counts(), ['requests', 'accepted', 'pending', 'failures'])
      && provider.counts().requests === i + 1 && provider.counts().accepted === i + 1
      && provider.counts().failures === 0, 'PROVIDER_SINGLE_SUBMISSION_INVALID');
  }
  await control('/v1/dispatch-one', 'dispatch', 'IDLE');
  demand(tools.count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=${quote(actor)} AND state='SUBMITTED';`) === 6
    && tools.count(`SELECT count(*) FROM wa.message_attempt WHERE instance_id=${quote(instance)} AND state='SUBMITTED';`) === 6
    && provider.counts().pending === 0 && provider.counts().requests === 6, 'OUTBOX_RESULTS_NOT_EXACTLY_ONCE');
  passed('six_control_dispatches_six_fake_provider_submissions_six_durable_receipts');

  const before = counts();
  receipt(await backendRequest('/events', lastBody), 'DUPLICATE', lastId);
  await interruption.wait(() => worker.stop()); await interruption.wait(() => worker.start());
  await control('/v1/process-inbox', 'process', 'IDLE');
  await control('/v1/dispatch-one', 'dispatch', 'IDLE');
  demand(counts() === before && provider.counts().requests === 6, 'WORKER_RESTART_REPEATED_EFFECT');
  passed('event_replay_and_real_worker_restart_remain_idle_without_resend');
  return { uniqueEvents: 6, duplicateEvents: 7, commandReceipts: 6, outbox: 6, supportCases: 1,
    providerRequests: 6, submittedAttempts: 6, workerStarts: 2, modelCalls: 0 };
}
