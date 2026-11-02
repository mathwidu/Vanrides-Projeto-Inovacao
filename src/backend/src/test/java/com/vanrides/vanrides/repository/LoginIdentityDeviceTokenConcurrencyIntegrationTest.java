package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.SqlResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.assertBlocked;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.execute;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.insertDevice;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LoginIdentityDeviceTokenConcurrencyIntegrationTest {

    private static final String DEVICE_OWNER_CONSTRAINT =
            "ck_device_token_login_identity_owner";
    private static final long RESULT_TIMEOUT_SECONDS = 15;

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_device_concurrency_test"
    );

    @BeforeEach
    void migrateCleanDatabase() {
        LoginIdentityPostgresTestSupport.resetLatest(postgres);
    }

    @Test
    void shouldInvalidateAliasArtifactsThatCommitBeforeTheEmailChange() throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "artifact.first@example.com");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> accountWriterPid = new CompletableFuture<>();
        SqlResult accountResult;
        try (Connection artifactWriter = LoginIdentityPostgresTestSupport.open(postgres)) {
            artifactWriter.setAutoCommit(false);
            insertDevice(
                    artifactWriter,
                    "device-artifact-first",
                    "MOTORISTA",
                    driverId,
                    "  ARTIFACT.FIRST@EXAMPLE.COM  ",
                    companyId,
                    true
            );
            LoginIdentityPostgresTestSupport.update(artifactWriter, """
                    INSERT INTO refresh_token (
                        token_hash, user_id, user_role, user_email, company_id,
                        expires_at, revoked, created_at, updated_at
                    ) VALUES (
                        'refresh-artifact-first', ?, 'MOTORISTA',
                        'artifact.first@example.com', ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, driverId, companyId);
            LoginIdentityPostgresTestSupport.update(artifactWriter, """
                    INSERT INTO email_verification_token (
                        user_role, user_id, email_snapshot, token_hash, expires_at, created_at
                    ) VALUES (
                        'MOTORISTA', ?, 'artifact.first@example.com',
                        'verification-artifact-first', CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP
                    )
                    """, driverId);

            Future<SqlResult> accountWriter = executor.submit(() -> execute(
                    postgres,
                    accountWriterPid,
                    connection -> LoginIdentityPostgresTestSupport.update(
                            connection,
                            "UPDATE motorista SET email = ? WHERE id = ?",
                            "artifact.after@example.com",
                            driverId
                    )
            ));
            assertBlocked(postgres, accountWriterPid, accountWriter);

            artifactWriter.commit();
            accountResult = accountWriter.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            shutdown(executor);
        }

        assertThat(accountResult.committed()).isTrue();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active
                           AND invalidated_at IS NOT NULL
                           AND user_role = 'DRIVER'
                           AND user_email = 'artifact.after@example.com'
                    FROM device_token WHERE token_hash = 'device-artifact-first'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT revoked FROM refresh_token
                    WHERE token_hash = 'refresh-artifact-first'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT consumed_at IS NOT NULL FROM email_verification_token
                    WHERE token_hash = 'verification-artifact-first'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT vr_login_identity_role_canonical(CAST(? AS TEXT))
                    """, "\tRESPONSA\u0301VEL\r\n")).isEqualTo("GUARDIAN");
            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT vr_login_identity_role_canonical(CAST(? AS TEXT))
                    """, "\t MOTORISTA \n")).isEqualTo("DRIVER");
            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT vr_login_identity_role_canonical(CAST(? AS TEXT))
                    """, "RESPONSA\u0304VEL")).isNull();
        }
    }

    @Test
    void shouldRejectAStaleDeviceThatWaitedForEmailChangeCommit() throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "account.first@example.com");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> deviceWriterPid = new CompletableFuture<>();
        SqlResult deviceResult;
        try (Connection accountWriter = LoginIdentityPostgresTestSupport.open(postgres)) {
            accountWriter.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    accountWriter,
                    "UPDATE motorista SET email = ? WHERE id = ?",
                    "account.after@example.com",
                    driverId
            );

            Future<SqlResult> deviceWriter = executor.submit(() -> execute(
                    postgres,
                    deviceWriterPid,
                    connection -> insertDevice(
                            connection,
                            "device-account-first",
                            "MOTORISTA",
                            driverId,
                            "account.first@example.com",
                            companyId,
                            true
                    )
            ));
            assertBlocked(postgres, deviceWriterPid, deviceWriter);

            accountWriter.commit();
            deviceResult = deviceWriter.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            shutdown(executor);
        }

        assertGuardRejected(deviceResult);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM device_token
                    WHERE token_hash = 'device-account-first' AND active = TRUE
                    """)).isZero();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "account.after@example.com", "DRIVER", driverId
            )).isTrue();
        }
    }

    @Test
    void shouldAllowTheWaitingDeviceWhenTheEmailChangeRollsBack() throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "account.rollback@example.com");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> deviceWriterPid = new CompletableFuture<>();
        SqlResult deviceResult;
        try (Connection accountWriter = LoginIdentityPostgresTestSupport.open(postgres)) {
            accountWriter.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    accountWriter,
                    "UPDATE motorista SET email = ? WHERE id = ?",
                    "temporary@example.com",
                    driverId
            );

            Future<SqlResult> deviceWriter = executor.submit(() -> execute(
                    postgres,
                    deviceWriterPid,
                    connection -> insertDevice(
                            connection,
                            "device-account-rollback",
                            "MOTORISTA",
                            driverId,
                            "account.rollback@example.com",
                            companyId,
                            true
                    )
            ));
            assertBlocked(postgres, deviceWriterPid, deviceWriter);

            accountWriter.rollback();
            deviceResult = deviceWriter.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            shutdown(executor);
        }

        assertThat(deviceResult.committed()).isTrue();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT active
                           AND user_role = 'DRIVER'
                           AND user_email = 'account.rollback@example.com'
                    FROM device_token WHERE token_hash = 'device-account-rollback'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "account.rollback@example.com", "DRIVER", driverId
            )).isTrue();
        }
    }

    @Test
    void shouldRejectReactivationAfterDeleteWithoutLeavingAnActiveOrphan()
            throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "reactivation.delete@example.com");
            insertDevice(
                    setup,
                    "device-reactivation-delete",
                    "DRIVER",
                    driverId,
                    "reactivation.delete@example.com",
                    companyId,
                    false
            );
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> reactivationPid = new CompletableFuture<>();
        SqlResult reactivationResult;
        try (Connection deletion = LoginIdentityPostgresTestSupport.open(postgres)) {
            deletion.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    deletion, "DELETE FROM motorista WHERE id = ?", driverId
            );

            Future<SqlResult> reactivation = executor.submit(() -> execute(
                    postgres,
                    reactivationPid,
                    connection -> LoginIdentityPostgresTestSupport.update(connection, """
                            UPDATE device_token
                            SET active = TRUE,
                                invalidated_at = NULL,
                                user_email = 'reactivation.delete@example.com'
                            WHERE token_hash = 'device-reactivation-delete'
                            """)
            ));
            assertBlocked(postgres, reactivationPid, reactivation);

            deletion.commit();
            reactivationResult = reactivation.get(
                    RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS
            );
        } finally {
            shutdown(executor);
        }

        assertGuardRejected(reactivationResult);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active AND user_email IS NULL
                    FROM device_token WHERE token_hash = 'device-reactivation-delete'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*)
                    FROM device_token device
                    LEFT JOIN login_identity registry
                      ON registry.user_role = device.user_role
                     AND registry.user_id = device.user_id
                     AND registry.normalized_email = device.user_email
                    WHERE device.active = TRUE
                      AND registry.normalized_email IS NULL
                    """)).isZero();
        }
    }

    private void assertGuardRejected(SqlResult result) {
        assertThat(result.committed()).isFalse();
        assertThat(result.sqlState()).isEqualTo("23514");
        assertThat(result.constraint()).isEqualTo(DEVICE_OWNER_CONSTRAINT);
    }

}
