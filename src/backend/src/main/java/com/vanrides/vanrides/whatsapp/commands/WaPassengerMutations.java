package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * All expected denials are checked before the first domain write. Receipt/outbox share the outer
 * transaction.
 */
final class WaPassengerMutations {
    record Prepared(ObjectNode link, ObjectNode point, ObjectNode invite, ObjectNode pin) {}

    record Result(UUID link, UUID pickup, String kind, String text, String peerText) {}

    private final WaPassengerData data;

    WaPassengerMutations(WaPassengerData data) {
        this.data = data;
    }

    Prepared prepare(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        return new WaPassengerValidation(data).prepare(actor, event, draft);
    }

    Result apply(
            WaProfileState actor,
            WaInboxAccess.Context event,
            ObjectNode draft,
            Prepared prepared) {
        String action = draft.path("action").asText();
        UUID driver = UUID.fromString(draft.path("driverId").asText());
        Timestamp now = Timestamp.from(data.clock.now());
        if (action.equals("PUBLISH_COMMON")) {
            data.jdbc.update(
                    "INSERT INTO"
                        + " wa.common_pickup_point(id,driver_id,name,latitude,longitude,source_inbox_event_id,created_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    driver,
                    draft.path("pointLabel").asText(),
                    prepared.pin().path("latitude").asDouble(),
                    prepared.pin().path("longitude").asDouble(),
                    UUID.fromString(draft.path("sourceEvent").asText()),
                    now);
            return new Result(
                    null,
                    null,
                    null,
                    "Ponto comum publicado. Seus passageiros podem selecioná-lo no cadastro.",
                    null);
        }
        if (action.equals("REQUEST_LINK")) {
            return request(actor, event, draft, prepared, now);
        }
        UUID link = UUID.fromString(prepared.link().path("id").asText());
        if (action.equals("PROPOSE_PICKUP")) {
            rejectProposals(link, now);
            UUID pickup = insertPoint(link, driver, actor.identityId(), draft, prepared.pin(), now);
            data.jdbc.update(
                    "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", link);
            return new Result(
                    link,
                    pickup,
                    "POINT_CHANGE",
                    "Correção de ponto solicitada. Aguarde a aprovação do motorista; o ponto"
                            + " anterior continua válido.",
                    "Correção de ponto de "
                            + draft.path("passengerName").asText()
                            + ". "
                            + selectionSummary(link)
                            + reference(draft)
                            + " Envie PASSAGEIROS para revisar.");
        }
        UUID pickup =
                prepared.point() == null
                        ? null
                        : UUID.fromString(prepared.point().path("id").asText());
        if (action.equals("RECTIFY")) {
            data.jdbc.update(
                    "UPDATE wa.pickup_point SET state='REJECTED',version=version+1,valid_until=?"
                            + " WHERE id=?",
                    now,
                    pickup);
            data.jdbc.update(
                    "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", link);
            return new Result(
                    link,
                    pickup,
                    "RECTIFY",
                    "Pedido de correção encaminhado ao passageiro.",
                    "O motorista pediu que você revise o ponto de embarque. Envie CORRIGIR PONTO"
                            + " para propor um pin fixo e uma referência.");
        }
        if (action.equals("CLOSE_LINK")) {
            String kind = draft.path("operation").asText();
            data.jdbc.update(
                    "UPDATE wa.driver_passenger_link SET state=?,valid_until=?,version=version+1"
                            + " WHERE id=?",
                    kind,
                    now,
                    link);
            data.jdbc.update(
                    "UPDATE wa.pickup_point SET state=CASE WHEN state='PROPOSED' THEN 'REJECTED'"
                        + " ELSE 'RETIRED' END,valid_until=?,version=version+1 WHERE link_id=? AND"
                        + " state IN ('APPROVED','PROPOSED')",
                    now,
                    link);
            touchRoutines(link, now);
            data.jdbc.update(
                    "UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE link_id=?"
                            + " AND state='ACTIVE'",
                    link);
            return new Result(
                    link,
                    pickup,
                    kind,
                    kind.equals("REJECTED")
                            ? "Solicitação recusada."
                            : "Vínculo encerrado. O histórico das saídas foi preservado.",
                    kind.equals("REJECTED")
                            ? "O motorista recusou sua solicitação. Você pode pedir orientação ao"
                                    + " suporte."
                            : "O vínculo de transporte foi encerrado. Saídas já iniciadas conservam"
                                    + " o histórico.");
        }
        // Retire before approving to respect the one-current-approved-point invariant.
        data.jdbc.update(
                "UPDATE wa.pickup_point SET state='RETIRED',valid_until=?,version=version+1 WHERE"
                        + " link_id=? AND state='APPROVED' AND id<>?",
                now,
                link,
                pickup);
        data.jdbc.update(
                "UPDATE wa.pickup_point SET"
                    + " state='APPROVED',approved_by=?,approved_at=?,version=version+1 WHERE id=?",
                driver,
                now,
                pickup);
        data.jdbc.update(
                "UPDATE wa.driver_passenger_link SET"
                    + " state='APPROVED',approved_by=?,approved_at=COALESCE(approved_at,?),version=version+1"
                    + " WHERE id=?",
                driver,
                now,
                link);
        if (action.equals("APPROVE_LINK")) {
            data.jdbc.update(
                    """
                    INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order,weekdays,version,created_at)
                    SELECT gen_random_uuid(),s.routine_id,s.driver_id,s.passenger_id,s.link_id,?,
                      COALESCE((SELECT max(m.stop_order) FROM wa.routine_member m WHERE m.routine_id=s.routine_id AND m.state='ACTIVE'),0)+1,s.weekdays,1,?
                    FROM wa.passenger_link_routine s WHERE s.link_id=?
                    ON CONFLICT(routine_id,passenger_id) DO UPDATE SET link_id=EXCLUDED.link_id,pickup_point_id=EXCLUDED.pickup_point_id,
                      stop_order=EXCLUDED.stop_order,weekdays=EXCLUDED.weekdays,state='ACTIVE',version=wa.routine_member.version+1
                    """,
                    pickup,
                    now,
                    link);
        } else {
            data.jdbc.update(
                    "UPDATE wa.routine_member SET pickup_point_id=?,version=version+1 WHERE"
                            + " link_id=? AND state='ACTIVE'",
                    pickup,
                    link);
        }
        touchRoutines(link, now);
        String kind = action.equals("APPROVE_LINK") ? "APPROVED" : "POINT_APPROVED";
        return new Result(
                link,
                pickup,
                kind,
                kind.equals("APPROVED")
                        ? "Vínculo e ponto aprovados. Revise a ordem das rotinas antes da próxima"
                                + " saída."
                        : "Novo ponto aprovado. Revise a ordem antes da próxima saída.",
                kind.equals("APPROVED")
                        ? "Cadastro confirmado. "
                                + selectionSummary(link)
                                + " Seu ponto foi aprovado pelo motorista."
                        : "Seu novo ponto foi aprovado. Saídas já iniciadas mantêm o ponto"
                                + " anterior.");
    }

