import { readFileSync } from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { allowedRoute } from './060-pairing-tls.mjs';
import { extractSyntheticIntent, fixtureTimes, menuOption } from './110-run-wire.mjs';
import { decideDeterministically } from '../agent/deterministic.mjs';
const api=JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml',import.meta.url)));
const {schemas,examples}=api.components;
const id='00000000-0000-4000-8000-000000000110';
test('a prepared chat command has exactly the same fields on both sides of a reviewed departure',()=>{
  for (const [name,refs] of Object.entries({AdvanceRunSetup:[],RequestStart:['OCCURRENCE','CONFIRMATION'],
    RequestResume:['OCCURRENCE','CONFIRMATION'],SetOperationalAttendance:['OCCURRENCE','PASSENGER','CONFIRMATION']})) {
    const v=examples[name].value,keys=Object.keys(v.payload);
    const ctx={inboxEventId:id,entityVersion:9,actorState:'DRIVER',step:'READY',registrationStep:'READY',eventFreshness:'CURRENT',
      allowedCommands:[v.commandType],options:keys.map((k,i)=>({kind:refs[i],label:'Opção',ref:v.payload[k]})),serverTime:'2026-10-31T12:00:00Z'};
    const result=decideDeterministically({type:'TEXT',sourceMessageId:'event_'+id},ctx);
    assert.deepEqual(result,{kind:'COMMAND',commandType:v.commandType,payload:v.payload});
    assert.deepEqual(Object.keys(schemas[name+'Payload'].properties).sort(),keys.sort());
  }
});
test('the pairing-only TLS proxy cannot reach run control; the run proxy is limited to exact device routes',()=>{
  for(const [method,path] of [['GET','tracking-status'],['POST','start-intents/'+id+'/activate'],
    ['POST','tracking-sessions/'+id+'/samples'],['POST','tracking-sessions/'+id+'/state'],['POST','runs/'+id+'/resume']]) {
    assert.equal(!!allowedRoute(method,'/driver-app/v1/'+path),false);
    assert.equal(allowedRoute(method,'/driver-app/v1/'+path,true),true);
  }
  for (const path of ['/driver-app/v1/start-intents/'+id+'/samples','/driver-app/v1/runs/'+id+'/finish','/driver-app/v1/tracking-status?value=0'])
    assert.equal(!!allowedRoute('POST',path,true),false);
});
test('fixture clock schedules two same-day routines; synthetic link cannot point the test client at a real host',()=>{
  for(let h=0;h<24;h++){
    const {schedules}=fixtureTimes(new Date(Date.UTC(2026,9,31,h,59)));
    assert.equal(schedules.length,2);assert.ok(schedules[0][2]<schedules[1][2]);
  }
  const url='https://vanrides.invalid/motorista/saida#v1.'+id+'.0.'+'Q'.repeat(43);
  assert.equal(extractSyntheticIntent('Abra: '+url)?.id,id);
  for(const bad of [url.replace('vanrides.invalid','example.org'),url+'x',url+' '+url])
    assert.equal(extractSyntheticIntent(bad),null);
  assert.equal(menuOption('Escolha. 1 — 31/10/2026, ida às 06:00 (Ida escolar). 2 — volta.','31/10/2026, ida'),'1');
});
test('the original pairing response stays closed and unchanged; stop pending is distinguishable from confirmed stop',()=>{
  assert.equal(api.paths['/driver-app/v1/status'].get.responses['200'].content['application/json'].schema.$ref,'#/components/schemas/DriverStatusResponse');
  assert.deepEqual(Object.keys(schemas.DriverStatusResponse.properties).sort(),['deviceId','driverId','displayName','profileStatus','deviceState','readiness','runId','serverTime'].sort());
  assert.deepEqual(new Set(schemas.TrackingStatusResponse.properties.sessionState.enum),new Set(['RUNNING','STOP_PENDING','STOPPED','IDLE']));
  assert.deepEqual(api.paths['/driver-app/v1/tracking-status'].get.security,[{DeviceSession:[],DPoPProof:[]}]);
});
