import { demand, exact, quote, uuid } from './070-worker-common.mjs';
import { extractPassengerInviteCode } from './080-driver-evidence.mjs';
import { passengerConversation } from './090-passenger-conversation.mjs';
import { prepareReminderWindow } from './100-attendance-window.mjs';

const advance = 'ADVANCE_ATTENDANCE';
const answer = 'SET_ATTENDANCE';
const setup = 'ADVANCE_PASSENGER_SETUP';
const pin = { locationMessage: { degreesLatitude: 0, degreesLongitude: 0 } };
const cited = (text, id) => ({ extendedTextMessage: { text, contextInfo: { stanzaId: id } } });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export async function registerCohort(chat, schedules = [['Ida escolar','IDA','06:00'],['Volta escolar','VOLTA','18:00']], zone = 'MANAUS', { remindersForFirst = true } = {}) {
  const { send, confirm, driver, passenger, otherDriver: second } = chat;
  const driverStep = msg => send(driver, msg, 'ADVANCE_DRIVER_SETUP');
  await chat.bootstrap(driver, 'Alex Exemplo');
  for (const [name, direction, time] of schedules) {
    for (const msg of ['ROTINAS','CRIAR',name,direction,'TODOS OS DIAS',time,zone]) await driverStep(msg);
    await confirm(driver, await driverStep(pin), 'CONFIGURE_ROUTINE');
  }
  await driverStep('CONVIDAR'); await driverStep('2');
  const invite = extractPassengerInviteCode((await confirm(driver, await driverStep('PADRÃO'), 'CREATE_INVITE')).reply);
  demand(invite, 'ATTENDANCE_INVITE_MISSING');
  for (const [person, name, reminders] of [[passenger,'Lia Exemplo',remindersForFirst ? 'SIM' : 'NÃO'],[second,'Cris Exemplo','NÃO']]) {
    const step = msg => send(person, msg, setup);
    for (const msg of ['PASSAGEIRO '+invite,'CONTINUAR',name,'ACEITO','SIM',reminders])
      await step(msg);
    let question = await step('1,2');
    for (const routine of ['Ida escolar', 'Volta escolar']) {
      demand(question.reply?.includes(routine) && question.reply.includes('em quais dias'), 'ATTENDANCE_WEEKDAY_QUESTION_MISSING');
      question = await step('TODOS OS DIAS');
    }
    await step(pin);
    await confirm(person, await step('PULAR'), 'REQUEST_LINK', { notify: driver });
    const list = await send(driver, 'PASSAGEIROS', setup);
    const option = [1, 2].find(number => list.reply?.includes(`${number}: ${name} (solicitação)`));
    demand(option, 'ATTENDANCE_PASSENGER_REQUEST_MISSING');
    await confirm(driver, await send(driver, `APROVAR ${option}`, setup), 'APPROVE_LINK', { notify: person });
  }
}

async function checkDriverSummary(chat, label, totals, names) {
  const menu = await chat.send(chat.driver, 'PRESENÇAS', advance);
  demand(menu.reply && !menu.reply.includes('Lia Exemplo'), 'ATTENDANCE_DRIVER_SELECTION_INVALID');
  const option = [1,2,3,4,5,6,7,8].find(number => menu.reply.includes(`${number} — ${label}.`));
  demand(option, 'ATTENDANCE_DRIVER_OCCURRENCE_MISSING');
  const { reply } = await chat.send(chat.driver, String(option), 'GET_SUMMARY');
  demand(reply?.includes(`Presenças de ${label}. ${totals}.`) && reply.includes('pendentes continuam previstos')
    && names.every(name => reply.includes(name)), 'ATTENDANCE_DRIVER_SUMMARY_INVALID');
}

