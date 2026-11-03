package com.vanrides.vanrides.whatsapp.pairing;

import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.pairing.WaPairingTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WaPairingConcurrencyIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_pairing_concurrency");
    private WaPairingTestSupport h;
    @BeforeEach void reset() { h = new WaPairingTestSupport(postgres); }

    @Test
    void shouldReturnTheSameSessionToConcurrentRetriesOfTheSameRedemption() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-concurrent-0001");
        List<Outcome> results = race(
                () -> h.service.redeem(pairing.client().proof(), redeemBody(pairing), "redeem-concurrent-0001"),
                () -> h.service.redeem(pairing.client().proof(), redeemBody(pairing), "redeem-concurrent-0001"));
        assertThat(code(results.get(0))).isNull(); assertThat(code(results.get(1))).isNull();
        WaPairingServiceIntegrationTest.sameResponse(results.get(0).decision().body(), results.get(1).decision().body());
        assertThat(h.number("SELECT count(*) FROM wa.driver_device")).isEqualTo(1);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.device_token")).isEqualTo(2);
    }

    @Test
    void shouldAllowOnlyOneOfTwoApprovalsOfTheSameDriverGenerationToRedeem() throws Exception {
        UUID driver = h.driver(); Approved first = h.approved(client(), driver, "begin-generation-0001");
        Approved second = h.approved(client(), driver, "begin-generation-0002");
        List<Outcome> results = race(
                () -> h.service.redeem(first.client().proof(), redeemBody(first), "redeem-generation-0001"),
                () -> h.service.redeem(second.client().proof(), redeemBody(second), "redeem-generation-0002"));
        assertThat(results.stream().filter(result -> code(result) == null).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> code(result) == WaProblem.Code.INTENT_EXPIRED).count()).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_device WHERE status='ACTIVE'")).isEqualTo(1);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.pairing_challenge WHERE state='REDEEMED'")).isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentRetriesOfTheSameRefreshWithoutRevokingItsFamily() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-refresh-race-0001");
        var session = h.redeem(pairing, "redeem-refresh-race-0001");
        List<Outcome> results = race(
                () -> h.service.refresh(pairing.client().proof(), refreshBody(session), "refresh-concurrent-0001"),
                () -> h.service.refresh(pairing.client().proof(), refreshBody(session), "refresh-concurrent-0001"));
        assertThat(code(results.get(0))).isNull(); assertThat(code(results.get(1))).isNull();
        WaPairingServiceIntegrationTest.sameResponse(results.get(0).decision().body(), results.get(1).decision().body());
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE consumed_at IS NOT NULL")).isEqualTo(1);
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("ACTIVE");
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
    }

    @Test
    void shouldSerializeTheSameKeyAcrossDriversWithoutRevokingTheWinningDriver() throws Exception {
        Client client = client(); UUID firstDriver = h.driver(); UUID secondDriver = h.driver();
        Approved first = h.approved(client, firstDriver, "begin-cross-driver-0001");
        Approved second = h.approved(client, secondDriver, "begin-cross-driver-0002");
        List<Outcome> results = race(
                () -> h.service.redeem(client.proof(), redeemBody(first), "redeem-cross-driver-0001"),
                () -> h.service.redeem(client.proof(), redeemBody(second), "redeem-cross-driver-0002"));
        assertThat(results.stream().filter(result -> code(result) == null).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> code(result) == WaProblem.Code.ACTION_NOT_ALLOWED).count()).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_device WHERE status='ACTIVE'")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_authorization WHERE generation=1")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_authorization WHERE generation=0")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE revoked_at IS NULL")).isEqualTo(2);
    }

    @Test
    void shouldObserveAProfileBlockCommittedWhileRefreshWaitsOnTheProfileLock() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-profile-race-0001");
        var session = h.redeem(pairing, "redeem-profile-race-0001");
        Outcome result = whileProfileLocked(pairing.driver(),
                () -> h.service.refresh(pairing.client().proof(), refreshBody(session), "refresh-profile-race-0001"),
                connection -> {
                    try (var statement = connection.prepareStatement("UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?")) {
                        statement.setObject(1, pairing.driver()); statement.executeUpdate();
                    }
                });
        assertThat(code(result)).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE consumed_at IS NOT NULL")).isZero();
        assertThat(h.number("SELECT count(*) FROM wa.device_token")).isEqualTo(2);
    }

    @Test
    void shouldRecheckTheFiveMinuteDeadlineAfterWaitingForTheProfileLock() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-clock-race-0001");
        Outcome result = whileProfileLocked(pairing.driver(),
                () -> h.service.redeem(pairing.client().proof(), redeemBody(pairing), "redeem-clock-race-0001"),
                connection -> h.time.advance(Duration.ofMinutes(5)));
        assertThat(code(result)).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(h.number("SELECT count(*) FROM wa.driver_device")).isZero();
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isZero();
    }

    @Test
    void shouldRecheckPairingExpiryAfterTheDeviceKeyLockWithoutRevokingTheCurrentDevice() throws Exception {
        UUID driver = h.driver(); Approved current = h.approved(client(), driver, "begin-advisory-current-0001");
        var originalSession = h.redeem(current, "redeem-advisory-current-0001");
        Approved replacement = h.approved(client(), driver, "begin-advisory-next-0001");
        byte[] digest = WaCrypto.sha256(("DEVICE_JKT\0" + replacement.client().proof().jkt()).getBytes(StandardCharsets.UTF_8));
        long advisoryKey = ByteBuffer.wrap(digest).getLong();
        Outcome result = whileLocked(connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, advisoryKey); statement.executeQuery().close();
            }
        }, () -> h.service.redeem(replacement.client().proof(), redeemBody(replacement), "redeem-advisory-next-0001"),
                connection -> h.time.advance(Duration.ofSeconds(301)));
        assertThat(code(result)).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", driver)).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_device")).isEqualTo(1);
        assertThat(h.string("SELECT status FROM wa.driver_device WHERE id=?", WaJson.uuid(originalSession, "deviceId")))
                .isEqualTo("ACTIVE");
        assertThat(h.number("SELECT count(*) FROM wa.device_token")).isEqualTo(2);
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE revoked_at IS NOT NULL OR consumed_at IS NOT NULL")).isZero();
        assertThat(h.string("SELECT state FROM wa.pairing_challenge WHERE id=?", WaJson.uuid(replacement.begin(), "pairingId")))
                .isEqualTo("EXPIRED");
    }

    private Outcome whileProfileLocked(UUID driver, Supplier<WaDecision> action, LockedAction beforeCommit) throws Exception {
        return whileLocked(connection -> {
            try (var statement = connection.prepareStatement("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE")) {
                statement.setObject(1, driver); statement.executeQuery().close();
            }
        }, action, beforeCommit);
    }

    private Outcome whileLocked(LockedAction acquire, Supplier<WaDecision> action, LockedAction beforeCommit) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            owner.setAutoCommit(false);
            try {
                long pid;
                try (var statement = owner.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                    rows.next(); pid = rows.getLong(1);
                }
                acquire.run(owner);
                CountDownLatch submitted = new CountDownLatch(1);
                Future<Outcome> waiting = executor.submit(() -> {
                    submitted.countDown();
                    try { return new Outcome(action.get(), null); }
                    catch (WaProblem failure) { return new Outcome(null, failure.code()); }
                });
                assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitDatabaseWait(pid); assertThat(waiting.isDone()).isFalse();
                beforeCommit.run(owner); owner.commit();
                return waiting.get(10, TimeUnit.SECONDS);
            } finally {
                owner.rollback(); executor.shutdown();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally { executor.shutdownNow(); }
    }

    private void awaitDatabaseWait(long ownerPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (h.number("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                        AND wait_event_type='Lock' AND ?::integer=ANY(pg_blocking_pids(pid))
                    """, ownerPid) > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Pairing request did not wait on the transaction owner");
    }

    private static List<Outcome> race(Supplier<WaDecision> first, Supplier<WaDecision> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> a = executor.submit(() -> execute(first, ready, start));
            Future<Outcome> b = executor.submit(() -> execute(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static Outcome execute(Supplier<WaDecision> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent service start timed out");
        try { return new Outcome(action.get(), null); }
        catch (WaProblem failure) { return new Outcome(null, failure.code()); }
    }
    private static WaProblem.Code code(Outcome value) { return value.failure() != null ? value.failure() : value.decision().error(); }
    private record Outcome(WaDecision decision, WaProblem.Code failure) { }
    @FunctionalInterface private interface LockedAction { void run(Connection connection) throws Exception; }
}
