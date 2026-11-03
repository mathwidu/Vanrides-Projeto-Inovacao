package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Five-second local API watchdog; no dependency on n8n or incoming packets. */
@Service
public class WaTrackingWatchdog {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock; private final WaTrackingStore store;
    public WaTrackingWatchdog(JdbcTemplate jdbc,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc; this.settings=settings; this.clock=clock; store=new WaTrackingStore(jdbc,crypto).environment(settings.environment());
    }
    @Scheduled(fixedDelay=5000,initialDelay=5000) @Transactional public int tick() {
        if(!settings.configured()) return 0;
        jdbc.update("DELETE FROM wa.approach_fix fix USING wa.tracking_session s,wa.driver_device d WHERE s.id=fix.session_id AND d.id=s.device_id AND d.environment=? AND (fix.expires_at<=? OR s.state<>'ACTIVE' OR ?=false)",settings.environment(),ts(clock.now()),settings.approachEnabled());
        int processed=0;
        for(int batch=0;batch<10;batch++) {
            var drivers=jdbc.query("""
                SELECT p.identity_id FROM wa.profile p WHERE EXISTS(
                  SELECT 1 FROM wa.operation_run r JOIN wa.tracking_session s ON s.run_id=r.id
                  JOIN wa.driver_device d ON d.id=s.device_id JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
                  WHERE r.driver_id=p.identity_id AND d.environment=? AND (
                    (s.state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED') AND (
                      ?=false OR d.status<>'ACTIVE' OR d.generation<>a.generation OR p.status<>'ACTIVE' OR NOT p.driver_enabled
                      OR s.lease_expires_at<=? OR r.state IN ('COMPLETED','CANCELED')
                      OR (r.state='STARTING' AND s.start_deadline<=?)
                      OR (r.state='TRACKING' AND (s.last_captured_at IS NULL OR s.last_captured_at<?))))
                    OR (r.state='START_FAILED' AND r.start_failure_expires_at<=?)))
                ORDER BY p.identity_id LIMIT 100 FOR UPDATE OF p SKIP LOCKED
                """,(rs,n)->rs.getObject(1,UUID.class),settings.environment(),settings.enabled(),ts(clock.now()),ts(clock.now()),ts(clock.now().minusSeconds(20)),ts(clock.now()));
            if(drivers.isEmpty()) break;
            for(UUID driver:drivers) {
                new WaTrackingAccess(jdbc,settings,clock).lockDriver(driver);
                var sessions=jdbc.query("""
                    SELECT s.id FROM wa.tracking_session s JOIN wa.driver_device d ON d.id=s.device_id
                    JOIN wa.operation_run r ON r.id=s.run_id WHERE s.driver_id=? AND d.environment=?
                      AND (s.state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED')
                        OR r.state='START_FAILED' AND r.start_failure_expires_at<=?
                          AND s.generation=(SELECT max(latest.generation) FROM wa.tracking_session latest WHERE latest.run_id=r.id))
                    ORDER BY s.run_id,s.generation LIMIT 100
                    """,(rs,n)->rs.getObject(1,UUID.class),driver,settings.environment(),ts(clock.now()));
                for(UUID id:sessions) {
                    Session session=store.session(id,driver); if(session==null) continue;
                    if(!settings.enabled() && collecting(session)) store.stop(session,"STOP_REQUESTED",clock.now());
                    new WaTrackingLifecycle(store,clock).reconcile(store.session(id,driver),clock.now()); processed++;
                }
            }
        }
        jdbc.update("UPDATE wa.start_intent SET state='EXPIRED' WHERE environment=? AND state='PENDING' AND expires_at<=?",settings.environment(),ts(clock.now()));
        jdbc.update("""
            UPDATE wa.message_outbox o SET start_payload_ciphertext=NULL FROM wa.start_intent i
            WHERE i.id=o.start_intent_id AND i.environment=? AND o.start_payload_ciphertext IS NOT NULL
              AND (o.expires_at<=? OR o.state IN ('SUBMITTED','DELIVERED','READ','UNKNOWN','FAILED','EXPIRED','CANCELED'))
            """,settings.environment(),ts(clock.now()));
        return processed;
    }
}
