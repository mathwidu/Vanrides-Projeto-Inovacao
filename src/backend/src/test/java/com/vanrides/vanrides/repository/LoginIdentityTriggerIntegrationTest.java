package com.vanrides.vanrides.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers(disabledWithoutDocker = true)
class LoginIdentityTriggerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_trigger_test"
    );

    @BeforeEach
    void migrateCleanDatabase() {
        LoginIdentityPostgresTestSupport.resetLatest(postgres);
    }

    @Test
    void shouldNormalizeLegacyWritersAndReserveEveryRole() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            long adminId = LoginIdentityPostgresTestSupport.insertAdmin(
                    connection, companyId, "  ADMIN.DIRECT@EXAMPLE.COM  ");
            long passengerId = LoginIdentityPostgresTestSupport.insertPassenger(
                    connection, companyId, "  PASSENGER.DIRECT@EXAMPLE.COM  ");
            long guardianId = LoginIdentityPostgresTestSupport.insertGuardian(
                    connection, companyId, "  GUARDIAN.DIRECT@EXAMPLE.COM  ", null);
            long driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "  DRIVER.DIRECT@EXAMPLE.COM  ");

            assertThat(LoginIdentityPostgresTestSupport.queryLong(
                    connection, "SELECT count(*) FROM login_identity"
            )).isEqualTo(4);
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM company_admin WHERE id = ?", adminId
            )).isEqualTo("admin.direct@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM passageiro WHERE id = ?", passengerId
            )).isEqualTo("passenger.direct@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM guardian WHERE id = ?", guardianId
            )).isEqualTo("guardian.direct@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM motorista WHERE id = ?", driverId
            )).isEqualTo("driver.direct@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection, "SELECT senha IS NULL FROM guardian WHERE id = ?", guardianId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "admin.direct@example.com", "COMPANY_ADMIN", adminId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "passenger.direct@example.com", "PASSENGER", passengerId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "guardian.direct@example.com", "GUARDIAN", guardianId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "driver.direct@example.com", "DRIVER", driverId
            )).isTrue();
        }
    }

    @Test
    void shouldRejectInvalidAndSequentialCrossRoleWritesWithoutOrphans() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);

            Throwable invalid = catchThrowable(() -> LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "invalid email@example.com"
            ));
            assertThat(invalid).isInstanceOf(SQLException.class);
            assertThat(LoginIdentityPostgresTestSupport.sqlState(invalid)).isEqualTo("22023");
            assertThat(LoginIdentityPostgresTestSupport.constraintName(invalid))
                    .isEqualTo("ck_login_email_format");
            assertThat(LoginIdentityPostgresTestSupport.queryLong(
                    connection, "SELECT count(*) FROM login_identity"
            )).isZero();

            long driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "taken.direct@example.com");
            Throwable collision = catchThrowable(
                    () -> LoginIdentityPostgresTestSupport.insertGuardian(
                            connection, companyId, "TAKEN.DIRECT@EXAMPLE.COM", null
                    )
            );

            assertThat(LoginIdentityPostgresTestSupport.sqlState(collision)).isEqualTo("23505");
            assertThat(LoginIdentityPostgresTestSupport.constraintName(collision))
                    .isEqualTo(LoginIdentityPostgresTestSupport.GLOBAL_EMAIL_CONSTRAINT);
            assertThat(LoginIdentityPostgresTestSupport.sourceCountByEmail(
                    connection, "taken.direct@example.com"
            )).isEqualTo(1);
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "taken.direct@example.com", "DRIVER", driverId
            )).isTrue();
        }
    }

    @Test
    void shouldMoveIdentityAndInvalidateEveryOldArtifactOnEmailChange() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            long driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "driver.before@example.com");

            LoginIdentityPostgresTestSupport.update(connection, """
                    UPDATE motorista
                    SET email_verified = TRUE,
                        email_verified_at = CURRENT_TIMESTAMP,
                        email_verification_last_sent_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, driverId);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection, "SELECT email_verified FROM motorista WHERE id = ?", driverId
            )).isTrue();

            LoginIdentityPostgresTestSupport.seedArtifacts(
                    connection,
                    "DRIVER",
                    driverId,
                    companyId,
                    "driver.before@example.com",
                    "email-change"
            );
            LoginIdentityPostgresTestSupport.update(
                    connection,
                    "UPDATE motorista SET email = ? WHERE id = ?",
                    "  DRIVER.AFTER@EXAMPLE.COM  ",
                    driverId
            );

            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM motorista WHERE id = ?", driverId
            )).isEqualTo("driver.after@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT email_verified
                           AND email_verified_at IS NULL
                           AND email_verification_last_sent_at IS NULL
                    FROM motorista WHERE id = ?
                    """, driverId)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "driver.before@example.com", "DRIVER", driverId
            )).isFalse();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "driver.after@example.com", "DRIVER", driverId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection,
                    "SELECT revoked FROM refresh_token WHERE token_hash = 'refresh-email-change'"
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT consumed_at IS NOT NULL FROM email_verification_token
                    WHERE token_hash = 'verification-email-change'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT active
                           AND invalidated_at IS NOT NULL
                           AND user_email = 'driver.after@example.com'
                    FROM device_token WHERE token_hash = 'device-email-change'
                    """)).isTrue();
        }
    }

    @Test
    void shouldRollbackInsertUpdateAndConflictWithoutDamagingOldOwner() throws Exception {
        long companyId;
        long driverId;
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "rollback.driver@example.com");
            LoginIdentityPostgresTestSupport.seedArtifacts(
                    connection, "DRIVER", driverId, companyId,
                    "rollback.driver@example.com", "rollback-driver"
            );
            LoginIdentityPostgresTestSupport.insertPassenger(
                    connection, companyId, "occupied.target@example.com");
        }

        try (Connection transaction = LoginIdentityPostgresTestSupport.open(postgres)) {
            transaction.setAutoCommit(false);
            long guardianId = LoginIdentityPostgresTestSupport.insertGuardian(
                    transaction, companyId, "rollback.guardian@example.com", null);
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    transaction, "rollback.guardian@example.com", "GUARDIAN", guardianId
            )).isTrue();
            transaction.rollback();
        }

        try (Connection transaction = LoginIdentityPostgresTestSupport.open(postgres)) {
            transaction.setAutoCommit(false);
            LoginIdentityPostgresTestSupport.update(
                    transaction,
                    "UPDATE motorista SET email = 'temporary.driver@example.com' WHERE id = ?",
                    driverId
            );
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    transaction, "temporary.driver@example.com", "DRIVER", driverId
            )).isTrue();
            transaction.rollback();
        }

        Throwable conflict;
        try (Connection transaction = LoginIdentityPostgresTestSupport.open(postgres)) {
            transaction.setAutoCommit(false);
            conflict = catchThrowable(() -> LoginIdentityPostgresTestSupport.update(
                    transaction,
                    "UPDATE motorista SET email = 'occupied.target@example.com' WHERE id = ?",
                    driverId
            ));
            transaction.rollback();
        }

        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            assertThat(LoginIdentityPostgresTestSupport.constraintName(conflict))
                    .isEqualTo(LoginIdentityPostgresTestSupport.GLOBAL_EMAIL_CONSTRAINT);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT EXISTS (
                        SELECT 1 FROM login_identity
                        WHERE normalized_email = 'rollback.guardian@example.com'
                    ) AND NOT EXISTS (
                        SELECT 1 FROM guardian
                        WHERE email = 'rollback.guardian@example.com'
                    )
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryString(
                    connection, "SELECT email FROM motorista WHERE id = ?", driverId
            )).isEqualTo("rollback.driver@example.com");
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "rollback.driver@example.com", "DRIVER", driverId
            )).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.ownerExists(
                    connection, "temporary.driver@example.com", "DRIVER", driverId
            )).isFalse();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT NOT revoked FROM refresh_token
                    WHERE token_hash = 'refresh-rollback-driver'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT consumed_at IS NULL FROM email_verification_token
                    WHERE token_hash = 'verification-rollback-driver'
                    """)).isTrue();
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                    SELECT active FROM device_token
                    WHERE token_hash = 'device-rollback-driver'
                    """)).isTrue();
        }
    }
}
