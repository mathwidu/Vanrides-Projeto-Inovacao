package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

final class WaPrivacyTestSupport {
    static final String ENV = "privacy-test", INSTANCE = "synthetic-privacy";
    static final WaPrivacyService.Scope SCOPE = new WaPrivacyService.Scope(ENV, INSTANCE);
    static final String NAME = "PESSOA_ALFA_PRIVADA", REFERENCE = "REFERENCIA_ALFA_PRIVADA";
    static final String ALIAS = "000000000000001@s.whatsapp.net";
    final JdbcTemplate jdbc;
    final DataSourceTransactionManager transactions;
    final Time time = new Time();
    final WaClock clock = new WaClock(time);
    final Journal journal = new Journal();
    final UUID cohort = UUID.randomUUID(), driver = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
    final UUID routine = UUID.randomUUID(), occurrence = UUID.randomUUID(), run = UUID.randomUUID();
    final Map<UUID, UUID> points = new LinkedHashMap<>(), inboxes = new LinkedHashMap<>(), aliases = new LinkedHashMap<>();

    WaPrivacyTestSupport(PostgreSQLContainer<?> postgres, boolean terminal) {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source); transactions = new DataSourceTransactionManager(source);
        jdbc.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        var flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,true)", cohort, ENV);
        person(driver, "Motorista sintético", "000000000000000@s.whatsapp.net", "DRIVER");
        person(first, NAME, ALIAS, "PASSENGER");
        person(second, "PESSOA_BETA_INTACTA", "000000000000002@s.whatsapp.net", "PASSENGER");
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,
              destination_longitude,state,policy_version,stop_order_pending)
            VALUES (?,?,'ROTINA_DRIVER_PRIVADA','OUTBOUND',ARRAY[1,2,3,4,5,6,7]::smallint[],'09:00','UTC',10,11,'ACTIVE','privacy-test',false)
            """, routine, driver);
        pickup(first, 12.3456, REFERENCE); pickup(second, 23.4567, "REFERENCIA_BETA_INTACTA");
        jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)
            VALUES (?,?,?,'2030-01-01',?)
            """, occurrence, routine, driver, timestamp());
        ObjectNode snapshot = WaJson.object("{}");
        snapshot.put("rosterDigest", "f".repeat(64));
        snapshot.set("routine", WaJson.object(string("SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=?", routine)));
        snapshot.set("schedule", WaJson.object("{}").put("name", "ROTINA_DRIVER_PRIVADA").put("direction", "OUTBOUND"));
        var participants = snapshot.putArray("participants");
        var names = snapshot.putObject("passengerNames");
        for (UUID person : java.util.List.of(first, second)) {
            boolean alpha = person.equals(first);
            String name = alpha ? NAME : "PESSOA_BETA_INTACTA";
            names.put(person.toString(), name);
            participants.addObject().put("passenger", person.toString()).put("point", points.get(person).toString())
                .put("name", name).put("latitude", alpha ? 12.3456 : 23.4567).put("longitude", 45.6789)
                .put("reference", alpha ? REFERENCE : "REFERENCIA_BETA_INTACTA")
                .put("order", alpha ? 1 : 2).put("attendance", "GOING");
            jdbc.update("""
                INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,state,actor_identity_id,answered_at)
                VALUES (?,?,?,'2030-01-01','GOING',?,?)
                """, UUID.randomUUID(), routine, person, person, timestamp());
        }
        jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,
              snapshot_json,snapshot_at,started_at,completed_at)
            VALUES (?,?,?,?,'2030-01-01',?,'privacy-test',?::jsonb,?,?,?)
            """, run, occurrence, routine, driver, terminal ? "COMPLETED" : "TRACKING", snapshot.toString(), timestamp(), timestamp(), terminal ? timestamp() : null);
        for (UUID person : java.util.List.of(first, second)) {
            boolean alpha = person.equals(first);
            jdbc.update("""
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
                  latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot,state)
                VALUES (?,?,?,?,?,?,?,0,?,45.6789,?,'GOING',?)
                """, UUID.randomUUID(), run, driver, person, UUID.randomUUID(), alpha ? 1 : 2, points.get(person),
                alpha ? 12.3456 : 23.4567, alpha ? REFERENCE : "REFERENCIA_BETA_INTACTA", terminal ? "PASSED" : "WAITING");
            box(person, alpha ? NAME + " " + REFERENCE + " 12.3456" : "CONTEUDO_BETA_INTACTO", null);
        }
        box(driver, NAME + ", PESSOA_BETA_INTACTA", occurrence);
        jdbc.update("""
            INSERT INTO wa.common_pickup_point(id,driver_id,name,latitude,longitude,source_inbox_event_id)
            VALUES (?,?,'PONTO_DRIVER_PRIVADO',56.789,67.891,?)
            """, UUID.randomUUID(), driver, inboxes.get(driver));
        UUID job = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at)
            VALUES (?,'SYSTEM_JOB',?,?,?,?,?)
            """, job, ENV, "e".repeat(64), INSTANCE, timestamp(), Timestamp.from(time.now.plusSeconds(3600)));
        for (UUID passenger : java.util.List.of(first, second)) question(passenger, job);

        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at)
            VALUES (?,'PAUSED',?::jsonb,?,?)
            """, first, context("passengerDraft", WaJson.object("{}").put("name", NAME).put("reference", REFERENCE).put("latitude", 12.3456)), inboxes.get(first), timestamp());
        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,context_json,updated_at) VALUES (?,?::jsonb,?)
            """, driver, context("attendanceDraft", WaJson.object("{}").put("occurrence", occurrence.toString()).put("rosterText", NAME)), timestamp());
        jdbc.update("""
            INSERT INTO wa.occurrence_roster_override(id,occurrence_id,routine_id,driver_id,passenger_id,local_date,included,
              revision,reason,actor_identity_id,source_inbox_event_id,created_at)
            VALUES (?,?,?,?,?,'2030-01-01',true,1,?,?,?,?)
            """, UUID.randomUUID(), occurrence, routine, driver, first, REFERENCE, driver, inboxes.get(driver), timestamp());
        jdbc.update("INSERT INTO wa.audit_event(id,actor_identity_id,actor_kind,action,correlation_id,created_at) VALUES (?,?,'PASSENGER','ATTENDANCE_CHANGED','synthetic-audit',?)",
            UUID.randomUUID(), first, timestamp());
    }

    WaPrivacyService service() { return service(WaPrivacyPolicy.syntheticTests(), journal); }
    WaPrivacyService service(WaPrivacyPolicy policy, WaPrivacyJournal port) {
        return new WaPrivacyService(jdbc, transactions, clock, policy, port);
    }
    Timestamp timestamp() { return Timestamp.from(time.now); }
    String string(String sql, Object... args) { return jdbc.queryForObject(sql, String.class, args); }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM wa." + table, Long.class); }

    private static String context(String key, ObjectNode value) {
        ObjectNode context = WaJson.object("{}"); context.set(key, value); return context.toString();
    }

    private void person(UUID id, String name, String address, String role) {
        UUID alias = UUID.randomUUID(), event = UUID.randomUUID(); aliases.put(id, alias); inboxes.put(id, event);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", id, cohort);
        jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN',?)", alias, id, INSTANCE, address);
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,created_at,updated_at)
            VALUES (?,?,'ACTIVE',?,'wa-mvp-v1',?,?,?,?)
            """, id, role, name, timestamp(), role.equals("DRIVER"), timestamp(), timestamp());
        ObjectNode payload = WaJson.object("{}").put("senderAliasId", alias.toString()).put("text", name).put("latitude", id.equals(first) ? 12.3456 : 23.4567);
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,payload_json,created_at)
            VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','COMPLETED',?::jsonb,?)
            """, event, id, INSTANCE, "synthetic-message-" + event, payload.toString(), timestamp());
    }

    private void pickup(UUID passenger, double latitude, String reference) {
        UUID link = UUID.randomUUID(), point = UUID.randomUUID(); points.put(passenger, point);
        jdbc.update("""
            INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at,requested_source_event_id)
            VALUES (?,?,?,'APPROVED',?,?,?)
            """, link, driver, passenger, driver, timestamp(), inboxes.get(passenger));
        jdbc.update("""
            INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,
              source_inbox_event_id,state,approved_by,approved_at)
            VALUES (?,?,?,?,?,45.6789,?,?,?,'APPROVED',?,?)
            """, point, link, driver, passenger, latitude, reference, reference, inboxes.get(passenger), driver, timestamp());
        jdbc.update("""
            INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order)
            VALUES (?,?,?,?,?,?,?)
            """, UUID.randomUUID(), routine, driver, passenger, link, point, passenger.equals(first) ? 1 : 2);
    }

    private void question(UUID passenger, UUID job) {
        jdbc.update("""
            INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,local_date,
              routine_name,direction,local_time,timezone,member_id,link_id,pickup_point_id,instance_id,environment,
              job_credential_id,link_version,member_version,pickup_version,schedule_hash,intent_hash,
              opens_at,reminder_at,send_until,answer_until)
            SELECT ?,a.id,?,a.routine_id,m.driver_id,a.passenger_id,a.local_date,
              'ROTINA_DRIVER_PRIVADA','OUTBOUND','09:00','UTC',m.id,m.link_id,m.pickup_point_id,?,?,?,0,0,0,?,?,
              ?::timestamptz-INTERVAL '2 hours',?::timestamptz-INTERVAL '1 hour',
              ?::timestamptz+INTERVAL '1 hour',?::timestamptz+INTERVAL '12 hours'
            FROM wa.attendance a JOIN wa.routine_member m ON m.routine_id=a.routine_id AND m.passenger_id=a.passenger_id
            WHERE a.passenger_id=?
            """, UUID.randomUUID(), occurrence, INSTANCE, ENV, job, "d".repeat(64), "c".repeat(64),
            timestamp(), timestamp(), timestamp(), timestamp(), passenger);
    }

    private void box(UUID recipient, String text, UUID day) {
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,occurrence_id,reminder_window,
              policy_version,payload_json,state,created_at,not_before,expires_at)
            VALUES (?,?,?,?,?,?,'attendance-v1',?::jsonb,'DELIVERED',?,?,?)
            """, UUID.randomUUID(), recipient, day == null ? "OPERATIONAL" : "REMINDER", "synthetic-" + recipient,
            day, day == null ? null : "pre-departure", WaJson.object("{}").put("type", "TEXT").put("text", text).toString(),
            timestamp(), timestamp(), Timestamp.from(time.now.plusSeconds(300)));
    }

    static final class Time extends Clock {
        Instant now = Instant.parse("2030-01-01T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void days(int days) { now = now.plusSeconds(days * 86400L); }
    }
    static final class Journal implements WaPrivacyJournal {
        boolean available = true;
        final Map<UUID, Ack> records = new LinkedHashMap<>();
        public Ack append(Entry entry) {
            if (!available) throw new IllegalStateException("SYNTHETIC_JOURNAL_UNAVAILABLE");
            return records.computeIfAbsent(entry.eventId(), id -> new Ack(id, entry.payloadHash(), records.size() + 1,
                WaCrypto.digest("synthetic-test-journal:" + entry.payloadHash())));
        }
        public boolean verify(Entry entry, Ack ack) { return available && ack.equals(records.get(entry.eventId())); }
    }
}
