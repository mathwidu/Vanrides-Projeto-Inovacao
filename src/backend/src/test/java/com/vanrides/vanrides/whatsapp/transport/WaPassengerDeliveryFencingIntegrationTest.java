package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerDeliveryFencingIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = postgres("wa_passenger_delivery_fencing");

    private WaPassengerDeliveryTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaPassengerDeliveryTestSupport(POSTGRES);
    }

    @Test
    void changedLinkRevisionCancelsLeasedNotificationBeforeAddressLeaves() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        f.h.jdbc.update(
                "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", pair.link());
        f.canceled(lease);
    }

    @Test
    void changedPickupRevisionCancelsLeasedNotificationBeforeMapLeaves() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        f.h.jdbc.update(
                "UPDATE wa.pickup_point SET longitude=1,version=version+1 WHERE id=?",
                pair.point());
        f.canceled(lease);
    }

    @Test
    void changedRoutineScheduleInvalidatesCapturedSelection() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        f.h.jdbc.update("UPDATE wa.routine SET local_time='07:00',version=version+1 WHERE id=?", pair.routine());
        f.canceled(lease);
    }

    @Test
    void suspendedSourceActorCannotNotifyItsStillActivePeer() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        f.h.jdbc.update(
                "UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?", pair.passenger());
        f.canceled(lease);
    }

    @Test
    void differentWorkerOrInstanceCannotReadLeasedCounterparty() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        for (var worker :
                new WaPrincipal[] {
                    f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER),
                    f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER, "other-synthetic-instance")
                }) {
            assertThatThrownBy(
                            () ->
                                    f.outbox.authorize(
                                            worker,
                                            box,
                                            json("{}")
                                                    .put(
                                                            "leaseToken",
                                                            lease.path("leaseToken").asText())))
                    .isInstanceOfSatisfying(
                            WaProblem.class,
                            error ->
                                    assertThat(error.code())
                                            .isEqualTo(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE));
        }
        var otherSettings =
                new WaSettings(
                        new org.springframework.mock.env.MockEnvironment()
                                .withProperty("vanrides.wa.enabled", "true")
                                .withProperty("vanrides.wa.environment", "other-transport-test")
                                .withProperty(
                                        "vanrides.wa.public-base-url", "http://127.0.0.1:8080")
                                .withProperty(
                                        "vanrides.wa.hash-key",
                                        Base64.getUrlEncoder()
                                                .withoutPadding()
                                                .encodeToString(f.h.settings.hashKey()))
                                .withProperty(
                                        "vanrides.wa.response-key",
                                        Base64.getUrlEncoder()
                                                .withoutPadding()
                                                .encodeToString(f.h.settings.responseKey()))
                                .withProperty(
                                        "vanrides.wa.operator-credential",
                                        f.h.settings.operatorCredential())
                                .withProperty(
                                        "vanrides.wa.operator-expires-at",
                                        f.h.settings.operatorExpiresAt().toString()));
        var otherWorker = f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        f.h.jdbc.update(
                "UPDATE wa.service_credential SET environment='other-transport-test' WHERE id=?",
                otherWorker.credentialId());
        var otherService =
                f.h.proxy(
                        new WaOutboxService(
                                f.h.jdbc,
                                new WaTransportAccess(f.h.jdbc, otherSettings, f.h.clock),
                                new WaDeliveryReconciler(f.h.jdbc, f.h.clock),
                                otherSettings,
                                f.h.clock));
        assertThat(
                        otherService
                                .claim(otherWorker, json("{\"batchLimit\":10,\"leaseSeconds\":60}"))
                                .path("items"))
                .isEmpty();
        assertThatThrownBy(
                        () ->
                                otherService.authorize(
                                        otherWorker,
                                        box,
                                        json("{}")
                                                .put(
                                                        "leaseToken",
                                                        lease.path("leaseToken").asText())))
                .isInstanceOfSatisfying(
                        WaProblem.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE));
        assertThat(f.count("message_attempt")).isZero();
    }

    @Test
    void leaseExpiryDuringSubjectLockWaitRollsBackCancellationAndAttempt() throws Exception {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        assertThat(waitForSubject(pair.link(), lease, 61)).isEqualTo("RESOURCE_NOT_ACCESSIBLE");
        assertThat(f.state(box)).isEqualTo("LEASED");
        assertThat(f.count("message_attempt")).isZero();
    }

    @Test
    void credentialExpiryDuringSubjectLockWaitRollsBackAttempt() throws Exception {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        assertThat(waitForSubject(pair.link(), lease, 3601)).isEqualTo("INVALID_CREDENTIAL");
        assertThat(f.state(box)).isEqualTo("LEASED");
        assertThat(f.count("message_attempt")).isZero();
    }

    @Test
    void pausedActorConversationCancelsItsPendingWizardResponse() {
        UUID actor = f.actor(false),
                command = f.command(actor, "ADVANCE_PASSENGER_SETUP"),
                box = UUID.randomUUID();
        f.h.transaction.executeWithoutResult(
                tx ->
                        WaPassengerResponses.saveActor(
                                f.h.jdbc,
                                box,
                                command,
                                actor,
                                "Pergunta sintética.",
                                f.h.clock.now()));
        pause(actor);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(box)).isEqualTo("CANCELED");
        assertThat(f.count("message_attempt")).isZero();
    }

    @Test
    void pauseAfterClaimPreventsConflictingPeerNotification() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box);
        pause(pair.driver());
        f.canceled(lease);
    }

    @Test
    void pauseAfterReservationPreservesAttemptResultAndCallbacks() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var reservation = f.authorize(f.claim(box));
        pause(pair.driver());
        assertThat(f.submitted(reservation, "AUTHORIZED_BEFORE_PAUSE").path("state").asText())
                .isEqualTo("SUBMITTED");
        f.callback("AUTHORIZED_BEFORE_PAUSE", "READ");
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(box)).isEqualTo("READ");
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }

    private void pause(UUID actor) {
        f.h.jdbc.update(
                """
                INSERT INTO wa.conversation_state(identity_id,stage,updated_at) VALUES (?,'PAUSED',?)
                ON CONFLICT(identity_id) DO UPDATE SET stage='PAUSED',version=wa.conversation_state.version+1
                """,
                actor,
                java.sql.Timestamp.from(f.h.clock.now()));
    }

    private String waitForSubject(UUID link, ObjectNode lease, long seconds) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var result = new AtomicReference<Future<String>>();
        try {
            f.h.transaction.executeWithoutResult(
                    tx -> {
                        f.h.jdbc.queryForList(
                                "SELECT id FROM wa.driver_passenger_link WHERE id=? FOR UPDATE",
                                link);
                        result.set(
                                executor.submit(
                                        () -> {
                                            try {
                                                f.authorize(lease);
                                                return "RESERVED";
                                            } catch (WaProblem error) {
                                                return error.code().name();
                                            }
                                        }));
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        boolean waiting = false;
                        while (System.nanoTime() < deadline) {
                            f.h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
                            waiting =
                                    Boolean.TRUE.equals(
                                            f.h.jdbc.queryForObject(
                                                    """
                                                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock'
                                                      AND query LIKE 'SELECT driver_id,passenger_id,state,version,valid_from,valid_until FROM wa.driver_passenger_link%')
                                                    """,
                                                    Boolean.class));
                            if (waiting) {
                                break;
                            }
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError("Interrupted subject lock observation");
                            }
                        }
                        assertThat(waiting).as("real PostgreSQL subject lock observed").isTrue();
                        f.h.time.advance(seconds);
                    });
            return result.get().get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
