package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaAttendanceMaterializer {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaAttendanceScope scope;

    WaAttendanceMaterializer(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
        this.scope = new WaAttendanceScope(jdbc, settings);
    }

    void apply(Map<String, Object> work, String instance, UUID credential) {
        UUID routine = (UUID) work.get("routine_id");
        UUID driver = (UUID) work.get("driver_id");
        LocalDate date = ((Date) work.get("local_date")).toLocalDate();
        var routines = jdbc.queryForList("SELECT * FROM wa.routine WHERE id=? FOR UPDATE", routine);
        if (routines.size() != 1 || !scope.driver(driver, instance)) {
            return;
        }
        Map<String, Object> row = routines.get(0);
        if (!row.get("state").equals("ACTIVE")) {
            return;
        }
        if (!calendarEligible(routine, date, (String) row.get("timezone"))) {
            removeDay(work, date);
            return;
        }
        Instant scheduled;
        Instant reminder;
        try {
            scheduled = WaAttendanceCalendar.scheduledAt(date, ((Time) row.get("local_time")).toLocalTime(),
                    (String) row.get("timezone"));
            reminder = WaAttendanceCalendar.reminderAt(date, (String) row.get("timezone"));
        } catch (DateTimeException | WaProblem failure) {
            return;
        }
        Timestamp now = Timestamp.from(clock.now());
        jdbc.update("""
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at,
                  materialized_routine_version,schedule_snapshot)
                VALUES (?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(routine_id,local_date) DO NOTHING
                """, UUID.randomUUID(), routine, driver, Date.valueOf(date), Timestamp.from(scheduled), now,
                row.get("version"), snapshot(row));
        Map<String, Object> occurrence = jdbc.queryForMap(
                "SELECT * FROM wa.routine_occurrence WHERE routine_id=? AND local_date=? FOR UPDATE", routine, Date.valueOf(date));
        UUID occurrenceId = (UUID) occurrence.get("id");
        if (!occurrence.get("state").equals("PLANNED") || started(occurrenceId)) {
            return;
        }
        jdbc.update("""
                UPDATE wa.routine_occurrence SET scheduled_at=?,materialized_routine_version=?,schedule_snapshot=?::jsonb,calendar_eligible=true,
                  version=version+CASE WHEN scheduled_at<>? THEN 1 ELSE 0 END WHERE id=?
                """, Timestamp.from(scheduled), row.get("version"), snapshot(row), Timestamp.from(scheduled), occurrenceId);
        UUID member = (UUID) work.get("member_id");
        if (member == null) {
            return;
        }
        var members = jdbc.queryForList("""
                SELECT m.*,l.version link_version,p.version pickup_version FROM wa.routine_member m
                JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.state='APPROVED'
                JOIN wa.integration_inbox admission ON admission.id=l.requested_source_event_id
                  AND admission.identity_id=m.passenger_id AND admission.instance_id=?
                JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.state='APPROVED'
                WHERE m.id=? AND m.routine_id=? AND m.state='ACTIVE' AND ?=ANY(m.weekdays)
                  AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
                  AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
                """, instance, member, routine, date.getDayOfWeek().getValue(), now, now, now, now);
        if (members.size() != 1) {
            return;
        }
        Map<String, Object> membership = members.get(0);
        UUID passenger = (UUID) membership.get("passenger_id");
        if (!scope.pair(driver, passenger)) {
            return;
        }
        jdbc.update("""
                INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,created_at)
                VALUES (?,?,?,?,?) ON CONFLICT(routine_id,passenger_id,local_date) DO NOTHING
                """, UUID.randomUUID(), routine, passenger, Date.valueOf(date), now);
        Map<String, Object> attendance = jdbc.queryForMap("""
                SELECT * FROM wa.attendance WHERE routine_id=? AND passenger_id=? AND local_date=? FOR UPDATE
                """, routine, passenger, Date.valueOf(date));
        question(attendance, membership, occurrenceId, driver, routine, date, scheduled, reminder,
                row, instance, credential);
    }

    private void question(Map<String, Object> attendance, Map<String, Object> member, UUID occurrence,
                          UUID driver, UUID routine, LocalDate date, Instant scheduled, Instant reminder,
                          Map<String, Object> routineData, String instance, UUID credential) {
        UUID attendanceId = (UUID) attendance.get("id");
        String hash = WaAttendanceCalendar.scheduleHash(jdbc, routine);
        String intentHash = WaAttendanceCalendar.intentHash(jdbc, routine);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT * FROM wa.attendance_question WHERE attendance_id=? AND state IN ('OPEN','ANSWERED') FOR UPDATE
                """, attendanceId);
        if (!existing.isEmpty() && same(existing.get(0), member, hash, instance)) {
            return;
        }
        if (!existing.isEmpty() && !existing.get(0).get("intent_hash").equals(intentHash)
                && attendance.get("state").equals("GOING")) {
            jdbc.update("""
                    UPDATE wa.attendance SET state='PENDING',actor_identity_id=NULL,credential_id=NULL,
                      answered_at=NULL,version=version+1 WHERE id=?
                    """, attendanceId);
        }
        jdbc.update("""
                UPDATE wa.attendance_question SET state='CLOSED',version=version+1
                WHERE attendance_id=? AND state IN ('OPEN','ANSWERED')
                """, attendanceId);
        Instant until = WaAttendanceCalendar.answerUntil(date, (String) routineData.get("timezone"));
        if (!until.isAfter(clock.now())) {
            return;
        }
        UUID question = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.now());
        jdbc.update("""
                INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,
                  local_date,routine_name,direction,local_time,timezone,member_id,link_id,pickup_point_id,
                  instance_id,environment,job_credential_id,
                  link_version,member_version,pickup_version,schedule_hash,intent_hash,opens_at,reminder_at,send_until,answer_until,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, question, attendanceId, occurrence, routine, driver, attendance.get("passenger_id"), Date.valueOf(date),
                routineData.get("name"), routineData.get("direction"), routineData.get("local_time"), routineData.get("timezone"),
                member.get("id"), member.get("link_id"), member.get("pickup_point_id"), instance, settings.environment(), credential,
                member.get("link_version"), member.get("version"), member.get("pickup_version"), hash, intentHash, now,
                Timestamp.from(reminder), Timestamp.from(WaAttendanceCalendar.sendUntil(reminder, scheduled)),
                Timestamp.from(until), now);
        jdbc.update("UPDATE wa.attendance SET question_ref=? WHERE id=?", question, attendanceId);
    }

    private boolean same(Map<String, Object> q, Map<String, Object> member, String hash, String instance) {
        return q.get("schedule_hash").equals(hash) && q.get("member_id").equals(member.get("id"))
                && q.get("link_version").equals(member.get("link_version"))
                && q.get("member_version").equals(member.get("version"))
                && q.get("pickup_version").equals(member.get("pickup_version"))
                && q.get("instance_id").equals(instance) && q.get("environment").equals(settings.environment());
    }

    private boolean started(UUID occurrence) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.operation_run WHERE occurrence_id=? AND state<>'PLANNED')
                """, Boolean.class, occurrence));
    }

    private String snapshot(Map<String, Object> routine) {
        return WaJson.write(Map.of("name", routine.get("name"), "direction", routine.get("direction"),
                "local_time", ((Time) routine.get("local_time")).toLocalTime().toString(),
                "timezone", routine.get("timezone")));
    }

    private boolean calendarEligible(UUID routine, LocalDate date, String timezone) {
        try {
            LocalDate today = clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
            return !date.isBefore(today) && date.isBefore(today.plusDays(7))
                    && Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT ?=ANY(weekdays) FROM wa.routine WHERE id=?", Boolean.class,
                            date.getDayOfWeek().getValue(), routine));
        } catch (DateTimeException failure) {
            return false;
        }
    }

    private void removeDay(Map<String, Object> work, LocalDate date) {
        var occurrences = jdbc.queryForList("""
                SELECT id,state FROM wa.routine_occurrence WHERE routine_id=? AND local_date=? FOR UPDATE
                """, work.get("routine_id"), Date.valueOf(date));
        if (occurrences.isEmpty() || !occurrences.get(0).get("state").equals("PLANNED")) {
            return;
        }
        UUID occurrence = (UUID) occurrences.get(0).get("id");
        if (started(occurrence)) {
            return;
        }
        jdbc.update("""
                UPDATE wa.routine_occurrence SET calendar_eligible=false,
                  version=version+CASE WHEN calendar_eligible THEN 1 ELSE 0 END WHERE id=?
                """, occurrence);
        if (work.get("passenger_id") != null) {
            jdbc.update("""
                    UPDATE wa.attendance_question SET state='CLOSED',version=version+1
                    WHERE occurrence_id=? AND passenger_id=? AND state IN ('OPEN','ANSWERED')
                    """, occurrence, work.get("passenger_id"));
        }
    }
}
