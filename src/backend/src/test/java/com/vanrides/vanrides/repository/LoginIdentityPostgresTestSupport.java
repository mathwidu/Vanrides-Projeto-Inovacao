package com.vanrides.vanrides.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class LoginIdentityPostgresTestSupport {

    static final String GLOBAL_EMAIL_CONSTRAINT = "pk_login_identity_email";
    static final String TEST_BCRYPT_HASH =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    private LoginIdentityPostgresTestSupport() {
    }

    static PostgreSQLContainer<?> postgres(String databaseName) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(databaseName)
                .withUsername("vanrides_test")
                .withPassword("vanrides_test");
    }

    static void resetTo(PostgreSQLContainer<?> postgres, String version) {
        clean(postgres);
        configuration(postgres).target(version).load().migrate();
    }

    static void resetLatest(PostgreSQLContainer<?> postgres) {
        clean(postgres);
        flyway(postgres).migrate();
    }

    static void migrateLatest(PostgreSQLContainer<?> postgres) {
        flyway(postgres).migrate();
    }

    static void clean(PostgreSQLContainer<?> postgres) {
        // Disposable Testcontainers database only. V26 owns wa outside Flyway's public search path.
        try (Connection connection = open(postgres); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not reset isolated test schema", failure);
        }
        flyway(postgres).clean();
    }

    static Flyway flyway(PostgreSQLContainer<?> postgres) {
        return configuration(postgres).load();
    }

    static Connection open(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    static long defaultCompanyId(Connection connection) throws SQLException {
        return queryLong(connection, "SELECT id FROM company WHERE slug = 'default'");
    }

    static long insertCompany(Connection connection, String suffix) throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO company (nome, slug, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, "Company " + suffix, "company-" + suffix);
    }

    static long insertAccount(
            Connection connection,
            String role,
            long companyId,
            String email,
            boolean guardianWithPassword) throws SQLException {
        return switch (role) {
            case "COMPANY_ADMIN" -> insertAdmin(connection, companyId, email);
            case "PASSENGER" -> insertPassenger(connection, companyId, email);
            case "GUARDIAN" -> insertGuardian(
                    connection,
                    companyId,
                    email,
                    guardianWithPassword ? TEST_BCRYPT_HASH : null
            );
            case "DRIVER" -> insertDriver(connection, companyId, email);
            default -> throw new IllegalArgumentException("Unsupported role: " + role);
        };
    }

    static long insertDriver(Connection connection, long companyId, String email)
            throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO motorista (nome, email, telefone, senha, company_id)
                VALUES ('Driver IT', ?, '51000000001', ?, ?)
                RETURNING id
                """, email, TEST_BCRYPT_HASH, companyId);
    }

    static long insertPassenger(Connection connection, long companyId, String email)
            throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO passageiro (
                    nome, email, telefone, senha, endereco, bairro, cidade, estado, cep,
                    company_id
                ) VALUES (
                    'Passenger IT', ?, '51000000002', ?, 'Rua IT', 'Centro',
                    'Porto Alegre', 'RS', '90000-000', ?
                )
                RETURNING id
                """, email, TEST_BCRYPT_HASH, companyId);
    }

    static long insertGuardian(
            Connection connection,
            long companyId,
            String email,
            String password) throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO guardian (
                    company_id, nome, email, telefone, senha, created_at, updated_at
                ) VALUES (
                    ?, 'Guardian IT', ?, '51000000003', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """, companyId, email, password);
    }

    static long insertAdmin(Connection connection, long companyId, String email)
            throws SQLException {
        return insertReturningId(connection, """
                INSERT INTO company_admin (
                    company_id, nome, email, telefone, senha, created_at, updated_at
                ) VALUES (
                    ?, 'Admin IT', ?, '51000000004', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """, companyId, email, TEST_BCRYPT_HASH);
    }

    static void seedArtifacts(
            Connection connection,
            String role,
            long userId,
            long companyId,
            String email,
            String suffix) throws SQLException {
        update(connection, """
                INSERT INTO refresh_token (
                    token_hash, user_id, user_role, user_email, company_id, expires_at,
                    revoked, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "refresh-" + suffix, userId, role, email, companyId);
        update(connection, """
                INSERT INTO email_verification_token (
                    user_role, user_id, email_snapshot, token_hash, expires_at, created_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', CURRENT_TIMESTAMP)
                """, role, userId, email, "verification-" + suffix);
        update(connection, """
                INSERT INTO device_token (
                    token_hash, token_value, platform, user_id, user_role, user_email,
                    company_id, active, last_registered_at, created_at, updated_at
                ) VALUES (?, ?, 'ANDROID', ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "device-" + suffix, "value-" + suffix, userId, role, email, companyId);
    }

    static int update(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    static long queryLong(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Expected one result for query");
                }
                return resultSet.getLong(1);
            }
        }
    }

    static String queryString(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Expected one result for query");
                }
                return resultSet.getString(1);
            }
        }
    }

    static boolean queryBoolean(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Expected one result for query");
                }
                return resultSet.getBoolean(1);
            }
        }
    }

    static long sourceCountByEmail(Connection connection, String email) throws SQLException {
        return queryLong(connection, """
                SELECT count(*) FROM (
                    SELECT email FROM company_admin
                    UNION ALL SELECT email FROM passageiro
                    UNION ALL SELECT email FROM guardian
                    UNION ALL SELECT email FROM motorista
                ) accounts WHERE email = ?
                """, email);
    }

    static boolean ownerExists(
            Connection connection,
            String email,
            String role,
            long userId) throws SQLException {
        return queryBoolean(connection, """
                SELECT EXISTS (
                    SELECT 1 FROM login_identity
                    WHERE normalized_email = ? AND user_role = ? AND user_id = ?
                )
                """, email, role, userId);
    }

    static String constraintName(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PSQLException postgresFailure
                    && postgresFailure.getServerErrorMessage() != null) {
                String constraint = postgresFailure.getServerErrorMessage().getConstraint();
                if (constraint != null) {
                    return constraint;
                }
            }
        }
        return null;
    }

    static String sqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlFailure && sqlFailure.getSQLState() != null) {
                return sqlFailure.getSQLState();
            }
        }
        return null;
    }

    static String allMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }

    private static FluentConfiguration configuration(PostgreSQLContainer<?> postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .locations("classpath:db/migration");
    }

    private static long insertReturningId(
            Connection connection,
            String sql,
            Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Insert did not return an id");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters)
            throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
