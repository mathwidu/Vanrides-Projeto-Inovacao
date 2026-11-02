package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.SqlOperation;
import com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.SqlResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.assertBlocked;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.execute;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class LoginIdentityConcurrencyIntegrationTest {
    private static final long START_TIMEOUT_SECONDS = 5;
    private static final long RESULT_TIMEOUT_SECONDS = 15;
    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_concurrency_test"
    );
    @BeforeEach
    void migrateCleanDatabase() {
        LoginIdentityPostgresTestSupport.resetLatest(postgres);
    }
    @Test
    void shouldAllowExactlyOneAdminOrPassengerToOwnTheSameEmail() throws Exception {
        long companyId;
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
        }

        List<OperationResult> results = race(
                new RaceTask("admin", connection -> LoginIdentityPostgresTestSupport.insertAdmin(
                        connection, companyId, "race.admin-passenger@example.com")),
                new RaceTask("passenger", connection -> LoginIdentityPostgresTestSupport.insertPassenger(
                        connection, companyId, "RACE.ADMIN-PASSENGER@EXAMPLE.COM"))
        );

        assertGlobalRaceResult(results);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.sourceCountByEmail(
                    connection, "race.admin-passenger@example.com"
            )).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM login_identity
                    WHERE normalized_email = 'race.admin-passenger@example.com'
                    """)).isEqualTo(1);
        }
    }

    @Test
    void shouldAllowExactlyOneDriverOrPasswordlessGuardianToOwnTheSameEmail()
            throws Exception {
        long companyId;
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
        }

        List<OperationResult> results = race(
                new RaceTask("driver", connection -> LoginIdentityPostgresTestSupport.insertDriver(
                        connection, companyId, "race.driver-guardian@example.com")),
                new RaceTask("guardian", connection -> LoginIdentityPostgresTestSupport.insertGuardian(
                        connection, companyId, "RACE.DRIVER-GUARDIAN@EXAMPLE.COM", null))
        );

        assertGlobalRaceResult(results);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.sourceCountByEmail(
                    connection, "race.driver-guardian@example.com"
            )).isEqualTo(1);
            long guardianRows = LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM guardian
                    WHERE email = 'race.driver-guardian@example.com' AND senha IS NULL
                    """);
            boolean guardianWon = results.stream()
                    .anyMatch(result -> result.name().equals("guardian") && result.committed());
            assertThat(guardianRows).isEqualTo(guardianWon ? 1 : 0);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM login_identity
                    WHERE normalized_email = 'race.driver-guardian@example.com'
                    """)).isEqualTo(1);
        }
    }

    @Test
    void shouldSerializeEmailChangeAgainstConcurrentAccountCreation() throws Exception {
        long companyId;
        long driverId;
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "race.driver-old@example.com");
        }

        List<OperationResult> results = race(
                new RaceTask("update", connection -> LoginIdentityPostgresTestSupport.update(
                        connection,
                        "UPDATE motorista SET email = 'race.target@example.com' WHERE id = ?",
                        driverId
                )),
                new RaceTask("create", connection -> LoginIdentityPostgresTestSupport.insertPassenger(
                        connection, companyId, "RACE.TARGET@EXAMPLE.COM"))
        );

        assertGlobalRaceResult(results);
        boolean updateWon = results.stream()
                .anyMatch(result -> result.name().equals("update") && result.committed());
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.sourceCountByEmail(
                    connection, "race.target@example.com"
            )).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM login_identity
                    WHERE normalized_email = 'race.target@example.com'
                    """)).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM motorista WHERE id = ?", driverId
            )).isEqualTo(updateWon
                    ? "race.target@example.com"
                    : "race.driver-old@example.com");
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "race.driver-old@example.com", "DRIVER", driverId
            )).isEqualTo(!updateWon);
        }
    }

    @Test
    void shouldReleaseDeletedEmailOnlyAfterCommitAndRestoreEverythingOnRollback()
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            verifyDeleteRollbackKeepsOwner(executor);
            verifyDeleteCommitReleasesOwner(executor);
        } finally {
            shutdown(executor);
        }
    }

    private void verifyDeleteRollbackKeepsOwner(ExecutorService executor) throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "delete.rollback@example.com");
            LoginIdentityPostgresTestSupport.seedArtifacts(
                    setup, "DRIVER", driverId, companyId,
                    "delete.rollback@example.com", "delete-rollback"
            );
        }

        CountDownLatch ready = new CountDownLatch(1);
        OperationResult creatorResult;
        try (Connection deletion = LoginIdentityPostgresTestSupport.open(postgres)) {
            deletion.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    deletion, "DELETE FROM motorista WHERE id = ?", driverId
            );
            Future<OperationResult> creator = executor.submit(() -> insertPassenger(
                    companyId, "delete.rollback@example.com", ready
            ));
            assertThat(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creator.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            deletion.rollback();
            creatorResult = creator.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(creatorResult.committed()).isFalse();
        assertThat(creatorResult.constraint())
                .isEqualTo(LoginIdentityPostgresTestSupport.GLOBAL_EMAIL_CONSTRAINT);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "delete.rollback@example.com", "DRIVER", driverId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT revoked FROM refresh_token
                    WHERE token_hash = 'refresh-delete-rollback'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT consumed_at IS NULL FROM email_verification_token
                    WHERE token_hash = 'verification-delete-rollback'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT active FROM device_token
                    WHERE token_hash = 'device-delete-rollback'
                    """)).isTrue();
        }
    }

    private void verifyDeleteCommitReleasesOwner(ExecutorService executor) throws Exception {
        long companyId;
        long driverId;
        try (Connection setup = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(setup);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    setup, companyId, "delete.commit@example.com");
            LoginIdentityPostgresTestSupport.seedArtifacts(
                    setup, "DRIVER", driverId, companyId,
                    "delete.commit@example.com", "delete-commit"
            );
        }

        CountDownLatch ready = new CountDownLatch(1);
        OperationResult creatorResult;
        try (Connection deletion = LoginIdentityPostgresTestSupport.open(postgres)) {
            deletion.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    deletion, "DELETE FROM motorista WHERE id = ?", driverId
            );
            Future<OperationResult> creator = executor.submit(() -> insertPassenger(
                    companyId, "delete.commit@example.com", ready
            ));
            assertThat(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creator.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            deletion.commit();
            creatorResult = creator.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(creatorResult.committed()).isTrue();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM login_identity
                    WHERE normalized_email = 'delete.commit@example.com'
                      AND user_role = 'PASSENGER'
                    """)).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT revoked FROM refresh_token
                    WHERE token_hash = 'refresh-delete-commit'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT consumed_at IS NOT NULL FROM email_verification_token
                    WHERE token_hash = 'verification-delete-commit'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active AND user_email IS NULL FROM device_token
                    WHERE token_hash = 'device-delete-commit'
                    """)).isTrue();
        }
    }

    private List<OperationResult> race(RaceTask first, RaceTask second) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> contenderPid = new CompletableFuture<>();
        try (Connection firstConnection = LoginIdentityPostgresTestSupport.open(postgres)) {
            firstConnection.setAutoCommit(false);
            first.operation().execute(firstConnection);

            Future<OperationResult> secondResult = executor.submit(() -> {
                SqlResult result = execute(postgres, contenderPid, second.operation());
                return new OperationResult(
                        second.name(),
                        result.committed(),
                        result.sqlState(),
                        result.constraint()
                );
            });
            assertBlocked(postgres, contenderPid, secondResult);

            firstConnection.commit();
            return List.of(
                    new OperationResult(first.name(), true, null, null),
                    secondResult.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
        } finally {
            shutdown(executor);
        }
    }

    private OperationResult insertPassenger(long companyId, String email, CountDownLatch ready)
            throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            connection.setAutoCommit(false);
            ready.countDown();
            try {
                LoginIdentityPostgresTestSupport.insertPassenger(connection, companyId, email);
                connection.commit();
                return new OperationResult("creator", true, null, null);
            } catch (SQLException failure) {
                connection.rollback();
                return new OperationResult(
                        "creator",
                        false,
                        LoginIdentityPostgresTestSupport.sqlState(failure),
                        LoginIdentityPostgresTestSupport.constraintName(failure)
                );
            }
        }
    }

    private void assertGlobalRaceResult(List<OperationResult> results) {
        assertThat(results).filteredOn(OperationResult::committed).hasSize(1);
        assertThat(results).filteredOn(result -> !result.committed())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.sqlState()).isEqualTo("23505");
                    assertThat(result.constraint())
                            .isEqualTo(LoginIdentityPostgresTestSupport.GLOBAL_EMAIL_CONSTRAINT);
                });
    }

    private record RaceTask(String name, SqlOperation operation) {
    }

    private record OperationResult(
            String name,
            boolean committed,
            String sqlState,
            String constraint) {
    }
}
