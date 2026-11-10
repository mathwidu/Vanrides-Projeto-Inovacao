package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class WaTeamTestDriver {
    public enum Decision {
        APPROVE_LINK("APPROVE_LINK", null, null),
        REJECT_LINK("CLOSE_LINK", "REJECTED", null),
        RECTIFY_POINT("RECTIFY", null, null),
        APPROVE_POINT("APPROVE_PICKUP", null, null),
        CLOSE_LINK("CLOSE_LINK", "CLOSED", "APPROVED");

        private final String action;
        private final String operation;
        private final String requiredLinkState;

        Decision(String action, String operation, String requiredLinkState) {
            this.action = action;
            this.operation = operation;
            this.requiredLinkState = requiredLinkState;
        }
    }
    public record Outcome(UUID pickup, String kind, String passengerText) { }

    private final JdbcTemplate jdbc;
    private final WaPassengerData data;

    public WaTeamTestDriver(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc;
        this.data = new WaPassengerData(jdbc, clock, settings);
    }

    public ArrayNode links(UUID driver) {
        ArrayNode links = data.array("""
            SELECT jsonb_build_object('linkId',l.id,'passengerName',p.display_name,'state',l.state,
              'requestedAt',l.created_at,'pointPending',EXISTS(SELECT 1 FROM wa.pickup_point pp
                WHERE pp.link_id=l.id AND pp.state='PROPOSED' AND pp.valid_until IS NULL))::text
            FROM wa.driver_passenger_link l JOIN wa.profile p ON p.identity_id=l.passenger_id
            WHERE l.driver_id=? AND l.state IN ('REQUESTED','APPROVED') ORDER BY l.created_at DESC,l.id LIMIT 20
            """, driver);
        for (JsonNode link : links)
            ((ObjectNode) link).put("routines", data.selectionSummary(UUID.fromString(link.path("linkId").asText())));
        return links;
    }

    public String linkState(UUID driver, UUID link) {
        var rows = jdbc.query("SELECT state FROM wa.driver_passenger_link WHERE id=? AND driver_id=?",
            (rs, i) -> rs.getString(1), link, driver);
        if (rows.size() != 1) throw WaProblem.missing();
        return rows.get(0);
    }

    public Outcome decide(UUID driver, UUID link, Decision decision) {
        if (!data.teamTestDriver(driver)) throw WaProblem.denied();
        var actors = jdbc.query("""
            SELECT * FROM wa.profile WHERE identity_id=? AND role='DRIVER' AND status='ACTIVE' AND driver_enabled FOR UPDATE
            """, WaProfileState::read, driver);
        if (actors.size() != 1) throw WaProblem.denied();
        ObjectNode draft = WaJson.MAPPER.createObjectNode()
            .put("action", decision.action)
            .put("driverId", driver.toString())
            .put("linkId", link.toString());
        if (decision.operation != null) draft.put("operation", decision.operation);
        var proposed = jdbc.query("""
            SELECT id FROM wa.pickup_point WHERE link_id=? AND state='PROPOSED' AND valid_until IS NULL
            """, (rs, i) -> rs.getObject(1, UUID.class), link);
        if (proposed.size() == 1) draft.put("pickupId", proposed.get(0).toString());
        var mutations = new WaPassengerMutations(data);
        var prepared = mutations.prepare(actors.get(0), null, draft);
        if (decision.requiredLinkState != null && !decision.requiredLinkState.equals(prepared.link().path("state").asText()))
            throw WaProblem.denied();
        var result = mutations.apply(actors.get(0), null, draft, prepared);
        return new Outcome(result.pickup(), result.kind(), result.peerText());
    }
}
