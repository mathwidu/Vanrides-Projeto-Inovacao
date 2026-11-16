import test from 'node:test';
import assert from 'node:assert/strict';
import { decideDeterministically, validateDecision } from './deterministic.mjs';
import { createModelDecision } from './model-policy.mjs';

const id = '00000000-0000-4000-8000-000000000140';
const msg = { sourceMessageId: 'event_' + id, type: 'TEXT' };
const context = { inboxEventId:id, entityVersion:4, actorState:'DRIVER', step:'READY', registrationStep:'READY',
  eventFreshness:'CURRENT', allowedCommands:['RESPOND_TO_MESSAGE','FINISH_RUN'], serverTime:'2030-01-01T12:00:00Z',
  options:[{kind:'RUN',ref:'server_owned_finish_run',label:'Viagem autorizada'}] };
const expected = {kind:'COMMAND',commandType:'FINISH_RUN',payload:{runRef:'server_owned_finish_run',reason:'MANUAL_COMPLETION'}};

test('copies the authenticated review offer without classifying private text', async () => {
  const decide = createModelDecision({classify:()=>assert.fail('Run finish must not call a model')});
  assert.deepEqual(await decide(msg,context,{deadline:Date.now()+1000}),expected);
  assert.doesNotThrow(()=>validateDecision(expected,context));
});
test('cleanup offer works while ordinary new effects are paused', () => {
  const paused={...context,step:'PAUSED',registrationStep:'PAUSED'};
  assert.deepEqual(decideDeterministically(msg,paused),expected);
  assert.doesNotThrow(()=>validateDecision(expected,paused));
});
test('no passenger, stale message, missing or conflicting capability can finish', () => {
  for (const changes of [{actorState:'PASSENGER'},{eventFreshness:'STALE'},{options:[]},
    {allowedCommands:['FINISH_RUN','REQUEST_START']},{options:[...context.options,...context.options]}]) {
    assert.throws(()=>validateDecision(expected,{...context,...changes}));
  }
  assert.equal(decideDeterministically(msg,{...context,options:[]}).kind,'NO_ACTION');
});
test('reason and selected run cannot be replaced or extended by a model', () => {
  for (const payload of [{...expected.payload,reason:'SAFETY_STOP'},{...expected.payload,runRef:'other_run'},
    {...expected.payload,driverId:id},{...expected.payload,latitude:1}])
    assert.throws(()=>validateDecision({...expected,payload},context));
});
