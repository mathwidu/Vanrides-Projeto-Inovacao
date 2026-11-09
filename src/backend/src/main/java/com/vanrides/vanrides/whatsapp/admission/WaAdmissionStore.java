package com.vanrides.vanrides.whatsapp.admission;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizer.Alias;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared, transaction-local checks for admissions and recoverable bootstrap invitations. */
@Component
public final class WaAdmissionStore {
    public record Profile(String role, String status) {
        public boolean canIssueDriverInvite() { return status == null || "ONBOARDING".equals(status); }
    }

    private final JdbcTemplate jdbc;
    private final WaCrypto crypto;
    public WaAdmissionStore(JdbcTemplate jdbc, WaCrypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    public void requireCohort(String environment, UUID cohort, boolean lock) {
        String sql = "SELECT synthetic FROM wa.test_cohort WHERE id=? AND environment=?";
        var rows = jdbc.query(lock ? sql + " FOR SHARE" : sql, (rs, i) -> rs.getBoolean(1), cohort, environment);
        if (rows.size() != 1 || rows.get(0)) throw WaProblem.denied();
    }

    public Instant lockChannels(String environment, String instance, Instant now) {
        var expirations = jdbc.query("""
            SELECT expires_at FROM wa.service_credential WHERE principal='CHANNEL' AND environment=? AND allowed_instance_id=?
              AND revoked_at IS NULL AND expires_at>? ORDER BY id FOR SHARE
            """, (rs, i) -> rs.getTimestamp(1).toInstant(), environment, instance, Timestamp.from(now));
        return expirations.stream().max(Comparator.naturalOrder()).orElseThrow(WaProblem::denied);
    }

    public void lockAliases(String instance, List<Alias> aliases) {
        for (String alias : aliases.stream().map(a -> a.type() + "\0" + a.value()).distinct().sorted().toList())
            jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs, i) -> 0, WaEventService.lockKey("ALIAS", instance, alias));
    }

    public UUID owner(String instance, Alias alias) {
        var rows = jdbc.query("""
            SELECT identity_id FROM wa.identity_alias WHERE provider='EVOLUTION' AND instance_id=?
              AND alias_type=? AND alias_value=?
            """, (rs, i) -> rs.getObject(1, UUID.class), instance, alias.type(), alias.value());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void createIdentity(UUID identity, UUID cohort, Instant now) {
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id,created_at) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING",
            identity, cohort, Timestamp.from(now));
    }

    public void requireIdentity(UUID identity, UUID cohort, boolean lock) {
        String sql = "SELECT id FROM wa.identity WHERE id=? AND test_cohort_id=?";
        var rows = jdbc.query(lock ? sql + " FOR UPDATE" : sql, (rs, i) -> rs.getObject(1, UUID.class), identity, cohort);
        if (rows.size() != 1) throw WaProblem.denied();
    }

    public void requireCompatible(String instance, UUID identity, Alias alias) {
        Boolean conflict = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND
              (provider<>'EVOLUTION' OR instance_id<>? OR alias_type=? AND alias_value<>?))
            """, Boolean.class, identity, instance, alias.type(), alias.value());
        if (Boolean.TRUE.equals(conflict)) throw WaProblem.denied();
    }

    public boolean teamAdmitted(UUID identity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            WITH actor(id) AS (VALUES (?::uuid))
            SELECT EXISTS(
                SELECT 1 FROM wa.team_admission_operation WHERE identity_id=actor.id
                UNION ALL SELECT 1 FROM wa.profile WHERE identity_id=actor.id
                UNION ALL SELECT 1 FROM wa.driver_bootstrap_invite WHERE identity_id=actor.id
            ) FROM actor
            """, Boolean.class, identity));
    }

    public boolean hasPhone(String instance, UUID identity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION'
              AND instance_id=? AND alias_type='PN')
            """, Boolean.class, identity, instance));
    }

    public void bind(String instance, UUID identity, Alias alias, Instant now) {
        requireCompatible(instance, identity, alias);
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value,created_at)
            VALUES (?,?,'EVOLUTION',?,?,?,?) ON CONFLICT (provider,instance_id,alias_type,alias_value) DO NOTHING
            """, UUID.randomUUID(), identity, instance, alias.type(), alias.value(), Timestamp.from(now));
        if (!identity.equals(owner(instance, alias))) throw WaProblem.denied();
    }

    public Profile profile(UUID identity, boolean lock) {
        String sql = "SELECT role,status FROM wa.profile WHERE identity_id=?";
        var rows = jdbc.query(lock ? sql + " FOR UPDATE" : sql, (rs, i) -> new Profile(rs.getString(1), rs.getString(2)), identity);
        return rows.isEmpty() ? new Profile(null, null) : rows.get(0);
    }

    public String bootstrapCode(String environment, UUID cohort, UUID identity, UUID invite) {
        // The PRF is shared so a real-cohort invitation originally issued by the local operator is recoverable.
        return WaCrypto.base64(HexFormat.of().parseHex(crypto.secretHash("operator-driver-bootstrap-v1",
            environment + "\0" + cohort + "\0" + identity + "\0" + invite)));
    }

    public String bootstrapHash(String code) { return crypto.secretHash("driver-bootstrap", code); }
}
