package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** All writes run under the driver's profile fence and the enclosing service transaction. */
final class WaTrackingStore {
    record Run(UUID id,UUID driver,String state,long version,Instant failureExpiry) { }
    record Session(UUID id,UUID run,UUID driver,UUID device,long generation,String state,Instant lease,
                   long sequence,Instant captured,Instant startDeadline,UUID stopRef,String gps,UUID intent,Instant stoppedAt) { }
    record Intent(UUID id,UUID driver,UUID occurrence,UUID device,UUID run,String state,String hash,
                  String action,long version,String roster,Instant expiry) { }
    final JdbcTemplate jdbc; private final WaCrypto crypto;
    WaTrackingStore(JdbcTemplate jdbc,WaCrypto crypto) { this.jdbc=jdbc; this.crypto=crypto; }
    Run run(UUID id,UUID driver) {
        return one(jdbc.query("SELECT * FROM wa.operation_run WHERE id=? AND driver_id=? FOR UPDATE",(rs,n)->
            new Run(id,driver,rs.getString("state"),rs.getLong("version"),instant(rs,"start_failure_expires_at")),id,driver));
    }
    Intent intent(UUID id,UUID driver) {
        return one(jdbc.query("SELECT * FROM wa.start_intent WHERE id=? AND driver_id=? AND environment=? FOR UPDATE",(rs,n)->
            new Intent(id,driver,rs.getObject("occurrence_id",UUID.class),rs.getObject("device_id",UUID.class),rs.getObject("run_id",UUID.class),
                rs.getString("state"),rs.getString("token_hash"),rs.getString("intent_action"),rs.getLong("expected_version"),
                rs.getString("roster_digest"),instant(rs,"expires_at")),id,driver,environment));
    }
    private String environment;
    WaTrackingStore environment(String value) { environment=value; return this; }
    Session session(UUID id,UUID driver) {
        var owners=jdbc.queryForList("SELECT run_id FROM wa.tracking_session WHERE id=? AND driver_id=?",id,driver);
        if(owners.size()!=1) return null;
        run((UUID)owners.get(0).get("run_id"),driver);
        return one(jdbc.query("SELECT * FROM wa.tracking_session WHERE id=? AND driver_id=? FOR UPDATE",WaTrackingStore::session,id,driver));
    }
    Session current(UUID run) {
        return one(jdbc.query("SELECT * FROM wa.tracking_session WHERE run_id=? ORDER BY generation DESC LIMIT 1 FOR UPDATE",WaTrackingStore::session,run));
    }
    Session forIntent(UUID intent) {
        return one(jdbc.query("SELECT * FROM wa.tracking_session WHERE origin_intent_id=? ORDER BY generation DESC LIMIT 1 FOR UPDATE",WaTrackingStore::session,intent));
    }
    Session forDevice(UUID run,UUID device) {
        return one(jdbc.query("SELECT * FROM wa.tracking_session WHERE run_id=? AND device_id=? ORDER BY generation DESC LIMIT 1 FOR UPDATE",WaTrackingStore::session,run,device));
    }
    Session create(UUID run,UUID driver,UUID device,UUID intent,Instant now) {
        Long generation=jdbc.queryForObject("SELECT COALESCE(max(generation),0)+1 FROM wa.tracking_session WHERE run_id=?",Long.class,run);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,lease_expires_at,start_deadline,origin_intent_id,created_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,id,run,driver,device,generation,ts(now.plusSeconds(WaTrackingPolicy.LEASE_SECONDS)),ts(now.plusSeconds(WaTrackingPolicy.START_SECONDS)),intent,ts(now));
        return session(id,driver);
    }
    void stop(Session session,String state,Instant now) {
        jdbc.update("""
            UPDATE wa.tracking_session SET state=?,stop_requested_at=COALESCE(stop_requested_at,?),
              stop_request_ref=COALESCE(stop_request_ref,?),lease_expires_at=LEAST(lease_expires_at,?),gps_state='STALE',version=version+1 WHERE id=?
            """,state,ts(now),UUID.randomUUID(),ts(now),session.id());
    }
    ObjectNode replay(String kind,UUID device,String key,ObjectNode request,Instant now) {
        var rows=jdbc.queryForList("SELECT * FROM wa.operation_replay WHERE operation_kind=? AND scope_id=? AND request_key=?",kind,device,key);
        if(rows.isEmpty()) return null; var row=rows.get(0);
        if(!WaCrypto.same((String)row.get("request_hash"),WaCrypto.digest(WaJson.canonical(request)))) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        if(!((Timestamp)row.get("expires_at")).toInstant().isAfter(now)) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        return WaJson.object(crypto.decrypt(aad(kind,device,key),(byte[])row.get("response_ciphertext")));
    }
    void remember(String kind,UUID device,String key,ObjectNode request,ObjectNode response,Instant now) {
        jdbc.update("""
            INSERT INTO wa.operation_replay(id,operation_kind,scope_id,request_key,request_hash,response_ciphertext,expires_at) VALUES (?,?,?,?,?,?,?)
            """,UUID.randomUUID(),kind,device,key,WaCrypto.digest(WaJson.canonical(request)),
            crypto.encrypt(aad(kind,device,key),WaJson.write(response)),ts(now.plusSeconds(300)));
    }
    static boolean collecting(Session s) { return s!=null && List.of("STARTING","ACTIVE","DEGRADED").contains(s.state()); }
    static boolean terminal(Run r) { return r==null || List.of("COMPLETED","CANCELED").contains(r.state()); }
    static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static String aad(String kind,UUID device,String key) { return kind+"\0"+device+"\0"+key; }
    private static <T> T one(List<T> rows) { return rows.size()==1?rows.get(0):null; }
    private static Instant instant(ResultSet rs,String field) throws SQLException { var v=rs.getTimestamp(field); return v==null?null:v.toInstant(); }
    private static Session session(ResultSet rs,int n) throws SQLException {
        return new Session(rs.getObject("id",UUID.class),rs.getObject("run_id",UUID.class),rs.getObject("driver_id",UUID.class),
            rs.getObject("device_id",UUID.class),rs.getLong("generation"),rs.getString("state"),instant(rs,"lease_expires_at"),
            rs.getLong("last_sequence"),instant(rs,"last_captured_at"),instant(rs,"start_deadline"),rs.getObject("stop_request_ref",UUID.class),
            rs.getString("gps_state"),rs.getObject("origin_intent_id",UUID.class),instant(rs,"stopped_at"));
    }
}
