package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaTeamTestDriver;
import com.vanrides.vanrides.whatsapp.commands.WaTeamTestDriver.Decision;
import com.vanrides.vanrides.whatsapp.transport.WaTeamPassengerNotices;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.vanrides.vanrides.whatsapp.team.WaTeamProblem.Code.*;

@Service
class WaTeamTestDriverOperations {
    enum Action {
        ISSUE_TEST_PASSENGER_INVITE(null), APPROVE_TEST_LINK(Decision.APPROVE_LINK), REJECT_TEST_LINK(Decision.REJECT_LINK),
        RECTIFY_TEST_POINT(Decision.RECTIFY_POINT), APPROVE_TEST_POINT(Decision.APPROVE_POINT), CLOSE_TEST_LINK(Decision.CLOSE_LINK);
        final Decision decision;
        Action(Decision decision) { this.decision = decision; }
    }
    record Request(Action action, UUID id, UUID link) {
        @Override public String toString() { return "WaTeamTestDriverOperation[" + action + ", " + id + "]"; }
    }
    private record Receipt(long actor, String fingerprint, String action, UUID driver, UUID invite, UUID link) { }

    private final JdbcTemplate jdbc;
    private final WaAdmissionStore store;
    private final WaTeamOperations admissions;
    private final WaTeamTestDriver drivers;
    private final WaTeamTestDriverStore testDrivers;
    private final WaTeamSettings team;
    private final WaSettings settings;
    private final WaCrypto crypto;
    private final WaClock clock;

    WaTeamTestDriverOperations(JdbcTemplate jdbc, WaAdmissionStore store, WaTeamOperations admissions,
                               WaTeamTestDriver drivers, WaTeamTestDriverStore testDrivers, WaTeamSettings team, WaSettings settings,
                               WaCrypto crypto, WaClock clock) {
        this.jdbc = jdbc;
        this.store = store;
        this.admissions = admissions;
        this.drivers = drivers;
        this.testDrivers = testDrivers;
        this.team = team;
        this.settings = settings;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObjectNode status() {
        requireCohort(false);
        UUID driver = testDrivers.find(team.cohort());
        ObjectNode result = WaJson.MAPPER.createObjectNode().put("driverReady", driver != null)
            .put("driverName", WaTeamTestDriverStore.NAME);
        result.set("links", driver == null ? WaJson.MAPPER.createArrayNode() : drivers.links(driver));
        return result;
    }

    @Transactional(readOnly = true)
    public ObjectNode replay(WaTeamGitHub.Actor actor, Request request) {
        requireCohort(false);
        return replay(actor, request, receipt(request.id()));
    }

    @Transactional
    public ObjectNode execute(WaTeamGitHub.Actor actor, Request request, UUID correlation, Runnable assertSessionLive) {
        requireCohort(true);
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs, i) -> 0,
            WaEventService.lockKey("TEAM_OPERATION", team.instance(), request.id().toString()));
        ObjectNode prior = replay(actor, request, receipt(request.id()));
        if (prior != null) {
            assertSessionLive.run();
            return prior;
        }
        Instant now = clock.now().truncatedTo(ChronoUnit.MICROS);
        UUID driver = testDrivers.ensure(team.cohort(), team.instance(), actor.id(), now);
        UUID invite = null;
        WaTeamTestDriver.Outcome outcome = null;
        if (request.action().decision == null) {
            invite = UUID.randomUUID();
            testDrivers.insertInvite(driver, invite, crypto.secretHash("passenger-invite", code(driver, invite)),
                now.plusSeconds(86400), actor.id(), now);
        } else {
            try { outcome = drivers.decide(driver, request.link(), request.action().decision); }
            catch (WaProblem unavailable) { throw new WaTeamProblem(LINK_UNAVAILABLE); }
        }
        jdbc.update("""
            INSERT INTO wa.team_admission_operation(id,github_actor_id,github_login,action,request_fingerprint,
                identity_id,passenger_invite_id,link_id,correlation_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)
            """, request.id(), actor.id(), actor.login(), request.action().name(), fingerprint(request),
            driver, invite, request.link(), correlation, Timestamp.from(now));
        if (outcome != null)
            WaTeamPassengerNotices.save(jdbc, request.id(), request.link(), outcome.pickup(), outcome.kind(),
                outcome.passengerText(), settings.environment(), team.instance(), now);
        ObjectNode result = result(request, driver, invite);
        assertSessionLive.run();
        return result;
    }

    private void requireCohort(boolean lock) {
        admissions.requireReady();
        store.requireCohort(settings.environment(), team.cohort(), lock);
    }

    private ObjectNode replay(WaTeamGitHub.Actor actor, Request request, Receipt receipt) {
        if (receipt == null) return null;
        if (receipt.actor() != actor.id() || !request.action().name().equals(receipt.action())
                || !WaCrypto.same(receipt.fingerprint(), fingerprint(request)))
            throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        return result(request, receipt.driver(), receipt.invite());
    }

    private ObjectNode result(Request request, UUID driver, UUID invite) {
        ObjectNode result = WaJson.MAPPER.createObjectNode().put("operationId", request.id().toString())
            .put("action", request.action().name());
        if (invite != null) {
            WaTeamTestDriverStore.Invite current = testDrivers.invite(driver, invite, clock.now());
            result.put("expiresAt", current.expires().toString()).put("inviteStatus", current.usable() ? "ACTIVE" : "UNAVAILABLE");
            if (current.usable()) {
                String code = code(driver, invite);
                if (!WaCrypto.same(current.hash(), crypto.secretHash("passenger-invite", code))) throw new WaTeamProblem(INVITE_UNAVAILABLE);
                result.put("passengerInviteCode", code);
            }
        } else {
            result.put("linkId", request.link().toString()).put("linkState", drivers.linkState(driver, request.link()));
        }
        return result;
    }

    private String code(UUID driver, UUID invite) {
        return WaCrypto.base64(HexFormat.of().parseHex(crypto.secretHash("team-passenger-invite-v1",
            settings.environment() + "\0" + team.cohort() + "\0" + driver + "\0" + invite)));
    }

    private Receipt receipt(UUID id) {
        return jdbc.query("""
            SELECT github_actor_id,request_fingerprint,action,identity_id,passenger_invite_id,link_id
            FROM wa.team_admission_operation WHERE id=?
            """, (rs, row) -> new Receipt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
                rs.getObject(5, UUID.class), rs.getObject(6, UUID.class)), id).stream().findFirst().orElse(null);
    }

    private String fingerprint(Request request) {
        return crypto.secretHash("team-test-driver-request-v1", settings.environment() + "\0" + team.cohort() + "\0"
            + team.instance() + "\0" + request.action() + "\0" + request.link());
    }
}
