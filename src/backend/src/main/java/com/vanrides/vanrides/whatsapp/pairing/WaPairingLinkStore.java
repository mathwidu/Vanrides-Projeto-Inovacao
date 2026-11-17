package com.vanrides.vanrides.whatsapp.pairing;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Handoff owns a pending challenge without weakening the legacy PENDING constraint. */
final class WaPairingLinkStore {
    record Link(UUID id, UUID driver, String environment, String instance, UUID inbox, UUID command,
                String hash, long generation, String state, UUID pairing, String claimHash,
                Instant expires, UUID review) { }
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaPairingRepository pairing;
    WaPairingLinkStore(JdbcTemplate jdbc, WaSettings settings) {
        this.jdbc=jdbc; this.settings=settings; pairing=new WaPairingRepository(jdbc);
    }
    Link find(UUID id,boolean lock) {
        return jdbc.query("SELECT * FROM wa.pairing_handoff WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Link(
            rs.getObject("id",UUID.class),rs.getObject("driver_id",UUID.class),rs.getString("environment"),rs.getString("instance_id"),
            rs.getObject("source_inbox_id",UUID.class),rs.getObject("source_command_id",UUID.class),rs.getString("token_hash"),
            rs.getLong("generation"),rs.getString("state"),rs.getObject("pairing_id",UUID.class),rs.getString("claim_hash"),
            rs.getTimestamp("expires_at").toInstant(),rs.getObject("review_outbox_id",UUID.class)),id).stream().findFirst().orElse(null);
    }
    Link current(UUID driver) {
        var ids=jdbc.queryForList("SELECT id FROM wa.pairing_handoff WHERE driver_id=? AND environment=? AND state<>'REVOKED'",UUID.class,driver,settings.environment());
        return ids.size()==1?find(ids.get(0),true):null;
    }
    long driver(UUID id) {
        var driver=pairing.lockDriver(id);
        if(driver==null || !"DRIVER".equals(driver.role()) || !"ACTIVE".equals(driver.status()) || !driver.enabled()
                || !driver.noticeAccepted() || settings.cohortOnly() && !settings.environment().equals(driver.cohortEnvironment())
                || Boolean.TRUE.equals(jdbc.queryForObject("SELECT privacy_erased_at IS NOT NULL FROM wa.profile WHERE identity_id=?",Boolean.class,id)))
            throw WaProblem.denied();
        return driver.generation();
    }
    void requireLive(Link link,long generation,Instant now) {
        if(link==null || !settings.environment().equals(link.environment())) throw WaProblem.missing();
        if("REVOKED".equals(link.state()) || !link.expires().isAfter(now) || link.generation()!=generation)
            throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
    }
    boolean sameDevice(UUID driver,String jkt) {
        return device(driver,jkt,true);
    }
    boolean replacesDevice(UUID driver,String jkt) {
        return device(driver,jkt,false);
    }
    private boolean device(UUID driver,String jkt,boolean same) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.driver_device d JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
              WHERE d.driver_id=? AND d.environment=? AND d.generation=a.generation AND d.status='ACTIVE'
                AND d.jkt """+(same?"=":"<>")+"?)",Boolean.class,driver,settings.environment(),jkt));
    }
    boolean activeTrip(UUID driver) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.operation_run r
              LEFT JOIN LATERAL (SELECT s.state FROM wa.tracking_session s WHERE s.run_id=r.id ORDER BY s.generation DESC LIMIT 1) s ON true
              WHERE r.driver_id=? AND r.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')
                AND (s.state IS NULL OR s.state<>'STOPPED'))
            """,Boolean.class,driver));
    }
    void replacePending(UUID driver) {
        jdbc.update("""
            UPDATE wa.pairing_challenge SET state='REVOKED' WHERE state IN ('PENDING','APPROVED')
              AND id IN (SELECT pairing_id FROM wa.pairing_handoff WHERE driver_id=? AND environment=? AND state<>'REVOKED')
            """,driver,settings.environment());
        jdbc.update("UPDATE wa.pairing_handoff SET state='REVOKED' WHERE driver_id=? AND environment=? AND state<>'REVOKED'",driver,settings.environment());
    }
    String instance(UUID inbox,UUID actor) {
        var instances=jdbc.queryForList("""
            SELECT instance_id FROM wa.integration_inbox WHERE id=? AND identity_id=? AND category='MESSAGE'
            """,String.class,inbox,actor);
        if(instances.size()!=1) throw WaProblem.denied();
        return instances.get(0);
    }
    String state(Link link) {
        if(link.pairing()==null) return "READY";
        var found=pairing.pairing(link.pairing(),false);
        if(found==null || !java.util.Set.of("PENDING","APPROVED","REDEEMED").contains(found.state()))
            throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        return found.state();
    }
    void requireRedeem(UUID pairingId,UUID driver,long generation,Instant now) {
        var ids=jdbc.queryForList("SELECT id FROM wa.pairing_handoff WHERE pairing_id=?",UUID.class,pairingId);
        if(ids.isEmpty()) return;
        Link link=find(ids.get(0),true);requireLive(link,generation,now);
        if(!driver.equals(link.driver()) || !"APPROVED".equals(link.state())) throw WaProblem.denied();
        if(activeTrip(driver)) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
    }
    static Timestamp ts(Instant time) { return Timestamp.from(time); }
}
