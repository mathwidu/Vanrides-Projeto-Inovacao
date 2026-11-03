package com.vanrides.vanrides.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class PostgresLockTestSupport {

    static final long START_TIMEOUT_SECONDS = 5;
    static final long OPERATION_TIMEOUT_SECONDS = 15;

    private final PostgreSQLContainer<?> postgres;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    PostgresLockTestSupport(
            PostgreSQLContainer<?> postgres,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.postgres = postgres;
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    <T> T inTransaction(TransactionCallback<T> callback) {
        return transactions.execute(callback);
    }

    int currentBackendPid() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class)
        );
    }

    void assertBlocked(
            CompletableFuture<Integer> backendPid,
            Future<?> future) throws Exception {
        int pid = backendPid.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
        boolean waitingForLock = false;
        try (Connection observer = openConnection()) {
            while (!waitingForLock && System.nanoTime() < deadline) {
                waitingForLock = queryBoolean(observer, """
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_stat_activity
                            WHERE pid = ?
                              AND wait_event_type = 'Lock'
                        )
                        """, pid);
                if (!waitingForLock) {
                    TimeUnit.MILLISECONDS.sleep(25L);
                }
            }
        }
        assertThat(waitingForLock)
                .as("backend %s should be waiting for a PostgreSQL lock", pid)
                .isTrue();
        assertThatThrownBy(() -> future.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private boolean queryBoolean(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    void await(CountDownLatch latch) {
        try {
            if (!latch.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while coordinating passenger delete race",
                    exception
            );
        }
    }

    void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
}
