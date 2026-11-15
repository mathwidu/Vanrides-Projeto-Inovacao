package com.vanrides.vanrides.whatsapp.admission;

import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizer.Alias;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** One bounded passenger admission on authenticated team ingress; no profile or consent. */
public final class WaLinkInviteAdmission {
    private static final String DRIVER_SCOPE = """
        i.test_cohort_id=? AND c.environment=? AND NOT c.synthetic
        AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled AND p.notice_version=?
        AND (EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id
          AND a.provider='EVOLUTION' AND a.instance_id=? AND a.alias_type='PN')
        OR EXISTS(SELECT 1 FROM wa.team_test_driver t WHERE t.driver_id=i.id
          AND t.test_cohort_id=i.test_cohort_id AND t.instance_id=?))
        """;
    private final JdbcTemplate jdbc;
    private final WaAdmissionStore admission;
    private final WaClock clock;

    public WaLinkInviteAdmission(JdbcTemplate jdbc, WaAdmissionStore admission, WaClock clock) {
        this.jdbc = jdbc;
        this.admission = admission;
        this.clock = clock;
    }

    /** Do not repurpose a legacy or conflicting alias as a new link identity. Alias locks are held. */
    public boolean newSender(String instance, List<Alias> aliases) {
        return aliases.stream().anyMatch(a -> a.type().equals("PN") && a.value().matches("[1-9][0-9]{7,14}@s\\.whatsapp\\.net"))
            && aliases.stream().noneMatch(a -> admission.owner(instance, a) != null);
    }

    /** Take issuer profile then authorization before locking an existing recipient identity. */
    public UUID lockIssuer(String instance, String environment, UUID cohort, String hash, Instant occurred, UUID recipient) {
        if (hash.isEmpty() || !current(occurred, clock.now()) || !eligibleRecipient(recipient)) return null;
        var candidates = jdbc.query("""
            SELECT v.driver_id FROM wa.driver_invite v
            WHERE v.code_hash=? AND v.allow_first_contact AND v.onboarding_identity_id IS NULL
              AND v.revoked_at IS NULL AND v.use_count=0 AND v.expires_at>?
              AND v.created_at<?
            """, (rs, n) -> rs.getObject(1, UUID.class), hash, Timestamp.from(clock.now()),
            Timestamp.from(occurred.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)));
        if (candidates.size() != 1 || candidates.get(0).equals(recipient)) return null;
        UUID driver = candidates.get(0);
        var profiles = jdbc.query("""
            SELECT p.identity_id FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id
              JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE p.identity_id=? AND
            """ + DRIVER_SCOPE + " FOR SHARE OF p",
            (rs, n) -> rs.getObject(1, UUID.class), driver, cohort, environment,
            WaOnboardingCommands.NOTICE_VERSION, instance, instance);
        if (profiles.size() != 1) return null;
        var authorizations = jdbc.query("SELECT driver_id FROM wa.driver_authorization WHERE driver_id=? FOR SHARE",
            (rs, n) -> rs.getObject(1, UUID.class), driver);
        return authorizations.size() == 1 ? driver : null;
    }

    /** Called in the same transaction as the durable inbox insert, with alias and issuer locks held. */
    public UUID claim(UUID driver, String instance, UUID cohort, String hash, Instant occurred, UUID recipient, List<Alias> aliases) {
        var invites = jdbc.query("""
            SELECT id,expires_at,created_at FROM wa.driver_invite
            WHERE code_hash=? AND driver_id=? AND allow_first_contact AND onboarding_identity_id IS NULL
              AND revoked_at IS NULL AND use_count=0 FOR UPDATE
            """, (rs, n) -> new Invite(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
            rs.getTimestamp(3).toInstant()), hash, driver);
        if (invites.size() != 1) return null;
        var invite = invites.get(0);
        Instant now = clock.now();
        if (!current(occurred, now) || !invite.expires().isAfter(now)
                || occurred.isBefore(invite.created().truncatedTo(ChronoUnit.SECONDS))
                || !eligibleRecipient(recipient)) return null;
        UUID identity = recipient;
        if (identity == null) {
            identity = UUID.randomUUID();
            admission.createIdentity(identity, cohort, now);
            for (Alias alias : aliases) admission.bind(instance, identity, alias, now);
        }
        jdbc.update("UPDATE wa.driver_invite SET onboarding_identity_id=?,version=version+1 WHERE id=?",
            identity, invite.id());
        return identity;
    }

    /** Before profile creation, only the still-live grant permits ordinary follow-up messages. */
    public boolean liveGrant(String instance, String environment, UUID cohort, UUID recipient) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.driver_invite v JOIN wa.driver_authorization d ON d.driver_id=v.driver_id
              JOIN wa.profile p ON p.identity_id=d.driver_id JOIN wa.identity i ON i.id=p.identity_id
              JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE v.onboarding_identity_id=? AND v.allow_first_contact AND v.revoked_at IS NULL
              AND v.expires_at>? AND v.use_count=0 AND
            """ + DRIVER_SCOPE + ")", Boolean.class, recipient, Timestamp.from(clock.now()), cohort, environment,
            WaOnboardingCommands.NOTICE_VERSION, instance, instance));
    }

    private boolean eligibleRecipient(UUID recipient) {
        return recipient == null || Boolean.TRUE.equals(jdbc.queryForObject("""
            WITH recipient(id) AS (VALUES (?::uuid))
            SELECT NOT EXISTS(
                SELECT 1 FROM wa.profile p WHERE p.identity_id=recipient.id AND
                  (p.role<>'PASSENGER' OR p.status NOT IN ('ONBOARDING','ACTIVE'))
                UNION ALL SELECT 1 FROM wa.driver_bootstrap_invite b WHERE b.identity_id=recipient.id
                  AND b.revoked_at IS NULL AND b.consumed_at IS NULL AND b.expires_at>?
                UNION ALL SELECT 1 FROM wa.conversation_state s WHERE s.identity_id=recipient.id AND s.stage='PAUSED'
                UNION ALL SELECT 1 FROM wa.driver_passenger_link l WHERE l.passenger_id=recipient.id AND l.state IN ('REQUESTED','APPROVED')
            ) FROM recipient
            """, Boolean.class, recipient, Timestamp.from(clock.now())));
    }

    private static boolean current(Instant occurred, Instant now) {
        return occurred != null && !occurred.isBefore(now.minusSeconds(300)) && !occurred.isAfter(now.plusSeconds(30));
    }

    private record Invite(UUID id, Instant expires, Instant created) {}
}
