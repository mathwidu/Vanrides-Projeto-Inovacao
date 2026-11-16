package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** All callers hold profile/authorization/run fences; no external call is made in this transaction. */
public final class WaRunFinishes {
    private final WaTrackingStore store;
    private final WaSettings settings;
    public WaRunFinishes(JdbcTemplate jdbc, WaSettings settings) {
        this.store = new WaTrackingStore(jdbc, null).environment(settings.environment()); this.settings = settings;
    }
    public boolean manual(UUID driver, UUID runId, long expectedVersion, Instant now) {
        new WaTrackingAccess(store.jdbc, settings, new WaClock(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC))).lockDriver(driver);
        Run run = store.run(runId, driver);
        if (run == null) throw WaProblem.missing();
        if (terminal(run)) return false;
        if (run.version() != expectedVersion) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        return finish(run, "MANUAL_COMPLETION", now, false);
    }
    void accept(Session session, UUID runId, JsonNode fix, Instant now, boolean lastFix) {
        if (!settings.autoFinishEnabled()) return;
        var rows = store.jdbc.queryForList("SELECT routine_id,snapshot_json::text FROM wa.operation_run WHERE id=? AND driver_id=? AND state='TRACKING'",
            runId, session.driver());
        if (rows.size() != 1 || !settings.approachReviewed((UUID) rows.get(0).get("routine_id"))) return;
        JsonNode routine = WaJson.object(rows.get(0).get("snapshot_json").toString()).path("routine");
        if (!coordinate(routine, "destination_latitude", -90, 90) || !coordinate(routine, "destination_longitude", -180, 180)) return;
        Instant captured = WaTrackingPolicy.instant(fix, "capturedAt");
        var point = new WaApproachPolicy.Fix(fix.path("sequence").longValue(), captured, fix.path("latitude").doubleValue(),
            fix.path("longitude").doubleValue(), fix.path("accuracyM").doubleValue(), null);
        double distance = WaApproachPolicy.distance(point, routine.path("destination_latitude").doubleValue(), routine.path("destination_longitude").doubleValue());
        var prior = store.jdbc.queryForList("SELECT * FROM wa.run_finish_progress WHERE run_id=? FOR UPDATE", runId);
        WaRunFinishPolicy.Progress previous = null;
        if (!prior.isEmpty() && session.id().equals(prior.get(0).get("session_id"))) {
            var old = prior.get(0);
            if (((Number) old.get("last_sequence")).longValue() >= point.sequence()) return;
            previous = new WaRunFinishPolicy.Progress(Boolean.TRUE.equals(old.get("observed_outside")),
                old.get("arrived_since") instanceof Timestamp since ? since.toInstant() : null,
                ((Timestamp) old.get("last_captured_at")).toInstant());
        }
        boolean complete = Boolean.TRUE.equals(store.jdbc.queryForObject(
            "SELECT NOT EXISTS(SELECT 1 FROM wa.run_stop WHERE run_id=? AND state IN ('WAITING','APPROACHING'))", Boolean.class, runId));
        var decision = WaRunFinishPolicy.assess(previous, captured, now, distance, point.accuracy(),
            fix.hasNonNull("speedMps") ? fix.path("speedMps").doubleValue() : null, complete);
        store.jdbc.update("""
            INSERT INTO wa.run_finish_progress(run_id,session_id,last_sequence,last_captured_at,observed_outside,arrived_since,policy_version,last_reason)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(run_id) DO UPDATE SET session_id=EXCLUDED.session_id,last_sequence=EXCLUDED.last_sequence,
              last_captured_at=EXCLUDED.last_captured_at,observed_outside=EXCLUDED.observed_outside,arrived_since=EXCLUDED.arrived_since,
              policy_version=EXCLUDED.policy_version,last_reason=EXCLUDED.last_reason
            """, runId, session.id(), point.sequence(), ts(captured), decision.progress().outside(),
            decision.progress().arrivedSince() == null ? null : ts(decision.progress().arrivedSince()), WaRunFinishPolicy.VERSION, decision.reason());
        if (lastFix && decision.finish()) finish(store.run(runId, session.driver()), "AUTO_ARRIVAL", now, true);
    }
    private boolean finish(Run run, String reason, Instant now, boolean notify) {
        if (terminal(run)) return false;
        int changed = store.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=?,completion_reason=?,start_failure_expires_at=NULL,version=version+1 WHERE id=? AND state NOT IN ('COMPLETED','CANCELED')",
            ts(now), reason, run.id());
        if (changed == 0) return false;
        store.jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE (run_id=? OR occurrence_id=(SELECT occurrence_id FROM wa.operation_run WHERE id=?)) AND environment=? AND state IN ('PENDING','ACTIVATED')", run.id(), run.id(), settings.environment());
        var sessions = store.jdbc.query("SELECT id FROM wa.tracking_session WHERE run_id=? ORDER BY generation", (rs, n) -> rs.getObject(1, UUID.class), run.id());
        Session latest = null;
        for (UUID id : sessions) {
            Session session = store.session(id, run.driver()); latest = session;
            if (collecting(session) || "STOP_REQUESTED".equals(session.state())) store.stop(session, "REVOKED", now);
        }
        // Delivery takes outbox then profile SHARE. Skip its rows: it revalidates terminal eligibility after this profile fence.
        store.jdbc.update("""
            UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,
              payload_json=CASE WHEN payload_kind='PIN' THEN '{}'::jsonb ELSE payload_json END,version=version+1
            WHERE id IN (SELECT o.id FROM wa.message_outbox o WHERE (o.run_id=? OR o.start_intent_id IN
              (SELECT id FROM wa.start_intent WHERE run_id=?)) AND o.state IN ('PENDING','LEASED') FOR UPDATE SKIP LOCKED)
            """, run.id(), run.id());
        store.jdbc.update("DELETE FROM wa.run_finish_progress WHERE run_id=?", run.id());
        store.jdbc.update("DELETE FROM wa.approach_fix WHERE session_id IN (SELECT id FROM wa.tracking_session WHERE run_id=?)", run.id());
        if (notify && latest != null) new WaTrackingNotices(store.jdbc).finished(latest, now);
        return true;
    }
    private static boolean coordinate(JsonNode object, String field, double min, double max) {
        JsonNode value = object.get(field);
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() >= min && value.doubleValue() <= max;
    }
}
