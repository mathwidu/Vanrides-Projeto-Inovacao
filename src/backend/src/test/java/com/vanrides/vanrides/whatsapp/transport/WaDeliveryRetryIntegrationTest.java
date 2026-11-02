package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRetryTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryRetryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_retry");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f = new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); }

    @Test void onlyThreeTotalAttemptsWithFiveAndFifteenSecondBackoffSurviveRestart() {
        var box = f.enqueue(); var tokens = new HashSet<String>();
        for (int number = 1; number <= 3; number++) {
            var lease = f.lease(); assertThat(tokens.add(lease.path("leaseToken").asText())).isTrue();
            var reservation = f.authorize(lease); assertThat(tokens.add(reservation.path("attemptToken").asText())).isTrue();
            var before = f.h.clock.now(); var receipt = result(f, reservation);
            assertThat(receipt.path("state").asText()).isEqualTo(number < 3 ? "PENDING" : "FAILED");
            assertThat(f.h.jdbc.queryForObject("SELECT expires_at FROM wa.message_outbox WHERE id=?", Timestamp.class, box.id()))
                .isEqualTo(Timestamp.from(NOW.plusSeconds(300)));
            assertThat(f.h.jdbc.queryForObject("SELECT lease_generation FROM wa.message_outbox WHERE id=?", Long.class, box.id())).isEqualTo((long) number);
            assertThat(f.h.jdbc.queryForObject("SELECT lease_token_hash FROM wa.message_outbox WHERE id=?", String.class, box.id())).isNull();
            if (number < 3) {
                int delay = number == 1 ? 5 : 15;
                assertThat(f.h.jdbc.queryForObject("SELECT not_before FROM wa.message_outbox WHERE id=?", Timestamp.class, box.id())).isEqualTo(Timestamp.from(before.plusSeconds(delay)));
                f.h.time.advance(delay - 1);
                assertThat(f.service().claim(f.worker, json("{\"batchLimit\":1,\"leaseSeconds\":60}")).path("items")).isEmpty();
                f.h.time.advance(1);
            }
        }
        f.h.time.advance(45); assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.h.jdbc.queryForList("SELECT attempt_number FROM wa.message_attempt ORDER BY attempt_number", Integer.class)).containsExactly(1, 2, 3);
        assertThat(f.h.jdbc.queryForList("SELECT state FROM wa.message_attempt", String.class)).containsOnly("NOT_SUBMITTED");
    }
    @Test void oldReceiptReplayCannotAlterTheNextReservationOrItsFinalResult() {
        f.enqueue(); var first = f.authorize(f.lease()); var receipt = result(f, first);
        f.h.time.advance(5); var next = f.authorize(f.lease());
        assertThat(result(f, first)).isEqualTo(receipt);
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt WHERE attempt_number=2", String.class)).isEqualTo("SUBMITTING");
        assertThat(f.state(WaJson.uuid(next, "outboxId"))).isEqualTo("LEASED");
        assertThatThrownBy(() -> f.outbox.result(f.worker, WaJson.uuid(first, "outboxId"), unsent(first).put("outcome", "FAILED")))
            .isInstanceOfSatisfying(WaProblem.class, error -> assertThat(error.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        f.submitted(next, "synthetic_retry_submitted");
        assertThat(result(f, first)).isEqualTo(receipt);
        assertThat(f.state(WaJson.uuid(next, "outboxId"))).isEqualTo("SUBMITTED");
    }
    @Test void everyRetryRequiresANewLeaseAndAttemptToken() {
        f.enqueue(); var oldLease = f.lease(); var oldAttempt = f.authorize(oldLease); result(f, oldAttempt);
        assertThatThrownBy(() -> f.authorize(oldLease)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE));
        f.h.time.advance(5); var lease = f.lease();
        assertThatThrownBy(() -> f.authorize(oldLease)).isInstanceOf(WaProblem.class);
        var reservation = f.authorize(lease);
        assertThat(reservation.path("attemptToken")).isNotEqualTo(oldAttempt.path("attemptToken"));
        assertThatThrownBy(() -> f.authorize(lease)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
    }
    @Test void unknownAndFailedReceiptsNeverEnterSafeRetry() {
        for (String outcome : new String[]{"UNKNOWN", "FAILED"}) {
            var box = f.enqueue(); var reservation = f.authorize(f.lease());
            var request = unsent(reservation).put("outcome", outcome);
            var receipt = f.outbox.result(f.worker, box.id(), request);
            f.h.time.advance(20); assertThat(f.claim().path("items")).isEmpty();
            assertThat(f.state(box.id())).isEqualTo(outcome);
            assertThat(f.outbox.result(f.worker, box.id(), request)).isEqualTo(receipt);
        }
        assertThat(f.count("message_attempt")).isEqualTo(2);
    }
    @Test void abandonedUnknownCannotBeReopenedByLateProofOfNoPost() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        f.h.time.advance(11); f.claim(); assertThat(f.state(box.id())).isEqualTo("UNKNOWN");
        assertThat(result(f, reservation).path("state").asText()).isEqualTo("UNKNOWN");
        f.h.time.advance(20); assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }
    @Test void closedConnectionDefersSafeRetryUntilOpenWithoutRenewingTtl() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        f.h.time.advance(1); f.connection("close"); assertThat(result(f, reservation).path("state").asText()).isEqualTo("PENDING");
        f.h.time.advance(5); assertThat(f.claim().path("items")).isEmpty();
        f.connection("open"); assertThat(f.authorize(f.lease()).path("outboxId").asText()).isEqualTo(box.id().toString());
        assertThat(f.h.jdbc.queryForObject("SELECT expires_at FROM wa.message_outbox WHERE id=?", Timestamp.class, box.id())).isEqualTo(Timestamp.from(NOW.plusSeconds(300)));
    }
    @Test void expiredTtlOrNoRoomForTheNextDelayExpiresInsteadOfRequeue() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        f.h.jdbc.update("UPDATE wa.message_outbox SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(5)), box.id());
        assertThat(result(f, reservation).path("state").asText()).isEqualTo("EXPIRED");
        var later = f.enqueue(); var lateReservation = f.authorize(f.lease()); f.h.time.advance(301);
        assertThat(result(f, lateReservation).path("state").asText()).isEqualTo("EXPIRED");
        assertThat(f.state(later.id())).isEqualTo("EXPIRED"); assertThat(f.claim().path("items")).isEmpty();
    }
    @Test void lostBusinessEligibilityCancelsRatherThanRequeues() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        f.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','SUSPENDED')", box.actor());
        assertThat(result(f, reservation).path("state").asText()).isEqualTo("CANCELED");
        f.h.time.advance(5); assertThat(f.claim().path("items")).isEmpty();
    }
    @Test void onlyTheOriginalAttemptCredentialCanRecordItsSafeResultAcrossWorkerRotation() {
        f.enqueue(); var first = f.authorize(f.lease()); var other = f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        assertThatThrownBy(() -> f.outbox.result(other, WaJson.uuid(first, "outboxId"), unsent(first))).isInstanceOf(WaProblem.class);
        var foreign = f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER, "synthetic-other");
        assertThatThrownBy(() -> f.outbox.result(foreign, WaJson.uuid(first, "outboxId"), unsent(first))).isInstanceOf(WaProblem.class);
        result(f, first); f.h.time.advance(5);
        var lease = f.outbox.claim(other, json("{\"batchLimit\":1,\"leaseSeconds\":60}")).path("items").get(0);
        var next = f.outbox.authorize(other, WaJson.uuid(lease, "outboxId"), json("{}").put("leaseToken", lease.path("leaseToken").asText())).requireSuccess();
        assertThatThrownBy(() -> result(f, next)).isInstanceOf(WaProblem.class);
        assertThat(f.outbox.result(other, WaJson.uuid(next, "outboxId"), unsent(next)).path("state").asText()).isEqualTo("PENDING");
    }
    @Test void unsafeNotSubmittedCombinationsAreRejectedBeforeAnyMutation() {
        f.enqueue(); var reservation = f.authorize(f.lease());
        for (ObjectNode invalid : new ObjectNode[]{unsent(reservation).put("failureCode", "TIMEOUT"),
                unsent(reservation).put("failureCode", "REJECTED"), unsent(reservation).put("externalMessageId", "synthetic_no_post"),
                unsent(reservation).put("retry", true), unsent(reservation).removeAll().put("outcome", "NOT_SUBMITTED")}) {
            assertThatThrownBy(() -> f.outbox.result(f.worker, WaJson.uuid(reservation, "outboxId"), invalid))
                .isInstanceOfSatisfying(WaProblem.class, error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        }
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class)).isEqualTo("SUBMITTING");
        assertThat(f.h.jdbc.queryForObject("SELECT result_request_hash FROM wa.message_attempt", String.class)).isNull();
    }
    @Test void databaseRejectsInvalidNoPostFactsAndAReceiptlessRowCannotAuthorizeAgain() {
        f.enqueue(); var reservation = f.authorize(f.lease());
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.message_attempt SET state='NOT_SUBMITTED'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.message_attempt SET attempt_number=4"))
            .isInstanceOf(DataIntegrityViolationException.class);
        f.h.jdbc.update("UPDATE wa.message_attempt SET state='NOT_SUBMITTED',error_code='CHANNEL_UNAVAILABLE',completed_at=?", Timestamp.from(NOW));
        f.h.time.advance(61); assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.count("message_attempt")).isEqualTo(1);
        assertThat(f.state(WaJson.uuid(reservation, "outboxId"))).isEqualTo("LEASED");
    }
    @Test void lateResultsNeverReopenAnExpiredOrCanceledOutbox() {
        for (String state : new String[]{"CANCELED", "EXPIRED"}) for (boolean safe : new boolean[]{true, false}) {
            var box = f.enqueue(); var reservation = f.authorize(f.lease());
            f.h.jdbc.update("UPDATE wa.message_outbox SET state=? WHERE id=?", state, box.id());
            var response = safe ? result(f, reservation) : f.submitted(reservation, "synthetic_terminal_" + state);
            assertThat(response.path("state").asText()).isEqualTo(state);
            f.h.time.advance(5); assertThat(f.claim().path("items")).isEmpty();
        }
    }
}
