package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.time.Instant;
import java.util.Set;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Shared by watchdog and online requests; every transition is monotonic under the same fences. */
final class WaTrackingLifecycle {
    private final WaTrackingStore store; private final WaTrackingNotices notices;
    WaTrackingLifecycle(WaTrackingStore store,WaClock clock) { this.store=store; notices=new WaTrackingNotices(store.jdbc); }
    void reconcile(Session session,Instant now) {
        Run run=store.run(session.run(),session.driver());
        Session newest=store.current(session.run());
        if(newest!=null && !newest.id().equals(session.id())) {
            if(collecting(session) || "STOP_REQUESTED".equals(session.state())) store.stop(session,"REVOKED",now);
            return;
        }
        if(terminal(run)) {
            if(collecting(session)) store.stop(session,"STOP_REQUESTED",now);
            else if("STOP_REQUESTED".equals(session.state()) && !session.lease().isAfter(now)) store.stop(session,"EXPIRED",now);
            return;
        }
        boolean authorized=Boolean.TRUE.equals(store.jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.driver_device d JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
              JOIN wa.profile p ON p.identity_id=d.driver_id WHERE d.id=? AND d.status='ACTIVE' AND d.generation=a.generation
              AND p.status='ACTIVE' AND p.driver_enabled)
            """,Boolean.class,session.device()));
        if(!authorized && collecting(session)) { store.stop(session,"REVOKED",now); failOrDegrade(session,run,now); return; }
        if(Set.of("STARTING","ACTIVE","DEGRADED","STOP_REQUESTED").contains(session.state()) && !session.lease().isAfter(now)) {
            store.stop(session,"EXPIRED",now); failOrDegrade(session,run,now);
        } else if(collecting(session) && "STARTING".equals(run.state()) && session.startDeadline()!=null && !session.startDeadline().isAfter(now)) {
            store.stop(session,"STOP_REQUESTED",now); failOrDegrade(session,run,now);
        } else if(collecting(session) && "TRACKING".equals(run.state()) && !WaTrackingPolicy.fresh(session.captured(),now)) {
            store.jdbc.update("UPDATE wa.tracking_session SET state='DEGRADED',gps_state='STALE',version=version+1 WHERE id=?",session.id());
            store.jdbc.update("UPDATE wa.operation_run SET state='DEGRADED',version=version+1 WHERE id=? AND state='TRACKING'",run.id());
            notices.unavailable(session,now);
        }
        Run current=store.run(run.id(),run.driver()); Session latest=store.current(run.id());
        if("START_FAILED".equals(current.state()) && current.failureExpiry()!=null && !current.failureExpiry().isAfter(now)
                && (latest==null || !collecting(latest) || !latest.lease().isAfter(now))) {
            store.jdbc.update("UPDATE wa.operation_run SET state='CANCELED',completed_at=?,completion_reason='START_EXPIRED',version=version+1 WHERE id=? AND state='START_FAILED'",ts(now),run.id());
            store.jdbc.update("UPDATE wa.start_intent SET state='EXPIRED' WHERE run_id=? AND state='PENDING'",run.id());
        }
    }
    void failOrDegrade(Session session,Run run,Instant now) {
        if("STARTING".equals(run.state())) {
            if(collecting(session)) store.stop(session,"STOP_REQUESTED",now);
            store.jdbc.update("UPDATE wa.operation_run SET state='START_FAILED',start_failure_expires_at=?,version=version+1 WHERE id=? AND state='STARTING'",ts(now.plusSeconds(300)),run.id());
        } else if("TRACKING".equals(run.state())) store.jdbc.update("UPDATE wa.operation_run SET state='DEGRADED',version=version+1 WHERE id=? AND state='TRACKING'",run.id());
        notices.unavailable(session,now);
    }
}
