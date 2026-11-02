package com.vanrides.vanrides.whatsapp.transport;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** A quoted provider message remains bound to its original question and recipient. */
public final class WaAttendanceQuotes {
    private WaAttendanceQuotes() {}

    public static Optional<UUID> resolveQuestion(
            JdbcTemplate jdbc, UUID recipient, String instance, String environment, String externalMessageId) {
        if (recipient == null || instance == null || environment == null || externalMessageId == null
                || !externalMessageId.matches("[A-Za-z0-9_-]{1,160}")) return Optional.empty();
        var matches = jdbc.query("""
            SELECT q.id FROM wa.message_attempt attempt JOIN wa.message_outbox outbox ON outbox.id=attempt.outbox_id
            JOIN wa.attendance_question q ON q.outbox_id=outbox.id
              AND q.passenger_id=outbox.recipient_identity_id AND q.occurrence_id=outbox.occurrence_id
            WHERE attempt.instance_id=? AND attempt.external_message_id=?
              AND attempt.state IN ('SUBMITTED','DELIVERED','READ') AND attempt.credential_id IS NOT NULL
              AND outbox.purpose='REMINDER' AND outbox.policy_version='attendance-reminder-v1' AND outbox.command_id IS NULL
              AND q.passenger_id=? AND q.instance_id=? AND q.environment=?
            """, (rs, row) -> rs.getObject(1, UUID.class), instance, externalMessageId, recipient, instance, environment);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }
}
