package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.sql.Timestamp;
import java.util.UUID;

/** A nominal presence list is rendered only for the owning driver. */
final class WaAttendanceSummary {
    private final WaAttendanceQuestions questions;
    WaAttendanceSummary(WaAttendanceQuestions questions) { this.questions = questions; }

    ArrayNode occurrences(UUID driver, String instance) {
        ArrayNode result = WaJson.MAPPER.createArrayNode();
        questions.jdbc.query("""
            SELECT (to_jsonb(o)||jsonb_build_object(
              'routineName',CASE WHEN frozen.value THEN COALESCE(o.schedule_snapshot->>'name',history.routine_name,r.name) ELSE r.name END,
              'direction',CASE WHEN frozen.value THEN COALESCE(o.schedule_snapshot->>'direction',history.direction,r.direction) ELSE r.direction END,
              'time',CASE WHEN frozen.value THEN COALESCE(o.schedule_snapshot->>'local_time',history.local_time::text,r.local_time::text) ELSE r.local_time::text END,
              'timezone',display.timezone))::text
            FROM wa.routine_occurrence o JOIN wa.routine r ON r.id=o.routine_id AND r.driver_id=o.driver_id
            CROSS JOIN LATERAL (SELECT o.state<>'PLANNED' OR EXISTS(SELECT 1 FROM wa.operation_run run
              WHERE run.occurrence_id=o.id AND run.state<>'PLANNED') AS value) frozen
            LEFT JOIN LATERAL (SELECT q.routine_name,q.direction,q.local_time,q.timezone
              FROM wa.attendance_question q WHERE q.occurrence_id=o.id
              ORDER BY q.created_at DESC,q.id DESC LIMIT 1) history ON true
            CROSS JOIN LATERAL (SELECT CASE WHEN frozen.value THEN COALESCE(o.schedule_snapshot->>'timezone',history.timezone,r.timezone)
              ELSE r.timezone END AS timezone) display
            WHERE o.driver_id=? AND o.local_date>=timezone(display.timezone,?::timestamptz)::date
              AND o.local_date<timezone(display.timezone,?::timestamptz)::date+7
              AND (frozen.value OR (o.calendar_eligible AND r.state='ACTIVE'
                AND extract(isodow FROM o.local_date)::smallint=ANY(r.weekdays)))
              AND EXISTS(SELECT 1 FROM wa.integration_inbox e JOIN wa.identity_alias alias
                ON alias.id::text=e.payload_json->>'senderAliasId' AND alias.identity_id=e.identity_id
                  AND alias.provider=e.provider AND alias.instance_id=e.instance_id
                WHERE e.identity_id=o.driver_id AND e.instance_id=? AND e.provider='EVOLUTION'
                  AND e.category='MESSAGE' AND e.state IN ('CLAIMED','COMPLETED') AND alias.alias_type IN ('PN','LID'))
            ORDER BY o.scheduled_at,o.id LIMIT 101
            """, (rs, row) -> WaJson.object(rs.getString(1)), driver, Timestamp.from(questions.clock.now()),
            Timestamp.from(questions.clock.now()), instance).forEach(result::add);
        return result;
    }

    ObjectNode own(UUID driver, String instance, UUID occurrence) {
        for (JsonNode row : occurrences(driver, instance)) {
            if (row.path("id").asText().equals(occurrence.toString())) return (ObjectNode) row;
        }
        return null;
    }

