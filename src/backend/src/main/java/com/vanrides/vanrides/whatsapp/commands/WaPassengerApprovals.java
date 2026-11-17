package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;

/** Approval is scoped to the persisted proposal's routine, never all of a driver's passengers. */
final class WaPassengerApprovals {
    private final WaPassengerData data;
    WaPassengerApprovals(WaPassengerData data) { this.data = data; }

    void validate(ObjectNode link, ObjectNode point) {
        var selected = new WaPassengerReviewStore(data).selections(UUID.fromString(link.path("id").asText()), UUID.fromString(point.path("id").asText()));
        if (selected.size()!=1) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        for (var routine : selected) {
            if (!routine.path("state").asText().equals("ACTIVE")
                    || !WaPassengerAnswers.subset(routine.path("selectedDays"), routine.path("weekdays"))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        }
    }

    WaPassengerMutations.Result apply(ObjectNode draft, WaPassengerMutations.Prepared prepared) {
        UUID link = UUID.fromString(prepared.link().path("id").asText());
        UUID driver = UUID.fromString(prepared.link().path("driver_id").asText());
        UUID passenger = UUID.fromString(prepared.link().path("passenger_id").asText());
        UUID point = UUID.fromString(prepared.point().path("id").asText());
        Timestamp now = Timestamp.from(data.clock.now());
        data.jdbc.update("UPDATE wa.pickup_point SET state='APPROVED',approved_by=?,approved_at=?,version=version+1 WHERE id=?", driver, now, point);
        if (!prepared.link().path("state").asText().equals("APPROVED"))
            data.jdbc.update("UPDATE wa.driver_passenger_link SET state='APPROVED',approved_by=?,approved_at=COALESCE(approved_at,?),version=version+1 WHERE id=?", driver, now, link);
        WaPassengerMemberships memberships = new WaPassengerMemberships(data);
        for (var selected : new WaPassengerReviewStore(data).selections(link, point)) {
            UUID routine = UUID.fromString(selected.path("id").asText());
            var old = data.jdbc.query("SELECT pickup_point_id FROM wa.routine_member WHERE routine_id=? AND passenger_id=?",
                    (rs,row)->rs.getObject(1,UUID.class), routine, passenger);
            memberships.activate(link, driver, passenger, routine, point, selected.path("selectedDays"), now);
            if (!old.isEmpty() && !point.equals(old.get(0))) memberships.retireUnused(old.get(0), now);
        }
        boolean legacy = draft.path("action").asText().equals("APPROVE_LINK");
        return new WaPassengerMutations.Result(link, point, legacy ? "APPROVED" : "POINT_APPROVED",
                "Ponto aprovado para este trajeto. Confira a ordem antes da próxima saída. Para conferir outro ponto, envie REVISAR PEDIDO.",
                "Seu ponto foi aprovado.\n\n" + new WaPassengerReviewStore(data).summary(link, point)
                        + "\n\nOs outros trajetos foram mantidos.");
    }
}
