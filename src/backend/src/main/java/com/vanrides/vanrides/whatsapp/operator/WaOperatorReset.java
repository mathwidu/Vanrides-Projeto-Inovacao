package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.vanrides.vanrides.whatsapp.operator.WaOperatorService.*;

/** An allowlisted graph reset; no schema, public-table, credential or global proof cleanup. */
@Component
public final class WaOperatorReset {
    private static final String IDENTITIES = "SELECT id FROM wa.identity WHERE test_cohort_id=?";
    private final JdbcTemplate jdbc;
    public WaOperatorReset(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode reset(Actor actor) {
        nonProduction(actor.environment());
        var cohorts = jdbc.query("SELECT synthetic FROM wa.test_cohort WHERE id=? AND environment=? FOR UPDATE",
                (rs, row) -> rs.getBoolean(1), actor.batchId(), actor.environment());
        if (cohorts.size() != 1 || !cohorts.get(0)) throw new WaOperatorFailure();
        var identities = jdbc.query("SELECT id FROM wa.identity WHERE test_cohort_id=? ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), actor.batchId());
        jdbc.query("SELECT identity_id FROM wa.profile WHERE identity_id IN (" + IDENTITIES + ") ORDER BY identity_id FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), actor.batchId());
        jdbc.query("SELECT driver_id FROM wa.driver_authorization WHERE driver_id IN (" + IDENTITIES + ") ORDER BY driver_id FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), actor.batchId());
        rejectCrossBatchRelations(actor.batchId());
        deleteGraph(actor.batchId());
        return object().put("resourceId", actor.batchId().toString()).put("identitiesDeleted", identities.size());
    }

    private void rejectCrossBatchRelations(UUID batch) {
        Boolean crossing = jdbc.queryForObject("""
            WITH b AS (SELECT id FROM wa.identity WHERE test_cohort_id=?),
                 r AS (SELECT id FROM wa.routine WHERE driver_id IN (SELECT id FROM b)),
                 o AS (SELECT id FROM wa.routine_occurrence WHERE driver_id IN (SELECT id FROM b)),
                 n AS (SELECT id FROM wa.operation_run WHERE driver_id IN (SELECT id FROM b)),
                 c AS (SELECT command_id FROM wa.command_receipt WHERE identity_id IN (SELECT id FROM b))
            SELECT EXISTS(
                SELECT 1 FROM wa.driver_passenger_link
                WHERE (driver_id IN (SELECT id FROM b)) <> (passenger_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.routine_member
                WHERE (driver_id IN (SELECT id FROM b)) <> (passenger_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.passenger_link_routine
                WHERE (driver_id IN (SELECT id FROM b)) <> (passenger_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.passenger_response_route
                WHERE (actor_identity_id IN (SELECT id FROM b)) <> (recipient_identity_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.occurrence_roster_override
                WHERE (driver_id IN (SELECT id FROM b)) <> (passenger_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.run_stop
                WHERE (driver_id IN (SELECT id FROM b)) <> (passenger_id IN (SELECT id FROM b))
                UNION ALL
                SELECT 1 FROM wa.attendance
                WHERE (passenger_id IN (SELECT id FROM b) OR actor_identity_id IN (SELECT id FROM b) OR routine_id IN (SELECT id FROM r))
                  AND NOT (passenger_id IN (SELECT id FROM b) AND routine_id IN (SELECT id FROM r)
                      AND (actor_identity_id IS NULL OR actor_identity_id IN (SELECT id FROM b)))
                UNION ALL
                SELECT 1 FROM wa.attendance_question q
                WHERE (q.driver_id IN (SELECT id FROM b) OR q.passenger_id IN (SELECT id FROM b)
                    OR q.routine_id IN (SELECT id FROM r) OR q.occurrence_id IN (SELECT id FROM o))
                  AND NOT (q.driver_id IN (SELECT id FROM b) AND q.passenger_id IN (SELECT id FROM b)
                    AND q.routine_id IN (SELECT id FROM r) AND q.occurrence_id IN (SELECT id FROM o)
                    AND (q.outbox_id IS NULL OR EXISTS(SELECT 1 FROM wa.message_outbox box
                      WHERE box.id=q.outbox_id AND box.recipient_identity_id=q.passenger_id)))
                UNION ALL
                SELECT 1 FROM wa.message_outbox
                WHERE (recipient_identity_id IN (SELECT id FROM b) OR run_id IN (SELECT id FROM n)
                    OR occurrence_id IN (SELECT id FROM o) OR command_id IN (SELECT command_id FROM c))
                  AND NOT (recipient_identity_id IN (SELECT id FROM b)
                    AND (run_id IS NULL OR run_id IN (SELECT id FROM n))
                    AND (occurrence_id IS NULL OR occurrence_id IN (SELECT id FROM o))
                    AND (command_id IS NULL OR command_id IN (SELECT command_id FROM c)))
            )
            """, Boolean.class, batch);
        if (Boolean.TRUE.equals(crossing)) throw new WaOperatorFailure();
    }

    private void deleteGraph(UUID batch) {
        String people = "(" + IDENTITIES + ")";
        String runs = "(SELECT id FROM wa.operation_run WHERE driver_id IN " + people + ")";
        String sessions = "(SELECT id FROM wa.tracking_session WHERE driver_id IN " + people + ")";
        String devices = "(SELECT id FROM wa.driver_device WHERE driver_id IN " + people + ")";
        String outbox = "(SELECT id FROM wa.message_outbox WHERE recipient_identity_id IN " + people + ")";
        jdbc.update("DELETE FROM wa.approach_delivery_route WHERE session_id IN " + sessions, batch);
        jdbc.update("DELETE FROM wa.approach_progress WHERE run_id IN " + runs, batch);
        jdbc.update("DELETE FROM wa.tracking_sample_receipt WHERE session_id IN " + sessions, batch);
        jdbc.update("DELETE FROM wa.tracking_batch_receipt WHERE session_id IN " + sessions, batch);
        jdbc.update("DELETE FROM wa.tracking_session WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.occurrence_roster_override WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.attendance_question WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.message_attempt WHERE outbox_id IN " + outbox, batch);
        jdbc.update("DELETE FROM wa.passenger_response_route WHERE outbox_id IN " + outbox, batch);
        jdbc.update("DELETE FROM wa.message_outbox WHERE recipient_identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.start_intent WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.run_stop WHERE run_id IN " + runs, batch);
        jdbc.update("DELETE FROM wa.attendance WHERE passenger_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.operation_run WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.routine_occurrence WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.routine_member WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.passenger_link_routine WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.pickup_point WHERE passenger_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.common_pickup_point WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.driver_passenger_link WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.driver_invite WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.routine WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.conversation_state WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.support_case WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.command_receipt WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.integration_inbox WHERE identity_id IN " + people, batch);
        jdbc.update("""
            DELETE FROM wa.operation_replay WHERE
                (operation_kind='PAIR_REDEEM' AND scope_id IN (SELECT id FROM wa.pairing_challenge WHERE driver_id IN
            """ + people + ")) OR (operation_kind='REFRESH' AND scope_id IN (SELECT id FROM wa.device_token WHERE device_id IN "
                + devices + ")) OR (operation_kind IN ('SESSION_REVOKE','TRACK_ACTIVATE','TRACK_RESUME','TRACK_STATE') AND scope_id IN " + devices + ")", batch, batch, batch);
        jdbc.update("DELETE FROM wa.pairing_challenge WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.device_token WHERE device_id IN " + devices, batch);
        jdbc.update("DELETE FROM wa.driver_device WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.driver_authorization WHERE driver_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.driver_bootstrap_invite WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.actor_attempt_window WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.audit_event WHERE actor_identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.profile WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.identity_alias WHERE identity_id IN " + people, batch);
        jdbc.update("DELETE FROM wa.identity WHERE test_cohort_id=?", batch);
    }
}
