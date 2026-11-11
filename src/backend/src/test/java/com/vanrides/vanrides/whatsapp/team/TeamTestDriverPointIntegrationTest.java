package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.GITHUB;
import static com.vanrides.vanrides.whatsapp.team.WaTeamTestDriverOperations.Action.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TeamTestDriverPointIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_team_test_driver_point").withUsername("vanrides_test").withPassword("synthetic-team-db");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private TeamTestDriverTestSupport s;

    @BeforeEach
    void reset() { s = new TeamTestDriverTestSupport(DATABASE.nextDataSource()); }

    @Test
    void rectifyingARequestedLinkRejectsTheProposedPointAndAsksThePassengerToReviewIt() {
        UUID link = s.requestedLink(s.testDriver());
        var request = s.decision(RECTIFY_TEST_POINT, link);
        assertThat(s.execute(request).path("linkState").asText()).isEqualTo("REQUESTED");
        assertThat(s.jdbc.queryForMap("SELECT state,valid_until FROM wa.pickup_point WHERE link_id=?", link))
            .containsEntry("state", "REJECTED").doesNotContainEntry("valid_until", null);
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.driver_passenger_link WHERE id=?", String.class, link)).isEqualTo("REQUESTED");
        assertThat(s.count("wa.routine_member")).isZero();
        s.assertPassengerNotice(request.id(), link, "O motorista pediu que você revise o ponto");
        assertThat(s.count("wa.message_outbox")).isOne();
        assertThat(s.jdbc.queryForList("SELECT action,link_id FROM wa.team_admission_operation WHERE id=?", request.id()))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("action", "RECTIFY_TEST_POINT").containsEntry("link_id", link));
    }

    @Test
    void approvingAProposedPointRetiresThePreviousOneAndMovesTheRoutineMember() {
        UUID driver = s.testDriver();
        UUID link = s.requestedLink(driver);
        s.execute(s.decision(APPROVE_TEST_LINK, link));
        UUID previous = s.jdbc.queryForObject("SELECT id FROM wa.pickup_point WHERE link_id=? AND state='APPROVED'", UUID.class, link);
        UUID proposed = s.proposePoint(link);
        var request = s.decision(APPROVE_TEST_POINT, link);
        assertThat(s.execute(request).path("linkState").asText()).isEqualTo("APPROVED");
        assertThat(s.jdbc.queryForMap("SELECT state,approved_by FROM wa.pickup_point WHERE id=?", proposed))
            .containsEntry("state", "APPROVED").containsEntry("approved_by", driver);
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE id=?", String.class, previous)).isEqualTo("RETIRED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.driver_passenger_link WHERE id=?", String.class, link)).isEqualTo("APPROVED");
        assertThat(s.jdbc.queryForList("SELECT pickup_point_id FROM wa.routine_member WHERE link_id=? AND state='ACTIVE'", UUID.class, link))
            .containsExactly(proposed);
        s.assertPassengerNotice(request.id(), link, "Seu novo ponto foi aprovado");
        assertThat(s.count("wa.message_outbox")).isEqualTo(2);
        assertThat(s.jdbc.queryForObject("SELECT action FROM wa.team_admission_operation WHERE id=?", String.class, request.id()))
            .isEqualTo("APPROVE_TEST_POINT");
    }

    @Test
    void closingAnApprovedLinkRemovesTheMemberAndNotifiesThePassenger() {
        UUID link = s.requestedLink(s.testDriver());
        s.execute(s.decision(APPROVE_TEST_LINK, link));
        var request = s.decision(CLOSE_TEST_LINK, link);
        assertThat(s.execute(request).path("linkState").asText()).isEqualTo("CLOSED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.driver_passenger_link WHERE id=?", String.class, link)).isEqualTo("CLOSED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.routine_member WHERE link_id=?", String.class, link)).isEqualTo("REMOVED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE link_id=?", String.class, link)).isEqualTo("RETIRED");
        s.assertPassengerNotice(request.id(), link, "O vínculo de transporte foi encerrado");
        assertThat(s.count("wa.message_outbox")).isEqualTo(2);
        assertThat(s.operations.replay(GITHUB, request).path("linkState").asText()).isEqualTo("CLOSED");
    }

    @Test
    void pointAndCloseDecisionsOutsideTheirStateAreUnavailableWithoutWrites() {
        UUID driver = s.testDriver();
        UUID requested = s.requestedLink(driver), approved = s.requestedLink(driver);
        s.execute(s.decision(APPROVE_TEST_LINK, approved));
        var before = s.snapshot();
        for (var request : List.of(s.decision(CLOSE_TEST_LINK, requested), s.decision(APPROVE_TEST_POINT, approved),
                s.decision(APPROVE_TEST_POINT, requested), s.decision(RECTIFY_TEST_POINT, approved),
                s.decision(CLOSE_TEST_LINK, UUID.randomUUID())))
            assertThatThrownBy(() -> s.execute(request)).isInstanceOfSatisfying(WaTeamProblem.class,
                problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.LINK_UNAVAILABLE));
        assertThat(s.snapshot()).isEqualTo(before);
    }
}