export async function runAttendanceScenarios(runtime) {
  const { tools, worker, instance, passed, interruption } = runtime;
  const chat = passengerConversation(runtime);
  const { driver, passenger, otherDriver: second } = chat;
  const count = q => tools.count(q);
  const states = (person, state, direction) => count(`SELECT count(*) FROM wa.attendance a JOIN wa.routine r ON r.id=a.routine_id
    WHERE a.passenger_id=${quote(person.actor)} AND a.state=${quote(state)} AND r.direction=${quote(direction)}
    AND a.local_date=(CURRENT_TIMESTAMP AT TIME ZONE r.timezone)::date+1;`);
  let ticks = 0;
  async function tick() {
    const result = await interruption.wait(() => worker.request('/v1/tick-attendance'));
    demand(result.status === 200 && exact(result.data, ['status','calendarProcessed','reminderProcessed'])
      && ['IDLE','PROCESSED','MORE'].includes(result.data.status), 'ATTENDANCE_TICK_UNAVAILABLE');
    ticks++; return result.data;
  }
  const submittedReminders = () => count(`SELECT count(*) FROM wa.message_outbox o JOIN wa.attendance_question q ON q.outbox_id=o.id
    WHERE q.instance_id=${quote(instance)} AND o.purpose='REMINDER' AND o.state='SUBMITTED';`);
  try {
    await registerCohort(chat);
    demand(count(`SELECT count(*) FROM wa.routine_member WHERE driver_id=${quote(driver.actor)} AND state='ACTIVE';`) === 4,
      'ATTENDANCE_COHORT_NOT_REGISTERED');
    passed('driver_and_two_passengers_register_from_chat_with_independent_reminder_preferences');
    // W04 evaluates link and pickup validity at the start of the selected minute.
    // The next scheduled tick is the first one guaranteed to include both new approvals.
    await interruption.wait(() => sleep(60300 - Date.now()%60000));
    const initial = await tick();
    demand(initial.calendarProcessed > 0 && [passenger, second].every(person =>
      ['OUTBOUND','RETURN'].every(direction => states(person,'PENDING',direction) === 1)), 'ATTENDANCE_DEFAULT_NOT_PENDING');
    passed('materialization_creates_separate_pending_outbound_return_for_each_passenger');
    const destination = prepareReminderWindow(tools, instance, passenger.actor);
    const queued = id => count(`SELECT count(*) FROM wa.attendance_question WHERE id=${quote(id)} AND outbox_id IS NOT NULL;`);
    if (!queued(destination.id)) {
      // An identical root page in the same minute is a replay. Advance on the
      // next real UTC minute using the normal W04 endpoint.
      await interruption.wait(() => sleep(60300 - Date.now()%60000)); await tick();
    }
    demand(queued(destination.id) === 1, 'ATTENDANCE_DUE_REMINDER_NOT_QUEUED');
    const raw = tools.sql(`SELECT coalesce(json_agg(json_build_object('id',o.id,'text',o.payload_json->>'text',
      'person',q.passenger_id) ORDER BY o.created_at,o.id),'[]') FROM wa.message_outbox o JOIN wa.attendance_question q ON q.outbox_id=o.id
      WHERE q.instance_id=${quote(instance)} AND o.purpose='REMINDER' AND o.state='PENDING';`, { recordOutput: false });
    const boxes = JSON.parse(raw);
    demand(boxes.length >= 1 && boxes.every(box => box.person === passenger.actor), 'ATTENDANCE_OPTOUT_OR_RECIPIENT_INVALID');
    for (const box of boxes) await chat.deliverReminder(passenger, box);
    demand(submittedReminders() === boxes.length, 'ATTENDANCE_DELIVERIES_NOT_DURABLE');
    passed('eligible_reminder_is_submitted_to_its_passenger_and_opted_out_passenger_remains_pending');
    await chat.restart(); await tick();
    demand((await worker.request('/v1/dispatch-one')).data.status === 'IDLE' && submittedReminders() === boxes.length,
      'ATTENDANCE_RESTART_REPEATED_REMINDER');
    passed('a_repeated_schedule_and_worker_restart_do_not_resubmit_the_reminder');
    const external = tools.sql(`SELECT a.external_message_id FROM wa.message_attempt a JOIN wa.attendance_question q ON q.outbox_id=a.outbox_id
      WHERE q.id=${quote(destination.id)} AND a.state='SUBMITTED';`, { recordOutput: false });
    demand(/^wa070_provider_[0-9]+$/u.test(external), 'ATTENDANCE_QUOTE_RECEIPT_MISSING');
    await chat.send(passenger, cited('SIM', external), answer); await chat.replay();
    demand(states(passenger,'GOING','OUTBOUND') === 1 && states(passenger,'PENDING','RETURN') === 1, 'ATTENDANCE_CITATION_CHANGED_WRONG_DATE');
    const refused = await chat.send(second, cited('SIM', external), advance);
    demand(refused.reply.includes('mensagem citada') && states(second,'PENDING','OUTBOUND') === 1, 'ATTENDANCE_FOREIGN_QUOTE_ALLOWED');
    passed('submitted_message_citation_confirms_only_its_original_passenger_date_and_direction');
    const date = tools.sql(`SELECT to_char(local_date,'DD/MM/YYYY') FROM wa.attendance_question WHERE id=${quote(destination.id)};`, { recordOutput: false });
    const label = `${date}, ida às 06:00 (Ida escolar)`;
    await checkDriverSummary(chat, label, 'Confirmados 1. Não vão 0. Pendentes 1', ['Lia Exemplo — confirmado', 'Cris Exemplo — pendente']);
    await chat.send(second, 'NÃO VOU amanhã na ida', answer);
    demand(states(second,'NOT_GOING','OUTBOUND') === 1 && states(second,'PENDING','RETURN') === 1,
      'ATTENDANCE_MANUAL_ABSENCE_INVALID');
    await chat.send(passenger, 'NÃO VOU amanhã na ida', answer);
    demand(states(passenger,'NOT_GOING','OUTBOUND') === 1 && states(passenger,'PENDING','RETURN') === 1,
      'ATTENDANCE_CORRECTION_INVALID');
    passed('explicit_absence_and_a_change_of_mind_update_one_occurrence_even_with_reminders_disabled');
    await checkDriverSummary(chat, label, 'Confirmados 0. Não vão 2. Pendentes 0', ['Lia Exemplo — não vai', 'Cris Exemplo — não vai']);
    passed('driver_selects_a_dated_direction_and_receives_a_nominal_summary_with_pending_policy');
    const totals = chat.totals();
    return { ...totals, actors: 3, ticks, reminderSubmissions: boxes.length, seededReminderWindows: destination.seeded,
      modelMode: 'disabled', realWhatsApp: false };
  } finally { chat.dispose(); }
}