    private Result request(
            WaProfileState actor,
            WaInboxAccess.Context event,
            ObjectNode draft,
            Prepared prepared,
            Timestamp now) {
        UUID link = UUID.randomUUID(), driver = UUID.fromString(draft.path("driverId").asText());
        data.jdbc.update(
                "INSERT INTO"
                    + " wa.driver_passenger_link(id,driver_id,passenger_id,invite_id,requested_source_event_id,created_at,valid_from)"
                    + " VALUES (?,?,?,?,?,?,?)",
                link,
                driver,
                actor.identityId(),
                UUID.fromString(prepared.invite().path("id").asText()),
                event.eventId(),
                now,
                now);
        for (JsonNode selection : draft.path("selections")) {
            data.jdbc.update(
                    """
                    INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version,created_at)
                    VALUES (?,?,?,?,?,?::smallint[],?,?)
                    """,
                    UUID.randomUUID(),
                    link,
                    UUID.fromString(selection.path("id").asText()),
                    driver,
                    actor.identityId(),
                    sqlDays(selection.path("selectedDays")),
                    selection.path("version").asLong(),
                    now);
        }
        UUID pickup = insertPoint(link, driver, actor.identityId(), draft, prepared.pin(), now);
        data.jdbc.update(
                "UPDATE wa.driver_invite SET use_count=use_count+1,version=version+1 WHERE id=?",
                UUID.fromString(prepared.invite().path("id").asText()));
        ObjectNode preferences =
                WaPassengerDraftStore.json()
                        .put("APPROACH_MESSAGES", draft.path("approachMessages").asBoolean())
                        .put("ATTENDANCE_REMINDERS", draft.path("attendanceReminders").asBoolean());
        data.jdbc.update(
                "UPDATE wa.profile SET preferences_json=preferences_json||?::jsonb,status='ACTIVE'"
                        + " WHERE identity_id=?",
                preferences.toString(),
                actor.identityId());
        return new Result(
                link,
                pickup,
                "REQUESTED",
                "Solicitação encaminhada ao motorista. Seu cadastro aguarda aprovação do vínculo e"
                        + " do ponto.",
                "Solicitação de "
                        + draft.path("passengerName").asText()
                        + ". "
                        + selectionSummary(link)
                        + reference(draft)
                        + " Envie PASSAGEIROS para revisar vínculo e ponto.");
    }

