package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WaDriverAttendanceNotices {
    public static final String POLICY = "driver-attendance-v1";
    static final String ELIGIBLE = """
        (o.purpose IN ('REMINDER','OPERATIONAL') AND o.policy_version='driver-attendance-v1' AND o.command_id IS NULL AND EXISTS (
          SELECT 1 FROM wa.driver_attendance_notice n WHERE n.outbox_id=o.id AND n.driver_id=o.recipient_identity_id
            AND n.instance_id=delivery_scope.instance_id AND n.environment=delivery_scope.environment))
        """;

    private final JdbcTemplate jdbc;
    private final WaSettings settings;

    WaDriverAttendanceNotices(JdbcTemplate jdbc, WaSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    public static UUID latestSource(JdbcTemplate jdbc, UUID driver, String instance) {
        return jdbc.query("""
            SELECT e.id FROM wa.integration_inbox e
            JOIN wa.identity_alias alias ON alias.id::text=e.payload_json->>'senderAliasId'
              AND alias.identity_id=e.identity_id AND alias.provider=e.provider AND alias.instance_id=e.instance_id
            WHERE e.identity_id=? AND e.provider='EVOLUTION' AND e.instance_id=? AND e.category='MESSAGE'
              AND alias.alias_type IN ('PN','LID') ORDER BY e.created_at DESC,e.id DESC LIMIT 1
            """, (rs, row) -> rs.getObject(1, UUID.class), driver, instance).stream().findFirst().orElse(null);
    }

    public static void saveDeparture(JdbcTemplate jdbc, UUID occurrence, UUID driver, UUID source, String text,
                                     String environment, String instance, Instant now, Instant departure) {
        UUID outbox = UUID.randomUUID();
        int inserted = jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,occurrence_id,reminder_window,
              policy_version,payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'REMINDER',?,'pre-departure',?,'TEXT',?::jsonb,?,?,?)
            ON CONFLICT DO NOTHING
            """, outbox, driver, occurrence, POLICY,
            WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(departure), Timestamp.from(now));
        if (inserted == 1) jdbc.update("""
            INSERT INTO wa.driver_attendance_notice(outbox_id,kind,occurrence_id,driver_id,recipient_source_event_id,
              environment,instance_id,deadline_at,created_at)
            VALUES (?,'DEPARTURE',?,?,?,?,?,?,?)
            """, outbox, occurrence, driver, source, environment, instance, Timestamp.from(departure), Timestamp.from(now));
    }

    public static void saveStopCanceled(JdbcTemplate jdbc, UUID occurrence, UUID driver, UUID run, UUID stop, UUID passenger,
                                        UUID source, String text, String environment, String instance, Instant now) {
        UUID outbox = UUID.randomUUID();
        Instant expires = now.plusSeconds(300);
        int inserted = jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'OPERATIONAL',?,?,'TEXT',?::jsonb,?,?,?)
            ON CONFLICT DO NOTHING
            """, outbox, driver, "attendance-stop-canceled:" + stop, POLICY,
            WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(expires), Timestamp.from(now));
        if (inserted == 1) jdbc.update("""
            INSERT INTO wa.driver_attendance_notice(outbox_id,kind,occurrence_id,driver_id,run_id,stop_id,passenger_id,
              recipient_source_event_id,environment,instance_id,deadline_at,created_at)
            VALUES (?,'STOP_CANCELED',?,?,?,?,?,?,?,?,?,?)
            """, outbox, occurrence, driver, run, stop, passenger, source, environment, instance,
            Timestamp.from(expires), Timestamp.from(now));
    }

    boolean current(UUID outbox, String instance, boolean lock) {
        var notices = notice(outbox, instance);
        if (notices.size() != 1) return false;
        var notice = notices.get(0);
        UUID driver = (UUID) notice.get("driver_id");
        boolean valid = notice.get("kind").equals("DEPARTURE") ? departureCurrent(notice, lock) : stopCanceledCurrent(notice, lock);
        return valid && !WaPassengerDelivery.paused(jdbc, driver, lock) && aliases(outbox, instance).size() == 1;
    }

    private boolean departureCurrent(Map<String, Object> notice, boolean lock) {
        return !jdbc.queryForList("""
            SELECT o.id FROM wa.routine_occurrence o
            JOIN wa.routine r ON r.id=o.routine_id AND r.driver_id=o.driver_id
            JOIN wa.profile p ON p.identity_id=o.driver_id
            WHERE o.id=? AND o.driver_id=? AND o.state='PLANNED' AND r.state='ACTIVE'
              AND p.status='ACTIVE' AND p.driver_enabled
              AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=o.id AND run.state<>'PLANNED')
            """ + (lock ? " FOR SHARE OF o,r,p" : ""), notice.get("occurrence_id"), notice.get("driver_id")).isEmpty();
    }

    private boolean stopCanceledCurrent(Map<String, Object> notice, boolean lock) {
        return !jdbc.queryForList("""
            SELECT stop.id FROM wa.operation_run run
            JOIN wa.run_stop stop ON stop.run_id=run.id AND stop.driver_id=run.driver_id
            WHERE run.id=? AND run.driver_id=? AND stop.id=? AND stop.state='CANCELED'
              AND run.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')
            """ + (lock ? " FOR SHARE OF run,stop" : ""), notice.get("run_id"), notice.get("driver_id"), notice.get("stop_id")).isEmpty();
    }

    List<Map<String, Object>> aliases(UUID outbox, String instance) {
        var notices = notice(outbox, instance);
        if (notices.size() != 1) return List.of();
        return WaPassengerDelivery.senderAlias(jdbc, (UUID) notices.get(0).get("recipient_source_event_id"),
            (UUID) notices.get(0).get("driver_id"), instance, true);
    }

    Instant deadline(UUID outbox) {
        return jdbc.query("SELECT deadline_at FROM wa.driver_attendance_notice WHERE outbox_id=? AND environment=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(), outbox, settings.environment()).stream().findFirst().orElse(Instant.MIN);
    }

    private List<Map<String, Object>> notice(UUID outbox, String instance) {
        return jdbc.queryForList("SELECT * FROM wa.driver_attendance_notice WHERE outbox_id=? AND instance_id=? AND environment=?",
            outbox, instance, settings.environment());
    }
}
