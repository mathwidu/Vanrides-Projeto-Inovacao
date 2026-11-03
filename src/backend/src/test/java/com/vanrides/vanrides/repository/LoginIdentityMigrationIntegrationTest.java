package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.service.EmailAddressNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers(disabledWithoutDocker = true)
class LoginIdentityMigrationIntegrationTest {

    private static final String LATEST_SCHEMA_VERSION = "37";

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_migration_test"
    );

    @BeforeEach
    void migrateThroughV24() {
        LoginIdentityPostgresTestSupport.resetTo(postgres, "24");
    }

    @Test
    void shouldBackfillEveryRoleAndInstallTheCompleteInvariant() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            long adminId = LoginIdentityPostgresTestSupport.insertAdmin(
                    connection, companyId, "admin.backfill@example.com");
            long passengerId = LoginIdentityPostgresTestSupport.insertPassenger(
                    connection, companyId, "passenger.backfill@example.com");
            long guardianId = LoginIdentityPostgresTestSupport.insertGuardian(
                    connection, companyId, "guardian.backfill@example.com", null);
            long driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "driver.backfill@example.com");
            LoginIdentityPostgresTestSupport.update(
                    connection,
                    "UPDATE passageiro SET senha = ? WHERE id = ?",
                    LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH.replace("$2a$", "$2y$"),
                    passengerId
            );
            LoginIdentityPostgresTestSupport.update(
                    connection,
                    "UPDATE motorista SET senha = ? WHERE id = ?",
                    LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH.replace("$2a$", "$2b$"),
                    driverId
            );
            LoginIdentityPostgresTestSupport.update(connection, """
                    INSERT INTO device_token (
                        token_hash, token_value, platform, user_id, user_role, user_email,
                        company_id, active, last_registered_at, created_at, updated_at
                    ) VALUES
                        ('device-valid-admin-alias', 'value-valid-admin-alias',
                         'ANDROID', ?, 'ADMIN', '  ADMIN.BACKFILL@EXAMPLE.COM  ', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('device-valid-passenger-alias', 'value-valid-passenger-alias',
                         'ANDROID', ?, 'PASSAGEIRO',
                         '  PASSENGER.BACKFILL@EXAMPLE.COM  ', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('device-valid-guardian-alias', 'value-valid-guardian-alias',
                         'ANDROID', ?, 'RESPONSÁVEL',
                         '  GUARDIAN.BACKFILL@EXAMPLE.COM  ', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('device-valid-driver-alias', 'value-valid-driver-alias',
                         'ANDROID', ?, 'MOTORISTA',
                         '  DRIVER.BACKFILL@EXAMPLE.COM  ', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('device-invalid-role', 'value-invalid-role', 'ANDROID', ?,
                         'UNKNOWN', 'driver.backfill@example.com', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('device-stale-email', 'value-stale-email', 'ANDROID', ?,
                         'DRIVER', 'stale@example.com', ?, TRUE,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    adminId, companyId,
                    passengerId, companyId,
                    guardianId, companyId,
                    driverId, companyId,
                    driverId, companyId,
                    driverId, companyId);

            LoginIdentityPostgresTestSupport.migrateLatest(postgres);

            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT version FROM flyway_schema_history
                    WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo(LATEST_SCHEMA_VERSION);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(
                    connection, "SELECT count(*) FROM login_identity"
            )).isEqualTo(4);
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "admin.backfill@example.com", "COMPANY_ADMIN", adminId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "passenger.backfill@example.com", "PASSENGER", passengerId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "guardian.backfill@example.com", "GUARDIAN", guardianId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "driver.backfill@example.com", "DRIVER", driverId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT senha IS NULL FROM guardian WHERE id = ?
                    """, guardianId)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM pg_constraint
                    WHERE conname IN (
                        'pk_login_identity_email', 'uk_login_identity_owner',
                        'ck_login_identity_role', 'ck_login_identity_user_id_positive',
                        'ck_login_identity_email_canonical',
                        'ck_company_admin_email_canonical',
                        'ck_passageiro_email_canonical',
                        'ck_guardian_email_canonical',
                        'ck_motorista_email_canonical'
                    )
                    """)).isEqualTo(9);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM pg_trigger
                    WHERE NOT tgisinternal AND tgname LIKE 'trg_%_login_%'
                    """)).isEqualTo(8);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*) FROM pg_trigger
                    WHERE NOT tgisinternal
                      AND tgrelid = 'device_token'::regclass
                      AND tgname = 'trg_device_token_owner_guard'
                    """)).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.queryLong(connection, """
                    SELECT count(*)
                    FROM device_token
                    WHERE active = TRUE
                      AND (
                          (token_hash = 'device-valid-admin-alias'
                           AND user_role = 'COMPANY_ADMIN'
                           AND user_email = 'admin.backfill@example.com')
                          OR (token_hash = 'device-valid-passenger-alias'
                              AND user_role = 'PASSENGER'
                              AND user_email = 'passenger.backfill@example.com')
                          OR (token_hash = 'device-valid-guardian-alias'
                              AND user_role = 'GUARDIAN'
                              AND user_email = 'guardian.backfill@example.com')
                          OR (token_hash = 'device-valid-driver-alias'
                              AND user_role = 'DRIVER'
                              AND user_email = 'driver.backfill@example.com')
                      )
                    """)).isEqualTo(4);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active AND invalidated_at IS NOT NULL
                    FROM device_token WHERE token_hash = 'device-invalid-role'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active AND invalidated_at IS NOT NULL
                    FROM device_token WHERE token_hash = 'device-stale-email'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection,
                    "SELECT vr_normalize_login_email('  SQL.PARITY@EXAMPLE.COM  ')"
            )).isEqualTo("sql.parity@example.com");
        }
    }

    @Test
    void shouldWaitForAnInFlightV24WriterBeforeCrossingTheMigrationBoundary()
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch migrationStarted = new CountDownLatch(1);
        long driverId;
        try (Connection writer = LoginIdentityPostgresTestSupport.open(postgres)) {
            writer.setAutoCommit(false);
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(writer);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    writer, companyId, "writer.boundary@example.com");

            Future<Void> migration = executor.submit(() -> {
                migrationStarted.countDown();
                LoginIdentityPostgresTestSupport.migrateLatest(postgres);
                return null;
            });

            assertThat(migrationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitMigrationTableLock();
            assertThatThrownBy(() -> migration.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            writer.commit();
            assertThat(migration.get(15, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection,
                    "writer.boundary@example.com",
                    "DRIVER",
                    driverId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT version FROM flyway_schema_history
                    WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo(LATEST_SCHEMA_VERSION);
        }
    }

    @Test
    void shouldKeepJavaAndPostgresNormalizationEquivalentAcrossTheFrozenMatrix()
            throws Exception {
        LoginIdentityPostgresTestSupport.migrateLatest(postgres);
        EmailAddressNormalizer normalizer = new EmailAddressNormalizer();
        String maximum = "a".repeat(242) + "@example.com";
        String tooLong = "a".repeat(243) + "@example.com";
        record ParityCase(String rawEmail, String expectedCanonical) {
        }
        List<ParityCase> cases = List.of(
                new ParityCase("I@EXAMPLE.COM", "i@example.com"),
                new ParityCase("a@b", "a@b"),
                new ParityCase("  User.Name+Tag@EXAMPLE.COM  ",
                        "user.name+tag@example.com"),
                new ParityCase(maximum, maximum),
                new ParityCase(tooLong, null),
                new ParityCase("a@@b", null),
                new ParityCase("@example.com", null),
                new ParityCase("user@", null),
                new ParityCase("a b@example.com", null),
                new ParityCase("\tuser@example.com", null),
                new ParityCase("\u00a0user@example.com", null),
                new ParityCase("usér@example.com", null)
        );

        Locale previous = Locale.getDefault();
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            for (ParityCase parityCase : cases) {
                Optional<String> javaCanonical = normalizer.tryNormalize(parityCase.rawEmail());
                boolean sqlValid = LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                        SELECT COALESCE(vr_login_email_is_valid(CAST(? AS TEXT)), FALSE)
                        """, parityCase.rawEmail());

                assertThat(javaCanonical.orElse(null))
                        .as("Java canonical for %s", parityCase.rawEmail())
                        .isEqualTo(parityCase.expectedCanonical());
                assertThat(sqlValid)
                        .as("SQL validity for %s", parityCase.rawEmail())
                        .isEqualTo(parityCase.expectedCanonical() != null);
                if (parityCase.expectedCanonical() != null) {
                    assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                            SELECT vr_normalize_login_email(CAST(? AS TEXT))
                            """, parityCase.rawEmail()))
                            .isEqualTo(parityCase.expectedCanonical());
                } else {
                    Throwable sqlFailure = catchThrowable(() ->
                            LoginIdentityPostgresTestSupport.queryString(connection, """
                                    SELECT vr_normalize_login_email(CAST(? AS TEXT))
                                    """, parityCase.rawEmail())
                    );
                    assertThat(LoginIdentityPostgresTestSupport.sqlState(sqlFailure))
                            .isEqualTo("22023");
                    assertThat(LoginIdentityPostgresTestSupport.constraintName(sqlFailure))
                            .isEqualTo("ck_login_email_format");
                }
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void awaitMigrationTableLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (Connection observer = LoginIdentityPostgresTestSupport.open(postgres)) {
            while (System.nanoTime() < deadline) {
                if (LoginIdentityPostgresTestSupport.queryBoolean(observer, """
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_locks
                            WHERE relation = 'motorista'::regclass
                              AND mode = 'ShareRowExclusiveLock'
                              AND NOT granted
                        )
                        """)) {
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(25L);
            }
        }
        throw new AssertionError("V25 did not wait for the in-flight V24 writer");
    }
}
