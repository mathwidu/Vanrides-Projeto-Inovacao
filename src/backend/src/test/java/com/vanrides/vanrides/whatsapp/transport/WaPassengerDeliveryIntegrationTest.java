package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

import static org.assertj.core.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerDeliveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_passenger_delivery");
    private WaPassengerDeliveryTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaPassengerDeliveryTestSupport(POSTGRES);
    }

    @Test
    void actorResponseUsesItsAuthenticatedLidWithoutCounterpartyData() {
        UUID actor = f.actor(false, "LID"),
                command = f.command(actor, "ADVANCE_PASSENGER_SETUP"),
                box = UUID.randomUUID();
        f.h.transaction.executeWithoutResult(
                tx ->
                        WaPassengerResponses.saveActor(
                                f.h.jdbc,
                                box,
                                command,
                                actor,
                                "Etapa sintética.",
                                f.h.clock.now()));
        assertThatThrownBy(() -> f.h.transaction.executeWithoutResult(tx -> WaPassengerResponses.saveActor(f.h.jdbc,
            UUID.randomUUID(), command, actor, "x".repeat(2001), f.h.clock.now()))).isInstanceOf(WaProblem.class);
        var lease = f.claim(box);
        assertThat(lease.has("recipient")).isFalse();
        assertThat(lease.has("content")).isFalse();
        var reservation = f.authorize(lease);
        assertThat(reservation.path("recipient").path("address").asText())
                .isEqualTo(f.address(actor));
        assertThat(reservation.path("recipient").path("addressType").asText()).isEqualTo("LID");
        assertThat(reservation.path("content").path("text").asText()).isEqualTo("Etapa sintética.");
    }

    @Test
    void requestedPeerIsDriverAndMapIsRenderedOnlyWhenSubmissionIsAuthorized() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        assertThatThrownBy(() -> f.h.transaction.executeWithoutResult(tx -> WaPassengerResponses.savePeer(f.h.jdbc,
            UUID.randomUUID(), pair.requestCommand(), pair.passenger(), pair.link(), pair.point(), "REQUESTED", "x".repeat(1801), f.h.clock.now())))
            .isInstanceOf(WaProblem.class);
        assertThat(
                        f.h.jdbc.queryForObject(
                                "SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",
                                String.class,
                                box))
                .doesNotContain("maps");
        var reservation = f.authorize(f.claim(box));
        assertThat(reservation.path("recipient").path("address").asText())
                .isEqualTo(f.address(pair.driver()));
        assertThat(reservation.path("content").path("text").asText())
                .endsWith("https://www.google.com/maps/search/?api=1&query=0.0%2C0.0");
        assertThat(
                        f.h.jdbc.queryForObject(
                                "SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",
                                String.class,
                                box))
                .doesNotContain("maps");
    }

    @Test
    void approvedPeerIsPassengerBoundToItsOriginalRequestEvent() {
        var pair = f.pair("APPROVED");
        UUID box = f.peer(pair, "APPROVED");
        var reservation = f.authorize(f.claim(box));
        assertThat(reservation.path("recipient").path("address").asText())
                .isEqualTo(f.address(pair.passenger()));
        assertThat(reservation.path("content").path("text").asText()).doesNotContain("maps");
        assertThat(
                        f.h.jdbc.queryForObject(
                                """
                                SELECT r.recipient_source_event_id=l.requested_source_event_id FROM wa.passenger_response_route r
                                JOIN wa.driver_passenger_link l ON l.id=r.link_id WHERE r.outbox_id=?
                                """,
                                Boolean.class,
                                box))
                .isTrue();
    }

    @Test
    void outsiderCannotChooseASubjectOrItsCounterparty() {
        var pair = f.pair("REQUESTED");
        UUID outsider = f.actor(false), command = f.command(outsider, "REQUEST_LINK");
        assertThatThrownBy(() -> f.peer(pair, command, outsider, "REQUESTED", pair.point()))
                .isInstanceOf(WaProblem.class);
        assertThat(f.count("message_outbox")).isZero();
        assertThat(f.count("passenger_response_route")).isZero();
    }

    @Test
    void commandKindAndForeignPickupCannotBeSubstituted() {
        var pair = f.pair("REQUESTED");
        var other = f.pair("REQUESTED");
        UUID wrongCommand = f.command(pair.passenger(), "SET_PREFERENCE");
        assertThatThrownBy(
                        () ->
                                f.peer(
                                        pair,
                                        wrongCommand,
                                        pair.passenger(),
                                        "REQUESTED",
                                        pair.point()))
                .isInstanceOf(WaProblem.class);
        assertThatThrownBy(
                        () ->
                                f.peer(
                                        pair,
                                        pair.requestCommand(),
                                        pair.passenger(),
                                        "REQUESTED",
                                        other.point()))
                .isInstanceOf(WaProblem.class);
        assertThat(f.count("message_outbox")).isZero();
    }

    @Test
    void ambiguousProposalRequiresExplicitPickupAndRendersThatExactPin() {
        var pair = f.pair("REQUESTED");
        UUID selected = f.point(pair.link(), "PROPOSED", 1), box = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                f.h.transaction.executeWithoutResult(
                                        tx ->
                                                WaPassengerResponses.savePeer(
                                                        f.h.jdbc,
                                                        box,
                                                        pair.requestCommand(),
                                                        pair.passenger(),
                                                        pair.link(),
                                                        "REQUESTED",
                                                        "Resumo sintético.",
                                                        f.h.clock.now())))
                .isInstanceOf(WaProblem.class);
        UUID explicit =
                f.peer(pair, pair.requestCommand(), pair.passenger(), "REQUESTED", selected);
        assertThat(f.authorize(f.claim(explicit)).path("content").path("text").asText())
                .endsWith("query=0.0%2C1.0");
    }

    @Test
    void pairInAnotherCohortIsNeverClaimed() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED"), cohort = UUID.randomUUID();
        f.h.jdbc.update(
                "INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES"
                        + " (?,'transport-test',true)",
                cohort);
        f.h.jdbc.update(
                "UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, pair.driver());
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(box)).isEqualTo("CANCELED");
    }

    @Test
    void aliasedRouteCannotDiscloseAnotherIdentityAtAuthorization() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED"), outsider = f.actor(true);
        var lease = f.claim(box);
        UUID alias =
                f.h.jdbc.queryForObject(
                        "SELECT id FROM wa.identity_alias WHERE identity_id=?",
                        UUID.class,
                        outsider);
        f.h.jdbc.update(
                """
                UPDATE wa.integration_inbox SET payload_json=jsonb_set(payload_json,'{senderAliasId}',to_jsonb(?::text))
                WHERE id=(SELECT recipient_source_event_id FROM wa.passenger_response_route WHERE outbox_id=?)
                """,
                alias.toString(),
                box);
        f.canceled(lease);
    }

    @Test
    void historicalResponseAndPassengerPolicyWithoutRouteCannotSendToPeer() {
        var pair = f.pair("REQUESTED");
        UUID command = f.command(pair.passenger(), "START_ONBOARDING"), box = UUID.randomUUID();
        WaFoundationResponses.save(
                f.h.jdbc, box, command, pair.passenger(), "START_ONBOARDING", f.h.clock.now());
        f.h.jdbc.update(
                "UPDATE wa.message_outbox SET recipient_identity_id=? WHERE id=?",
                pair.driver(),
                box);
        assertThat(f.claim().path("items")).isEmpty();
        f.h.jdbc.update(
                "UPDATE wa.command_receipt SET command_type='REQUEST_LINK' WHERE command_id=?",
                command);
        f.h.jdbc.update(
                "UPDATE wa.message_outbox SET"
                    + " policy_version='passenger-setup-v1',response_part='passenger-peer' WHERE"
                    + " id=?",
                box);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.count("message_attempt")).isZero();
    }

    @Test
    void routeBindingIsImmutableAndOutboxDestinationHasACompositeForeignKey() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED"), outsider = f.actor(true);
        assertThatThrownBy(
                        () ->
                                f.h.jdbc.update(
                                        "UPDATE wa.passenger_response_route SET"
                                                + " recipient_identity_id=? WHERE outbox_id=?",
                                        outsider,
                                        box))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                f.h.jdbc.update(
                                        "UPDATE wa.message_outbox SET recipient_identity_id=? WHERE"
                                                + " id=?",
                                        outsider,
                                        box))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void pointChangeWorksForPendingCorrectionAndApprovedLinkWithExplicitProposal() {
        for (String state : new String[] {"REQUESTED", "APPROVED"}) {
            var pair = f.pair(state);
            UUID point = f.point(pair.link(), "PROPOSED", 1);
            UUID command = f.command(pair.passenger(), "PROPOSE_PICKUP");
            UUID box = f.peer(pair, command, pair.passenger(), "POINT_CHANGE", point);
            var reservation = f.authorize(f.claim(box));
            assertThat(reservation.path("recipient").path("address").asText())
                    .isEqualTo(f.address(pair.driver()));
            assertThat(reservation.path("content").path("text").asText())
                    .endsWith("query=0.0%2C1.0");
            f.submitted(reservation, "POINT_CHANGE_" + state);
        }
    }

    @Test
    void pointApprovalNotifiesOnlyPassengerAndCarriesNoNewMap() {
        var pair = f.pair("APPROVED");
        UUID box = f.peer(pair, "POINT_APPROVED");
        var reservation = f.authorize(f.claim(box));
        assertThat(reservation.path("recipient").path("address").asText())
                .isEqualTo(f.address(pair.passenger()));
        assertThat(reservation.path("content").path("text").asText()).doesNotContain("maps");
    }

    @Test
    void anotherPassengerApprovalOnSameRoutinePreservesAdmittedAndRequestedMessages() {
        for (String kind : new String[]{"REQUESTED", "APPROVED"}) {
            var first = f.pair(kind);
            UUID box = f.peer(first, kind);
            var second = f.approveAnotherPassengerOnSameRoutine(first);
            assertThat(first.routine()).isEqualTo(second.routine());
            assertThat(f.h.jdbc.queryForObject("SELECT version FROM wa.routine WHERE id=?", Long.class, first.routine())).isEqualTo(1);
            var reservation = f.authorize(f.claim(box));
            UUID recipient = kind.equals("REQUESTED") ? first.driver() : first.passenger();
            assertThat(reservation.path("recipient").path("address").asText()).isEqualTo(f.address(recipient));
            assertThat(f.submitted(reservation, "SHARED_ROUTINE_" + kind).path("state").asText()).isEqualTo("SUBMITTED");
        }
    }
}
