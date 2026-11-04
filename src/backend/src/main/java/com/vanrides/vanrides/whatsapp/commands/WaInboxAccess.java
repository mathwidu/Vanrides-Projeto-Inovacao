package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public final class WaInboxAccess {
    public record Context(UUID eventId, UUID identityId, ObjectNode normalizedPayload, String normalizedKind,
                          Instant leaseExpiresAt, Instant credentialExpiresAt) { }
    private record CredentialLease(String instance, Instant expiresAt) { }
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    public WaInboxAccess(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
    }
    public Context lock(UUID eventId, String lease, WaPrincipal worker) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("WA_TRANSACTION_REQUIRED");
        if (worker == null || worker.kind() != WaPrincipal.Kind.CONVERSATION_WORKER) throw WaProblem.denied();
        try { WaDpopVerifier.canonical32(lease); } catch (Exception e) { throw WaProblem.unauthorized(); }
        List<CredentialLease> instances = jdbc.query("""
            SELECT allowed_instance_id,expires_at FROM wa.service_credential
            WHERE id=? AND principal='CONVERSATION_WORKER' AND environment=? AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """, (rs, i) -> new CredentialLease(rs.getString(1), rs.getTimestamp(2).toInstant()),
                worker.credentialId(), settings.environment(), Timestamp.from(clock.now()));
        if (instances.size() != 1 || instances.get(0).instance() == null) throw WaProblem.unauthorized();
        List<Context> found = jdbc.query("""
            SELECT id,identity_id,payload_json::text,normalized_kind,lease_expires_at FROM wa.integration_inbox
            WHERE id=? AND instance_id=? AND category='MESSAGE' AND state='CLAIMED'
              AND lease_credential_id=? AND lease_token_hash=? AND lease_generation>0 AND lease_expires_at>?
              AND (?=false OR provider='SYNTHETIC') FOR UPDATE
            """, (rs, i) -> new Context(rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class),
                WaJson.object(rs.getString("payload_json")), rs.getString("normalized_kind"), rs.getTimestamp("lease_expires_at").toInstant(), instances.get(0).expiresAt()),
            eventId, instances.get(0).instance(), worker.credentialId(),
            WaCrypto.digest(lease), Timestamp.from(clock.now()),settings.teamApiOnly());
        if (found.size() != 1 || found.get(0).identityId() == null) throw WaProblem.missing();
        Context context = found.get(0);
        revalidate(context);
        if (settings.cohortOnly()) {
            Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.identity i JOIN wa.test_cohort c ON c.id=i.test_cohort_id
                    WHERE i.id=? AND c.environment=? AND (?=false OR c.synthetic))
                    """, Boolean.class, context.identityId(), settings.environment(),settings.teamApiOnly());
            if (!Boolean.TRUE.equals(allowed)) throw WaProblem.denied();
        }
        return context;
    }
    /** Existing row locks fence ownership; the clock fence must also survive waits for later locks. */
    public void revalidate(Context context) {
        Instant now = clock.now();
        if (!context.credentialExpiresAt().isAfter(now)) throw WaProblem.unauthorized();
        if (!context.leaseExpiresAt().isAfter(now)) throw WaProblem.missing();
    }
}
