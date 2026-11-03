package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Credential locks always precede queue/actor locks; time is checked again after waits. */
@Component
public final class WaTransportAccess {
    public record Grant(UUID credentialId, String instance, Instant expiresAt) { }
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    public WaTransportAccess(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
    }
    public Grant lock(WaPrincipal principal, WaPrincipal.Kind kind) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("WA_TRANSACTION_REQUIRED");
        if (principal == null || principal.kind() != kind || principal.credentialId() == null) throw WaProblem.denied();
        var rows = jdbc.query("""
            SELECT id,allowed_instance_id,expires_at FROM wa.service_credential
            WHERE id=? AND principal=? AND environment=? AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """, (rs, i) -> new Grant(rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant()),
            principal.credentialId(), kind.name(), settings.environment(), Timestamp.from(clock.now()));
        if (rows.size() != 1 || rows.get(0).instance() == null
                || !rows.get(0).instance().equals(principal.instanceId())) throw WaProblem.unauthorized();
        revalidate(rows.get(0));
        return rows.get(0);
    }
    public void revalidate(Grant grant) {
        if (!grant.expiresAt().isAfter(clock.now())) throw WaProblem.unauthorized();
    }
    public boolean allowedIdentity(UUID identity) {
        if (identity == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity i
              LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
              LEFT JOIN wa.profile p ON p.identity_id=i.id
              WHERE i.id=? AND (?=false OR c.environment=?)
                AND (p.status IS NULL OR p.status NOT IN ('SUSPENDED','CLOSED')))
            """, Boolean.class, identity, settings.cohortOnly(), settings.environment()));
    }
}
