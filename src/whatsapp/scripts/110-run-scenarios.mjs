import crypto from 'node:crypto';
import path from 'node:path';
import { createConsumer } from './060-pairing-consumer.mjs';
import { prepareTls, startTlsProxy, tlsJson } from './060-pairing-tls.mjs';
import { demand, digest, quote, token, uuid } from './070-worker-common.mjs';
import { passengerConversation } from './090-passenger-conversation.mjs';
import { registerCohort } from './100-attendance-scenarios.mjs';
import { extractSyntheticIntent, fixtureTimes, menuOption } from './110-run-wire.mjs';

const advance='ADVANCE_RUN_SETUP', dsetup='ADVANCE_DRIVER_SETUP';
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms));
export async function runTrackingScenarios(runtime) {
  const { tools, worker, passed, interruption, work }=runtime, chat=passengerConversation(runtime);
  const driver=chat.driver, driverQ=quote(driver.actor);
  const read=sql=>tools.sql(sql,{recordOutput:false});
  const count=sql=>tools.count(sql);
  const tls=prepareTls(path.join(work,'local-tls'));
  const device=createConsumer(tls,(t,m,r,b,h)=>tlsJson(t,m,r,b,h,18443,true));
  let proxy, pairing, access, run;
  const beginIds=new Set(),keys=new Set([device.jkt]);
  const send=(message,cmd,opts)=>chat.send(driver,message,cmd,opts);
  const ownRuns=()=>count('SELECT count(*) FROM wa.operation_run WHERE driver_id='+driverQ);
  const notices=part=>JSON.parse(read("SELECT coalesce(json_agg(json_build_object('id',id,'text',payload_json->>'text')),'[]') FROM wa.message_outbox WHERE recipient_identity_id="+driverQ+" AND response_part="+quote(part)));
  const collect=async(part,pattern)=>{const boxes=notices(part);demand(boxes.length===1,'RUN_NOTICE_COUNT');await chat.deliverRunNotice(driver,boxes[0],pattern);};
  const status=()=>device.request('GET','/driver-app/v1/tracking-status',undefined,access);
  try {
    proxy=await startTlsProxy(tls,{runLab:true,onPairingBegin:o=>{beginIds.add(o.pairingId);keys.add(o.jkt);}});
    const {schedules,zone}=fixtureTimes();
    await registerCohort(chat,schedules,zone,{remindersForFirst:false});
    const menu=(await send('ROTINAS',dsetup)).reply;
    const option=/Rotina ([1-9][0-9]*): Ida escolar;/u.exec(menu)?.[1];demand(option,'RUN_ROUTINE_NOT_LISTED');
    await send('ORDEM '+option,dsetup);
    await chat.confirm(driver,await send('1,2',dsetup),'SET_STOP_ORDER');
    await interruption.wait(()=>sleep(60500-Date.now()%60000));
    demand((await worker.request('/v1/tick-attendance')).status===200,'RUN_OCCURRENCE_TICK');
    pairing=await device.begin();
    await send(pairing.userCode,'CONFIRM_PAIRING');delete pairing.userCode;
    const redeemed=await device.redeem(pairing.pairingId);
    demand(redeemed.status===200 && token(redeemed.data?.accessToken),'RUN_PAIR_REDEEM');
    access=redeemed.data.accessToken;
    passed('driver_routines_and_two_passengers_from_chat_device_bound_by_pkce_and_dpop');
    const occurrenceMenu=await send('CORRIGIR LISTA',advance);
    const today = read("SELECT to_char((CURRENT_TIMESTAMP AT TIME ZONE timezone)::date,'DD/MM/YYYY') FROM wa.routine WHERE driver_id="+driverQ+" AND direction='OUTBOUND'");
    const dayLabel=today+', ida';
    const selected=menuOption(occurrenceMenu.reply,dayLabel);demand(selected,'RUN_CORRECTION_DATE_MISSING');
    const people=await send(selected,advance), removed=menuOption(people.reply,'Cris Exemplo');
    demand(removed,'RUN_PREDEPARTURE_PASSENGER_MISSING');
    for(const msg of [removed,'EXCLUIR'])await send(msg,advance);
    await chat.confirm(driver,await send('Embarque dispensado no ensaio',advance),'SET_OPERATIONAL_ATTENDANCE');
    demand(count("SELECT count(*) FROM wa.occurrence_roster_override WHERE driver_id="+driverQ+" AND passenger_id="+quote(chat.otherDriver.actor)+" AND NOT included AND actor_identity_id=driver_id;")===1,'RUN_OVERRIDE_NOT_OWNED');
    const review=async ()=>{
      const m=await send('INICIAR SAÍDA',advance),o=menuOption(m.reply,dayLabel);demand(o,'RUN_START_DATE_MISSING');
      const reviewed=await send(o,advance);
      demand(reviewed.reply?.includes('Lia Exemplo — previsto') && reviewed.reply.includes('Cris Exemplo — fora da lista'),'RUN_REVIEW_MISMATCH');return reviewed;
    };
    const start=await chat.confirm(driver,await review(),'REQUEST_START',{verifyRunLink:(text,stored)=>!!extractSyntheticIntent(text) && !stored?.includes('#v1.')});
    const intent=extractSyntheticIntent(start.reply);
    demand(intent&&ownRuns()===0&&notices('run-gps-confirmed').length===0,'RUN_STARTED_BY_LINK_PREVIEW');
    passed('driver_correction_preserves_personal_attendance_and_delivered_link_does_not_start_a_run');
    const url='/driver-app/v1/start-intents/'+intent.id+'/activate', body={expectedVersion:intent.expectedVersion,intentToken:intent.intentToken,foregroundConfirmed:'CONFIRMED'};
    const act=()=>device.request('POST',url,body,access,intent.id);
    const a=await act(),again=await act();run=a.data;
    demand(a.status===200&&again.status===200&&uuid(run.runId)&&uuid(run.trackingSessionId)&&again.data.trackingSessionId===run.trackingSessionId
      &&run.state==='STARTING'&&ownRuns()===1&&notices('run-gps-confirmed').length===0,'RUN_ACTIVATION_REPLAY');
    const names=JSON.parse(read("SELECT snapshot_json->'passengerNames' FROM wa.operation_run WHERE id="+quote(run.runId)));
    demand(Object.keys(names).length===1&&names[chat.passenger.actor]==='Lia Exemplo','RUN_SNAPSHOT_WRONG_PASSENGER');
    passed('reviewed_snapshot_activated_once_by_bound_device_without_early_gps_confirmation');
    const sid=run.trackingSessionId,generation=run.generation,track='/driver-app/v1/tracking-sessions/'+sid;
    const then=Date.now(),iso=t=>new Date(t).toISOString(),sample=(sequence,t)=>({sequence,capturedAt:iso(t),latitude:0,longitude:0,accuracyM:6});
    const batch={sessionId:sid,generation,batchId:crypto.randomUUID(),deviceTime:iso(then),samples:[sample(1,then-60000),sample(2,then)]};
    const postSamples=b=>device.request('POST',track+'/samples',b,access,b.batchId);
    const ack=await postSamples(batch),retry=await postSamples(batch);
    demand(ack.status===200&&retry.status===200&&ack.data.accepted?.join(',')==='2'&&ack.data.rejected?.[0]?.sequence===1
      &&retry.data.accepted?.join(',')==='2'&&ack.data.gpsState==='FRESH','RUN_PARTIAL_ACK');
    await collect('run-gps-confirmed',/saída iniciada/iu);
    demand((await status()).data.runState==='TRACKING','RUN_EXPECT_TRACKING');
    passed('first_valid_acquisition_confirms_driver_once_and_ack_replay_preserves_partial_results');
    // A status GET also reconciles. Read the database first so the assertion can
    // be satisfied only by the periodic watchdog while both device and n8n are silent.
    const deadline=then+30000;
    let degraded=false;
    while(Date.now() < deadline && !degraded) {
      await interruption.wait(()=>sleep(Math.min(1100,deadline-Date.now())));
      degraded=count("SELECT count(*) FROM wa.operation_run WHERE id="+quote(run.runId)+" AND state='DEGRADED'")===1;
    }
    demand(degraded,'RUN_PERIODIC_WATCHDOG_MISSED_DEADLINE');
    const old=await status();
    demand(old.status===200&&old.data.runState==='DEGRADED'&&old.data.gpsState==='STALE','RUN_WATCHDOG_DID_NOT_DEGRADE');
    await collect('run-gps-unavailable',/indisponível/iu);
    const againOld=await postSamples(batch);demand(againOld.data.gpsState==='STALE'&&notices('run-gps-confirmed').length===1,'RUN_REPLAY_UPDATED_FRESHNESS');
    passed('api_watchdog_degrades_in_silence_without_n8n_and_replay_does_not_restore_freshness');
    const newTime=Date.now(),fresh=await postSamples({...batch,batchId:crypto.randomUUID(),deviceTime:iso(newTime),samples:[sample(3,newTime)]});
    demand(fresh.data.gpsState==='FRESH'&&(await status()).data.runState==='TRACKING','RUN_NEW_FIX_NOT_RECOVERED');
    const indicate=(state,ref)=>device.request('POST',track+'/state',{sessionId:sid,generation,state,...(ref?{stopRequestRef:ref}:{})},access);
    const stop=await indicate('PERMISSION_DENIED');demand(stop.status===200&&stop.data.action==='STOP'&&uuid(stop.data.stopRequestRef),'RUN_STOP_NOT_REQUESTED');
    demand(count("SELECT count(*) FROM wa.tracking_session WHERE id="+quote(sid)+" AND stop_requested_at IS NOT NULL AND stopped_at IS NULL")===1,'RUN_STOP_FABRICATED');
    const stopped=await indicate('STOPPED',stop.data.stopRequestRef);demand(stopped.status===200&&stopped.data.action==='STOP','RUN_STOP_NOT_ACKED');
    demand(count("SELECT count(*) FROM wa.tracking_session WHERE id="+quote(sid)+" AND stopped_at IS NOT NULL")===1 && (await postSamples(batch)).data.action==='STOP','RUN_STOPPED_REOPENED');
    passed('new_acquisition_recovers_same_run_and_stop_requires_distinct_device_ack');
    return {...chat.totals(),actors:3,sessionGenerations:1,syntheticFixtures:true,realWhatsApp:false,physicalDevices:false};
  } finally {
    device.dispose();chat.dispose();await proxy?.stop();
    for(const id of beginIds)if(uuid(id)) tools.sql("DELETE FROM wa.pairing_challenge WHERE id="+quote(id)+" AND driver_id IS NULL AND environment='wa070-local'");
    for(const jkt of keys) {
      const key=digest('wa070-local\0'+jkt).slice(0,32).replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/u,'$1-$2-$3-$4-$5');
      tools.sql("BEGIN; DELETE FROM wa.operation_replay WHERE operation_kind='PAIR_BEGIN' AND scope_id="+quote(key)+"; DELETE FROM wa.proof_nonce WHERE environment='wa070-local' AND jkt="+quote(jkt)+"; DELETE FROM wa.proof_replay WHERE jkt="+quote(jkt)+"; COMMIT;");
    }
  }
}
