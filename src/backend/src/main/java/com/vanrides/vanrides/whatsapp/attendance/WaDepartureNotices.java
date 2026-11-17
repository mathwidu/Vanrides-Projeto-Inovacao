package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceRoster.Group;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceRoster.Member;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaDriverAttendanceNotices;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaDepartureNotices {
    private static final String DUE = """
            FROM wa.routine_occurrence o JOIN wa.routine r ON r.id=o.routine_id AND r.driver_id=o.driver_id AND r.state='ACTIVE'
            JOIN wa.profile driver ON driver.identity_id=o.driver_id AND driver.role='DRIVER' AND driver.status='ACTIVE' AND driver.driver_enabled
              AND driver.notice_version IS NOT NULL
            JOIN wa.identity di ON di.id=o.driver_id
            LEFT JOIN wa.test_cohort cohort ON cohort.id=di.test_cohort_id
            WHERE o.state='PLANNED' AND o.calendar_eligible
              AND o.scheduled_at-INTERVAL '30 minutes'<=? AND o.scheduled_at>?
              AND extract(isodow FROM o.local_date)=ANY(r.weekdays)
              AND (cohort.environment=? OR (?=false AND di.test_cohort_id IS NULL))
              AND EXISTS(SELECT 1 FROM wa.routine_member m WHERE m.routine_id=r.id AND m.state='ACTIVE')
              AND EXISTS(SELECT 1 FROM wa.integration_inbox source JOIN wa.identity_alias alias
                ON alias.id::text=source.payload_json->>'senderAliasId' AND alias.identity_id=source.identity_id
                WHERE source.identity_id=o.driver_id AND source.instance_id=?
                  AND source.provider='EVOLUTION' AND source.category='MESSAGE'
                  AND alias.instance_id=source.instance_id AND alias.provider=source.provider
                  AND alias.alias_type IN ('PN','LID'))
              AND NOT EXISTS(SELECT 1 FROM wa.conversation_state paused
                WHERE paused.identity_id=o.driver_id AND paused.stage='PAUSED')
              AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=o.id AND run.state<>'PLANNED')
              AND NOT EXISTS(SELECT 1 FROM wa.message_outbox box WHERE box.purpose='REMINDER'
                AND box.occurrence_id=o.id AND box.recipient_identity_id=o.driver_id AND box.reminder_window='pre-departure')
            """;
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaAttendanceScope scope;

    WaDepartureNotices(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
        scope = new WaAttendanceScope(jdbc, settings);
    }

    List<Map<String, Object>> candidates(String instance, String after, int limit) {
        Timestamp now = Timestamp.from(clock.now());
        return jdbc.queryForList("SELECT o.id,o.id::text work_key,'DEPARTURE' kind " + DUE
                + " AND o.id::text>? ORDER BY work_key LIMIT ?",
                now, now, settings.environment(), settings.cohortOnly(), instance, after, limit);
    }

    void apply(Map<String, Object> work, String instance) {
        Timestamp now = Timestamp.from(clock.now());
        var occurrences = jdbc.queryForList("""
                SELECT o.id,o.driver_id,o.routine_id,o.local_date,o.scheduled_at,r.name,r.direction,r.local_time,r.timezone
                """ + DUE + " AND o.id=? FOR UPDATE OF o",
                now, now, settings.environment(), settings.cohortOnly(), instance, work.get("id"));
        if (occurrences.size() != 1) {
            return;
        }
        Map<String, Object> occurrence = occurrences.get(0);
        UUID driver = (UUID) occurrence.get("driver_id");
        UUID source = WaDriverAttendanceNotices.latestSource(jdbc, driver, instance);
        if (source == null || !scope.driver(driver, instance)) {
            return;
        }
        var members = WaAttendanceRoster.beforeStart(jdbc, driver, (UUID) occurrence.get("id"),
                (UUID) occurrence.get("routine_id"), ((Date) occurrence.get("local_date")).toLocalDate());
        if (members.isEmpty() || members.size() > 1000) {
            return;
        }
        String header = "Lista da viagem de " + WaAttendanceLabels.day(((Date) occurrence.get("local_date")).toLocalDate(),
                clock.now(), (String) occurrence.get("timezone")) + ", " + WaAttendanceLabels.trip((String) occurrence.get("direction"),
                ((Time) occurrence.get("local_time")).toLocalTime(), (String) occurrence.get("name"));
        String text = text(header, members, true);
        if (text.codePointCount(0, text.length()) > 2000) {
            text = text(header, members, false);
        }
        WaDriverAttendanceNotices.saveDeparture(jdbc, (UUID) occurrence.get("id"), driver, source, text,
                settings.environment(), instance, clock.now(), ((Timestamp) occurrence.get("scheduled_at")).toInstant());
    }

    private static String text(String header, List<Member> members, boolean names) {
        StringBuilder text = new StringBuilder(header);
        group(text, "Confirmados", members, Group.CONFIRMED, names);
        group(text, "Pendentes, continuam previstos", members, Group.PENDING, names);
        group(text, "Não vão", members, Group.ABSENT, names);
        if (!names) {
            text.append("\nEnvie PRESENÇAS para ver os nomes.");
        }
        return text.append("\nPara começar, envie Começar viagem.").toString();
    }

    private static void group(StringBuilder text, String label, List<Member> members, Group group, boolean names) {
        var selected = members.stream().filter(member -> member.group() == group).map(Member::name).toList();
        if (selected.isEmpty()) {
            return;
        }
        text.append('\n').append(label);
        if (names) {
            text.append(" (").append(selected.size()).append("): ").append(String.join(", ", selected));
        } else {
            text.append(": ").append(selected.size());
        }
    }
}
