package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.DeviceTokenPlatform;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.service.DeviceTokenService;
import com.vanrides.vanrides.service.EmailAddressNormalizer;
import com.vanrides.vanrides.service.LoginIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({DeviceTokenService.class, LoginIdentityService.class, EmailAddressNormalizer.class})
class DeviceTokenRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_device_repository_test")
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

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Test
    void shouldExecuteAtomicDeviceRegistrationThroughTheRealJpaRepositories() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();
        Motorista motorista = new Motorista();
        motorista.setNome("Motorista Device Atomic");
        motorista.setEmail("device.atomic@vanrides.dev");
        motorista.setTelefone("51990000011");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);
        motorista = motoristaRepository.saveAndFlush(motorista);

        deviceTokenService.register(
                UserRole.DRIVER,
                motorista.getId(),
                motorista.getEmail(),
                defaultCompany.getId(),
                new DeviceTokenRegistrationRequest("same-jpa-token", "ios")
        );
        deviceTokenService.register(
                UserRole.DRIVER,
                motorista.getId(),
                motorista.getEmail(),
                defaultCompany.getId(),
                new DeviceTokenRegistrationRequest("same-jpa-token", "android")
        );

        assertThat(deviceTokenRepository.findAllByUserIdAndUserRoleAndActiveTrue(
                motorista.getId(),
                UserRole.DRIVER.claimValue()
        )).singleElement().satisfies(device -> {
            assertThat(device.getTokenValue()).isEqualTo("same-jpa-token");
            assertThat(device.getPlatform()).isEqualTo(DeviceTokenPlatform.ANDROID);
            assertThat(device.getUserEmail()).isEqualTo("device.atomic@vanrides.dev");
            assertThat(device.getCompanyId()).isEqualTo(defaultCompany.getId());
            assertThat(device.isActive()).isTrue();
        });
        assertThat(deviceTokenRepository.findAll())
                .extracting(DeviceToken::getTokenValue)
                .containsExactly("same-jpa-token");
    }
}
