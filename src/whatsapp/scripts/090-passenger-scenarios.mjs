import { demand, quote } from './070-worker-common.mjs';
import { extractPassengerInviteCode } from './080-driver-evidence.mjs';
import { passengerConversation } from './090-passenger-conversation.mjs';

const setup = 'ADVANCE_PASSENGER_SETUP';
const driverSetup = 'ADVANCE_DRIVER_SETUP';
const pin = longitude => ({ locationMessage: { degreesLatitude: 0, degreesLongitude: longitude } });

function mapPoint(text) {
  const link = text?.match(/https:\/\/www\.google\.com\/maps\/search\/\?[^\s]+/u)?.[0];
  if (!link) return null;
  const parts = new URL(link).searchParams.get('query')?.split(',');
  return parts?.length === 2 ? parts.map(Number) : null;
}

/** Register through human messages, then verify the server's durable state and the provider's actual recipients. */
export async function runPassengerScenarios(runtime) {
  const { tools, passed } = runtime;
  const chat = passengerConversation(runtime);
  const { driver, passenger, otherDriver, send, confirm } = chat;
  const wherePair = `driver_id=${quote(driver.actor)} AND passenger_id=${quote(passenger.actor)}`;
  const links = state => tools.count(`SELECT count(*) FROM wa.driver_passenger_link WHERE ${wherePair} AND state=${quote(state)};`);
  const points = state => tools.count(`SELECT count(*) FROM wa.pickup_point WHERE ${wherePair} AND state=${quote(state)};`);
  const memberships = state => tools.count(`SELECT count(*) FROM wa.routine_member WHERE ${wherePair} AND state=${quote(state)};`);
  const uses = () => tools.count(`SELECT coalesce(sum(use_count),0) FROM wa.driver_invite WHERE driver_id=${quote(driver.actor)};`);
  const p = message => send(passenger, message, setup);
  const review = word => send(driver, word, setup);
  const advanceDriver = message => send(driver, message, driverSetup);

  try {
    await chat.bootstrap(driver, 'Alex Exemplo');
    await chat.bootstrap(otherDriver, 'Nilo Exemplo');
    for (const [label, direction, time] of [['Ida escolar', 'IDA', '06:00'], ['Volta escolar', 'VOLTA', '18:00']]) {
      for (const msg of ['ROTINAS', 'CRIAR', label, direction, 'SEGUNDA A SEXTA', time, 'MANTER']) await advanceDriver(msg);
      await confirm(driver, await advanceDriver(pin(0)), 'CONFIGURE_ROUTINE');
    }
    await review('PONTO COMUM'); await review('Ponto escolar compartilhado');
    await confirm(driver, await review(pin(0.001)), setup);
    demand(tools.count(`SELECT count(*) FROM wa.common_pickup_point WHERE driver_id=${quote(driver.actor)}
      AND state='PUBLISHED' AND longitude=0.001;`) === 1, 'PASSENGER_COMMON_POINT_NOT_PUBLISHED');
    await advanceDriver('CONVIDAR');
    const issued = await confirm(driver, await advanceDriver('PADRÃO'), 'CREATE_INVITE');
    let invite = extractPassengerInviteCode(issued.reply);
    demand(invite !== null && uses() === 0, 'PASSENGER_INVITATION_NOT_ISSUED');
    const opened = await p('PASSAGEIRO ' + invite); invite = null;
    demand(opened.reply.includes('Alex Exemplo') && uses() === 0 && links('REQUESTED') === 0, 'PASSENGER_DRIVER_PREVIEW_REQUIRED');
    passed('invitation_from_driver_conversation_opens_draft_without_reserving_a_use');

    for (const msg of ['SIM', 'Lia Exemplo', 'ACEITO', 'SIM', 'NÃO', '1', '1,3,5']) await p(msg);
    const live = await send(passenger, { liveLocationMessage: { degreesLatitude: 0, degreesLongitude: 0 } }, 'RESPOND_TO_MESSAGE');
    demand(live.reply?.includes('estático'), 'PASSENGER_FIXED_PIN_GUIDANCE_MISSING');
    await p('Não sei o endereço exato');
    demand(points('PROPOSED') === 0 && tools.count(`SELECT count(*) FROM wa.conversation_state
      WHERE identity_id=${quote(passenger.actor)} AND context_json->'passengerDraft'->>'stage'='POINT'
      AND NOT ((context_json->'passengerDraft') ?| ARRAY['sourceEvent','selectionEvent','commonId']);`) === 1,
    'PASSENGER_LIVE_OR_AMBIGUOUS_INPUT_BECAME_PICKUP');
    await p(pin(0));
    const summary = await p('PULAR');
    await chat.restart();
    const sent = await confirm(passenger, summary, 'REQUEST_LINK', { notify: driver });
    demand(uses() === 1 && links('REQUESTED') === 1 && links('APPROVED') === 0 && points('PROPOSED') === 1
      && memberships('ACTIVE') === 0 && mapPoint(sent.peer)?.every(value => value === 0)
      && sent.peer.includes('Lia Exemplo') && sent.peer.includes('Ida escolar'), 'PASSENGER_REVIEW_SCOPE_INVALID');
    demand(tools.count(`SELECT count(*) FROM wa.profile WHERE identity_id=${quote(passenger.actor)}
      AND notice_version='wa-mvp-v1' AND preferences_json @> '{"APPROACH_MESSAGES":true,"ATTENDANCE_REMINDERS":false}'::jsonb;`) === 1
      && tools.count(`SELECT count(*) FROM wa.pickup_point p JOIN wa.integration_inbox e
        ON e.id=p.source_inbox_event_id AND e.identity_id=p.passenger_id
        WHERE p.passenger_id=${quote(passenger.actor)} AND e.normalized_kind='PIN' AND e.instance_id=${quote(runtime.instance)};`) === 1,
    'PASSENGER_PREFERENCES_OR_PIN_PROVENANCE_INVALID');
    await chat.replay();
    demand(uses() === 1 && links('REQUESTED') === 1, 'PASSENGER_REPLAY_CONSUMED_INVITATION');
    passed('fixed_pin_optional_reference_and_explicit_days_requested_once_after_restart');

    const unrelated = await send(otherDriver, 'PASSAGEIROS', setup);
    demand(!unrelated.reply.includes('Lia Exemplo') && !mapPoint(unrelated.reply) && links('REQUESTED') === 1,
      'PASSENGER_INFORMATION_DISCLOSED_TO_OTHER_DRIVER');
    await review('PASSAGEIROS');
    const fix = await confirm(driver, await review('RETIFICAR 1'), setup, { notify: passenger });
    demand(fix.peer && !mapPoint(fix.peer) && links('REQUESTED') === 1 && points('REJECTED') === 1 && points('PROPOSED') === 0,
      'PASSENGER_RECTIFICATION_NOT_DELIVERED');
    await p('CORRIGIR PONTO'); await p(pin(0));
    await confirm(passenger, await p('Entrada de teste'), 'PROPOSE_PICKUP', { notify: driver });
    demand(uses() === 1 && links('REQUESTED') === 1 && points('PROPOSED') === 1, 'PASSENGER_RECTIFICATION_USED_ANOTHER_INVITE');
    passed('request_for_a_corrected_point_reaches_the_passenger_and_keeps_the_same_invitation');
    await review('PASSAGEIROS');
    const approved = await confirm(driver, await review('APROVAR 1'), 'APPROVE_LINK', { notify: passenger });
    demand(approved.peer?.includes('Ida escolar') && !mapPoint(approved.peer) && links('APPROVED') === 1
      && points('APPROVED') === 1 && memberships('ACTIVE') === 1,
      'PASSENGER_APPROVAL_NOT_ATOMIC');
    demand(tools.count(`SELECT count(*) FROM wa.routine_member m JOIN wa.routine r ON r.id=m.routine_id
      JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.driver_id=m.driver_id AND p.passenger_id=m.passenger_id
      WHERE m.driver_id=${quote(driver.actor)} AND m.passenger_id=${quote(passenger.actor)} AND m.state='ACTIVE'
      AND r.direction='OUTBOUND' AND r.stop_order_pending AND p.state='APPROVED' AND m.weekdays=ARRAY[1,3,5]::smallint[];`) === 1,
      'PASSENGER_DAYS_DIRECTION_OR_STOP_ORDER_INVALID');
    await chat.replay();
    passed('only_the_owning_driver_sees_request_and_both_receive_the_approved_result');

    const choices = await p('CORRIGIR PONTO');
    demand(choices.reply.includes('Ponto escolar compartilhado'), 'PASSENGER_COMMON_POINT_NOT_OFFERED');
    await p('PONTO 1');
    const correction = await confirm(passenger, await p('Portão de teste'), 'PROPOSE_PICKUP', { notify: driver });
    const proposed = mapPoint(correction.peer);
    demand(proposed?.[0] === 0 && proposed?.[1] === 0.001 && points('APPROVED') === 1 && points('PROPOSED') === 1,
      'PASSENGER_CORRECTION_REPLACED_APPROVED_POINT_TOO_EARLY');
    await review('PASSAGEIROS');
    await confirm(driver, await review('APROVAR 1'), 'APPROVE_PICKUP', { notify: passenger });
    demand(points('RETIRED') === 1 && points('APPROVED') === 1 && memberships('ACTIVE') === 1 && uses() === 1
      && tools.count(`SELECT count(*) FROM wa.routine_member m JOIN wa.pickup_point p ON p.id=m.pickup_point_id
        WHERE m.driver_id=${quote(driver.actor)} AND m.passenger_id=${quote(passenger.actor)} AND p.state='APPROVED'
          AND p.longitude=0.001 AND p.common_pickup_point_id IS NOT NULL AND p.reference_text='Portão de teste';`) === 1,
    'PASSENGER_APPROVED_CORRECTION_INVALID');
    passed('correction_keeps_old_point_until_approval_and_uses_the_new_point_afterward');

    await confirm(passenger, await p('ENCERRAR'), 'CLOSE_LINK', { notify: driver });
    await chat.restart(); await chat.replay();
    demand(links('CLOSED') === 1 && memberships('ACTIVE') === 0 && memberships('REMOVED') === 1 && uses() === 1,
      'PASSENGER_CLOSURE_OR_REPLAY_INVALID');
    passed('closure_notifies_counterparty_removes_membership_and_survives_duplicate_after_restart');

    await advanceDriver('CONVIDAR');
    const secondInvite = await confirm(driver, await advanceDriver('PADRÃO'), 'CREATE_INVITE');
    const secondCode = extractPassengerInviteCode(secondInvite.reply);
    demand(secondCode, 'PASSENGER_SECOND_INVITE_MISSING');
    await p('PASSAGEIRO ' + secondCode);
    // Existing name and notice are retained. The passenger can choose a different direction and notifications.
    for (const msg of ['SIM', 'NÃO', 'SIM', '2', '2,4', 'PONTO 1']) await p(msg);
    await confirm(passenger, await p('PULAR'), 'REQUEST_LINK', { notify: driver });
    await review('PASSAGEIROS');
    const rejected = await confirm(driver, await review('RECUSAR 1'), 'CLOSE_LINK', { notify: passenger });
    demand(rejected.peer && !mapPoint(rejected.peer) && links('REJECTED') === 1 && links('APPROVED') === 0
      && memberships('ACTIVE') === 0 && uses() === 2, 'PASSENGER_REJECTION_NOT_DELIVERED');
    await chat.replay();
    passed('second_application_reuses_profile_and_rejection_is_delivered_exactly_once');
    const aggregate = chat.totals();
    const people = [driver, passenger, otherDriver].map(person => quote(person.actor)).join(',');
    demand(aggregate.crossActorDeliveries === 9
      && tools.count(`SELECT count(*) FROM wa.command_receipt WHERE identity_id IN (${people});`) === aggregate.uniqueEvents
      && tools.count(`SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id IN (${people}) AND state='SUBMITTED';`) === aggregate.submittedAttempts,
    'PASSENGER_DURABLE_TOTALS_INVALID');
    return { ...aggregate, actors: 3, modelMode: 'disabled', realWhatsApp: false };
  } finally { chat.dispose(); }
}
