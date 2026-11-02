package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.util.UUID;

final class WaRunGpsDeliveryTestSupport {
    static UUID notice(WaTrackingTestSupport t, String part) {
        return t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE response_part=? ORDER BY created_at DESC LIMIT 1", UUID.class, part);
    }
    static ObjectNode lease(WaTrackingTestSupport t, UUID box) {
        for (var item : t.f.s.outboxClaim().path("items")) if (box.toString().equals(item.path("outboxId").asText())) return (ObjectNode)item;
        throw new AssertionError("Expected current run notice");
    }
    static ObjectNode submitted(WaTrackingTestSupport t, ObjectNode reservation, String external) {
        return t.f.s.outbox.result(t.f.s.delivery, WaJson.uuid(reservation,"outboxId"), WaJson.object("{}")
            .put("attemptToken",reservation.path("attemptToken").asText()).put("outcome","SUBMITTED").put("externalMessageId",external));
    }
    static long attempts(WaTrackingTestSupport t, UUID box) {
        return t.f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",box);
    }
}
