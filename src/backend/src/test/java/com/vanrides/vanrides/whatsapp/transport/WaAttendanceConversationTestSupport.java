package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceCalendar;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/** Authenticated inbox and commands are real; recurring transport data is an isolated fixture. */
final class WaAttendanceConversationTestSupport {
    record Item(UUID question, UUID attendance, UUID occurrence, UUID routine, UUID passenger, UUID link, UUID point, UUID pin) { }
    record Step(WaConversationTestSupport.Lease lease, ObjectNode context, ObjectNode body, WaDecision decision, String text) { }
    final WaConversationTestSupport s;
    final WaTransportTestSupport h;
    final UUID driver;
    final UUID job;
    final UUID cohort;
    private int names;

    WaAttendanceConversationTestSupport(PostgreSQLContainer<?> postgres) {
        s = new WaConversationTestSupport(postgres); h = s.h;
        driver = s.actor(); s.driver(driver);
        cohort = h.jdbc.queryForObject("SELECT test_cohort_id FROM wa.identity WHERE id=?", UUID.class, driver);
        job = h.principal(WaPrincipal.Kind.SYSTEM_JOB).credentialId();
    }

    UUID passenger() {
        UUID id = s.actor();
        h.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, id);
        h.jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,preferences_json)
            VALUES (?,'PASSENGER','ACTIVE',?,'wa-mvp-v1',?,'{"ATTENDANCE_REMINDERS":false,"APPROACH_MESSAGES":true}')
            """, id, "Pessoa sintética " + ++names, Timestamp.from(h.clock.now()));
        return id;
    }

    Item question(UUID passenger, LocalDate day, String direction, String time) {
        UUID routine = UUID.randomUUID(), occurrence = UUID.randomUUID(), attendance = UUID.randomUUID();
        UUID link = UUID.randomUUID(), point = UUID.randomUUID(), member = UUID.randomUUID(), question = UUID.randomUUID();
        var existing = h.jdbc.query("SELECT id,pickup_point_id FROM wa.routine_member WHERE passenger_id=? LIMIT 1",
            (rs, row) -> rs.getObject("pickup_point_id", UUID.class), passenger);
        UUID pin = s.pending(passenger, json().set("locationMessage", json().put("degreesLatitude", 0).put("degreesLongitude", 0)), h.clock.now());
        s.finish(s.claim(pin));
        if (existing.isEmpty()) {
            h.jdbc.update("INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at,valid_from) VALUES (?,?,?,'APPROVED',?,?,?)",
                link, driver, passenger, driver, Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now().minusSeconds(60)));
            h.jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,state,approved_by,approved_at,valid_from)
                VALUES (?,?,?,?,0,0,?,'APPROVED',?,?,?)
                """, point, link, driver, passenger, pin, driver, Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now().minusSeconds(60)));
        } else {
            point = existing.get(0);
            link = h.jdbc.queryForObject("SELECT link_id FROM wa.pickup_point WHERE id=?", UUID.class, point);
        }
        h.jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,state,
              policy_version,destination_latitude,destination_longitude)
            VALUES (?,?,?, ?,ARRAY[1,2,3,4,5,6,7]::smallint[],?::time,'America/Sao_Paulo','ACTIVE','wa-mvp-v1',0,0)
            """, routine, driver, direction.equals("OUTBOUND") ? "Ida de teste" : "Volta de teste", direction, time);
        h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,1)",
            member, routine, driver, passenger, link, point);
        h.jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,?,?)", occurrence,
            routine, driver, day, Timestamp.from(day.atTime(LocalTime.parse(time)).atZone(ZoneId.of("America/Sao_Paulo")).toInstant()));
        h.jdbc.update("UPDATE wa.routine_occurrence SET schedule_snapshot=(SELECT jsonb_build_object('name',r.name,'direction',r.direction,'local_time',r.local_time::text,'timezone',r.timezone) FROM wa.routine r WHERE r.id=?) WHERE id=?",
            routine, occurrence);
        h.jdbc.update("INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,question_ref) VALUES (?,?,?,?,?)",
            attendance, routine, passenger, day, question);
        h.jdbc.update("""
            INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,local_date,
              routine_name,direction,local_time,timezone,member_id,link_id,pickup_point_id,instance_id,environment,job_credential_id,
              link_version,member_version,pickup_version,schedule_hash,intent_hash,opens_at,reminder_at,send_until,answer_until,created_at)
            VALUES (?,?,?,?,?,?,?, ?,?,?::time,'America/Sao_Paulo',?,?,?,?,?,?,0,0,0,?,?, ?,?,?,?,?)
            """, question, attendance, occurrence, routine, driver, passenger, day,
            direction.equals("OUTBOUND") ? "Ida de teste" : "Volta de teste", direction, time,
            member, link, point, WaTransportTestSupport.INSTANCE, h.settings.environment(), job,
            WaAttendanceCalendar.scheduleHash(h.jdbc, routine), WaAttendanceCalendar.intentHash(h.jdbc, routine),
            Timestamp.from(h.clock.now().minusSeconds(60)), Timestamp.from(h.clock.now().minusSeconds(30)),
            Timestamp.from(h.clock.now().plusSeconds(120)), Timestamp.from(day.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()),
            Timestamp.from(h.clock.now().minusSeconds(60)));
        return new Item(question, attendance, occurrence, routine, passenger, link, point, pin);
    }

    String sent(Item item) {
        UUID box = UUID.randomUUID(); String external = "synthetic-attendance-" + UUID.randomUUID();
        UUID alias = h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=? AND instance_id=?",
            UUID.class, item.passenger(), WaTransportTestSupport.INSTANCE);
        h.jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,occurrence_id,reminder_window,policy_version,
              payload_json,state,not_before,expires_at,created_at)
            VALUES (?,?,'REMINDER',?,'synthetic-attendance','attendance-reminder-v1','{"text":"Pergunta sintética"}','SUBMITTED',?,?,?)
            """, box, item.passenger(), item.occurrence(), Timestamp.from(h.clock.now().minusSeconds(30)),
            Timestamp.from(h.clock.now().plusSeconds(120)), Timestamp.from(h.clock.now().minusSeconds(30)));
        h.jdbc.update("UPDATE wa.attendance_question SET outbox_id=?,source_inbox_event_id=?,sender_alias_id=?,version=version+1 WHERE id=?",
            box, item.pin(), alias, item.question());
        h.jdbc.update("""
            INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,state,instance_id,
              external_message_id,started_at,completed_at,credential_id,submit_before)
            VALUES (?,?,1,1,?,'SUBMITTED',?,?,?,?,?,?)
            """, UUID.randomUUID(), box, WaCrypto.digest(UUID.randomUUID().toString()), WaTransportTestSupport.INSTANCE, external,
            Timestamp.from(h.clock.now().minusSeconds(20)), Timestamp.from(h.clock.now().minusSeconds(19)),
            s.delivery.credentialId(), Timestamp.from(h.clock.now().plusSeconds(10)));
        return external;
    }

    Step send(UUID actor, String text) { return execute(s.text(actor, text), true); }
    Step quote(UUID actor, String text, String external) {
        ObjectNode message = json();
        message.putObject("extendedTextMessage").put("text", text).putObject("contextInfo").put("stanzaId", external);
        return execute(s.claim(s.pending(actor, message, h.clock.now())), true);
    }

    Step execute(WaConversationTestSupport.Lease lease, boolean finish) {
        ObjectNode context = s.commands.context(s.worker, lease.id(), lease.token());
        var allowed = new ArrayList<String>();
        context.path("allowedCommands").forEach(value -> {
            if (Set.of("ADVANCE_ATTENDANCE", "SET_ATTENDANCE", "GET_SUMMARY").contains(value.asText())) allowed.add(value.asText());
        });
        assertEquals(1, allowed.size(), context.toString());
        ObjectNode payload = json();
        for (var option : context.path("options")) {
            String field = option.path("kind").asText().equals("QUESTION") ? "questionRef" : "summaryRef";
            payload.put(field, option.path("ref").asText());
        }
        if (context.has("attendanceChoice")) payload.put("choice", context.path("attendanceChoice").asText());
        ObjectNode body = s.body(lease, allowed.get(0), payload).put("expectedVersion", context.path("entityVersion").asLong());
        WaDecision result = s.execute(lease, body, "synthetic-attendance-command-" + lease.id());
        String reply = s.string("SELECT o.payload_json->>'text' FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE r.inbox_event_id=?", lease.id());
        if (finish) s.finish(lease);
        return new Step(lease, context, body, result, reply);
    }

    String state(Item item) { return s.string("SELECT state FROM wa.attendance WHERE id=?", item.attendance()); }
    UUID starting(Item item, String attendanceSnapshot) {
        UUID run = UUID.randomUUID();
        ObjectNode snapshot = json().put("preserved", true);
        snapshot.putObject("passengerNames").put(item.passenger().toString(),
            s.string("SELECT display_name FROM wa.profile WHERE identity_id=?", item.passenger()));
        h.jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at,started_at)
            VALUES (?,?,?,?,?,'STARTING','synthetic-attendance',?::jsonb,?,?)
            """, run, item.occurrence(), item.routine(), driver,
            h.jdbc.queryForObject("SELECT local_date FROM wa.routine_occurrence WHERE id=?", LocalDate.class, item.occurrence()),
            snapshot.toString(), Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now()));
        h.jdbc.update("""
            INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
              latitude_snapshot,longitude_snapshot,attendance_snapshot) VALUES (?,?,?,?,?,1,?,0,0,0,?)
            """, UUID.randomUUID(), run, driver, item.passenger(), UUID.randomUUID(), item.point(), attendanceSnapshot);
        return run;
    }

    static ObjectNode json() { return WaJson.MAPPER.createObjectNode(); }
}
