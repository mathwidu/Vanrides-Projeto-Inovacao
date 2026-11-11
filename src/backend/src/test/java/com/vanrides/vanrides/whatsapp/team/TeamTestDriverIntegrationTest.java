package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.*;
import static com.vanrides.vanrides.whatsapp.team.WaTeamTestDriverOperations.Action.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TeamTestDriverIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_team_test_driver").withUsername("vanrides_test").withPassword("synthetic-team-db");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private TeamTestDriverTestSupport s;

    @BeforeEach
    void reset() { s = new TeamTestDriverTestSupport(DATABASE.nextDataSource()); }

    @Test
    void invitesShareOneTestDriverAndStoreOnlyTheCodeHashWithAnAuditRow() {
        assertThat(s.operations.status().path("driverReady").asBoolean()).isFalse();
        var first = s.execute(s.issue());
        var second = s.execute(s.issue());
        assertThat(s.operations.status().path("driverReady").asBoolean()).isTrue();
        assertThat(s.count("wa.team_test_driver")).isOne();
        assertThat(s.jdbc.queryForObject("SELECT count(*) FROM wa.routine WHERE state='ACTIVE'", Long.class)).isOne();
        assertThat(s.count("wa.driver_invite")).isEqualTo(2);
        UUID driver = s.store.find(s.cohort);
        assertThat(s.jdbc.queryForObject("SELECT test_cohort_id FROM wa.identity WHERE id=?", UUID.class, driver)).isEqualTo(s.cohort);
        String code = first.path("passengerInviteCode").asText();
        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        assertThat(second.path("passengerInviteCode").asText()).isNotEqualTo(code);
        assertThat(first.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(86400).toString());
        String hash = s.crypto.secretHash("passenger-invite", code);
        var invite = s.jdbc.queryForMap("SELECT * FROM wa.driver_invite WHERE code_hash=?", hash);
        assertThat(invite).containsEntry("driver_id", driver).containsEntry("max_uses", 1)
            .containsEntry("use_count", 0).containsEntry("created_by_github_id", GITHUB.id());
        assertThat(((Timestamp) invite.get("expires_at")).toInstant()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(s.jdbc.queryForObject("SELECT count(*) FROM wa.driver_invite WHERE code_hash LIKE ?", Long.class, "%" + code + "%"))
            .isZero();
        assertThat(s.jdbc.queryForList("SELECT github_actor_id,action,identity_id,passenger_invite_id,link_id FROM wa.team_admission_operation WHERE id=?",
            UUID.fromString(first.path("operationId").asText())))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("github_actor_id", GITHUB.id())
                .containsEntry("action", "ISSUE_TEST_PASSENGER_INVITE").containsEntry("identity_id", driver)
                .containsEntry("passenger_invite_id", invite.get("id")).containsEntry("link_id", null));
        assertThat(s.count("wa.team_admission_operation")).isEqualTo(2);
    }

    @Test
    void replayReshowsTheSameCodeAndIsBoundToTheGithubActor() {
        var request = s.issue();
        var first = s.execute(request);
        assertThat(s.operations.replay(GITHUB, request)).isEqualTo(first);
        assertThat(s.execute(request)).isEqualTo(first);
        assertThat(s.count("wa.driver_invite")).isOne();
        assertThat(s.count("wa.team_admission_operation")).isOne();
        assertThatThrownBy(() -> s.operations.replay(ANOTHER, request)).isInstanceOfSatisfying(WaProblem.class,
            problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> s.execute(ANOTHER, request)).isInstanceOfSatisfying(WaProblem.class,
            problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        var other = new WaTeamTestDriverOperations.Request(APPROVE_TEST_LINK, request.id(), UUID.randomUUID());
        assertThatThrownBy(() -> s.operations.replay(GITHUB, other)).isInstanceOfSatisfying(WaProblem.class,
            problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        assertThat(s.count("wa.driver_invite")).isOne();
    }

    @Test
    void approvingARequestedLinkApprovesTheLinkPickupAndRoutineMemberAndReplaysItsState() {
        UUID driver = s.testDriver();
        UUID link = s.requestedLink(driver);
        assertThat(s.operations.status().path("links")).singleElement().satisfies(item -> {
            assertThat(item.path("linkId").asText()).isEqualTo(link.toString());
            assertThat(item.path("state").asText()).isEqualTo("REQUESTED");
            assertThat(item.path("pointPending").asBoolean()).isTrue();
        });
        var request = s.decision(APPROVE_TEST_LINK, link);
        var result = s.execute(request);
        assertThat(result.path("linkState").asText()).isEqualTo("APPROVED");
        assertThat(s.jdbc.queryForMap("SELECT state,approved_by FROM wa.driver_passenger_link WHERE id=?", link))
            .containsEntry("state", "APPROVED").containsEntry("approved_by", driver);
        assertThat(s.jdbc.queryForMap("SELECT state,approved_by FROM wa.pickup_point WHERE link_id=?", link))
            .containsEntry("state", "APPROVED").containsEntry("approved_by", driver);
        assertThat(s.jdbc.queryForObject("SELECT count(*) FROM wa.routine_member WHERE link_id=? AND state='ACTIVE'", Long.class, link)).isOne();
        assertThat(s.operations.replay(GITHUB, request).path("linkState").asText()).isEqualTo("APPROVED");
        s.assertPassengerNotice(request.id(), link, "Cadastro confirmado.");
        assertThat(s.count("wa.message_outbox")).isOne();
        assertThat(s.jdbc.queryForList("SELECT action,link_id,passenger_invite_id FROM wa.team_admission_operation WHERE id=?", request.id()))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("action", "APPROVE_TEST_LINK")
                .containsEntry("link_id", link).containsEntry("passenger_invite_id", null));
    }

    @Test
    void rejectingARequestedLinkClosesItWithoutAMember() {
        UUID driver = s.testDriver();
        UUID link = s.requestedLink(driver);
        var request = s.decision(REJECT_TEST_LINK, link);
        assertThat(s.execute(request).path("linkState").asText()).isEqualTo("REJECTED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.driver_passenger_link WHERE id=?", String.class, link)).isEqualTo("REJECTED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE link_id=?", String.class, link)).isEqualTo("REJECTED");
        assertThat(s.count("wa.routine_member")).isZero();
        assertThat(s.operations.replay(GITHUB, request).path("linkState").asText()).isEqualTo("REJECTED");
        s.assertPassengerNotice(request.id(), link, "O motorista recusou");
        assertThat(s.count("wa.message_outbox")).isOne();
        assertThat(s.operations.status().path("links")).isEmpty();
    }

    @Test
    void decisionsOnLinksThatAreNotRequestedOrBelongToAnotherDriverAreUnavailableWithoutWrites() {
        UUID driver = s.testDriver();
        UUID approved = s.requestedLink(driver), rejected = s.requestedLink(driver), foreign = s.requestedLink(s.normalDriver(), null);
        s.execute(s.decision(APPROVE_TEST_LINK, approved));
        s.execute(s.decision(REJECT_TEST_LINK, rejected));
        var before = s.snapshot();
        for (var request : List.of(s.decision(APPROVE_TEST_LINK, approved), s.decision(REJECT_TEST_LINK, approved),
                s.decision(APPROVE_TEST_LINK, rejected), s.decision(REJECT_TEST_LINK, rejected),
                s.decision(APPROVE_TEST_LINK, foreign), s.decision(REJECT_TEST_LINK, foreign),
                s.decision(APPROVE_TEST_LINK, UUID.randomUUID())))
            assertThatThrownBy(() -> s.execute(request)).isInstanceOfSatisfying(WaTeamProblem.class,
                problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.LINK_UNAVAILABLE));
        assertThat(s.snapshot()).isEqualTo(before);
    }
}
