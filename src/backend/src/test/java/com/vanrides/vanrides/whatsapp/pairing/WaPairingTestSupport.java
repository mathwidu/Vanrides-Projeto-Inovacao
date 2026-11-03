package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCredentials;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier.Proof;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

final class WaPairingTestSupport {
    record Client(Proof proof, ObjectNode jwk, String verifier) { }
    record Approved(Client client, UUID driver, ObjectNode begin) { }
    final JdbcTemplate jdbc;
    final MutableClock time = new MutableClock();
    final WaClock clock = new WaClock(time);
    final WaSettings settings;
    final WaCrypto crypto;
    final TransactionTemplate transaction;
    final DataSourceTransactionManager transactionManager;
    final WaPairingService service;

    static PostgreSQLContainer<?> postgres(String name) {
        return new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(name)
                .withUsername("vanrides_test").withPassword("synthetic-pairing-database-password");
    }

    WaPairingTestSupport(PostgreSQLContainer<?> postgres) {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        settings = settings(true, "lab"); crypto = new WaCrypto(settings);
        transactionManager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(transactionManager);
        service = service(settings);
    }

    WaPairingService service(WaSettings selected) {
        var target = new WaPairingService(jdbc, selected, clock, new WaCrypto(selected));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (WaPairingService) proxy.getProxy();
    }

    static WaSettings settings(boolean enabled, String environment) {
        return new WaSettings(new MockEnvironment()
                .withProperty("vanrides.wa.enabled", Boolean.toString(enabled))
                .withProperty("vanrides.wa.environment", environment)
                .withProperty("vanrides.wa.public-base-url", "http://127.0.0.1:8080")
                .withProperty("vanrides.wa.test-cohort-only", "true")
                .withProperty("vanrides.wa.hash-key", testKey((byte) 11))
                .withProperty("vanrides.wa.response-key", testKey((byte) 22))
                .withProperty("vanrides.wa.operator-credential", testKey((byte) 33))
                .withProperty("vanrides.wa.operator-expires-at", "2035-01-01T00:00:00Z"));
    }

    UUID driver() {
        UUID cohort = UUID.randomUUID(); UUID driver = UUID.randomUUID();
        // false exercises an authorized, non-resettable cohort without any real data.
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'lab',FALSE)", cohort);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", driver, cohort);
        jdbc.update("""
                INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled)
                VALUES (?,'DRIVER','ACTIVE','Synthetic driver','synthetic-v1',?,TRUE)
                """, driver, Timestamp.from(clock.now()));
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        return driver;
    }

    Approved approved(Client client, UUID driver, String key) {
        ObjectNode result = service.begin(client.proof(), beginBody(client), key).requireSuccess();
        WaDecision confirmed = transaction.execute(status -> service.confirm(driver, result.path("userCode").asText()));
        assertThat(confirmed).isNotNull(); confirmed.requireSuccess();
        return new Approved(client, driver, result);
    }

    ObjectNode redeem(Approved pairing, String key) {
        return service.redeem(pairing.client().proof(), redeemBody(pairing), key).requireSuccess();
    }

    ObjectNode refresh(Client client, ObjectNode session, String key) {
        return service.refresh(client.proof(), refreshBody(session), key).requireSuccess();
    }

    WaPrincipal principal(ObjectNode session, boolean revokedAllowed) {
        return new WaCredentials(jdbc, settings, clock).device(session.path("accessToken").asText(), revokedAllowed);
    }

    long number(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value;
    }

    String string(String sql, Object... args) { return jdbc.queryForObject(sql, String.class, args); }

    UUID tracking(UUID driver, ObjectNode session) {
        UUID routine = UUID.randomUUID(); UUID occurrence = UUID.randomUUID(); UUID run = UUID.randomUUID();
        UUID tracking = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,policy_version)
                VALUES (?,?,'Synthetic routine','OUTBOUND',ARRAY[1]::smallint[],TIME '07:30','UTC','synthetic-v1')
                """, routine, driver);
        jdbc.update("""
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)
                VALUES (?,?,?,DATE '2030-01-01',?)
                """, occurrence, routine, driver, Timestamp.from(clock.now()));
        jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_at)
                VALUES (?,?,?,?,DATE '2030-01-01','STARTING','synthetic-v1',?)
                """, run, occurrence, routine, driver, Timestamp.from(clock.now()));
        jdbc.update("""
                INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,state,lease_expires_at)
                VALUES (?,?,?,?,1,'ACTIVE',?)
                """, tracking, run, driver, WaJson.uuid(session, "deviceId"), Timestamp.from(clock.now().plusSeconds(300)));
        return tracking;
    }

    static Client client() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        ObjectNode jwk = object().put("kty", "EC").put("crv", "P-256")
                .put("x", WaCrypto.base64(coordinate(key.getW().getAffineX().toByteArray())))
                .put("y", WaCrypto.base64(coordinate(key.getW().getAffineY().toByteArray())));
        var material = WaDpopVerifier.publicMaterial(jwk);
        return new Client(new Proof(material, UUID.randomUUID().toString(), null, Instant.now()), jwk, WaCrypto.randomToken());
    }

    static ObjectNode beginBody(Client client) {
        ObjectNode body = object().put("pkceChallenge", pkce(client.verifier())).put("pkceMethod", "S256")
                .put("platform", "ANDROID").put("appVersion", "synthetic-1");
        body.set("publicJwk", client.jwk().deepCopy()); return body;
    }

    static ObjectNode redeemBody(Approved approved) {
        return object().put("pairingId", approved.begin().path("pairingId").asText())
                .put("pkceVerifier", approved.client().verifier());
    }

    static ObjectNode refreshBody(ObjectNode session) { return object().put("refreshToken", session.path("refreshToken").asText()); }
    static ObjectNode object() { return WaJson.MAPPER.createObjectNode(); }
    static String pkce(String verifier) { return WaCrypto.base64(WaCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII))); }
    static void problem(WaProblem.Code code, Runnable action) {
        Throwable failure = catchThrowable(action::run);
        assertThat(failure).isInstanceOf(WaProblem.class); assertThat(((WaProblem) failure).code()).isEqualTo(code);
    }

    private static byte[] coordinate(byte[] value) {
        byte[] output = new byte[32];
        int count = Math.min(32, value.length); System.arraycopy(value, value.length - count, output, 32 - count, count);
        return output;
    }
    private static String testKey(byte value) { byte[] key = new byte[32]; Arrays.fill(key, value); return WaCrypto.base64(key); }
    static final class MutableClock extends Clock {
        private volatile Instant current = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
        void advance(Duration duration) { current = current.plus(duration); }
    }
}
