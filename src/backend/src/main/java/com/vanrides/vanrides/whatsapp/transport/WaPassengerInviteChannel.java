package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resolves opted-in recipients from admitted inbox senders on the same Evolution channel. */
public final class WaPassengerInviteChannel {
    private static final String PN_SUFFIX = "@s.whatsapp.net";
    private final JdbcTemplate jdbc;
    private final WaClock clock;

    private record Scope(String instance, UUID cohort) {}
    private record Origin(UUID event, UUID driver, Instant expiresAt) {}
    record Permission(boolean accepted, boolean knownSender, Instant received, Instant occurred) {
        Instant revocationTime() { return occurred == null ? received : occurred; }
    }
    record Route(String address, Instant expiresAt) {}

    public WaPassengerInviteChannel(JdbcTemplate jdbc, WaClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public UUID recipient(UUID sourceEvent, UUID driver, String digits) {
        if (digits == null || !digits.matches("[1-9][0-9]{7,14}")) return null;
        Scope scope = scope(sourceEvent, driver, null, null);
        if (scope == null) return null;
        UUID candidate = owner(scope.instance(), digits);
        return route(scope, driver, candidate) == null ? null : candidate;
    }

    public boolean eligible(UUID sourceEvent, UUID driver, UUID passenger) {
        return route(scope(sourceEvent, driver, null, null), driver, passenger) != null;
    }

    Route current(UUID box, UUID passenger, String instance, String environment, boolean lock) {
        var origins = jdbc.query("""
            SELECT r.inbox_event_id,r.identity_id,i.expires_at FROM wa.message_outbox o
            JOIN wa.command_receipt r ON r.command_id=o.command_id AND r.command_type='CREATE_INVITE'
            JOIN wa.driver_invite i ON i.id=o.private_invite_id AND i.driver_id=r.identity_id
            WHERE o.id=? AND o.recipient_identity_id=? AND i.onboarding_identity_id=?
              AND o.private_payload_ciphertext IS NOT NULL AND NOT i.allow_first_contact
              AND i.revoked_at IS NULL AND i.use_count<i.max_uses AND i.expires_at>?
            """, (rs, n) -> new Origin(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()),
            box, passenger, passenger, Timestamp.from(clock.now()));
        if (origins.size() != 1) return null;
        Origin origin = origins.get(0);
        if (lock) lockEligibility(origin.driver(), passenger);
        Route route = route(scope(origin.event(), origin.driver(), instance, environment), origin.driver(), passenger);
        if (route == null || !origin.expiresAt().isAfter(clock.now())) return null;
        return origin.expiresAt().isBefore(route.expiresAt()) ? new Route(route.address(), origin.expiresAt()) : route;
    }

    private Scope scope(UUID event, UUID driver, String instance, String environment) {
        var rows = jdbc.query("""
            SELECT e.instance_id,c.id FROM wa.integration_inbox e
            JOIN wa.identity d ON d.id=e.identity_id
            JOIN wa.test_cohort c ON c.id=d.test_cohort_id
            JOIN wa.profile p ON p.identity_id=d.id
            WHERE e.id=? AND d.id=? AND e.provider='EVOLUTION' AND e.category='MESSAGE'
              AND (?::text IS NULL OR e.instance_id=?) AND (?::text IS NULL OR c.environment=?)
              AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled AND p.notice_version=?
            """, (rs, n) -> new Scope(rs.getString(1), rs.getObject(2, UUID.class)),
            event, driver, instance, instance, environment, environment, WaOnboardingCommands.NOTICE_VERSION);
        return rows.size() == 1 ? rows.get(0) : null;
    }

    private Route route(Scope scope, UUID driver, UUID passenger) {
        if (scope == null || passenger == null || passenger.equals(driver)) return null;
        var addresses = jdbc.query("""
            SELECT a.alias_value FROM wa.identity p
            JOIN wa.identity_alias a ON a.identity_id=p.id AND a.provider='EVOLUTION' AND a.instance_id=? AND a.alias_type='PN'
            LEFT JOIN wa.profile f ON f.identity_id=p.id
            WHERE p.id=? AND p.test_cohort_id=?
              AND (f.role='PASSENGER' AND f.status IN ('ONBOARDING','ACTIVE') OR f.identity_id IS NULL AND EXISTS(
                SELECT 1 FROM wa.team_admission_operation op WHERE op.identity_id=p.id AND op.action='ADMIT_PASSENGER'))
              AND NOT EXISTS(SELECT 1 FROM wa.driver_bootstrap_invite b WHERE b.identity_id=p.id
                AND b.revoked_at IS NULL AND b.consumed_at IS NULL AND b.expires_at>?)
              AND NOT EXISTS(SELECT 1 FROM wa.conversation_state s WHERE s.identity_id=p.id AND s.stage='PAUSED')
            LIMIT 2
            """, (rs, n) -> rs.getString(1), scope.instance(), passenger, scope.cohort(), Timestamp.from(clock.now()));
        if (addresses.size() != 1) return null;
        String address = addresses.get(0);
        if (!address.matches("[1-9][0-9]{7,14}@s\\.whatsapp\\.net")) return null;
        String digits = address.substring(0, address.length() - PN_SUFFIX.length());
        if (!passenger.equals(owner(scope.instance(), digits))) return null;
        Instant permissionEnd = permissionEnd(scope.instance(), passenger);
        return permissionEnd == null ? null : new Route(address, permissionEnd);
    }

    private UUID owner(String instance, String digits) {
        var numbers = phoneVariants(digits);
        var owners = jdbc.query("""
            SELECT DISTINCT identity_id FROM wa.identity_alias
            WHERE provider='EVOLUTION' AND instance_id=? AND alias_type='PN' AND alias_value IN (?,?)
            LIMIT 2
            """, (rs, n) -> rs.getObject(1, UUID.class), instance,
            numbers.get(0) + PN_SUFFIX, numbers.get(1) + PN_SUFFIX);
        return owners.size() == 1 ? owners.get(0) : null;
    }

    private Instant permissionEnd(String instance, UUID passenger) {
        Instant now = clock.now();
        var permissions = jdbc.query("""
            SELECT e.payload_json->'invitePermission'='true'::jsonb AS accepted, a.id IS NOT NULL AS known_sender,
              e.created_at,e.payload_json->>'occurredAt' AS occurred_at
            FROM wa.integration_inbox e
            LEFT JOIN wa.identity_alias a ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=e.identity_id
              AND a.provider=e.provider AND a.instance_id=e.instance_id AND a.alias_type IN ('PN','LID')
            WHERE e.provider='EVOLUTION' AND e.instance_id=? AND e.identity_id=? AND e.category='MESSAGE'
              AND e.normalized_kind='TEXT' AND e.state<>'SKIPPED' AND e.created_at>? AND e.created_at<=?
              AND e.payload_json->'invitePermission' IN ('true'::jsonb,'false'::jsonb)
            ORDER BY e.created_at DESC, accepted, e.id DESC
            """, (rs, n) -> {
                Instant received = rs.getTimestamp("created_at").toInstant();
                return new Permission(rs.getBoolean("accepted"), rs.getBoolean("known_sender"), received,
                    occurred(rs.getString("occurred_at")));
            }, instance, passenger, Timestamp.from(now.minus(1, ChronoUnit.DAYS)), Timestamp.from(now));
        return permissionDeadline(permissions, clock.now());
    }

    static Instant permissionDeadline(List<Permission> permissions, Instant now) {
        if (permissions.isEmpty()) return null;
        Permission latest = permissions.get(0);
        if (!latest.accepted() || !latest.knownSender() || latest.occurred() == null
                || latest.occurred().isBefore(latest.received().minusSeconds(300))
                || latest.occurred().isAfter(latest.received().plusSeconds(30))) return null;
        // A delayed replay of an opt-in must not supersede a more recent opt-out.
        if (permissions.stream().anyMatch(p -> !p.accepted() && !p.revocationTime().isBefore(latest.occurred()))) return null;
        Instant basis = latest.occurred().isBefore(latest.received()) ? latest.occurred() : latest.received();
        Instant deadline = basis.plus(1, ChronoUnit.DAYS);
        return deadline.isAfter(now) ? deadline : null;
    }

    private static Instant occurred(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeException invalid) { return null; }
    }

    private void lockEligibility(UUID driver, UUID passenger) {
        // Commands hold their own profile first; ingress fences alias changes and opt-outs with the
        // recipient identity. FOR UPDATE also fences new profiles and bootstrap grants via their FKs.
        // Reset can already own both while waiting for outbox, so never wait here.
        if (jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE SKIP LOCKED", driver).isEmpty()
                || jdbc.queryForList("SELECT id FROM wa.identity WHERE id=? FOR UPDATE SKIP LOCKED", passenger).isEmpty()
                || jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE SKIP LOCKED", passenger).isEmpty()
                    && Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM wa.profile WHERE identity_id=?)", Boolean.class, passenger))) {
            throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        }
    }

    static List<String> phoneVariants(String digits) {
        // Ingress supplies E.164 digits, including the international country calling code.
        String alternate = digits;
        if (digits.matches("55[1-9][0-9][6-9][0-9]{7}")) {
            alternate = digits.substring(0, 4) + "9" + digits.substring(4);
        } else if (digits.matches("55[1-9][0-9]9[6-9][0-9]{7}")) {
            alternate = digits.substring(0, 4) + digits.substring(5);
        }
        return List.of(digits, alternate);
    }
}
