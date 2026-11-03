package com.vanrides.vanrides.repository;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class LoginIdentityPostgresConcurrencyTestSupport {

    private static final long START_TIMEOUT_SECONDS = 5;

    private LoginIdentityPostgresConcurrencyTestSupport() {
    }

    static SqlResult execute(
            PostgreSQLContainer<?> postgres,
            CompletableFuture<Integer> backendPid,
            SqlOperation operation) throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            connection.setAutoCommit(false);
            backendPid.complete(Math.toIntExact(
                    LoginIdentityPostgresTestSupport.queryLong(
                            connection, "SELECT pg_backend_pid()"
                    )
            ));
            try {
                operation.execute(connection);
                connection.commit();
                return new SqlResult(true, null, null);
            } catch (SQLException failure) {
                connection.rollback();
                return new SqlResult(
                        false,
                        LoginIdentityPostgresTestSupport.sqlState(failure),
                        LoginIdentityPostgresTestSupport.constraintName(failure)
                );
            }
        }
    }

    static void assertBlocked(
            PostgreSQLContainer<?> postgres,
            CompletableFuture<Integer> backendPid,
            Future<?> future) throws Exception {
        int pid = backendPid.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
        boolean waitingForLock = false;
        try (Connection observer = LoginIdentityPostgresTestSupport.open(postgres)) {
            while (!waitingForLock && System.nanoTime() < deadline) {
                waitingForLock = LoginIdentityPostgresTestSupport.queryBoolean(observer, """
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

    static void insertDevice(
            Connection connection,
            String tokenHash,
            String role,
            long userId,
            String email,
            long companyId,
            boolean active) throws SQLException {
        LoginIdentityPostgresTestSupport.update(connection, """
                INSERT INTO device_token (
                    token_hash, token_value, platform, user_id, user_role, user_email,
                    company_id, active, last_registered_at, invalidated_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, 'ANDROID', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                    CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, tokenHash, "value-" + tokenHash, userId, role, email, companyId,
                active, active);
    }

    static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface
    interface SqlOperation {
        void execute(Connection connection) throws SQLException;
    }

    record SqlResult(boolean committed, String sqlState, String constraint) {
    }
}
