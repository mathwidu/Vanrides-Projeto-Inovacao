package com.vanrides.vanrides.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers(disabledWithoutDocker = true)
class LoginIdentityMigrationPreflightIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_identity_migration_preflight_test"
    );

    @BeforeEach
    void migrateThroughV24() {
        LoginIdentityPostgresTestSupport.resetTo(postgres, "24");
    }

    @Test
    void shouldAbortInvalidOrNonCanonicalLegacyDataWithoutPartialDdl() throws Exception {
        record LegacyCase(String rawEmail, String expectedCounts) {
        }
        List<LegacyCase> cases = List.of(
                new LegacyCase(
                        "invalid email@example.com",
                        "invalid=1 noncanonical=0"
                ),
                new LegacyCase(
                        "UPPERCASE@example.com",
                        "invalid=0 noncanonical=1"
                ),
                new LegacyCase(
                        " trim@example.com ",
                        "invalid=0 noncanonical=1"
                )
        );

        for (LegacyCase legacyCase : cases) {
            LoginIdentityPostgresTestSupport.resetTo(postgres, "24");
            try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
                long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
                LoginIdentityPostgresTestSupport.insertDriver(
                        connection, companyId, legacyCase.rawEmail());

                Throwable failure = catchThrowable(
                        () -> LoginIdentityPostgresTestSupport.migrateLatest(postgres)
                );
                String messages = LoginIdentityPostgresTestSupport.allMessages(failure);

                assertThat(failure).isNotNull();
                assertThat(LoginIdentityPostgresTestSupport.sqlState(failure)).isEqualTo("23514");
                assertThat(LoginIdentityPostgresTestSupport.constraintName(failure))
                        .isEqualTo("ck_v25_login_email_preflight");
                assertThat(messages).contains(legacyCase.expectedCounts());
                assertThat(messages).doesNotContain(legacyCase.rawEmail());
                assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                        SELECT version FROM flyway_schema_history
                        WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1
                        """)).isEqualTo("24");
                assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                        connection, "SELECT to_regclass('login_identity') IS NULL"
                )).isTrue();
                assertThat(LoginIdentityPostgresTestSupport.queryBoolean(connection, """
                        SELECT to_regprocedure('vr_normalize_login_email(text)') IS NULL
                        """)).isTrue();
            }
        }
    }

    @Test
    void shouldAbortUnsupportedLegacyPasswordHashWithoutLeakingIt() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            long driverId = LoginIdentityPostgresTestSupport.insertDriver(
                    connection,
                    companyId,
                    "legacy.password@example.com"
            );
            String unsupportedHash = "$2a$10$" + "A".repeat(53);
            LoginIdentityPostgresTestSupport.update(
                    connection,
                    "UPDATE motorista SET senha = ? WHERE id = ?",
                    unsupportedHash,
                    driverId
            );

            Throwable failure = catchThrowable(
                    () -> LoginIdentityPostgresTestSupport.migrateLatest(postgres)
            );
            String messages = LoginIdentityPostgresTestSupport.allMessages(failure);

            assertThat(failure).isNotNull();
            assertThat(LoginIdentityPostgresTestSupport.sqlState(failure)).isEqualTo("23514");
            assertThat(LoginIdentityPostgresTestSupport.constraintName(failure))
                    .isEqualTo("ck_v25_login_email_preflight");
            assertThat(messages).contains("invalid_password_hashes=1");
            assertThat(messages).doesNotContain(unsupportedHash);
            assertThat(LoginIdentityPostgresTestSupport.queryString(connection, """
                    SELECT version FROM flyway_schema_history
                    WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("24");
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection,
                    "SELECT to_regclass('login_identity') IS NULL"
            )).isTrue();
        }
    }

    @Test
    void shouldAbortNormalizedIntraRoleCollisionAtomically() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            LoginIdentityPostgresTestSupport.insertPassenger(
                    connection, companyId, "Duplicate.Passenger@example.com");
            LoginIdentityPostgresTestSupport.insertPassenger(
                    connection, companyId, "duplicate.passenger@example.com");

            Throwable failure = catchThrowable(
                    () -> LoginIdentityPostgresTestSupport.migrateLatest(postgres)
            );
            String messages = LoginIdentityPostgresTestSupport.allMessages(failure);

            assertThat(LoginIdentityPostgresTestSupport.sqlState(failure)).isEqualTo("23514");
            assertThat(LoginIdentityPostgresTestSupport.constraintName(failure))
                    .isEqualTo("ck_v25_login_email_preflight");
            assertThat(messages).contains(
                    "intra_role_collision_groups=1 cross_role_collision_groups=0"
            );
            assertThat(messages)
                    .doesNotContain("Duplicate.Passenger@example.com")
                    .doesNotContain("duplicate.passenger@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection, "SELECT to_regclass('login_identity') IS NULL"
            )).isTrue();
        }
    }

    @Test
    void shouldAbortCrossRoleCollisionIncludingPasswordlessGuardian() throws Exception {
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            LoginIdentityPostgresTestSupport.insertDriver(
                    connection, companyId, "Shared.Legacy@example.com");
            long guardianId = LoginIdentityPostgresTestSupport.insertGuardian(
                    connection, companyId, "shared.legacy@example.com", null);
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection, "SELECT senha IS NULL FROM guardian WHERE id = ?", guardianId
            )).isTrue();

            Throwable failure = catchThrowable(
                    () -> LoginIdentityPostgresTestSupport.migrateLatest(postgres)
            );
            String messages = LoginIdentityPostgresTestSupport.allMessages(failure);

            assertThat(LoginIdentityPostgresTestSupport.sqlState(failure)).isEqualTo("23514");
            assertThat(LoginIdentityPostgresTestSupport.constraintName(failure))
                    .isEqualTo("ck_v25_login_email_preflight");
            assertThat(messages).contains(
                    "intra_role_collision_groups=0 cross_role_collision_groups=1"
            );
            assertThat(messages)
                    .doesNotContain("Shared.Legacy@example.com")
                    .doesNotContain("shared.legacy@example.com");
            assertThat(LoginIdentityPostgresTestSupport.queryBoolean(
                    connection, "SELECT to_regclass('login_identity') IS NULL"
            )).isTrue();
        }
    }
}
