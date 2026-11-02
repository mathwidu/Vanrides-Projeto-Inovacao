import crypto from 'node:crypto';
import { demand, quote, uuid } from './070-worker-common.mjs';

// The same-JAR scenario may run at any hour. Keep the production clock intact.
// Only when the natural reminder is unavailable, seed a due question as a test
// input in this one synthetic cohort. Calendar policy at 20:00 is tested with
// a controlled application clock in PostgreSQL integration tests.
export function prepareReminderWindow(tools, instance, passenger) {
  demand(/^wa070_synthetic_[a-f0-9]{32}$/u.test(instance) && uuid(passenger), 'ATTENDANCE_FIXTURE_SCOPE_INVALID');
  const filter = `q.instance_id=${quote(instance)} AND q.passenger_id=${quote(passenger)} AND q.state='OPEN'
    AND q.environment='wa070-local' AND r.direction='OUTBOUND'
    AND q.local_date=(CURRENT_TIMESTAMP AT TIME ZONE r.timezone)::date+1
    AND EXISTS(SELECT 1 FROM wa.identity i JOIN wa.test_cohort c ON c.id=i.test_cohort_id
      WHERE i.id=q.passenger_id AND c.synthetic AND c.environment=q.environment
      AND replace(c.id::text,'-','')=substring(q.instance_id from 17))`;
  const row = tools.sql(`SELECT json_build_object('id',q.id,'natural',q.outbox_id IS NOT NULL OR
    (q.reminder_at<=CURRENT_TIMESTAMP AND q.send_until>CURRENT_TIMESTAMP+interval '90 seconds'))
    FROM wa.attendance_question q JOIN wa.routine r ON r.id=q.routine_id WHERE ${filter};`, { recordOutput: false });
  demand(row && !row.includes('\n'), 'ATTENDANCE_QUESTION_FIXTURE_MISSING');
  const current = JSON.parse(row);
  demand(uuid(current.id) && typeof current.natural === 'boolean', 'ATTENDANCE_QUESTION_FIXTURE_INVALID');
  if (current.natural) return { id: current.id, seeded: 0 };
  const id = crypto.randomUUID();
  // Reproduce the existing legitimate owners, versions and snapshot. Close the
  // first question normally and INSERT a test question, preserving immutability.
  tools.sql(`BEGIN;
    UPDATE wa.attendance_question SET state='CLOSED',version=version+1 WHERE id=${quote(current.id)}
      AND state='OPEN' AND outbox_id IS NULL AND passenger_id=${quote(passenger)} AND instance_id=${quote(instance)};
    INSERT INTO wa.attendance_question SELECT (jsonb_populate_record(NULL::wa.attendance_question,
      to_jsonb(old)||jsonb_build_object('id',${quote(id)}::uuid,'state','OPEN','version',0,
      'reminder_at',CURRENT_TIMESTAMP-interval '20 seconds','send_until',CURRENT_TIMESTAMP+interval '15 minutes'))).*
      FROM wa.attendance_question old WHERE old.id=${quote(current.id)} AND old.state='CLOSED' AND old.outbox_id IS NULL;
    UPDATE wa.attendance a SET question_ref=q.id FROM wa.attendance_question q WHERE q.id=${quote(id)}
      AND a.id=q.attendance_id AND a.question_ref=${quote(current.id)} AND a.state='PENDING';
    COMMIT;`);
  demand(tools.count(`SELECT count(*) FROM wa.attendance a JOIN wa.attendance_question q ON q.id=a.question_ref
    WHERE q.id=${quote(id)} AND q.instance_id=${quote(instance)} AND q.passenger_id=${quote(passenger)};`) === 1,
  'ATTENDANCE_WINDOW_FIXTURE_NOT_READY');
  return { id, seeded: 1 };
}
