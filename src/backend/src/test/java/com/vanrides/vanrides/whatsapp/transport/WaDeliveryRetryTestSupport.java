package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class WaDeliveryRetryTestSupport {
    static ObjectNode unsent(ObjectNode reservation) {
        return WaJson.object("{}").put("attemptToken", reservation.path("attemptToken").asText())
            .put("outcome", "NOT_SUBMITTED").put("failureCode", "CHANNEL_UNAVAILABLE");
    }
    static ObjectNode result(WaDeliveryConnectionTestSupport f, ObjectNode reservation) {
        return f.outbox.result(f.worker, WaJson.uuid(reservation, "outboxId"), unsent(reservation));
    }
    static void awaitLock(WaTransportTestSupport h, String pattern) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
        while (System.nanoTime() < end) {
            h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
            waiting = Boolean.TRUE.equals(h.jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid<>pg_backend_pid()
                  AND wait_event_type='Lock' AND query LIKE ?)
                """, Boolean.class, pattern));
            if (waiting) break;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted lock observation"); }
        }
        assertThat(waiting).as("real PostgreSQL fence observed").isTrue();
    }
}
