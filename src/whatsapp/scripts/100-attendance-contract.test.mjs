import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { decideDeterministically, validateDecision } from '../agent/deterministic.mjs';
import { prepareReminderWindow } from './100-attendance-window.mjs';

const contract = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/openapi.yaml', import.meta.url)));
const catalog = JSON.parse(readFileSync(new URL('../../backend/contracts/whatsapp-mvp/fixtures/catalog.json', import.meta.url)));
const schemas = contract.components.schemas;
const fixture = id => catalog.fixtures.find(row => row.id === id);
const jobs = contract.paths['/integrations/whatsapp/v1/jobs/tick'].post;

test('100 exposes only its two scoped job types and three conversation operations', () => {
  assert.deepEqual(jobs['x-vanrides-principals'], ['SYSTEM_JOB']);
  assert.deepEqual(jobs['x-vanrides-supported-jobs-in-100'], ['MATERIALIZE_OCCURRENCES','DUE_REMINDERS']);
  for (const id of ['JobMaterializeOccurrences','JobDueReminders','SetAttendance','AdvanceAttendance']) {
    assert.equal(fixture(id).availability, 'CARD_100_ATTENDANCE');
    assert.deepEqual(contract.components.examples[id].value, fixture(id).value);
  }
  assert.deepEqual(contract.paths['/integrations/whatsapp/v1/commands'].post['x-vanrides-supported-commands-in-100'],
    ['ADVANCE_ATTENDANCE','SET_ATTENDANCE','GET_SUMMARY']);
  assert.equal(fixture('JobRetention').availability, 'FUTURE');
  assert.equal(schemas.JobTickRequest.properties.batchLimit.maximum, 100);
  assert.equal(schemas.AdvanceAttendancePayload.additionalProperties, false);
  assert.deepEqual(schemas.AdvanceAttendancePayload.properties, {});
  assert.deepEqual(schemas.SetAttendancePayload.properties.choice.enum, ['GOING','NOT_GOING']);
  assert.ok(schemas.OutboxClaimItem.properties.purpose.enum.includes('ATTENDANCE_REMINDER'));
});
test('the actual OpenAPI attendance context drives the matching worker decision', () => {
  const ctx = fixture('AttendanceContext').value;
  const result = decideDeterministically({ type: 'REPLY', sourceMessageId: 'event_'+ctx.inboxEventId }, ctx);
  assert.deepEqual(result, { kind: 'COMMAND', commandType: 'SET_ATTENDANCE', payload: fixture('SetAttendance').value.payload });
  validateDecision(result, ctx);
  assert.equal(schemas.ConversationContextResponse.required.includes('attendanceChoice'), false);
});
test('the due-window fixture cannot touch arbitrary instances or passengers, and preserves a naturally due question', () => {
  const id = '00000000-0000-4000-8000-000000000100', instance = 'wa070_synthetic_'+'0'.repeat(32);
  for (const args of [['real',id],[instance,'not-a-uuid']]) assert.throws(() => prepareReminderWindow({}, ...args), /FIXTURE_SCOPE_INVALID/);
  let reads = 0;
  assert.deepEqual(prepareReminderWindow({ sql(query, opts) {
    reads++; assert.ok(query.includes('c.synthetic')); assert.ok(query.includes('q.passenger_id='));
    assert.equal(opts.recordOutput, false); return JSON.stringify({ id, natural: true });
  } }, instance, id), { id, seeded: 0 });
  assert.equal(reads, 1);
});
