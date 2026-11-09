package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore.Profile;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizer.Alias;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.vanrides.vanrides.whatsapp.team.WaTeamProblem.Code.*;

@Service
class WaTeamOperations {
    enum Action { ADMIT_PASSENGER, ISSUE_DRIVER_INVITE, RENEW_DRIVER_INVITE }
    private enum InviteStatus { NONE, ACTIVE, EXPIRED, REVOKED, CONSUMED }
    record Request(String phone, Action action, UUID id) {
        @Override public String toString() { return "WaTeamOperation[" + action + ", " + id + "]"; }
    }
    private record Invite(UUID id, String hash, Instant expires, boolean revoked, boolean consumed) {
        InviteStatus status(Instant now) {
            if (consumed) return InviteStatus.CONSUMED;
            if (revoked) return InviteStatus.REVOKED;
            return expires.isAfter(now) ? InviteStatus.ACTIVE : InviteStatus.EXPIRED;
        }
    }
    private record Receipt(long actor, String fingerprint, UUID identity, UUID invite) { }

    private final JdbcTemplate jdbc;
    private final WaAdmissionStore store;
    private final WaTeamSettings team;
    private final WaSettings settings;
    private final WaCrypto crypto;
    private final WaClock clock;

    WaTeamOperations(JdbcTemplate jdbc, WaAdmissionStore store, WaTeamSettings team, WaSettings settings,
                     WaCrypto crypto, WaClock clock) {
        this.jdbc = jdbc;
        this.store = store;
        this.team = team;
        this.settings = settings;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObjectNode status(WaTeamEvolution.Contact contact) {
        requireCohort(false);
        UUID identity = findIdentity(contact, false);
        if (identity == null) return result(null, null, null, clock.now());
        requireIdentity(identity, false);
        if (!store.teamAdmitted(identity)) return result(null, null, null, clock.now());
        Profile profile = store.profile(identity, false);
        Instant now = clock.now();
        return result(identity, profile, currentInvite(identity, now, false), now);
    }

    @Transactional(readOnly = true)
    public ObjectNode replay(WaTeamGitHub.Actor actor, Request request) {
        requireCohort(false);
        return replay(actor, request, receipt(request.id()));
    }

    @Transactional
    public ObjectNode execute(WaTeamGitHub.Actor actor, Request request, WaTeamEvolution.Contact contact,
                              UUID correlation, Runnable assertSessionLive) {
        requireCohort(true);
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs, i) -> 0,
            WaEventService.lockKey("TEAM_OPERATION", team.instance(), request.id().toString()));
        ObjectNode prior = replay(actor, request, receipt(request.id()));
        if (prior != null) {
            assertSessionLive.run();
            return prior;
        }
        Instant channelExpires = store.lockChannels(settings.environment(), team.instance(), clock.now());
        List<Alias> locks = verifiedAliases(contact);
        if (!contact.inputPn.equals(contact.pn)) locks.add(new Alias("PN", contact.inputPn));
        store.lockAliases(team.instance(), locks);
        UUID identity = findIdentity(contact, true);
        if (identity == null) {
            if (request.action() == Action.RENEW_DRIVER_INVITE) throw new WaTeamProblem(INVITE_NOT_RENEWABLE);
            identity = UUID.randomUUID();
            store.createIdentity(identity, team.cohort(), clock.now());
        }
        requireIdentity(identity, true);
        Profile profile = store.profile(identity, true);
        Instant now = clock.now().truncatedTo(ChronoUnit.MICROS);
        Invite invite = currentInvite(identity, now, true);
        checkAction(request.action(), profile, invite, now);
        for (Alias alias : verifiedAliases(contact)) bind(identity, alias, now);

