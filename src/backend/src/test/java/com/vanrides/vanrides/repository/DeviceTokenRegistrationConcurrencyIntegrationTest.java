package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.SqlResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.assertBlocked;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.execute;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DeviceTokenRegistrationConcurrencyIntegrationTest {

    private static final long RESULT_TIMEOUT_SECONDS = 15;

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_device_registration_concurrency_test"
    );

    @BeforeEach
    void migrateCleanDatabase() {
        LoginIdentityPostgresTestSupport.resetLatest(postgres);
    }

    @Test
    void shouldSerializeConcurrentIdempotentRegistrationsForTheSameOwner()
            throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "same-token.owner@example.com");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> contenderPid = new CompletableFuture<>();
        SqlResult contenderResult;
        try (Connection first = LoginIdentityPostgresTestSupport.open(postgres)) {
            first.setAutoCommit(false);
            registerDeviceIdempotently(
                    first,
                    "same-token-same-owner",
                    "value-first",
                    "DRIVER",
                    driverId,
                    "same-token.owner@example.com",
                    companyId
            );

            Future<SqlResult> contender = executor.submit(() -> execute(
                    postgres,
                    contenderPid,
                    connection -> registerDeviceIdempotently(
                            connection,
                            "same-token-same-owner",
                            "value-second",
                            "DRIVER",
                            driverId,
                            "same-token.owner@example.com",
                            companyId
                    )
            ));
            assertBlocked(postgres, contenderPid, contender);

            first.commit();
            contenderResult = contender.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            shutdown(executor);
        }

        assertThat(contenderResult.committed()).isTrue();
        assertThat(contenderResult.sqlState()).isNull();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM device_token
                    WHERE token_hash = 'same-token-same-owner'
                    """)).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT active
                           AND token_value = 'value-second'
                           AND user_role = 'DRIVER'
                           AND user_id = ?
                           AND user_email = 'same-token.owner@example.com'
                    FROM device_token WHERE token_hash = 'same-token-same-owner'
                    """, driverId)).isTrue();
        }
    }

    @Test
    void shouldSerializeConcurrentReassignmentOfTheSameTokenAcrossOwners()
            throws Exception {
        long companyId;
        long driverId;
        long passengerId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "same-token.driver@example.com");
            passengerId = LoginIdentityPostgresTestSupport.insertPassenger(
                    setup, companyId, "same-token.passenger@example.com");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> contenderPid = new CompletableFuture<>();
        SqlResult contenderResult;
        try (Connection first = LoginIdentityPostgresTestSupport.open(postgres)) {
            first.setAutoCommit(false);
            registerDeviceIdempotently(
                    first,
                    "same-token-different-owner",
                    "value-driver",
                    "DRIVER",
                    driverId,
                    "same-token.driver@example.com",
                    companyId
            );

            Future<SqlResult> contender = executor.submit(() -> execute(
                    postgres,
                    contenderPid,
                    connection -> registerDeviceIdempotently(
                            connection,
                            "same-token-different-owner",
                            "value-passenger",
                            "PASSENGER",
                            passengerId,
                            "same-token.passenger@example.com",
                            companyId
                    )
            ));
            assertBlocked(postgres, contenderPid, contender);

            first.commit();
            contenderResult = contender.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            shutdown(executor);
        }

        assertThat(contenderResult.committed()).isTrue();
        assertThat(contenderResult.sqlState()).isNull();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM device_token
                    WHERE token_hash = 'same-token-different-owner'
                    """)).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT active
                           AND token_value = 'value-passenger'
                           AND user_role = 'PASSENGER'
                           AND user_id = ?
                           AND user_email = 'same-token.passenger@example.com'
                    FROM device_token WHERE token_hash = 'same-token-different-owner'
                    """, passengerId)).isTrue();
        }
    }

    private void registerDeviceIdempotently(
            Connection connection,
            String tokenHash,
            String tokenValue,
            String role,
            long userId,
            String email,
            long companyId) throws SQLException {
        String normalizedEmail = LoginIdentityPostgresTestSupport.queryString(connection, """
                SELECT normalized_email
                FROM login_identity
                WHERE normalized_email = ?
                  AND user_role = ?
                  AND user_id = ?
                FOR KEY SHARE
                """, email, role, userId);

        LoginIdentityPostgresTestSupport.update(connection, """
                INSERT INTO device_token (
                    token_hash, token_value, platform, user_id, user_role, user_email,
                    company_id, active, last_registered_at, invalidated_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, 'ANDROID', ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, NULL,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT ON CONSTRAINT uk_device_token_hash DO NOTHING
                """, tokenHash, tokenValue, userId, role, normalizedEmail, companyId);

        long deviceId = LoginIdentityPostgresTestSupport.queryLong(connection, """
                SELECT id FROM device_token WHERE token_hash = ? FOR UPDATE
                """, tokenHash);
        LoginIdentityPostgresTestSupport.update(connection, """
                UPDATE device_token
                SET token_value = ?,
                    platform = 'ANDROID',
                    user_id = ?,
                    user_role = ?,
                    user_email = ?,
                    company_id = ?,
                    active = TRUE,
                    invalidated_at = NULL,
                    last_registered_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, tokenValue, userId, role, normalizedEmail, companyId, deviceId);
    }
}
