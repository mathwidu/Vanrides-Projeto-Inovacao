package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaHumanReviewFreshness;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Recovery only: a fresh chat confirmation can finish an already acknowledged stop. */
final class WaStoppedRunFinish {
    private final JdbcTemplate jdbc;private final WaClock clock;
    WaStoppedRunFinish(JdbcTemplate jdbc,WaClock clock) { this.jdbc=jdbc;this.clock=clock; }
    ObjectNode review(UUID driver,Map<String,Object> run,UUID outbox) {
        ObjectNode stop=latest(driver,(UUID)run.get("id"));
        if(stop==null) return null;
        ObjectNode draft=WaJson.object("{}").put("action","FINISH").put("stage","REVIEW")
            .put("run",run.get("id").toString()).put("runVersion",((Number)run.get("version")).longValue())
            .put("expiresAt",clock.now().plusSeconds(300).toString()).put("reviewOutbox",outbox.toString());
        draft.set("confirmedStop",stop);return draft;
    }
    boolean confirms(WaProfileState actor,WaInboxAccess.Context event,ObjectNode draft) {
        try {
            var confirmation=draft.path("confirmation");
            if(!draft.has("confirmedStop") || !Instant.parse(draft.path("expiresAt").asText()).isAfter(clock.now())
                    || actor.version()!=confirmation.path("profileVersion").asLong(-1)
                    || !WaDriverDraftStore.digest(draft).equals(confirmation.path("draftDigest").asText())) return false;
            String instance=jdbc.queryForObject("SELECT instance_id FROM wa.integration_inbox WHERE id=?",String.class,event.eventId());
            return WaHumanReviewFreshness.accepted(jdbc,UUID.fromString(draft.path("reviewOutbox").asText()),actor.identityId(),
                instance,event.eventId(),event.normalizedPayload(),clock.now());
        } catch(RuntimeException invalid) { return false; }
    }
    void revalidate(UUID driver,ObjectNode draft) {
        UUID run=UUID.fromString(draft.path("run").asText());
        // The caller already owns profile; take the run before comparing the latest session.
        var rows=jdbc.queryForList("SELECT version FROM wa.operation_run WHERE id=? AND driver_id=? FOR UPDATE",run,driver);
        ObjectNode current=latest(driver,run);
        if(rows.size()!=1 || ((Number)rows.get(0).get("version")).longValue()!=draft.path("runVersion").asLong(-1)
                || current==null || !WaJson.canonical(current).equals(WaJson.canonical(draft.path("confirmedStop"))))
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
    }
    private ObjectNode latest(UUID driver,UUID run) {
        var rows=jdbc.queryForList("""
            SELECT id,generation,state,stopped_at,stop_request_ref FROM wa.tracking_session
            WHERE run_id=? AND driver_id=? ORDER BY generation DESC LIMIT 1 FOR UPDATE
            """,run,driver);
        if(rows.size()!=1 || !"STOPPED".equals(rows.get(0).get("state")) || !(rows.get(0).get("stopped_at") instanceof Timestamp at)) return null;
        var row=rows.get(0);
        ObjectNode value=WaJson.object("{}").put("sessionId",row.get("id").toString())
            .put("generation",((Number)row.get("generation")).longValue()).put("stoppedAt",at.toInstant().toString());
        if(row.get("stop_request_ref")==null) value.putNull("stopRequestRef");else value.put("stopRequestRef",row.get("stop_request_ref").toString());
        return value;
    }
}
