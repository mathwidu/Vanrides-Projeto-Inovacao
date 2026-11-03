package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/** Synthetic admitted memberships isolate calendar/job behavior from wizard090. */
final class WaAttendanceCalendarTestSupport {
    final WaTransportTestSupport h;
    final WaAttendanceJobs jobs;
    final WaPrincipal job;
    final UUID driver;
    final UUID routine;
    final UUID passenger;

    WaAttendanceCalendarTestSupport(PostgreSQLContainer<?> postgres) {
        h = new WaTransportTestSupport(postgres, true);
        h.time.set(Instant.parse("2030-01-01T22:00:00Z"));
        jobs = h.proxy(new WaAttendanceJobs(h.jdbc, h.access, h.settings, h.clock));
        job = h.principal(WaPrincipal.Kind.SYSTEM_JOB);
        h.jdbc.update("UPDATE wa.service_credential SET expires_at='2035-01-01T00:00:00Z' WHERE id=?", job.credentialId());
        driver = identity("DRIVER");
        h.jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        routine = routine("Ida habitual", "OUTBOUND", "06:00", "America/Sao_Paulo");
        passenger = member(routine, true);
        h.time.set(Instant.parse("2030-01-01T23:00:00Z"));
    }

    UUID identity(String role) {
        UUID identity = h.identity();
        if (driver != null) {
            h.jdbc.update("UPDATE wa.identity SET test_cohort_id=(SELECT test_cohort_id FROM wa.identity WHERE id=?) WHERE id=?",
                    driver, identity);
        }
        h.jdbc.update("""
                INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,created_at,updated_at)
                VALUES (?,?,'ACTIVE','Pessoa Sintética','wa-mvp-v1',?,?,?,?)
                """, identity, role, Timestamp.from(h.clock.now()), role.equals("DRIVER"),
                Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now()));
        UUID alias = UUID.randomUUID();
        h.jdbc.update("""
                INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
                VALUES (?,?,'EVOLUTION',?,'PN',?)
                """, alias, identity, WaTransportTestSupport.INSTANCE, "synthetic-" + identity + "@s.whatsapp.net");
        h.event(identity, json().put("senderAliasId", alias.toString()).put("text", "Mensagem sintética."));
        return identity;
    }

    UUID routine(String name, String direction, String time, String timezone) {
        UUID id = UUID.randomUUID();
        h.jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,state,
                  destination_latitude,destination_longitude,policy_version,created_at,updated_at)
                VALUES (?,?,?,?,ARRAY[1,2,3,4,5,6,7]::smallint[],?::time,?,'ACTIVE',0,0,'attendance-test',?,?)
                """, id, driver, name, direction, time, timezone, Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now()));
        return id;
    }

    UUID member(UUID target, boolean reminders) {
        UUID person = identity("PASSENGER");
        h.jdbc.update("UPDATE wa.profile SET preferences_json=jsonb_build_object('ATTENDANCE_REMINDERS',?::boolean) WHERE identity_id=?",
                reminders, person);
        UUID link = UUID.randomUUID();
        UUID point = UUID.randomUUID();
        UUID event = h.event(person, json().put("latitude", 0).put("longitude", 0));
        Timestamp now = Timestamp.from(h.clock.now());
        h.jdbc.update("""
                INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at,valid_from,created_at,requested_source_event_id)
                VALUES (?,?,?,'APPROVED',?,?,?,?,?)
                """, link, driver, person, driver, now, now, now, event);
        h.jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,
                  state,approved_by,approved_at,valid_from,created_at)
                VALUES (?,?,?,?,0,0,?,'APPROVED',?,?,?,?)
                """, point, link, driver, person, event, driver, now, now, now);
        h.jdbc.update("""
                INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order,weekdays)
                VALUES (?,?,?,?,?,?,?,ARRAY[1,2,3,4,5,6,7]::smallint[])
                """, UUID.randomUUID(), target, driver, person, link, point,
                1 + count("SELECT count(*) FROM wa.routine_member WHERE routine_id=?", target));
        return person;
    }

    ObjectNode tick(String type, int limit, String cursor) {
        ObjectNode request = json().put("jobType", type)
                .put("windowKey", h.clock.now().truncatedTo(ChronoUnit.MINUTES).toString()).put("batchLimit", limit);
        if (cursor != null) {
            request.put("cursor", cursor);
        }
        return jobs.tick(job, request);
    }

    void materialize() {
        String cursor = null;
        for (int page = 0; page < 100; page++) {
            ObjectNode result = tick("MATERIALIZE_OCCURRENCES", 100, cursor);
            if (result.path("nextCursor").isNull()) {
                return;
            }
            cursor = result.path("nextCursor").asText();
        }
        throw new AssertionError("Calendar cursor did not finish");
    }

    void nextWindow() {
        h.time.advance(60);
    }

    UUID question(UUID person, String date) {
        return h.jdbc.queryForObject("""
                SELECT id FROM wa.attendance_question WHERE passenger_id=? AND local_date=?::date
                  AND state IN ('OPEN','ANSWERED')
                """, UUID.class, person, date);
    }

    long count(String sql, Object... args) {
        return h.jdbc.queryForObject(sql, Long.class, args);
    }

    static ObjectNode json() {
        return WaJson.object("{}");
    }
}
