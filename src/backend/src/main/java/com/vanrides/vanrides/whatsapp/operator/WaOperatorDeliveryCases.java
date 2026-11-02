package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private, audited metadata only. No provider IDs, aliases, payloads or conversation support. */
final class WaOperatorDeliveryCases {
    private static final Set<String> ATTEMPT_STATES = Set.of("SUBMITTING", "SUBMITTED", "DELIVERED", "READ", "UNKNOWN", "FAILED", "EXPIRED", "NOT_SUBMITTED");
    static ObjectNode list(JdbcTemplate jdbc, WaOperatorRequest request, WaOperatorService.Actor actor) {
        WaOperatorService.requireCohort(jdbc, actor);
        String after = request.has("afterCaseId") ? request.uuid("afterCaseId").toString() : "";
        int limit = request.limit();
        var rows = jdbc.queryForList("""
            SELECT s.id,s.delivery_outbox_id,s.state,s.reason_code,s.correlation_id,s.created_at,
              a.state AS attempt_state,(a.external_message_id IS NOT NULL) AS has_external_message_id
            FROM wa.support_case s JOIN wa.identity person ON person.id=s.identity_id
            LEFT JOIN LATERAL (SELECT state,external_message_id FROM wa.message_attempt
              WHERE outbox_id=s.delivery_outbox_id ORDER BY attempt_number DESC LIMIT 1) a ON true
            WHERE s.purpose='DELIVERY' AND s.environment=? AND s.instance_id=?
              AND person.test_cohort_id=? AND s.id::text>? ORDER BY s.id::text LIMIT ?
            """, actor.environment(), request.text("allowedInstanceId", 1, 80), actor.batchId(), after, limit + 1);
        ObjectNode result = WaOperatorService.object(); var cases = result.putArray("cases"); UUID last = null;
        for (int index = 0; index < Math.min(limit, rows.size()); index++) {
            var row = rows.get(index); String attemptState = (String) row.get("attempt_state");
            if (attemptState == null || !ATTEMPT_STATES.contains(attemptState)) throw new WaOperatorFailure();
            last = (UUID) row.get("id");
            cases.addObject().put("caseId", last.toString()).put("outboxId", row.get("delivery_outbox_id").toString())
                .put("state", (String) row.get("state")).put("reasonCode", (String) row.get("reason_code"))
                .put("correlationId", (String) row.get("correlation_id")).put("createdAt", ((Timestamp) row.get("created_at")).toInstant().toString())
                .put("attemptState", attemptState).put("hasExternalMessageId", (Boolean) row.get("has_external_message_id"));
        }
        if (rows.size() > limit && last != null) result.put("nextAfterCaseId", last.toString());
        else result.putNull("nextAfterCaseId");
        return result;
    }
}
