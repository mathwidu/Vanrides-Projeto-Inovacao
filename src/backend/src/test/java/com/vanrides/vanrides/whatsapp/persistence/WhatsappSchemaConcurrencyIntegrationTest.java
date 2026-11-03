package com.vanrides.vanrides.whatsapp.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.whatsapp.persistence.WhatsappPostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WhatsappSchemaConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_concurrency");

    @BeforeEach
    void resetDatabase() throws Exception {
        resetTo(postgres, "26");
    }

    @Test
    void shouldSerializeCompetingRunsIncludingAStartFailedReservation() throws Exception {
        UUID driver;
        UUID routine;
        UUID firstOccurrence;
        UUID secondOccurrence;
        try (Connection connection = open(postgres)) {
            driver = identity(connection, "DRIVER");
            routine = routine(connection, driver);
            firstOccurrence = occurrence(connection, routine, driver, DATE);
            secondOccurrence = occurrence(connection, routine, driver, DATE.plusDays(1));
        }
        assertUniqueRace("uk_run_active_driver",
                connection -> run(connection, firstOccurrence, routine, driver, DATE, "START_FAILED"),
                connection -> run(connection, secondOccurrence, routine, driver, DATE.plusDays(1), "STARTING"));
    }

    @Test
    void shouldSerializeTwoDriversApprovingTheSamePassenger() throws Exception {
        UUID first;
        UUID second;
        UUID passenger;
        try (Connection connection = open(postgres)) {
            first = identity(connection, "DRIVER");
            second = identity(connection, "DRIVER");
            passenger = identity(connection, "PASSENGER");
        }
        assertUniqueRace("uk_link_active_passenger",
                connection -> link(connection, first, passenger, "APPROVED"),
                connection -> link(connection, second, passenger, "APPROVED"));
    }

    @Test
    void shouldSerializeNewDevicesForOneDriver() throws Exception {
        UUID driver;
        try (Connection connection = open(postgres)) {
            driver = identity(connection, "DRIVER");
        }
        assertUniqueRace("uk_device_active_driver",
                connection -> device(connection, driver, "A".repeat(43)),
                connection -> device(connection, driver, "B".repeat(43)));
    }

    @Test
    void shouldRejectASecondCommandEffectEvenWhenTheClientChangesItsKey() throws Exception {
        UUID actor;
        UUID event;
        try (Connection connection = open(postgres)) {
            actor = identity(connection, null);
            event = inbox(connection, actor);
        }
        assertUniqueRace("uk_command_inbox_event",
                connection -> command(connection, UUID.randomUUID(), actor, event, "client-key-one"),
                connection -> command(connection, UUID.randomUUID(), actor, event, "client-key-two"));
    }

    @Test
    void shouldAllowOnlyOneClaimedEventPerIdentityAcrossWorkers() throws Exception {
        UUID firstEvent;
        UUID secondEvent;
        UUID firstWorker;
        UUID secondWorker;
        try (Connection connection = open(postgres)) {
            UUID actor = identity(connection, null);
            firstEvent = inbox(connection, actor);
            secondEvent = inbox(connection, actor);
            firstWorker = credential(connection, "CONVERSATION_WORKER");
            secondWorker = credential(connection, "CONVERSATION_WORKER");
        }
        String claim = """
                UPDATE wa.integration_inbox SET state='CLAIMED',lease_credential_id=?,lease_token_hash=?,
                    lease_generation=1,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '1 minute' WHERE id=?
                """;
        assertUniqueRace("uk_inbox_claimed_identity",
                connection -> update(connection, claim, firstWorker, hash("first-lease"), firstEvent),
                connection -> update(connection, claim, secondWorker, hash("second-lease"), secondEvent));
    }

    private static void assertUniqueRace(String expectedConstraint, ConnectionAction firstAction,
                                         ConnectionAction secondAction) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = open(postgres); Connection second = open(postgres);
             Connection observer = open(postgres)) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            long firstPid = number(first, "SELECT pg_backend_pid()");
            long secondPid = number(second, "SELECT pg_backend_pid()");
            try {
                firstAction.run(first);
                CountDownLatch submitted = new CountDownLatch(1);
                Future<String> competing = executor.submit(() -> {
                    submitted.countDown();
                    try {
                        secondAction.run(second);
                        second.commit();
                        return "committed";
                    } catch (SQLException failure) {
                        second.rollback();
                        return failure.getSQLState() + ":" + constraint(failure);
                    }
                });
                assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitDatabaseLock(observer, firstPid, secondPid);
                assertThat(competing.isDone()).isFalse();
                first.commit();
                assertThat(competing.get(10, TimeUnit.SECONDS)).isEqualTo("23505:" + expectedConstraint);
            } finally {
                // Release the owner even if observation/assertion failed, before
                // closing the competing connection that may still be blocked.
                first.rollback();
                executor.shutdown();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitDatabaseLock(Connection observer, long blockerPid, long waitingPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (number(observer, """
                    SELECT count(*) FROM pg_stat_activity
                    WHERE pid=? AND wait_event_type='Lock' AND ?::integer = ANY(pg_blocking_pids(pid))
                    """, waitingPid, blockerPid) == 1) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Competing PostgreSQL transaction did not wait on the invariant owner");
    }

    @FunctionalInterface
    private interface ConnectionAction {
        void run(Connection connection) throws SQLException;
    }
}
