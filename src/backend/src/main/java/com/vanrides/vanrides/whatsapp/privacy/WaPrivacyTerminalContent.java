package com.vanrides.vanrides.whatsapp.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** One unit is exactly one queue row; a busy head stops the contiguous prefix. Never locks a profile. */
final class WaPrivacyTerminalContent {
    record Unit(String kind, UUID id, String key, boolean ambiguous) { }
    record Page(int examined, int erased, int deferred, String after, boolean complete, boolean endOfPrefix) { }
    private final JdbcTemplate jdbc;
    WaPrivacyTerminalContent(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    boolean complete(WaPrivacyService.Scope scope, Instant asOf, boolean syntheticOnly) {
        return !Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyContentQueries.CANDIDATES
            + "SELECT EXISTS(SELECT 1 FROM eligible)", Boolean.class, parameters(scope, asOf, syntheticOnly)));
    }

    Page sweep(WaPrivacyService.Scope scope, Instant asOf, Instant now, boolean syntheticOnly, String after, int budget, boolean dryRun) {
        List<Unit> work = jdbc.query(WaPrivacyContentQueries.CANDIDATES
            + "SELECT kind,id,work_key,ambiguous FROM eligible WHERE work_key>? ORDER BY work_key LIMIT ?",
            (rs, row) -> new Unit(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3),rs.getBoolean(4)),
            append(parameters(scope, asOf, syntheticOnly), after, budget));
        int examined = 0, erased = 0, deferred = 0;
        String prefix = after;
        boolean blocked = false;
        for (Unit unit : work) {
            examined++;
            if (dryRun) { deferred++; prefix = unit.key(); continue; }
            if (unit.ambiguous()) { deferred++; blocked = true; break; }
            String table = unit.kind().equals("I") ? "wa.integration_inbox" : "wa.message_outbox";
            var rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=? FOR UPDATE SKIP LOCKED", unit.id());
            if (rows.isEmpty()) {
                if (!eligible(scope, asOf, syntheticOnly, unit)) { prefix = unit.key(); continue; }
                deferred++; blocked = true; break;
            }
            var current = rows.get(0);
            if (unit.kind().equals("I") && "CLAIMED".equals(current.get("state"))
                && current.get("lease_expires_at") instanceof Timestamp lease && lease.toInstant().isAfter(now)) {
                deferred++; blocked = true; break;
            }
            if (unit.kind().equals("O") && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.message_attempt WHERE outbox_id=? AND state='SUBMITTING')
                """, Boolean.class, unit.id()))) { deferred++; blocked = true; break; }
            if (eligible(scope, asOf, syntheticOnly, unit)) {
                erased += unit.kind().equals("I") ? jdbc.update("UPDATE wa.integration_inbox i SET payload_json="
                    + WaPrivacyContentQueries.RETAIN_INBOX + """
                    ,state=CASE WHEN state IN ('PENDING','CLAIMED','RETRY','PAUSED') THEN 'EXPIRED' ELSE state END
                    WHERE i.id=?
                    """, unit.id()) : jdbc.update("""
                    UPDATE wa.message_outbox SET payload_json='{}',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,
                      state=CASE WHEN state IN ('PENDING','LEASED') THEN 'EXPIRED' ELSE state END,version=version+1 WHERE id=?
                    """, unit.id());
            }
            prefix = unit.key();
        }
        return new Page(examined, erased, deferred, prefix, complete(scope, asOf, syntheticOnly), !blocked && work.size()<budget);
    }

    private boolean eligible(WaPrivacyService.Scope scope, Instant asOf, boolean syntheticOnly, Unit unit) {
        return Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyContentQueries.CANDIDATES
            + "SELECT EXISTS(SELECT 1 FROM eligible WHERE kind=? AND id=?)", Boolean.class,
            append(parameters(scope, asOf, syntheticOnly), unit.kind(), unit.id())));
    }
    private static Object[] parameters(WaPrivacyService.Scope scope, Instant asOf, boolean syntheticOnly) {
        return new Object[]{scope.environment(), scope.instanceId(), syntheticOnly,
            Timestamp.from(asOf), Timestamp.from(asOf.minusSeconds(7L * 86400))};
    }
    private static Object[] append(Object[] first, Object... rest) {
        Object[] all = java.util.Arrays.copyOf(first, first.length + rest.length);
        System.arraycopy(rest, 0, all, first.length, rest.length); return all;
    }
}
