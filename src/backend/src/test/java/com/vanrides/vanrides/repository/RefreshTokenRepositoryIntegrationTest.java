package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_refresh_token_test")
            .withUsername("vanrides_refresh_token")
            .withPassword("vanrides_refresh_token");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void shouldPersistRefreshTokenWithoutCompanyForPassengerStillInOnboarding() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("hash-without-company");
        refreshToken.setUserId(12L);
        refreshToken.setUserRole("PASSENGER");
        refreshToken.setUserEmail("sem.empresa@vanrides.com");
        refreshToken.setCompanyId(null);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(3600));
        refreshToken.setRevoked(false);

        RefreshToken persisted = refreshTokenRepository.saveAndFlush(refreshToken);

        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getCompanyId()).isNull();
        assertThat(refreshTokenRepository.findByTokenHashAndRevokedFalse("hash-without-company"))
                .isPresent()
                .get()
                .extracting(RefreshToken::getCompanyId)
                .isNull();
    }
}
