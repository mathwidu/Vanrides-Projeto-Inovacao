package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared operator wiring; each concrete test class owns its container and migration lifecycle. */
abstract class WaOperatorPostgresTestSupport {
    protected AnnotationConfigApplicationContext context;
    protected JdbcTemplate jdbc;
    protected WaOperatorService service;
    protected WaCrypto crypto;
    protected String operator;
    protected Instant now;
    protected UUID batch;
    protected WaOperatorMutableClock mutableClock;
    @TempDir protected Path directory;
    protected static final String INSTANCE = "synthetic_operator_instance";

    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Transactions { }

    protected abstract PostgreSQLContainer<?> database();

    protected static PostgreSQLContainer<?> postgres(String name) {
        return new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(name)
                .withUsername("wa_operator_test").withPassword("synthetic-container-password");
    }

    protected static void migrate(PostgreSQLContainer<?> postgres) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void prepare() {
        operator = WaCrypto.randomToken(); now = Instant.now(); batch = UUID.randomUUID();
        mutableClock = new WaOperatorMutableClock(now);
        context = application("test", now.plusSeconds(3600));
        jdbc = context.getBean(JdbcTemplate.class); service = context.getBean(WaOperatorService.class);
        crypto = context.getBean(WaCrypto.class);
    }
    @AfterEach void close() { if (context != null) context.close(); }

    protected AnnotationConfigApplicationContext application(String environment, Instant expiry) {
        var app = new AnnotationConfigApplicationContext();
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("private-test-values", Map.of(
                "spring.main.web-application-type", "none", "vanrides.wa.enabled", "true",
                "vanrides.wa.environment", environment, "vanrides.wa.public-base-url", "http://127.0.0.1:18080",
                "vanrides.wa.hash-key", WaCrypto.randomToken(), "vanrides.wa.response-key", WaCrypto.randomToken(),
                "vanrides.wa.operator-credential", operator, "vanrides.wa.operator-expires-at", expiry.toString())));
        app.register(Transactions.class);
        app.registerBean(DataSource.class, () -> new DriverManagerDataSource(
                database().getJdbcUrl(), database().getUsername(), database().getPassword()));
        app.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(app.getBean(DataSource.class)));
        app.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(app.getBean(DataSource.class)));
        app.registerBean(WaClock.class, () -> new WaClock(mutableClock.source()));
        app.register(WaSettings.class, WaCrypto.class, WaOperatorMode.class, WaOperatorFixtures.class,
                WaOperatorReset.class, WaOperatorRevocation.class, WaOperatorService.class);
        app.refresh(); return app;
    }

    protected ObjectNode request(String operation) {
        return WaJson.MAPPER.createObjectNode().put("environment", context.getBean(WaSettings.class).environment())
                .put("operation", operation).put("batchId", batch.toString()).put("operatorCredential", operator);
    }
    protected static WaOperatorRequest parse(ObjectNode input) {
        return WaOperatorRequest.parse(WaJson.write(input).getBytes(StandardCharsets.UTF_8));
    }
    protected ObjectNode execute(ObjectNode input) {
        AtomicReference<ObjectNode> output = new AtomicReference<>();
        service.execute(parse(input), value -> output.set(value.deepCopy()));
        return output.get();
    }
    protected long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
}
