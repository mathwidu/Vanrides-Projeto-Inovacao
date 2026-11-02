package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Lock order matches pairing and human commands: profile, authorization, device, token, run. */
final class WaTrackingAccess {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock;
    WaTrackingAccess(JdbcTemplate jdbc, WaSettings settings, WaClock clock) { this.jdbc=jdbc; this.settings=settings; this.clock=clock; }
    void lockDriver(UUID driver) {
        if (jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",driver).isEmpty()) throw WaProblem.missing();
        if (jdbc.queryForList("SELECT driver_id FROM wa.driver_authorization WHERE driver_id=? FOR UPDATE",driver).isEmpty()) throw WaProblem.denied();
    }
    Instant device(WaPrincipal actor, boolean cleanup) {
        if (actor.kind()!=WaPrincipal.Kind.DEVICE || actor.deviceId()==null || actor.credentialId()==null) throw WaProblem.unauthorized();
        lockDriver(actor.driverId());
        var rows=jdbc.queryForList("""
            SELECT d.*,p.status profile_status,p.driver_enabled,a.generation current_generation,c.environment cohort_environment
            FROM wa.driver_device d JOIN wa.profile p ON p.identity_id=d.driver_id
            JOIN wa.driver_authorization a ON a.driver_id=d.driver_id JOIN wa.identity i ON i.id=d.driver_id
            LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id WHERE d.id=? FOR UPDATE OF d
            """,actor.deviceId());
        if(rows.size()!=1) throw WaProblem.unauthorized(); var row=rows.get(0);
        boolean allowed="ACTIVE".equals(row.get("status")) && "ACTIVE".equals(row.get("profile_status"))
            && Boolean.TRUE.equals(row.get("driver_enabled")) && ((Number)row.get("generation")).longValue()==((Number)row.get("current_generation")).longValue();
        if (!actor.driverId().equals(row.get("driver_id")) || !WaCrypto.same(actor.jkt(),(String)row.get("jkt"))
                || actor.generation()!=((Number)row.get("generation")).longValue() || !settings.environment().equals(row.get("environment"))
                || !cleanup && !allowed || settings.cohortOnly() && !settings.environment().equals(row.get("cohort_environment"))) throw WaProblem.unauthorized();
        var tokens=jdbc.queryForList("SELECT * FROM wa.device_token WHERE id=? AND device_id=? AND kind='ACCESS' FOR UPDATE",actor.credentialId(),actor.deviceId());
        if(tokens.size()!=1) throw WaProblem.unauthorized(); var token=tokens.get(0);
        Instant expires=((Timestamp)token.get("expires_at")).toInstant();
        if(!expires.isAfter(clock.now()) || !cleanup && token.get("revoked_at")!=null) throw WaProblem.unauthorized();
        return expires;
    }
}