    String render(UUID driver, JsonNode occurrence, int page) {
        if (occurrence.path("state").asText().equals("CANCELED")) {
            return "A ocorrência de " + WaAttendanceQuestions.label(occurrence) + " foi cancelada. Não há passageiros previstos para essa saída.";
        }
        var runs = questions.jdbc.queryForList("SELECT id,state FROM wa.operation_run WHERE occurrence_id=? AND driver_id=? AND state<>'PLANNED'",
            WaAttendanceQuestions.id(occurrence, "id"), driver);
        if (!runs.isEmpty() && runs.get(0).get("state").equals("CANCELED")) {
            return "A saída de " + WaAttendanceQuestions.label(occurrence) + " foi cancelada. Não há passageiros previstos para essa saída.";
        }
        boolean started = !runs.isEmpty();
        ArrayNode members = WaJson.MAPPER.createArrayNode();
        if (started) {
            questions.jdbc.query("""
                SELECT jsonb_build_object('name',COALESCE(run.snapshot_json->'passengerNames'->>stop.passenger_id::text,p.display_name,'Passageiro'),
                  'state',CASE WHEN stop.state IN ('CANCELED','SKIPPED') THEN 'NOT_GOING' ELSE stop.attendance_snapshot END)::text
                FROM wa.run_stop stop JOIN wa.operation_run run ON run.id=stop.run_id AND run.driver_id=stop.driver_id
                JOIN wa.profile p ON p.identity_id=stop.passenger_id
                WHERE stop.run_id=? AND stop.driver_id=? ORDER BY stop.stop_order,stop.passenger_id LIMIT 1001
                """, (rs, row) -> WaJson.object(rs.getString(1)), runs.get(0).get("id"), driver).forEach(members::add);
        } else questions.jdbc.query("""
            SELECT jsonb_build_object('name',p.display_name,'state',a.state,
              'operationalIncluded',ov.included,'operationalReason',ov.reason)::text
            FROM wa.attendance a JOIN wa.routine_member m ON m.routine_id=a.routine_id AND m.passenger_id=a.passenger_id
            JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.driver_id=m.driver_id AND l.passenger_id=m.passenger_id
            JOIN wa.profile p ON p.identity_id=a.passenger_id
            LEFT JOIN LATERAL (SELECT v.included,v.reason FROM wa.occurrence_roster_override v
              WHERE v.occurrence_id=? AND v.passenger_id=a.passenger_id ORDER BY v.revision DESC LIMIT 1) ov ON true
            WHERE a.routine_id=? AND a.local_date=?::date AND m.driver_id=? AND m.state='ACTIVE' AND l.state='APPROVED'
            ORDER BY p.display_name,a.passenger_id LIMIT 1001
            """, (rs, row) -> WaJson.object(rs.getString(1)), WaAttendanceQuestions.id(occurrence, "id"), WaAttendanceQuestions.id(occurrence, "routine_id"),
            occurrence.path("local_date").asText(), driver).forEach(members::add);
        if (members.size() > 1000) return "A lista excedeu o limite desta conversa. Envie FALAR COM SUPORTE para revisar a ocorrência.";
        int going = 0, absent = 0;
        for (JsonNode member : members) {
            if (member.path("state").asText().equals("NOT_GOING") || member.path("operationalIncluded").isBoolean()
                    && !member.path("operationalIncluded").asBoolean()) absent++;
            else if (member.path("state").asText().equals("GOING")) going++;
        }
        int pending = members.size() - going - absent;
        int pageSize = 8;
        for (JsonNode member : members) if (member.path("operationalIncluded").isBoolean()) { pageSize = 4; break; }
        int last = Math.max(0, (members.size() - 1) / pageSize);
        int selected = Math.max(0, Math.min(page, last));
        StringBuilder text = new StringBuilder("Presenças de ").append(WaAttendanceQuestions.label(occurrence))
            .append(". Confirmados ").append(going).append(". Não vão ").append(absent)
            .append(". Pendentes ").append(pending)
            .append(started ? ". Lista capturada na partida. Os pendentes entraram na lista da partida. Página " : ". Os pendentes continuam previstos. Página ")
            .append(selected + 1).append(" de ").append(last + 1).append(". ");
        for (int index = selected * pageSize; index < Math.min(selected * pageSize + pageSize, members.size()); index++) {
            JsonNode member = members.get(index);
            text.append(member.path("name").asText()).append(" — ").append(switch (member.path("state").asText()) {
                case "GOING" -> "confirmado";
                case "NOT_GOING" -> "não vai";
                default -> "pendente";
            });
            if (member.path("operationalIncluded").isBoolean()) text.append("; ajuste do motorista: ")
                .append(member.path("operationalIncluded").asBoolean() && !member.path("state").asText().equals("NOT_GOING") ? "incluído" : "fora da lista")
                .append("; motivo operacional: ").append(member.path("operationalReason").asText());
            text.append(". ");
        }
        if (selected < last) text.append("Para continuar, envie PRÓXIMA. ");
        if (selected > 0) text.append("Para voltar à página anterior, envie ANTERIOR. ");
        return text.append("Envie PRESENÇAS para escolher outra data e sentido.").toString();
    }
}
