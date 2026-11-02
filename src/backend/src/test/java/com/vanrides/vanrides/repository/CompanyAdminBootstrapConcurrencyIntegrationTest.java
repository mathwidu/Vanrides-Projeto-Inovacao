package com.vanrides.vanrides.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CompanyAdminBootstrapConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_admin_race_test")
            .withUsername("vanrides_test")
            .withPassword("vanrides_test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Test
    void shouldPersistExactlyOneFirstAdminUnderConcurrentInsert() throws Exception {
        long companyId = insertCompanyForAdminRace();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<AdminInsertResult> first = executor.submit(
                    () -> insertAdminConcurrently(companyId, "first.admin.race@vanrides.dev", ready, start));
            Future<AdminInsertResult> second = executor.submit(
                    () -> insertAdminConcurrently(companyId, "second.admin.race@vanrides.dev", ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<AdminInsertResult> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            assertThat(results).filteredOn(AdminInsertResult::inserted).hasSize(1);
            assertThat(results).filteredOn(result -> !result.inserted())
                    .singleElement()
                    .extracting(AdminInsertResult::sqlState)
                    .isEqualTo("23505");
            assertThat(countAdminsForCompany(companyId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(5).toSeconds(), TimeUnit.SECONDS)).isTrue();
            deleteAdminRaceFixture(companyId);
        }
    }

    private long insertCompanyForAdminRace() throws SQLException {
        try (Connection connection = openPostgresConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO company (nome, slug, created_at, updated_at)
                     VALUES ('Empresa Admin Race', 'empresa-admin-race', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     RETURNING id
                     """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private AdminInsertResult insertAdminConcurrently(
            long companyId,
            String email,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try (Connection connection = openPostgresConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO company_admin
                         (company_id, nome, email, telefone, senha, created_at, updated_at)
                     VALUES (?, 'Admin Race', ?, '51999999999', '$2a$12$integration.hash',
                             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     """)) {
            connection.setAutoCommit(false);
            statement.setLong(1, companyId);
            statement.setString(2, email);
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                connection.rollback();
                return new AdminInsertResult(false, "START_TIMEOUT");
            }
            try {
                statement.executeUpdate();
                connection.commit();
                return new AdminInsertResult(true, null);
            } catch (SQLException exception) {
                connection.rollback();
                return new AdminInsertResult(false, exception.getSQLState());
            }
        }
    }

    private long countAdminsForCompany(long companyId) throws SQLException {
        try (Connection connection = openPostgresConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM company_admin WHERE company_id = ?")) {
            statement.setLong(1, companyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private void deleteAdminRaceFixture(long companyId) throws SQLException {
        try (Connection connection = openPostgresConnection();
             PreparedStatement deleteAdmins = connection.prepareStatement(
                     "DELETE FROM company_admin WHERE company_id = ?");
             PreparedStatement deleteCompany = connection.prepareStatement(
                     "DELETE FROM company WHERE id = ?")) {
            deleteAdmins.setLong(1, companyId);
            deleteAdmins.executeUpdate();
            deleteCompany.setLong(1, companyId);
            deleteCompany.executeUpdate();
        }
    }

    private Connection openPostgresConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    private record AdminInsertResult(boolean inserted, String sqlState) {
    }
}
