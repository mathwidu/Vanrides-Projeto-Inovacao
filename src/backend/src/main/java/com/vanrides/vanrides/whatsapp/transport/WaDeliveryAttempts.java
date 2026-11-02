package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Only committed proof of zero POST permits another reservation. Caller holds delivery/outbox locks. */
final class WaDeliveryAttempts {
    static final String UNSENT = """
        NOT EXISTS(SELECT 1 FROM wa.message_attempt a WHERE a.outbox_id=o.id
          AND (a.state<>'NOT_SUBMITTED' OR a.result_request_hash IS NULL))
        """;
    static final String AVAILABLE = UNSENT + " AND (SELECT count(*) FROM wa.message_attempt a WHERE a.outbox_id=o.id)<3 ";
    private final JdbcTemplate jdbc;
    WaDeliveryAttempts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    int next(UUID box, long generation) {
        var history = jdbc.queryForList("""
            SELECT attempt_number,lease_generation,state,result_request_hash FROM wa.message_attempt
            WHERE outbox_id=? ORDER BY attempt_number
            """, box);
        if (history.size() >= 3) throw conflict();
        for (int i = 0; i < history.size(); i++) {
            var attempt = history.get(i);
            if (((Number) attempt.get("attempt_number")).intValue() != i + 1
                    || !"NOT_SUBMITTED".equals(attempt.get("state")) || attempt.get("result_request_hash") == null
                    || ((Number) attempt.get("lease_generation")).longValue() >= generation) throw conflict();
        }
        return history.size() + 1;
    }
    private WaProblem conflict() { return new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT); }
}
