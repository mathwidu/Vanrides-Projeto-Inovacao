package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** A delayed approval for A cannot approve B. Receipt time alone is not human causality. */
public final class WaHumanReviewFreshness {
    private WaHumanReviewFreshness() { }
    public static boolean accepted(JdbcTemplate jdbc,UUID review,UUID driver,String instance,UUID inbox,ObjectNode payload,Instant now) {
        if(review==null) return false;
        var acks=jdbc.queryForList("""
            SELECT a.external_message_id,a.completed_at FROM wa.message_attempt a JOIN wa.message_outbox o ON o.id=a.outbox_id
            WHERE a.outbox_id=? AND o.recipient_identity_id=? AND a.instance_id=? AND a.credential_id IS NOT NULL
              AND a.state IN ('SUBMITTED','DELIVERED','READ') AND a.external_message_id IS NOT NULL
              AND a.completed_at IS NOT NULL ORDER BY a.completed_at DESC LIMIT 1
            """,review,driver,instance);
        if(acks.size()!=1) return false;
        Instant ack=((Timestamp)acks.get(0).get("completed_at")).toInstant();
        var events=jdbc.queryForList("""
            SELECT created_at FROM wa.integration_inbox WHERE id=? AND identity_id=? AND instance_id=? AND category='MESSAGE'
            """,inbox,driver,instance);
        if(events.size()!=1 || !((Timestamp)events.get(0).get("created_at")).toInstant().isAfter(ack)) return false;
        if(payload.has("replyTo")) return payload.path("replyTo").isTextual()
            && payload.path("replyTo").asText().equals(acks.get(0).get("external_message_id"));
        try {
            Instant sent=Instant.parse(payload.path("messageSentAt").asText());
            return sent.isAfter(ack) && !sent.isAfter(now) && !sent.isBefore(now.minusSeconds(300));
        } catch(RuntimeException invalid) { return false; }
    }
}
