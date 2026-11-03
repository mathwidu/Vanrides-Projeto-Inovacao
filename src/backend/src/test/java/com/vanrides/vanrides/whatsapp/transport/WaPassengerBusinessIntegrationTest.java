package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerBusinessIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_business");

    WaPassengerTestSupport fixture;
    UUID driver;
    UUID routine;
    UUID passenger;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(POSTGRES);
        driver = fixture.driver();
        routine = fixture.routine(driver);
        passenger = fixture.passenger();
        code = fixture.invite(driver, 10);
    }

    @Test
    void correctDriverApprovesPointLinkAndSelectedRoutineDaysAtomically() {
        long routineVersion =
                fixture.conversation.number("SELECT version FROM wa.routine WHERE id=?", routine);
        UUID link = fixture.request(passenger, code);
        var review = fixture.driverReview(driver, "APROVAR");
        assertTrue(review.text().contains("Passageiro Sintético"));
        assertTrue(review.text().contains("(ida)"));
        assertTrue(review.text().contains("https://www.google.com/maps?q="));
        var approval = fixture.confirm(driver, review);
        assertEquals(2, approval.response().path("responseIds").size());
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE link_id=? AND state='APPROVED'"
                                + " AND approved_by=? AND version=1",
                        link,
                        driver));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.routine_member WHERE link_id=? AND"
                                + " weekdays=ARRAY[1,3,5]::smallint[] AND state='ACTIVE'",
                        link));
        assertEquals(
                routineVersion + 1,
                fixture.conversation.number("SELECT version FROM wa.routine WHERE id=?", routine));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.routine WHERE id=? AND stop_order_pending",
                        routine));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_outbox o JOIN wa.command_receipt r ON"
                            + " r.command_id=o.command_id WHERE r.inbox_event_id=? AND"
                            + " recipient_identity_id=? AND payload_json->>'text' LIKE '%(ida)%'",
                        approval.lease().id(), passenger));
    }

    @Test
    void declineNotifiesPassengerAndDoesNotReleaseOrConsumeInviteAgain() {
        UUID link = fixture.request(passenger, code);
        var result = fixture.confirm(driver, fixture.driverReview(driver, "RECUSAR"));
        assertEquals(
                "REJECTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(0, fixture.count("routine_member"));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.passenger_response_route WHERE"
                            + " event_kind='REJECTED' AND recipient_identity_id=? AND command_id=?",
                        passenger,
                        UUID.fromString(result.response().path("commandId").asText())));
    }

    @Test
    void rectificationAndResubmissionAreHumanMessagesAndPreserveReservation() {
        UUID link = fixture.request(passenger, code);
        var correction = fixture.confirm(driver, fixture.driverReview(driver, "RETIFICAR"));
        assertEquals("ADVANCE_PASSENGER_SETUP", correction.body().path("commandType").asText());
        assertTrue(correction.context().path("options").isEmpty());
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE link_id=? AND state='REJECTED'",
                        link));
        fixture.send(passenger, "CORRIGIR PONTO");
        fixture.pin(passenger);
        var review = fixture.send(passenger, "Portão lateral");
        var proposal = fixture.confirm(passenger, review);
        assertEquals("PROPOSE_PICKUP", proposal.body().path("commandType").asText());
        assertDoesNotThrow(
                () ->
                        UUID.fromString(
                                proposal.body().path("payload").path("sourceMessageId").asText()));
        fixture.approve(driver);
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
    }

    @Test
    void eitherCounterpartyCanCloseWithoutDeletingMembershipOrHistoricalPoint() {
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        var result = fixture.confirm(passenger, fixture.send(passenger, "ENCERRAR"));
        assertEquals(2, result.response().path("responseIds").size());
        assertEquals(
                "CLOSED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                "REMOVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.routine_member WHERE link_id=?", link));
        assertEquals(
                "RETIRED",
                fixture.conversation.string(
                        "SELECT state FROM wa.pickup_point WHERE link_id=?", link));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.passenger_response_route WHERE event_kind='CLOSED'"
                                + " AND recipient_identity_id=?",
                        driver));
    }

    @Test
    void driverPublishesCommonPointAndPassengerSelectionKeepsOwnSourceAndOriginForeignKey() {
        fixture.send(driver, "PONTO COMUM");
        fixture.send(driver, "Portaria pública");
        var review = fixture.pin(driver);
        fixture.confirm(driver, review);
        UUID common =
                fixture.transport.jdbc.queryForObject(
                        "SELECT id FROM wa.common_pickup_point WHERE driver_id=?",
                        UUID.class,
                        driver);
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        fixture.send(passenger, "NÃO");
        fixture.send(passenger, "SIM");
        fixture.send(passenger, "1");
        var points = fixture.send(passenger, "1,3");
        assertTrue(points.text().contains("PONTO 1: Portaria pública"));
        var selected = fixture.send(passenger, "PONTO 1");
        fixture.confirm(passenger, fixture.send(passenger, "PULAR"));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE passenger_id=? AND"
                                + " source_inbox_event_id=? AND common_pickup_point_id=? AND"
                                + " common_pickup_version=0",
                        passenger,
                        selected.lease().id(),
                        common));
        fixture.approve(driver);
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE passenger_id=? AND"
                                + " state='APPROVED'",
                        passenger));
    }

    @Test
    void aPersonalPickupNeverAppearsInAnotherPassengersCommonCatalogue() {
        fixture.request(passenger, code);
        fixture.approve(driver);
        fixture.transport.jdbc.update(
                "UPDATE wa.pickup_point SET label='Casa privada de outro passageiro' WHERE"
                        + " passenger_id=?",
                passenger);
        UUID other = fixture.passenger();
        fixture.open(other, code);
        fixture.send(other, "CONTINUAR");
        fixture.send(other, "Outro Sintético");
        fixture.send(other, "ACEITO");
        fixture.send(other, "NÃO");
        fixture.send(other, "NÃO");
        fixture.send(other, "1");
        var points = fixture.send(other, "1,3");
        assertFalse(points.text().contains("Casa privada"));
        assertTrue(fixture.draft(other).path("commonChoices").isEmpty());
    }

    @Test
    void expiredInviteDoesNotInvalidateARequestAlreadyDurablyReserved() {
        UUID link = fixture.request(passenger, code);
        fixture.transport.jdbc.update(
                "UPDATE wa.driver_invite SET revoked_at=? WHERE driver_id=?",
                Timestamp.from(fixture.transport.clock.now()),
                driver);
        fixture.approve(driver);
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
    }

    @Test
    void ninthPassengerCanBeApprovedFromAStableSecondMenuPage() {
        fixture.driverFixture.members(driver, routine, 8);
        fixture.transport.jdbc.update(
                "UPDATE wa.identity SET test_cohort_id=? WHERE id IN (SELECT passenger_id FROM"
                        + " wa.driver_passenger_link WHERE driver_id=?)",
                fixture.cohort,
                driver);
        UUID ninthLink = fixture.request(passenger, code);
        var first = fixture.send(driver, "PASSAGEIROS");
        assertTrue(first.text().contains("PRÓXIMA"));
        assertEquals(8, fixture.draft(driver).path("linkChoices").size());
        var second = fixture.send(driver, "PRÓXIMA");
        assertTrue(second.text().contains("página 2"));
        assertTrue(second.text().contains("ANTERIOR"));
        assertEquals(
                ninthLink.toString(),
                fixture.draft(driver).path("linkChoices").get(0).path("id").asText());
        fixture.send(driver, "ANTERIOR");
        fixture.send(driver, "PRÓXIMA");
        fixture.transport.jdbc.update(
                "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", ninthLink);
        var changed = fixture.send(driver, "APROVAR 1");
        assertTrue(changed.text().contains("Essa solicitação mudou"));
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", ninthLink));
        fixture.send(driver, "PRÓXIMA");
        fixture.confirm(driver, fixture.send(driver, "APROVAR 1"));
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", ninthLink));
        assertEquals(
                9,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.routine_member WHERE routine_id=? AND"
                                + " state='ACTIVE'",
                        routine));
    }
}
