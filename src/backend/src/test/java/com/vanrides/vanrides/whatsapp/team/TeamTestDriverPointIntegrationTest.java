package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.GITHUB;
import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.NOW;
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
        s.assertPassengerNotice(request.id(), link, "O motorista aprovou a sua solicitação da(s) rota(s):");
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

    @Test
    void twoPendingPickupsRequireExactSelectionAndAreApprovedOneAtATimeWithBoundReplay() {
        UUID driver=s.testDriver(), link=s.requestedLink(driver);
        UUID first=s.jdbc.queryForObject("SELECT pickup_point_id FROM wa.passenger_link_routine WHERE link_id=?", UUID.class, link);
        UUID second=secondSelection(link);
        s.jdbc.update("UPDATE wa.driver_passenger_link SET state='APPROVED',approved_by=?,approved_at=? WHERE id=?", driver, Timestamp.from(NOW), link);
        var options=s.operations.status().path("links").path(0).path("pickupOptions");
        assertThat(options).hasSize(2);
        assertThat(options.toString()).contains("dias segunda, quarta e sexta", "Volta explícita", "dias terça e quinta");
        assertThat(options.toString()).doesNotContain("latitude", "longitude", "maps?q");
        var before=s.snapshot();
        assertThatThrownBy(()->s.execute(s.decision(APPROVE_TEST_POINT, link))).isInstanceOfSatisfying(WaTeamProblem.class,
                failure->assertThat(failure.code).isEqualTo(WaTeamProblem.Code.POINT_SELECTION_REQUIRED));
        assertThat(s.snapshot()).isEqualTo(before);
        var request=new WaTeamTestDriverOperations.Request(APPROVE_TEST_POINT, UUID.randomUUID(), link, first);
        s.execute(request);
        assertThat(s.count("wa.routine_member")).isOne();
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE id=?", String.class, second)).isEqualTo("PROPOSED");
        assertThat(s.operations.replay(GITHUB, request).path("linkState").asText()).isEqualTo("APPROVED");
        assertThatThrownBy(()->s.execute(new WaTeamTestDriverOperations.Request(APPROVE_TEST_POINT, request.id(), link, second)))
                .isInstanceOfSatisfying(WaProblem.class, failure->assertThat(failure.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        s.execute(new WaTeamTestDriverOperations.Request(APPROVE_TEST_POINT, UUID.randomUUID(), link, second));
        assertThat(s.count("wa.routine_member")).isEqualTo(2);
        assertThat(s.jdbc.queryForObject("SELECT count(DISTINCT pickup_point_id) FROM wa.routine_member WHERE link_id=?", Long.class, link)).isEqualTo(2);
    }

    @Test
    void upgradedOrphanProposalIsNotListedOrDecidedAndTheUniqueReferencedCloneKeepsTheOldBody() {
        UUID driver=s.testDriver(), link=s.requestedLink(driver);
        UUID original=s.jdbc.queryForObject("SELECT pickup_point_id FROM wa.passenger_link_routine WHERE link_id=?", UUID.class, link);
        s.jdbc.update("UPDATE wa.pickup_point SET version=7 WHERE id=?", original);
        String before=s.jdbc.queryForObject("SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE id=?", String.class, original);
        UUID clone=s.proposePoint(link);
        var options=s.operations.status().path("links").path(0).path("pickupOptions");
        assertThat(options).hasSize(1);
        assertThat(options.path(0).path("pickupId").asText()).isEqualTo(clone.toString());
        assertThatThrownBy(()->s.execute(new WaTeamTestDriverOperations.Request(APPROVE_TEST_LINK, UUID.randomUUID(), link, original)))
                .isInstanceOfSatisfying(WaTeamProblem.class, failure->assertThat(failure.code).isEqualTo(WaTeamProblem.Code.LINK_UNAVAILABLE));
        s.execute(s.decision(APPROVE_TEST_LINK, link));
        assertThat(s.jdbc.queryForObject("SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE id=?", String.class, original)).isEqualTo(before);
        assertThat(s.operations.status().path("links").path(0).path("pointPending").asBoolean()).isFalse();
        assertThat(s.operations.status().path("links").path(0).path("pickupOptions")).isEmpty();
    }

    @Test
    void rectifyingOneOfTwoPickupsPreservesTheOtherAndTheAuthorizedLinkVersion() {
        UUID driver=s.testDriver(), link=s.requestedLink(driver);
        UUID first=s.jdbc.queryForObject("SELECT pickup_point_id FROM wa.passenger_link_routine WHERE link_id=?", UUID.class, link);
        UUID second=secondSelection(link);
        s.jdbc.update("UPDATE wa.driver_passenger_link SET state='APPROVED',approved_by=?,approved_at=? WHERE id=?", driver, Timestamp.from(NOW), link);
        long version=s.jdbc.queryForObject("SELECT version FROM wa.driver_passenger_link WHERE id=?", Long.class, link);
        s.execute(new WaTeamTestDriverOperations.Request(RECTIFY_TEST_POINT, UUID.randomUUID(), link, second));
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE id=?", String.class, second)).isEqualTo("REJECTED");
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.pickup_point WHERE id=?", String.class, first)).isEqualTo("PROPOSED");
        assertThat(s.jdbc.queryForObject("SELECT version FROM wa.driver_passenger_link WHERE id=?", Long.class, link)).isEqualTo(version);
        assertThat(s.count("wa.routine_member")).isZero();
    }

    private UUID secondSelection(UUID link) {
        UUID routine=UUID.randomUUID(), pickup=UUID.randomUUID();
        s.jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
                SELECT ?,r.driver_id,'Volta explícita','RETURN',r.weekdays,'18:00',r.timezone,r.destination_latitude,r.destination_longitude,'ACTIVE',r.policy_version
                FROM wa.routine r JOIN wa.passenger_link_routine s ON s.routine_id=r.id WHERE s.link_id=? LIMIT 1
                """, routine, link);
        s.jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id)
                SELECT ?,id,driver_id,passenger_id,0,0,requested_source_event_id FROM wa.driver_passenger_link WHERE id=?
                """, pickup, link);
        s.jdbc.update("""
                INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version,pickup_point_id)
                SELECT gen_random_uuid(),id,?,driver_id,passenger_id,ARRAY[2,4]::smallint[],0,? FROM wa.driver_passenger_link WHERE id=?
                """, routine, pickup, link);
        return pickup;
    }
}
