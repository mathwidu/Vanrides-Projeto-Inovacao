package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

final class PassengerTenantDeleteTestFixture {

    private static final String TEST_BCRYPT_HASH =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    private final PostgreSQLContainer<?> postgres;
    private final PassengerCompanyLinker passengerCompanyLinker;
    private final PassageiroRepository passageiroRepository;
    private final CompanyRepository companyRepository;
    private final MotoristaRepository motoristaRepository;
    private final JdbcTemplate jdbcTemplate;

    PassengerTenantDeleteTestFixture(
            PostgreSQLContainer<?> postgres,
            PassengerCompanyLinker passengerCompanyLinker,
            PassageiroRepository passageiroRepository,
            CompanyRepository companyRepository,
            MotoristaRepository motoristaRepository,
            JdbcTemplate jdbcTemplate) {
        this.postgres = postgres;
        this.passengerCompanyLinker = passengerCompanyLinker;
        this.passageiroRepository = passageiroRepository;
        this.companyRepository = companyRepository;
        this.motoristaRepository = motoristaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    void resetDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .locations("classpath:db/migration")
                .load();
        // This fixture's datasource is the same disposable PostgreSQLContainer.
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        flyway.clean();
        flyway.migrate();
    }

    void reassignWithRealLinkerAndFlush(PassengerTenantDeleteSeed seed) {
        Passageiro passenger = passageiroRepository.findById(seed.passengerId())
                .orElseThrow();
        Company destination = companyRepository.findById(seed.companyBId())
                .orElseThrow();
        Motorista destinationDriver = motoristaRepository
                .findByIdAndCompanyId(seed.driverBId(), seed.companyBId())
                .orElseThrow();
        passengerCompanyLinker.link(passenger, destination, destinationDriver);
        passageiroRepository.flush();
    }

    PassengerTenantDeleteSeed seed(String suffix) {
        Long companyAId = jdbcTemplate.queryForObject(
                "SELECT id FROM company WHERE slug = 'default'",
                Long.class
        );
        Long companyBId = jdbcTemplate.queryForObject("""
                INSERT INTO company (nome, slug, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, "Destination " + suffix, "destination-" + suffix);
        Long driverAId = insertDriver(companyAId, suffix + ".driver-a@example.com");
        Long driverBId = insertDriver(companyBId, suffix + ".driver-b@example.com");
        String passengerEmail = suffix + ".passenger@example.com";
        Long passengerId = jdbcTemplate.queryForObject("""
                INSERT INTO passageiro (
                    nome, email, telefone, senha, endereco, bairro, cidade, estado, cep,
                    motorista_id, company_id
                ) VALUES (
                    'Passenger race IT', ?, '51000000102', ?, 'Rua race', 'Centro',
                    'Porto Alegre', 'RS', '90000-000', ?, ?
                )
                RETURNING id
                """, Long.class, passengerEmail, TEST_BCRYPT_HASH, driverAId, companyAId);

        jdbcTemplate.update("""
                INSERT INTO refresh_token (
                    token_hash, user_id, user_role, user_email, company_id, expires_at,
                    revoked, created_at, updated_at
                ) VALUES (?, ?, 'PASSENGER', ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                          FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "refresh-" + suffix, passengerId, passengerEmail, companyAId);
        jdbcTemplate.update("""
                INSERT INTO email_verification_token (
                    user_role, user_id, email_snapshot, token_hash, expires_at, created_at
                ) VALUES ('PASSENGER', ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                          CURRENT_TIMESTAMP)
                """, passengerId, passengerEmail, "verification-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO device_token (
                    token_hash, token_value, platform, user_id, user_role, user_email,
                    company_id, active, last_registered_at, created_at, updated_at
                ) VALUES (?, ?, 'ANDROID', ?, 'PASSENGER', ?, ?, TRUE, CURRENT_TIMESTAMP,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "device-" + suffix, "value-" + suffix, passengerId,
                passengerEmail, companyAId);

        return new PassengerTenantDeleteSeed(
                Objects.requireNonNull(companyAId),
                Objects.requireNonNull(companyBId),
                Objects.requireNonNull(driverBId),
                Objects.requireNonNull(passengerId),
                passengerEmail,
                suffix
        );
    }

    private Long insertDriver(Long companyId, String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO motorista (nome, email, telefone, senha, company_id)
                VALUES ('Driver race IT', ?, '51000000101', ?, ?)
                RETURNING id
                """, Long.class, email, TEST_BCRYPT_HASH, companyId);
    }

    void assertActiveAccountAndArtifacts(PassengerTenantDeleteSeed seed) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM login_identity
                    WHERE normalized_email = ?
                      AND user_role = 'PASSENGER'
                      AND user_id = ?
                )
                """, Boolean.class, seed.passengerEmail(), seed.passengerId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT NOT revoked FROM refresh_token WHERE token_hash = ?",
                Boolean.class,
                "refresh-" + seed.suffix()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT consumed_at IS NULL FROM email_verification_token WHERE token_hash = ?",
                Boolean.class,
                "verification-" + seed.suffix()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM device_token WHERE token_hash = ?",
                Boolean.class,
                "device-" + seed.suffix()
        )).isTrue();
    }

    void assertDeletedAccountAndInvalidatedArtifacts(PassengerTenantDeleteSeed seed) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM passageiro WHERE id = ?",
                Long.class,
                seed.passengerId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM login_identity
                WHERE normalized_email = ?
                  AND user_role = 'PASSENGER'
                  AND user_id = ?
                """, Long.class, seed.passengerEmail(), seed.passengerId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked FROM refresh_token WHERE token_hash = ?",
                Boolean.class,
                "refresh-" + seed.suffix()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM email_verification_token WHERE token_hash = ?",
                Boolean.class,
                "verification-" + seed.suffix()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT NOT active AND invalidated_at IS NOT NULL FROM device_token WHERE token_hash = ?",
                Boolean.class,
                "device-" + seed.suffix()
        )).isTrue();
    }

    String sqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException
                    && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}

record PassengerTenantDeleteSeed(
        long companyAId,
        long companyBId,
        long driverBId,
        long passengerId,
        String passengerEmail,
        String suffix) {
}
