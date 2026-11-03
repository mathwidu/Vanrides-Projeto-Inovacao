import test from 'node:test';
import assert from 'node:assert/strict';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000110';
const msg = { sourceMessageId: 'event_' + id, type: 'TEXT' };
const ctx = (command, kinds=[]) => ({inboxEventId:id,entityVersion:1,actorState:'DRIVER',step:'READY',registrationStep:'READY',
  eventFreshness:'CURRENT',allowedCommands:['RESPOND_TO_MESSAGE',command],serverTime:'2026-10-31T12:00:00Z',
  options:kinds.map(kind=>({kind,ref:'eventbound_'+kind,label:'Opção'}))});
const occurrence={ occurrenceRef:'eventbound_OCCURRENCE',confirmationRef:'eventbound_CONFIRMATION' };
for (const [command,kinds,payload] of [
  ['ADVANCE_RUN_SETUP',[],{}],
  ['REQUEST_START',['OCCURRENCE','CONFIRMATION'],occurrence],
  ['REQUEST_RESUME',['OCCURRENCE','CONFIRMATION'],occurrence],
  ['SET_OPERATIONAL_ATTENDANCE',['OCCURRENCE','PASSENGER','CONFIRMATION'],{...occurrence,passengerRef:'eventbound_PASSENGER'}]
]) test(command + ' copies only references from this authenticated review', async ()=>{
  const context=ctx(command,kinds);
  const decide=createModelDecision({classify:()=>assert.fail('Private run review sent for classification')});
  const got=await decide(msg,context,{deadline:Date.now()+1000});
  assert.deepEqual(got,{kind:'COMMAND',commandType:command,payload});
  assert.doesNotThrow(()=>validateDecision(got,context));
  for(const substituted of [{...payload,driverId:id},{...payload,confirmationRef:'another_review'},
    {...payload,latitude:1}]) assert.throws(()=>validateDecision({...got,payload:substituted},context));
  for (const bad of [{...context,actorState:'PASSENGER'},{...context,allowedCommands:[command,'SET_ATTENDANCE']}]) {
    assert.equal(decideDeterministically({...msg,text:'confirmar'},bad).kind,'NO_ACTION');
    assert.throws(()=>validateDecision(got,bad));
  }
  assert.throws(()=>validateDecision(got,{...context,eventFreshness:'STALE'}));
  if(kinds.length) assert.equal(decideDeterministically(msg,{...context,options:context.options.slice(1)}).kind,'NO_ACTION');
});
