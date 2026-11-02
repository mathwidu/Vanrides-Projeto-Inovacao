import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { demand, digest, exact, quote, uuid, privateWrite } from './070-worker-common.mjs';
import { root, jarDigest } from './070-worker-preflight.mjs';
import { loadTls, startTlsProxy } from './060-pairing-tls.mjs';
import { passengerConversation } from './090-passenger-conversation.mjs';
import { registerCohort } from './100-attendance-scenarios.mjs';
import { fixtureTimes, extractSyntheticIntent,menuOption } from './110-run-wire.mjs';
import { openRunSocket } from './110-run-redmi-sidecar.mjs';

const sleep=ms=>new Promise(res=>setTimeout(res,ms));
const phoneChecks=new Set(['actual_apk_dex_excludes_spike030_origin_and_transport','verified_apk_and_explicit_launcher',
  'synthetic_pairing_confirmed_on_same_device','app_location_permission_accepted_by_own_system_dialog',
  'explicit_start_confirmed_only_after_backend_valid_gps','native_sensor_stopped_while_ack_offline',
  'durable_stop_ack_after_process_reopening_without_new_collection','cleanup_confirmed_native_stop']);
export function sanitizedPhoneProgress(phone){
  const status=['PASS','FAIL','ACTION_REQUIRED'].includes(phone?.status)?phone.status:'INVALID_RECEIPT';
  const reason=typeof phone?.reason==='string'&&/^[A-Z][A-Z_]{3,60}$/u.test(phone.reason)?phone.reason:null;
  return {status,...(reason?{reason}:{}),checks:[...new Set((Array.isArray(phone?.checks)?phone.checks:[]).filter(check=>phoneChecks.has(check)))]};
}
export async function runRedmiScenarios(runtime) {
  const { tools,worker,passed,interruption }=runtime,chat=passengerConversation(runtime),{driver}=chat;
  const scope=quote(driver.actor),read=q=>tools.sql(q,{recordOutput:false}),send=(m,c,o)=>chat.send(driver,m,c,o);
  const home=process.env.HOME,apk=process.env.WA110_DEVICE_APK;
  demand(typeof apk==='string'&&apk.startsWith(path.join(home,'.local/share/vanrides/wa110/apk/'))&&fs.realpathSync(apk)===apk
    &&fs.lstatSync(apk).isFile(),'RUN_REDMILAB_APK_REQUIRED');
  const tls=loadTls(path.join(home,'.local/share/vanrides/wa060/tls-v2'));
  const directory=fs.mkdtempSync('/private/tmp/vr110-');fs.chmodSync(directory,0o700);
  const observed=new Map();let proxy,sock,child,verified=false,closed,apkSha256;
  const executor=runtime.nativeExecutor;
  const assertExecutorSources=()=>{
    if(!executor)return;
    for(const [relative,sha] of Object.entries(executor.sourceFiles)){
      const file=path.join(executor.root,relative);
      demand(/^integration_test\/driver_(?:run|pairing)\/[A-Za-z0-9_.-]+\.(?:py|swift)$/u.test(relative)
        &&fs.realpathSync(file)===file&&jarDigest(file)===sha,'RUN_QA_SOURCE_CHANGED');
    }
  };
  if(executor) demand(runtime.profile==='NATIVE_SERIAL_PROVISIONING_V1'
    &&['wa110-apk-laboratorio','wa110-automacao-redmi'].some(name=>executor.root===path.join(home,'.local/share/vanrides/qa-worktrees',name))
    &&fs.realpathSync(executor.root)===executor.root
    &&jarDigest(path.join(executor.root,'integration_test/driver_run/android_lab.py'))===executor.runnerSha256
    &&fs.realpathSync(executor.readerJar)===executor.readerJar&&jarDigest(executor.readerJar)===executor.readerJarSha256,
    'RUN_QA_EXECUTOR_NOT_PINNED');
  assertExecutorSources();
  if(executor)demand(runtime.phaseBinding?.sourceSha===runtime.input.expectedSha
    &&runtime.phaseBinding.jarSha256===runtime.input.state.jar_sha256&&runtime.phaseBinding.apkSha256===jarDigest(apk)
    &&runtime.phaseBinding.caSha256===jarDigest(path.join(home,'.local/share/vanrides/wa060/tls-v2/ca.der')),'RUN_PHASE_BINDING_CHANGED');
  const waitUntil=async(check,ms,code)=>{const end=Date.now()+ms;do{interruption.assertActive();if(await check())return;await sleep(500);}while(Date.now()<end);demand(false,code);};
  try {
    proxy=await startTlsProxy(tls,{runLab:true,onPairingBegin:o=>observed.set(o.pairingId,o.jkt)});
    const t=fixtureTimes();await registerCohort(chat,t.schedules,t.zone,{remindersForFirst:false});
    const menu=await send('ROTINAS','ADVANCE_DRIVER_SETUP'),n=/Rotina ([1-9][0-9]*): Ida escolar;/u.exec(menu.reply)?.[1];
    demand(n,'RUN_ROUTINE_NOT_LISTED');await send('ORDEM '+n,'ADVANCE_DRIVER_SETUP');
    await chat.confirm(driver,await send('1,2','ADVANCE_DRIVER_SETUP'),'SET_STOP_ORDER');
    await interruption.wait(()=>sleep(60500-Date.now()%60000));demand((await worker.request('/v1/tick-attendance')).status===200,'RUN_OCCURRENCE_TICK');
    sock=await openRunSocket(directory,{
      '/pair':async b=>{demand(observed.size===1,'RUN_PHYSICAL_KEYS');await send(b.code,'CONFIRM_PAIRING');},
      '/intent':async()=>{
        demand(tools.count("SELECT count(*) FROM wa.driver_device WHERE driver_id="+scope+" AND status='ACTIVE'")===1,'RUN_REAL_PAIRING_REQUIRED');
        const menu=await send('INICIAR SAÍDA','ADVANCE_RUN_SETUP'),choice=menuOption(menu.reply,', ida');
        demand(choice,'RUN_START_DATE_MISSING');
        const reply=await chat.confirm(driver,await send(choice,'ADVANCE_RUN_SETUP'),'REQUEST_START',{verifyRunLink:(t,stored)=>!!extractSyntheticIntent(t)&&!stored?.includes('#v1.')});
        const intent=extractSyntheticIntent(reply.reply);demand(intent,'RUN_REAL_LINK_REQUIRED');
        passed('redmi_lab_apk_paired_with_whatsapp_driver_and_reviewed_start_link');
        return {uri:'vanridesdriver://run/start#v1.'+intent.id+'.'+intent.expectedVersion+'.'+intent.intentToken};
      },
      '/started':async()=>{
        await waitUntil(()=>tools.count("SELECT count(*) FROM wa.operation_run r JOIN wa.tracking_session s ON s.run_id=r.id WHERE r.driver_id="+scope+" AND r.state='TRACKING' AND s.last_captured_at>CURRENT_TIMESTAMP-interval '20 seconds'")===1,35000,'RUN_REAL_GPS_NOT_RECEIVED');
        const box=JSON.parse(read("SELECT json_build_object('id',id,'text',payload_json->>'text') FROM wa.message_outbox WHERE recipient_identity_id="+scope+" AND response_part='run-gps-confirmed'"));
        await chat.deliverRunNotice(driver,box,/saída iniciada/iu);passed('redmi_first_real_fix_confirmed_by_same_backend_and_delivered_to_driver');
      },
      '/stopped':async()=>{
        await waitUntil(()=>tools.count("SELECT count(*) FROM wa.tracking_session WHERE driver_id="+scope+" AND stopped_at IS NOT NULL")===1,22000,'RUN_REAL_STOP_NOT_ACKNOWLEDGED');
        passed('redmi_stop_confirmed_by_device_ack_in_same_session');
      }
    });
    const appRoot=executor?.root??path.join(root,'vanrides-app');
    assertExecutorSources();
    const socketStat=fs.lstatSync(sock.socket);
    demand(socketStat.isSocket()&&socketStat.uid===process.getuid()&&(socketStat.mode&0o777)===0o600,'RUN_SOCKET_BINDING_CHANGED');
    const socketProof=digest(JSON.stringify({path:sock.socket,dev:socketStat.dev,ino:socketStat.ino,mode:socketStat.mode,
      sourceSha:runtime.input.expectedSha,jarSha256:runtime.input.state.jar_sha256,apkSha256:jarDigest(apk)}));
    if(executor)privateWrite(path.join(runtime.work,'native-executor-proof.json'),JSON.stringify({profile:runtime.profile,
      sourceSha:runtime.input.expectedSha,jarSha256:runtime.input.state.jar_sha256,apkSha256:jarDigest(apk),
      caSha256:runtime.phaseBinding.caSha256,qaRunnerSha256:executor.runnerSha256,qaSourceTreeSha256:executor.sourceTreeSha256,
      officialReaderJarSha256:executor.readerJarSha256,socketBindingSha256:socketProof,socketMode:'0600'})+'\n');
    const args=[path.join(appRoot,'integration_test/driver_run/android_lab.py'),'--adb',path.join(home,'Library/Android/sdk/platform-tools/adb'),'--apk',apk,'--socket',sock.socket];
    if(executor){args.push('--official-reader-jar',executor.readerJar);if(executor.readerPermissions)args.push('--official-reader-permissions');}
    child=spawn('/usr/bin/python3',args,{cwd:appRoot,stdio:['ignore','pipe','ignore'],env:{HOME:home,PATH:'/usr/bin:/bin',PYTHONDONTWRITEBYTECODE:'1'}});
    let bytes=0;const chunks=[];child.stdout.on('data',chunk=>{bytes+=chunk.length;if(bytes<=8192)chunks.push(chunk);else child.kill('SIGTERM');});
    closed=new Promise((ok,no)=>{child.once('error',no);child.once('close',ok);});
    const timer=setTimeout(()=>child.kill('SIGTERM'),270000);
    try {
      const rc=await interruption.wait(()=>closed);
      let phone;try{phone=JSON.parse(Buffer.concat(chunks).toString());}catch{}
      if(executor)privateWrite(path.join(runtime.work,'phone-progress.json'),JSON.stringify({profile:runtime.profile,
        sourceSha:runtime.input.expectedSha,jarSha256:runtime.input.state.jar_sha256,apkSha256:jarDigest(apk),
        qaRunnerSha256:executor.runnerSha256,sidecarComplete:sock.complete(),exitCode:rc,
        ...sanitizedPhoneProgress(phone)})+'\n');
      if(rc!==0) { const safe=String(phone?.reason??'');demand(false,/^[A-Z][A-Z_]{3,60}$/u.test(safe)?'REDMI_'+safe:'RUN_PHYSICAL_EXECUTOR_FAILED');}
      demand(phone?.status==='PASS'&&sock.complete()&&phone.apkSha256===jarDigest(apk),'RUN_PHYSICAL_RECEIPT_MISSING');apkSha256=phone.apkSha256;verified=true;
    }finally{clearTimeout(timer);}
    assertExecutorSources();
    if(executor)demand(runtime.phaseBinding.caSha256===jarDigest(path.join(home,'.local/share/vanrides/wa060/tls-v2/ca.der')),'RUN_CA_CHANGED');
    demand(fs.lstatSync(sock.socket).ino===socketStat.ino&&fs.lstatSync(sock.socket).dev===socketStat.dev,'RUN_SOCKET_REPLACED');
    if(executor) demand(jarDigest(path.join(executor.root,'integration_test/driver_run/android_lab.py'))===executor.runnerSha256
      &&jarDigest(executor.readerJar)===executor.readerJarSha256,'RUN_QA_EXECUTOR_CHANGED');
    return {...chat.totals(),physicalAndroid:verified,realWhatsApp:false,apkSha256,
      ...(executor?{qaRunnerSha256:executor.runnerSha256,qaSourceTreeSha256:executor.sourceTreeSha256,
        officialReaderJarSha256:executor.readerJarSha256,qaSocketProofSha256:socketProof}: {})};
  }finally{
    if(child&&child.exitCode===null&&child.signalCode===null){child.kill('SIGTERM');await closed;}
    await sock?.stop();await proxy?.stop();chat.dispose();fs.rmdirSync(directory);
    for(const [pair,jkt] of observed){demand(uuid(pair),'RUN_SCOPED_CLEANUP');const seq=digest('wa070-local\0'+jkt).slice(0,32).replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/u,'$1-$2-$3-$4-$5');
      tools.sql("BEGIN; DELETE FROM wa.pairing_challenge WHERE id="+quote(pair)+" AND driver_id IS NULL AND environment='wa070-local'; DELETE FROM wa.operation_replay WHERE operation_kind='PAIR_BEGIN' AND scope_id="+quote(seq)+"; DELETE FROM wa.proof_nonce WHERE environment='wa070-local' AND jkt="+quote(jkt)+"; DELETE FROM wa.proof_replay WHERE jkt="+quote(jkt)+"; COMMIT;");}
  }
}