    private UUID insertPoint(
            UUID link,
            UUID driver,
            UUID passenger,
            ObjectNode draft,
            ObjectNode pin,
            Timestamp now) {
        UUID id = UUID.randomUUID();
        String source =
                draft.has("sourceEvent")
                        ? draft.path("sourceEvent").asText()
                        : draft.path("selectionEvent").asText();
        data.jdbc.update(
                """
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,source_inbox_event_id,
                    common_pickup_point_id,common_pickup_version,created_at,valid_from)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                link,
                driver,
                passenger,
                pin.path("latitude").asDouble(),
                pin.path("longitude").asDouble(),
                draft.has("pointLabel") ? draft.path("pointLabel").asText() : null,
                draft.has("reference") ? draft.path("reference").asText() : null,
                UUID.fromString(source),
                draft.has("commonId") ? UUID.fromString(draft.path("commonId").asText()) : null,
                draft.has("commonId") ? draft.path("commonVersion").asLong() : null,
                now,
                now);
        return id;
    }

    private void rejectProposals(UUID link, Timestamp now) {
        data.jdbc.update(
                "UPDATE wa.pickup_point SET state='REJECTED',valid_until=?,version=version+1 WHERE"
                        + " link_id=? AND state='PROPOSED'",
                now,
                link);
    }

    private void touchRoutines(UUID link, Timestamp now) {
        data.jdbc.update(
                "UPDATE wa.routine SET version=version+1,stop_order_pending=true,updated_at=? WHERE"
                    + " id IN (SELECT routine_id FROM wa.passenger_link_routine WHERE link_id=?)",
                now,
                link);
        data.jdbc.update(
                "UPDATE wa.passenger_link_routine SET version=version+1 WHERE link_id=?", link);
    }

    private String selectionSummary(UUID link) {
        var selections =
                data.array(
                        """
                        SELECT jsonb_build_object('name', r.name, 'direction', r.direction, 'time', r.local_time::text, 'days', s.weekdays)::text
                        FROM wa.passenger_link_routine s JOIN wa.routine r ON r.id=s.routine_id WHERE s.link_id=? ORDER BY r.created_at,r.id
                        """,
                        link);
        StringBuilder summary = new StringBuilder();
        for (JsonNode selection : selections) {
            summary.append(selection.path("name").asText())
                    .append(
                            selection.path("direction").asText().equals("OUTBOUND")
                                    ? " (ida)"
                                    : " (volta)")
                    .append(" às ")
                    .append(selection.path("time").asText())
                    .append(", dias ")
                    .append(selection.path("days"))
                    .append(". ");
        }
        return summary.toString().strip();
    }

    private static String reference(ObjectNode draft) {
        return draft.has("reference")
                ? " Referência: " + draft.path("reference").asText() + "."
                : "";
    }

    private static String sqlDays(JsonNode days) {
        var values = new java.util.ArrayList<String>();
        days.forEach(draft -> values.add(draft.asText()));
        return "{" + String.join(",", values) + "}";
    }
}
