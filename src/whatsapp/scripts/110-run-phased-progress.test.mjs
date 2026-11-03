import test from 'node:test';
import assert from 'node:assert/strict';
import { sanitizedPhoneProgress } from './110-run-redmi.mjs';
const milestones=['explicit_start_confirmed_only_after_backend_valid_gps','native_sensor_stopped_while_ack_offline',
  'durable_stop_ack_after_process_reopening_without_new_collection'];
test('failed device receipt retains only the allowlisted milestones and constant reason',()=>{
  assert.deepEqual(sanitizedPhoneProgress({status:'FAIL',reason:'RUN_REAL_STOP_NOT_ACKNOWLEDGED',checks:[...milestones,'SECRET_DYNAMIC_VALUE',milestones[0]],
    payload:'PRIVATE_SENTINEL',officialReader:{ui:'PRIVATE_SENTINEL'},message:'PRIVATE_SENTINEL'}),
    {status:'FAIL',reason:'RUN_REAL_STOP_NOT_ACKNOWLEDGED',checks:milestones});
});
test('dynamic reason and malformed check collection cannot enter sanitized evidence',()=>{
  assert.deepEqual(sanitizedPhoneProgress({status:'ACTION_REQUIRED',reason:'dynamic UI text123',checks:{payload:'PRIVATE_SENTINEL'}}),{status:'ACTION_REQUIRED',checks:[]});
});
test('a malformed receipt has no invented physical milestones',()=>{
  assert.deepEqual(sanitizedPhoneProgress(undefined),{status:'INVALID_RECEIPT',checks:[]});
});
