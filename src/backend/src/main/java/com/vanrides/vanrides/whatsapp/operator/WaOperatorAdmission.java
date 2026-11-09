package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizer.Alias;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.vanrides.vanrides.whatsapp.operator.WaOperatorService.*;

/** Called only within the existing authenticated operator transaction; never a public admission API. */
final class WaOperatorAdmission {
    private final JdbcTemplate jdbc;
    private final WaAdmissionStore store;
    private final WaOperatorMode mode;
    WaOperatorAdmission(JdbcTemplate jdbc,WaCrypto crypto,WaOperatorMode mode) {
        this.jdbc=jdbc;this.store=new WaAdmissionStore(jdbc,crypto);this.mode=mode;
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
        try { store.requireCohort(actor.environment(),actor.batchId(),true); }
        catch (WaProblem denied) { throw new WaOperatorFailure(); }
    }
    ObjectNode admit(WaOperatorRequest request,Actor actor) {
        requireCohort(actor);
        UUID identity=request.uuid("identityId");String instance=request.text("allowedInstanceId",1,80);
        Alias alias=new Alias("PN",request.text("phoneNumber",9,16).substring(1)+"@s.whatsapp.net");
        try {
            store.lockChannels(actor.environment(),instance,actor.now());
            store.lockAliases(instance,List.of(alias));
            store.createIdentity(identity,actor.batchId(),actor.now());
            store.requireIdentity(identity,actor.batchId(),true);
            store.bind(instance,identity,alias,actor.now());
        } catch (WaProblem denied) { throw new WaOperatorFailure(); }
        return object().put("resourceId",identity.toString()).put("identityId",identity.toString())
            .put("allowedInstanceId",instance).put("state","ADMITTED");
    }
    ObjectNode bootstrap(WaOperatorRequest request,Actor actor) {
        requireCohort(actor);UUID identity=request.uuid("identityId"),invite=request.uuid("inviteId");
        requireIdentity(jdbc,actor,identity);
        if(!store.profile(identity,true).canIssueDriverInvite()) throw new WaOperatorFailure();
        Boolean admitted=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION' AND alias_type='PN')",
            Boolean.class,identity);
        if(!Boolean.TRUE.equals(admitted)) throw new WaOperatorFailure();
        Instant expires=future(request.instant("expiresAt").truncatedTo(ChronoUnit.MICROS),actor.now(),86400);
        String raw=store.bootstrapCode(actor.environment(),actor.batchId(),identity,invite);
        String hash=store.bootstrapHash(raw);
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
