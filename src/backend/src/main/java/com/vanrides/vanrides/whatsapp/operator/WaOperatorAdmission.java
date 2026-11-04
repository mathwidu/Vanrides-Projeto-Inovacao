package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.vanrides.vanrides.whatsapp.operator.WaOperatorService.*;

/** Called only within the existing authenticated operator transaction; never a public admission API. */
final class WaOperatorAdmission {
    private final JdbcTemplate jdbc;
    private final WaCrypto crypto;
    private final WaOperatorMode mode;
    WaOperatorAdmission(JdbcTemplate jdbc,WaCrypto crypto,WaOperatorMode mode) {
        this.jdbc=jdbc;this.crypto=crypto;this.mode=mode;
    }
    ObjectNode createCohort(Actor actor) {
        mode.requireOnline();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic,created_at) VALUES (?,?,false,?) ON CONFLICT (id) DO NOTHING",
            actor.batchId(),actor.environment(),time(actor.now()));
        requireCohort(actor);
        return object().put("resourceId",actor.batchId().toString()).put("synthetic",false);
    }
    private void requireCohort(Actor actor) {
        mode.requireOnline();
        var rows=jdbc.query("SELECT synthetic FROM wa.test_cohort WHERE id=? AND environment=? FOR SHARE",
            (rs,i)->rs.getBoolean(1),actor.batchId(),actor.environment());
        if(rows.size()!=1 || rows.get(0)) throw new WaOperatorFailure();
    }
    ObjectNode admit(WaOperatorRequest request,Actor actor) {
        requireCohort(actor);
        UUID identity=request.uuid("identityId");String instance=request.text("allowedInstanceId",1,80);
        String pn=request.text("phoneNumber",9,16).substring(1)+"@s.whatsapp.net";
        var channels=jdbc.query("""
            SELECT id FROM wa.service_credential WHERE principal='CHANNEL' AND environment=? AND allowed_instance_id=?
              AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """,(rs,i)->rs.getObject(1,UUID.class),actor.environment(),instance,time(actor.now()));
        if(channels.isEmpty()) throw new WaOperatorFailure();
        jdbc.query("SELECT pg_advisory_xact_lock(?)",(rs,i)->0,WaEventService.lockKey("ALIAS",instance,"PN\0"+pn));
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id,created_at) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING",
            identity,actor.batchId(),time(actor.now()));
        var identities=jdbc.query("SELECT id FROM wa.identity WHERE id=? AND test_cohort_id=? FOR UPDATE",
            (rs,i)->rs.getObject(1,UUID.class),identity,actor.batchId());
        if(identities.size()!=1) throw new WaOperatorFailure();
        Boolean conflicting=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND
              (provider<>'EVOLUTION' OR instance_id<>? OR alias_type='PN' AND alias_value<>?))
            """,Boolean.class,identity,instance,pn);
        if(Boolean.TRUE.equals(conflicting)) throw new WaOperatorFailure();
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value,created_at)
            VALUES (?,?,'EVOLUTION',?,'PN',?,?) ON CONFLICT (provider,instance_id,alias_type,alias_value) DO NOTHING
            """,UUID.randomUUID(),identity,instance,pn,time(actor.now()));
        Boolean bound=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION'
              AND instance_id=? AND alias_type='PN' AND alias_value=?)
            """,Boolean.class,identity,instance,pn);
        if(!Boolean.TRUE.equals(bound)) throw new WaOperatorFailure();
        return object().put("resourceId",identity.toString()).put("identityId",identity.toString())
            .put("allowedInstanceId",instance).put("state","ADMITTED");
    }
    ObjectNode bootstrap(WaOperatorRequest request,Actor actor) {
        requireCohort(actor);UUID identity=request.uuid("identityId"),invite=request.uuid("inviteId");
        requireIdentity(jdbc,actor,identity);
        var profiles=jdbc.query("SELECT status FROM wa.profile WHERE identity_id=? FOR UPDATE",(rs,i)->rs.getString(1),identity);
        if(!profiles.isEmpty() && !profiles.get(0).equals("ONBOARDING")) throw new WaOperatorFailure();
        Boolean admitted=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION' AND alias_type='PN')",
            Boolean.class,identity);
        if(!Boolean.TRUE.equals(admitted)) throw new WaOperatorFailure();
        Instant expires=future(request.instant("expiresAt").truncatedTo(ChronoUnit.MICROS),actor.now(),86400);
        // Server-keyed PRF, domain-separated from stored invitation hashes. No caller-chosen secret or raw token persistence.
        String raw=WaCrypto.base64(HexFormat.of().parseHex(crypto.secretHash("operator-driver-bootstrap-v1",
            actor.environment()+"\0"+actor.batchId()+"\0"+identity+"\0"+invite)));
        String hash=crypto.secretHash("driver-bootstrap",raw);
        jdbc.update("""
            INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,expires_at,created_by,created_at)
            VALUES (?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
            """,invite,identity,hash,time(expires),actor.credentialId(),time(actor.now()));
        var rows=jdbc.query("""
            SELECT identity_id,code_hash,expires_at,revoked_at,consumed_at FROM wa.driver_bootstrap_invite WHERE id=? FOR UPDATE
            """,(rs,i)->identity.equals(rs.getObject(1,UUID.class)) && WaCrypto.same(hash,rs.getString(2))
                && expires.equals(rs.getTimestamp(3).toInstant()) && rs.getTimestamp(4)==null && rs.getTimestamp(5)==null,invite);
        if(rows.size()!=1 || !rows.get(0)) throw new WaOperatorFailure();
        return object().put("resourceId",invite.toString()).put("identityId",identity.toString()).put("driverInviteCode",raw)
            .put("expiresAt",expires.toString());
    }
}
