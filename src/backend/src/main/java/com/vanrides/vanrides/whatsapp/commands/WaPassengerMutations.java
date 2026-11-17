package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * All expected denials are checked before the first domain write. Receipt/outbox share the outer
 * transaction.
 */
final class WaPassengerMutations {
    record Prepared(ObjectNode link, ObjectNode point, ObjectNode invite, ObjectNode pin,
                    com.fasterxml.jackson.databind.node.ArrayNode selections) {
        Prepared(ObjectNode link, ObjectNode point, ObjectNode invite, ObjectNode pin) { this(link, point, invite, pin, null); }
    }

    record Result(UUID link, UUID pickup, String kind, String text, String peerText) {
        Result withoutPeer() {
            return new Result(link, pickup, null, text, null);
        }
    }

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
        if (prepared.selections() != null) return new WaPassengerRequests(data).apply(actor, event, draft, prepared);
        UUID link = UUID.fromString(prepared.link().path("id").asText());
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
            if (!prepared.link().path("state").asText().equals("APPROVED"))
                data.jdbc.update("UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", link);
            return new Result(
                    link,
                    pickup,
                    "RECTIFY",
                    "Pedido de correção encaminhado ao passageiro.",
                    "O motorista pediu que você revise o ponto de embarque.\n\nEnvie CORRIGIR PONTO"
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
                            : "Vínculo encerrado.\n\nO histórico das saídas foi preservado.",
                    kind.equals("REJECTED")
                            ? "O motorista recusou sua solicitação.\n\nVocê pode pedir orientação"
                                    + " ao suporte."
                            : "O vínculo de transporte foi encerrado.\n\nSaídas já iniciadas"
                                    + " conservam o histórico.");
        }
        return new WaPassengerApprovals(data).apply(draft, prepared);
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

}
