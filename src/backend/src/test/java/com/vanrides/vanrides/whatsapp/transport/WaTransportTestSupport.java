package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

final class WaTransportTestSupport {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    static final String INSTANCE = "synthetic-transport";
    final JdbcTemplate jdbc;
    final MutableClock time = new MutableClock();
    final WaClock clock = new WaClock(time);
    final WaSettings settings;
    final WaCrypto crypto;
    final WaTransportAccess access;
    final DataSourceTransactionManager manager;
    final TransactionTemplate transaction;

    static PostgreSQLContainer<?> postgres(String name) {
        return new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(name)
            .withUsername("vanrides_test").withPassword("synthetic-transport-database-password");
    }
    WaTransportTestSupport(PostgreSQLContainer<?> postgres) { this(postgres, true); }
    WaTransportTestSupport(PostgreSQLContainer<?> postgres, boolean cohortOnly) {
        this(rebuild(postgres), cohortOnly);
    }
    static DataSource rebuild(PostgreSQLContainer<?> postgres) {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(source).execute("DROP SCHEMA IF EXISTS wa CASCADE");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        return source;
    }
    WaTransportTestSupport(DataSource source, boolean cohortOnly) {
        jdbc = new JdbcTemplate(source);
        settings = settings(true, cohortOnly); crypto = new WaCrypto(settings);
        access = new WaTransportAccess(jdbc, settings, clock);
        manager = new DataSourceTransactionManager(source); transaction = new TransactionTemplate(manager);
    }
    static WaSettings settings(boolean enabled, boolean cohortOnly) {
        return settings(enabled, cohortOnly, "transport-test");
    }
    static WaSettings settings(boolean enabled, boolean cohortOnly, String environment) {
        return new WaSettings(new MockEnvironment().withProperty("vanrides.wa.enabled", Boolean.toString(enabled))
            .withProperty("vanrides.wa.environment", environment)
            .withProperty("vanrides.wa.public-base-url", "http://127.0.0.1:8080")
            .withProperty("vanrides.wa.start-link-base-url", "https://vanrides.invalid")
            .withProperty("vanrides.wa.test-cohort-only", Boolean.toString(cohortOnly))
            .withProperty("vanrides.wa.hash-key", key((byte) 1))
            .withProperty("vanrides.wa.response-key", key((byte) 2))
            .withProperty("vanrides.wa.operator-credential", key((byte) 3))
            .withProperty("vanrides.wa.operator-expires-at", "2035-01-01T00:00:00Z"));
    }
    private static String key(byte value) {
        byte[] bytes = new byte[32]; Arrays.fill(bytes, value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @SuppressWarnings("unchecked") <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    WaPrincipal principal(WaPrincipal.Kind kind) { return principal(kind, INSTANCE); }
    WaPrincipal principal(WaPrincipal.Kind kind, String instance) {
        return principal(kind, instance, "transport-test");
    }
    WaPrincipal principal(WaPrincipal.Kind kind, String instance, String environment) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at)
            VALUES (?,?,?,?,?,?,?)
            """, id, kind.name(), environment, WaCrypto.digest(WaCrypto.randomToken()), instance,
            Timestamp.from(clock.now()), Timestamp.from(clock.now().plusSeconds(3600)));
        return new WaPrincipal(kind, id, null, null, instance, null, 0, false);
    }
    ObjectNode connection(WaPrincipal channel, String state, Instant occurred) {
        return connection(channel, state, occurred, settings);
    }
    ObjectNode connection(WaPrincipal channel, String state, Instant occurred, WaSettings configuration) {
        var events = proxy(new WaEventService(jdbc, new WaTransportAccess(jdbc, configuration, clock),
            new WaEvolutionNormalizer(new WaCrypto(configuration)), configuration, clock));
        ObjectNode envelope = json("{}").put("event", "connection.update")
            .put("instance", channel.instanceId()).put("date_time", occurred.toString());
        envelope.putObject("data").put("state", state);
        return events.accept(channel, envelope);
    }
    UUID identity() {
        UUID cohort = UUID.randomUUID(), id = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'transport-test',true)", cohort);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", id, cohort);
        return id;
    }
    void alias(UUID actor, String type, String alias) {
        jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,?,?)",
            UUID.randomUUID(), actor, INSTANCE, type, alias);
    }
    UUID event(UUID actor, ObjectNode payload) {
        UUID event = UUID.randomUUID();
        payload = payload.deepCopy();
        if (!payload.has("occurredAt")) payload.put("occurredAt", clock.now().toString());
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,created_at,not_before)
            VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','TEXT',?::jsonb,?,?)
            """, event, actor, INSTANCE, event.toString(), payload.toString(), Timestamp.from(clock.now()), Timestamp.from(clock.now()));
        return event;
    }
    static ObjectNode json(String json) { return WaJson.object(json); }
    static final class MutableClock extends Clock {
        private volatile Instant instant = NOW;
        void advance(long seconds) { instant = instant.plusSeconds(seconds); }
        void set(Instant value) { instant = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
