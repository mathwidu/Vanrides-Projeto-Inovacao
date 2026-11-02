package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRecoveryTestSupport.unknown;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryRecoveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_recovery");
    private WaDeliveryConnectionTestSupport f;
    private WaDeliveryRecoveryTestSupport health;
    @BeforeEach void setup() {
        f = new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); health = new WaDeliveryRecoveryTestSupport(f.h);
    }
    @Test void unknownExpiresAtTtlWithClosedChannelAndFlagsDisabledWithoutPausingConversation() {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease()));
        f.h.time.advance(300); f.connection("close");
        var disabled = new WaDeliveryRecoveryTestSupport(f.h, settings(false, true));
        var first = disabled.tick(100, null);
        assertThat(first.path("processed").asInt()).isEqualTo(1); assertThat(first.path("nextCursor").isNull()).isTrue();
        assertThat(f.state(box.id())).isEqualTo("EXPIRED"); assertThat(attemptState()).isEqualTo("UNKNOWN");
        var support = f.h.jdbc.queryForMap("SELECT * FROM wa.support_case");
        assertThat(support.get("purpose")).isEqualTo("DELIVERY");
        assertThat(support.get("reason_code")).isEqualTo("DELIVERY_UNKNOWN_EXPIRED");
        assertThat(support.get("deduplication_key")).isEqualTo("delivery-unknown:" + box.id());
        assertThat(support.get("correlation_id")).isEqualTo(box.id().toString());
        assertThat(support.get("environment")).isEqualTo("transport-test"); assertThat(support.get("instance_id")).isEqualTo(INSTANCE);
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.support_case SET identity_id=?", f.h.identity()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.support_case SET correlation_id='invalid'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(f.count("conversation_state")).isZero();
        assertThat(disabled.tick(100, null)).isEqualTo(first);
        f.h.time.advance(60); assertThat(disabled.tick(100, null).path("processed").asInt()).isZero();
        assertThat(f.count("support_case")).isEqualTo(1);
    }
    @Test void maintenanceExpiresFreshPendingAndDeferredNoPostIntentWithoutClaimOrCase() {
        var deferred = f.enqueue(); WaDeliveryRetryTestSupport.result(f, f.authorize(f.lease())); var untouched = f.enqueue();
        f.h.time.advance(301); f.connection("close");
        assertThat(health.tick(100, null).path("processed").asInt()).isEqualTo(2);
        assertThat(f.state(deferred.id())).isEqualTo("EXPIRED"); assertThat(f.state(untouched.id())).isEqualTo("EXPIRED");
        assertThat(f.count("support_case")).isZero(); assertThat(attemptState()).isEqualTo("NOT_SUBMITTED");
    }
    @Test void onePageAbandonsAnExpiredReservationThenExpiresItsOutboxAndCreatesOneCase() {
        var box = f.enqueue(); f.authorize(f.lease()); f.h.time.advance(301);
        assertThat(health.tick(100, null).path("processed").asInt()).isEqualTo(1);
        assertThat(f.state(box.id())).isEqualTo("EXPIRED"); assertThat(attemptState()).isEqualTo("UNKNOWN");
        assertThat(f.count("support_case")).isEqualTo(1);
    }
    @Test void firstLateKnownResultAndMonotonicCallbacksImproveAttemptButNeverReopenExpiredOutbox() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        f.h.time.advance(11); health.tick(100, null); assertThat(f.state(box.id())).isEqualTo("UNKNOWN");
        f.h.time.advance(289); health.tick(100, null); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
        var receipt = f.submitted(reservation, "synthetic_late_known_recovery");
        assertThat(receipt.path("state").asText()).isEqualTo("EXPIRED"); assertThat(attemptState()).isEqualTo("SUBMITTED");
        f.callback("synthetic_late_known_recovery", "READ"); f.callback("synthetic_late_known_recovery", "SERVER_ACK"); f.callback("synthetic_late_known_recovery", "ERROR");
        f.h.time.advance(60); health.tick(100, null);
        assertThat(attemptState()).isEqualTo("READ"); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
        assertThat(f.submitted(reservation, "synthetic_late_known_recovery")).isEqualTo(receipt);
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.support_case", String.class)).isEqualTo("OPEN");
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox WHERE category='STATUS' AND state='COMPLETED'", Integer.class)).isEqualTo(3);
    }
    @Test void acknowledgedUnknownReceiptIsImmutableAndUncorrelatedCallbackCannotInventDelivery() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); var receipt = unknown(f, reservation);
        f.h.time.advance(300); health.tick(100, null);
        assertThat(unknown(f, reservation)).isEqualTo(receipt); assertThat(receipt.path("state").asText()).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> f.submitted(reservation, "synthetic_unproven_id")).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        f.callback("synthetic_unproven_id", "READ"); f.h.time.advance(60);
        assertThat(health.tick(100, null).path("processed").asInt()).isZero();
        assertThat(attemptState()).isEqualTo("UNKNOWN"); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE category='STATUS'", String.class)).isEqualTo("PENDING");
    }
    @Test void aLateFirstResultAtTtlExpiresTheAlreadyUnknownOutboxEvenBeforeNextHealthTick() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); f.h.time.advance(11); health.tick(100, null);
        f.h.time.advance(289);
        assertThat(f.submitted(reservation, "synthetic_late_before_health").path("state").asText()).isEqualTo("EXPIRED");
        assertThat(f.count("support_case")).isEqualTo(1); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
    }
    @Test void healthScopeIsBoundToAuthenticatedInstanceAndEnvironment() {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300);
        var foreign = f.h.principal(WaPrincipal.Kind.SYSTEM_JOB, "synthetic-other");
        assertThat(health.jobs.tick(foreign, health.request(100, null)).path("processed").asInt()).isZero();
        var otherEnvironment = new WaDeliveryRecoveryTestSupport(f.h, settings(true, true, "other-test"));
        assertThat(otherEnvironment.tick(100, null).path("processed").asInt()).isZero();
        assertThat(f.state(box.id())).isEqualTo("UNKNOWN"); assertThat(f.count("support_case")).isZero();
        health.tick(100, null); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
    }
    @Test void boundedCursorSurvivesRestartAndNextMinuteRecoversWorkAfterLostCursor() {
        f.enqueue(); f.enqueue(); f.enqueue(); f.h.time.advance(300);
        var first = health.tick(1, null); assertThat(first.path("processed").asInt()).isEqualTo(1);
        String cursor = first.path("nextCursor").asText(); assertThat(cursor).startsWith("jobcursor_");
        assertThat(health.tick(1, null)).isEqualTo(first);
        var restarted = new WaDeliveryRecoveryTestSupport(f.h);
        var second = restarted.tick(1, cursor); assertThat(second.path("processed").asInt()).isEqualTo(1);
        assertThat(second.path("nextCursor").isNull()).isFalse();
        f.h.time.advance(60); assertThat(restarted.tick(100, null).path("processed").asInt()).isEqualTo(1);
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE state='EXPIRED'", Integer.class)).isEqualTo(3);
    }
    @Test void healthCursorAndLimitsCannotCrossScopeWindowOrJobType() {
        f.enqueue(); f.enqueue(); f.h.time.advance(300); String cursor = health.tick(1, null).path("nextCursor").asText();
        for (ObjectNode invalid : new ObjectNode[]{health.request(0, null), health.request(101, null),
                health.request(1, cursor).put("jobType", "DUE_REMINDERS"), health.request(1, cursor).put("windowKey", "2030-01-01T12:04:00Z")})
            assertThatThrownBy(() -> health.jobs.tick(health.job, invalid)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> health.jobs.tick(f.worker, health.request(1, cursor))).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> health.jobs.tick(f.h.principal(WaPrincipal.Kind.SYSTEM_JOB, "synthetic-other"), health.request(1, cursor))).isInstanceOf(WaProblem.class);
        assertThat(health.tick(1, cursor).path("processed").asInt()).isEqualTo(1);
    }
    @Test void operationalCaseDoesNotReplaceHumanSupportOrPauseItsConversation() {
        var c = new WaConversationTestSupport(POSTGRES); UUID actor = c.actor(); var hello = c.text(actor, "Olá");
        c.execute(hello, "START_ONBOARDING", json("{}")).requireSuccess(); c.finish(hello);
        var reservation = c.authorize((ObjectNode) c.outboxClaim().path("items").get(0));
        c.outbox.result(c.delivery, WaJson.uuid(reservation, "outboxId"), json("{}").put("attemptToken", reservation.path("attemptToken").asText()).put("outcome", "UNKNOWN").put("failureCode", "TIMEOUT"));
        c.h.time.advance(300); new WaDeliveryRecoveryTestSupport(c.h).tick(100, null);
        assertThat(c.string("SELECT stage FROM wa.conversation_state WHERE identity_id=?", actor)).isNotEqualTo("PAUSED");
        var support = c.text(actor, "falar com suporte"); c.execute(support, "REQUEST_SUPPORT", json("{}").put("reason", "HELP").put("detail", "Pedido humano sintético.")).requireSuccess();
        assertThat(c.number("SELECT count(*) FROM wa.support_case WHERE identity_id=?", actor)).isEqualTo(2);
        assertThat(c.string("SELECT s.purpose FROM wa.support_case s JOIN wa.conversation_state state ON s.id::text=state.context_json->>'supportCaseId' WHERE state.identity_id=?", actor)).isEqualTo("CONVERSATION");
    }
    @Test void privateSafeRetryExpiresAndPurgesWhileNoWorkerCanClaimTheClosedChannel() {
        var driver = new WaDriverTestSupport(POSTGRES); UUID actor = driver.driver(); driver.invite(actor);
        var reservation = driver.s.authorize(driver.privateItem(actor)); UUID box = WaJson.uuid(reservation, "outboxId");
        driver.s.outbox.result(driver.s.delivery, box, WaDeliveryRetryTestSupport.unsent(reservation));
        assertThat(driver.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box)).isNotNull();
        driver.h.time.advance(301); driver.h.connection(driver.s.channel, "close", driver.h.clock.now());
        new WaDeliveryRecoveryTestSupport(driver.h, settings(false, true)).tick(100, null);
        assertThat(driver.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box)).isEqualTo("EXPIRED");
        assertThat(driver.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box)).isNull();
        assertThat(driver.s.number("SELECT count(*) FROM wa.support_case")).isZero();
    }
    private String attemptState() { return f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class); }
}