        if (request.action() == Action.RENEW_DRIVER_INVITE)
            jdbc.update("UPDATE wa.driver_bootstrap_invite SET revoked_at=? WHERE id=?", Timestamp.from(now), invite.id());
        if (request.action() != Action.ADMIT_PASSENGER && (invite == null || invite.status(now) == InviteStatus.EXPIRED))
            invite = issue(actor, identity, now);
        UUID recordedInvite = request.action() == Action.ADMIT_PASSENGER ? null : invite.id();
        jdbc.update("""
            INSERT INTO wa.team_admission_operation(id,github_actor_id,github_login,action,request_fingerprint,
                identity_id,invite_id,correlation_id,created_at) VALUES (?,?,?,?,?,?,?,?,?)
            """, request.id(), actor.id(), actor.login(), request.action().name(), fingerprint(request),
            identity, recordedInvite, correlation, Timestamp.from(now));
        Instant responseTime = clock.now();
        ObjectNode result = result(identity, profile, invite, responseTime).put("operationId", request.id().toString());
        if (recordedInvite != null && invite.status(responseTime) == InviteStatus.ACTIVE)
            addCode(result, identity, invite);
        if (!channelExpires.isAfter(clock.now())) throw WaProblem.unauthorized();
        assertSessionLive.run();
        return result;
    }

    void requireReady() {
        if (!team.enabled()) throw WaProblem.missing();
        if (!team.strictIngress(team.instance())) throw WaTeamRemote.unavailable();
    }

    private void requireCohort(boolean lock) {
        requireReady();
        store.requireCohort(settings.environment(), team.cohort(), lock);
    }

    private UUID findIdentity(WaTeamEvolution.Contact contact, boolean lock) {
        UUID identity = store.owner(team.instance(), new Alias("PN", contact.pn));
        if (!contact.inputPn.equals(contact.pn)) {
            UUID inputOwner = store.owner(team.instance(), new Alias("PN", contact.inputPn));
            if (inputOwner != null && !inputOwner.equals(identity)) {
                if (identity == null) throw new WaTeamProblem(ADMISSION_CONFLICT);
                requireIdentity(inputOwner, lock);
                if (!emptyLegacyAdmission(inputOwner)) throw new WaTeamProblem(ADMISSION_CONFLICT);
            }
        }
        if (contact.lid != null) {
            UUID lidOwner = store.owner(team.instance(), new Alias("LID", contact.lid));
            if (lidOwner != null && !lidOwner.equals(identity)) throw new WaTeamProblem(ADMISSION_CONFLICT);
        }
        return identity;
    }

    // A historical PN admitted before Evolution canonicalization may coexist with the correct identity.
    // Keep both historical rows. The original must have only its PN and no domain references.
    private boolean emptyLegacyAdmission(UUID legacy) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            WITH legacy(id) AS (VALUES (?::uuid))
            SELECT (SELECT count(*) FROM wa.identity_alias a WHERE a.identity_id=legacy.id)=1 AND NOT EXISTS(
                SELECT 1 FROM wa.profile WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.driver_bootstrap_invite WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.integration_inbox WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.command_receipt WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.actor_attempt_window WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.audit_event WHERE actor_identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.driver_passenger_link WHERE approved_by=legacy.id
                UNION ALL SELECT 1 FROM wa.pickup_point WHERE approved_by=legacy.id
                UNION ALL SELECT 1 FROM wa.attendance WHERE actor_identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.conversation_state WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.message_outbox WHERE recipient_identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.support_case WHERE identity_id=legacy.id
                UNION ALL SELECT 1 FROM wa.team_admission_operation WHERE identity_id=legacy.id
            ) FROM legacy
            """, Boolean.class, legacy));
    }

    private void requireIdentity(UUID identity, boolean lock) {
        try { store.requireIdentity(identity, team.cohort(), lock); }
        catch (WaProblem denied) { throw new WaTeamProblem(ADMISSION_CONFLICT); }
    }

    private void bind(UUID identity, Alias alias, Instant now) {
        try { store.bind(team.instance(), identity, alias, now); }
        catch (WaProblem denied) { throw new WaTeamProblem(ADMISSION_CONFLICT); }
    }

    private static List<Alias> verifiedAliases(WaTeamEvolution.Contact contact) {
        List<Alias> aliases = new ArrayList<>(2);
        aliases.add(new Alias("PN", contact.pn));
        if (contact.lid != null) aliases.add(new Alias("LID", contact.lid));
        return aliases;
    }

    private static void checkAction(Action action, Profile profile, Invite invite, Instant now) {
        InviteStatus status = invite == null ? InviteStatus.NONE : invite.status(now);
        if (action == Action.ADMIT_PASSENGER) {
            if (profile.role() != null && !("PASSENGER".equals(profile.role())
                    && ("ONBOARDING".equals(profile.status()) || "ACTIVE".equals(profile.status()))))
                throw new WaTeamProblem(PROFILE_CONFLICT);
            if (status == InviteStatus.ACTIVE) throw new WaTeamProblem(INVITE_UNAVAILABLE);
        } else {
            if (!profile.canIssueDriverInvite()) throw new WaTeamProblem(PROFILE_CONFLICT);
            if (action == Action.RENEW_DRIVER_INVITE && status != InviteStatus.EXPIRED)
                throw new WaTeamProblem(INVITE_NOT_RENEWABLE);
            if (action == Action.ISSUE_DRIVER_INVITE && status == InviteStatus.EXPIRED)
                throw new WaTeamProblem(INVITE_EXPIRED);
            if (status == InviteStatus.CONSUMED || status == InviteStatus.REVOKED)
                throw new WaTeamProblem(INVITE_NOT_RENEWABLE);
        }
    }

    private Invite currentInvite(UUID identity, Instant now, boolean lock) {
        String sql = """
            SELECT id,code_hash,expires_at,revoked_at,consumed_at FROM wa.driver_bootstrap_invite WHERE identity_id=?
            ORDER BY (revoked_at IS NULL AND consumed_at IS NULL AND expires_at>?) DESC, created_at DESC, id DESC LIMIT 1
            """;
        var rows = jdbc.query(lock ? sql + " FOR UPDATE" : sql, WaTeamOperations::invite, identity, Timestamp.from(now));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Invite invite(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Invite(rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(),
            rs.getTimestamp(4) != null, rs.getTimestamp(5) != null);
    }

    private Invite issue(WaTeamGitHub.Actor actor, UUID identity, Instant now) {
        UUID id = UUID.randomUUID();
        Instant expires = now.plusSeconds(86400);
        String hash = store.bootstrapHash(store.bootstrapCode(settings.environment(), team.cohort(), identity, id));
        jdbc.update("""
            INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,expires_at,created_by_github_id,created_at)
            VALUES (?,?,?,?,?,?)
            """, id, identity, hash, Timestamp.from(expires), actor.id(), Timestamp.from(now));
        return new Invite(id, hash, expires, false, false);
    }

    private ObjectNode replay(WaTeamGitHub.Actor actor, Request request, Receipt receipt) {
        if (receipt == null) return null;
        if (receipt.actor() != actor.id() || !WaCrypto.same(receipt.fingerprint(), fingerprint(request)))
            throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        requireIdentity(receipt.identity(), false);
        Profile profile = store.profile(receipt.identity(), false);
        Invite invite = receipt.invite() == null ? null : jdbc.query("""
            SELECT id,code_hash,expires_at,revoked_at,consumed_at FROM wa.driver_bootstrap_invite WHERE id=? AND identity_id=?
            """, WaTeamOperations::invite, receipt.invite(), receipt.identity()).stream().findFirst().orElse(null);
        Instant now = clock.now();
        if (request.action() == Action.ADMIT_PASSENGER) invite = currentInvite(receipt.identity(), now, false);
        ObjectNode result = result(receipt.identity(), profile, invite, now).put("operationId", request.id().toString());
        if (receipt.invite() != null && invite != null && invite.status(now) == InviteStatus.ACTIVE && profile.canIssueDriverInvite())
            addCode(result, receipt.identity(), invite);
        return result;
    }

    private void addCode(ObjectNode result, UUID identity, Invite invite) {
        String code = store.bootstrapCode(settings.environment(), team.cohort(), identity, invite.id());
        if (!WaCrypto.same(invite.hash(), store.bootstrapHash(code))) throw new WaTeamProblem(INVITE_UNAVAILABLE);
        result.put("driverInviteCode", code);
    }

    private Receipt receipt(UUID id) {
        return jdbc.query("""
            SELECT github_actor_id,request_fingerprint,identity_id,invite_id FROM wa.team_admission_operation WHERE id=?
            """, (rs, row) -> new Receipt(rs.getLong(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class)),
            id).stream().findFirst().orElse(null);
    }

    private String fingerprint(Request request) {
        return crypto.secretHash("team-admission-request-v1", settings.environment() + "\0" + team.cohort() + "\0"
            + team.instance() + "\0" + request.action() + "\0" + request.phone());
    }

    private static ObjectNode result(UUID identity, Profile profile, Invite invite, Instant now) {
        ObjectNode result = WaJson.MAPPER.createObjectNode()
            .put("admissionStatus", identity == null ? "NOT_ADMITTED" : "ADMITTED")
            .put("inviteStatus", invite == null ? "NONE" : invite.status(now).name());
        if (identity != null) result.put("identityId", identity.toString());
        if (profile != null && profile.role() != null) result.put("profileRole", profile.role()).put("profileStatus", profile.status());
        if (invite != null) result.put("inviteId", invite.id().toString()).put("expiresAt", invite.expires().toString());
        return result;
    }
}
