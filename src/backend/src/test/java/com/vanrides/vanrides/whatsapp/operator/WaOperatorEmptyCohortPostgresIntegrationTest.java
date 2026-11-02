package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WaOperatorEmptyCohortPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wa_operator_empty_cohort_test")
            .withUsername("wa_operator_test")
            .withPassword("synthetic-container-password");

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private WaOperatorService service;
    private String operator;
    private Instant now;
    private UUID batch;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions { }

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void prepare() {
        operator = WaCrypto.randomToken();
        now = Instant.now();
        batch = UUID.randomUUID();
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("private-test-values", Map.of(
                "spring.main.web-application-type", "none", "vanrides.wa.enabled", "true",
                "vanrides.wa.environment", "test", "vanrides.wa.public-base-url", "http://127.0.0.1:18080",
                "vanrides.wa.hash-key", WaCrypto.randomToken(), "vanrides.wa.response-key", WaCrypto.randomToken(),
                "vanrides.wa.operator-credential", operator, "vanrides.wa.operator-expires-at", now.plusSeconds(3600).toString())));
        context.register(Transactions.class);
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(context.getBean(DataSource.class)));
        context.registerBean(PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
        context.registerBean(WaClock.class, () -> new WaClock(new WaOperatorMutableClock(now).source()));
        context.register(WaSettings.class, WaCrypto.class, WaOperatorMode.class, WaOperatorFixtures.class,
                WaOperatorReset.class, WaOperatorRevocation.class, WaOperatorService.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(WaOperatorService.class);
    }

    @AfterEach
    void close() {
        if (context != null) context.close();
    }

    private ObjectNode request(String operation) {
        return WaJson.MAPPER.createObjectNode().put("environment", "test")
                .put("operation", operation).put("batchId", batch.toString()).put("operatorCredential", operator);
    }

    private ObjectNode execute(ObjectNode input) {
        AtomicReference<ObjectNode> output = new AtomicReference<>();
        service.execute(WaOperatorRequest.parse(WaJson.write(input).getBytes(StandardCharsets.UTF_8)),
                value -> output.set(value.deepCopy()));
        return output.get();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void resetOfEmptyCohortIsRepeatableAndPreservesCredentialsAndOutsideData() {
        execute(request("create-test-cohort").put("synthetic", true));
        ObjectNode credential = execute(request("provision-service-credential").put("principal", "CONVERSATION_WORKER")
                .put("allowedInstanceId", "synthetic_operator_instance").put("expiresAt", now.plusSeconds(600).toString()));
        UUID worker = UUID.fromString(credential.get("credentialId").asText());
        UUID operatorId = jdbc.queryForObject("SELECT id FROM wa.service_credential WHERE token_hash=?",
                UUID.class, WaCrypto.digest(operator));
        UUID outsideBatch = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", outsideBatch);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", outside, outsideBatch);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')", outside);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?", batch)).isZero();

        for (int resetNumber = 1; resetNumber <= 2; resetNumber++) {
            ObjectNode result = execute(request("reset-test-data"));
            assertThat(result.get("identitiesDeleted").asInt()).as("reset %s of empty cohort", resetNumber).isZero();
            assertThat(result.get("operation").asText()).isEqualTo("reset-test-data");
            assertThat(result.get("batchId").asText()).isEqualTo(batch.toString());
            assertThat(result.get("resourceId").asText()).isEqualTo(batch.toString());
            assertThat(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?", batch)).isZero();
            assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=? AND environment='test' AND synthetic", batch)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND principal='OPERATOR' AND environment='test' AND revoked_at IS NULL", operatorId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND principal='CONVERSATION_WORKER' AND environment='test' AND revoked_at IS NULL", worker)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=? AND environment='test' AND synthetic", outsideBatch)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.identity WHERE id=? AND test_cohort_id=?", outside, outsideBatch)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=? AND role='PASSENGER' AND status='ACTIVE'", outside)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.audit_event WHERE credential_id=? AND actor_kind='OPERATOR' AND action='RESET_TEST_DATA' AND resource_id=?",
                    operatorId, batch)).isEqualTo(resetNumber);
        }
    }
}
