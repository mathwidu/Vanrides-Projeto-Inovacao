package com.vanrides.vanrides.whatsapp.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaCommandService;
import com.vanrides.vanrides.whatsapp.commands.WaCommandStore;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaFoundationIntegrationTest {
    private static final class MutableClock extends java.time.Clock {
        private volatile java.time.Instant now = NOW;
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId ignored) { return this; }
        @Override public java.time.Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_foundation_test").withUsername("vanrides_test").withPassword("synthetic-local-test");
    private JdbcTemplate jdbc;
    private MutableClock time;
    private com.vanrides.vanrides.whatsapp.config.WaClock clock;
    private DataSourceTransactionManager transactions;
    private WaSettings settings;
    private WaCrypto crypto;
    private WaProofLedger ledger;
    private WaPairingService pairing;
    private WaCommandService commands;
    private WaPrincipal worker;
    private UUID actor;
    private String lease;
    private UUID event;

    @BeforeEach void prepareIsolatedDatabase() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source); jdbc.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        time = new MutableClock(); clock = new com.vanrides.vanrides.whatsapp.config.WaClock(time);
        transactions = new DataSourceTransactionManager(source); settings = settings(); crypto = new WaCrypto(settings);
        ledger = proxy(new WaProofLedger(jdbc, clock, settings));
        pairing = proxy(new WaPairingService(jdbc, settings, clock, crypto)); commands = commands(settings);
        UUID cohort = UUID.randomUUID(); actor = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", cohort);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", actor, cohort);
        UUID workerId = credential("CONVERSATION_WORKER", "instance-test");
        worker = new WaPrincipal(WaPrincipal.Kind.CONVERSATION_WORKER, workerId, null, null, "instance-test", null, 0, false);
        nextEvent(Map.of());
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private WaCommandService commands(WaSettings selected) {
        return proxy(new WaCommandService(jdbc, new WaInboxAccess(jdbc, selected, clock), new WaCommandStore(jdbc, clock),
            new WaOnboardingCommands(jdbc, crypto, clock), pairing, selected, clock, crypto));
    }
    private UUID credential(String kind, String instance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at) VALUES (?,?,'test',?,?,?,?)",
            id, kind, WaCrypto.digest(WaCrypto.randomToken()), instance, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(3600)));
        return id;
    }
    private void nextEvent(Map<String, String> metadata) {
        if (event != null) jdbc.update("UPDATE wa.integration_inbox SET state='COMPLETED' WHERE id=?", event);
        event = UUID.randomUUID(); lease = WaCrypto.randomToken();
        ObjectNode normalized = WaJson.object(WaJson.write(metadata)).put("occurredAt", clock.now().toString());
        if (!normalized.has("text") && !normalized.has("driverInviteCodeHash") && !normalized.has("pairingCodeHash"))
            normalized.put("text", "Mensagem sintética do cadastro");
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,
                state,lease_credential_id,lease_token_hash,lease_generation,lease_expires_at,created_at)
            VALUES (?,?,'SYNTHETIC','instance-test',?,'MESSAGE','TEXT',?::jsonb,'CLAIMED',?,?,1,?,?)
            """, event, actor, event.toString(), normalized.toString(), worker.credentialId(), WaCrypto.digest(lease),
            Timestamp.from(NOW.plusSeconds(300)), Timestamp.from(NOW));
    }
    private ObjectNode envelope(String type, long version, Map<String, String> payload) {
        return WaJson.object(WaJson.write(Map.of("commandType", type, "inboxEventId", event.toString(), "expectedVersion", version, "payload", payload)));
    }
    private WaDecision execute(String type, long version, Map<String, String> payload, String key) {
        return commands.execute(worker, lease, key, envelope(type, version, payload), UUID.randomUUID().toString());
    }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void expectCode(Runnable action, WaProblem.Code code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WaProblem.class, error -> assertThat(error.code()).isEqualTo(code));
    }
    private WaDpopVerifier.Proof proof(WaDpopVerifier.PublicMaterial material, String nonce, String jti) {
        return new WaDpopVerifier.Proof(material, jti, nonce, NOW);
    }
    private String documentedNotice() {
        try {
            return WaJson.object(java.nio.file.Files.readString(java.nio.file.Path.of("contracts/whatsapp-mvp/openapi.yaml")))
                .at("/components/examples/AcceptNotice/value/payload/noticeVersion").asText();
        } catch (java.io.IOException failure) { throw new AssertionError("Contract example unavailable", failure); }
    }

    @Test void leasedIdentityCanRegisterNameAndNoticeWithoutLegacyCompanyOrInventedActor() {
        var context = commands.context(worker, event, lease);
        assertThat(context.path("actorState").asText()).isEqualTo("UNREGISTERED");
        assertThat(context.path("allowedCommands")).hasSize(2);
        assertThat(context.path("allowedCommands").toString()).contains("START_ONBOARDING", "RESPOND_TO_MESSAGE");
        var start = execute("START_ONBOARDING", 0, Map.of(), "start-request-01").requireSuccess();
        assertThat(start.path("entityVersion").asLong()).isEqualTo(1);
        nextEvent(Map.of()); execute("SET_PROFILE_NAME", 1, Map.of("name", "Passageiro sintético"), "name-request-01").requireSuccess();
        nextEvent(Map.of("text", "ACEITO")); var accepted = execute("ACCEPT_NOTICE", 2, Map.of("noticeVersion", documentedNotice()), "notice-request-01").requireSuccess();
        assertThat(accepted.path("entityVersion").asLong()).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=? AND role='PASSENGER' AND status='ACTIVE'", actor)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.audit_event WHERE actor_identity_id=?", actor)).isEqualTo(3);
        assertThat(accepted.path("responseIds")).hasSize(1);
        assertThat(count("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?", actor)).isEqualTo(3);
    }
    @Test void replayByMessageOrIdempotencyReturnsOriginalBodyAndDoesNotRepeatMutation() {
        var body = envelope("START_ONBOARDING", 0, Map.of()); String correlation = UUID.randomUUID().toString();
        var first = commands.execute(worker, lease, "same-request-01", body, correlation).requireSuccess();
        assertThat(commands.execute(worker, lease, "same-request-01", body, UUID.randomUUID().toString()).requireSuccess()).isEqualTo(first);
        assertThat(commands.execute(worker, lease, "changed-key-01", body, UUID.randomUUID().toString()).requireSuccess()).isEqualTo(first);
        expectCode(() -> execute("SET_PROFILE_NAME", 1, Map.of("name", "Another"), "same-request-01"), WaProblem.Code.IDEMPOTENCY_CONFLICT);
        assertThat(count("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.message_outbox")).isEqualTo(1);
        assertThat(count("SELECT version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo(1);
        nextEvent(Map.of()); expectCode(() -> execute("SET_PROFILE_NAME", 1, Map.of("name", "Another"), "same-request-01"), WaProblem.Code.IDEMPOTENCY_CONFLICT);
    }
    @Test void expiredWrongOrReassignedLeaseAndRevokedWorkerCannotReadContextOrMutate() {
        expectCode(() -> commands.context(worker, event, key(1)), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        jdbc.update("UPDATE wa.integration_inbox SET lease_expires_at=? WHERE id=?", Timestamp.from(NOW), event);
        expectCode(() -> execute("START_ONBOARDING", 0, Map.of(), "expired-lease-01"), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        jdbc.update("UPDATE wa.integration_inbox SET lease_expires_at=?,lease_token_hash=?,lease_generation=2 WHERE id=?",
            Timestamp.from(NOW.plusSeconds(300)), WaCrypto.digest(key(2)), event);
        expectCode(() -> commands.context(worker, event, lease), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        lease = key(2); jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?", Timestamp.from(NOW), worker.credentialId());
        expectCode(() -> commands.context(worker, event, lease), WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(count("SELECT count(*) FROM wa.profile")).isZero();
    }
    @Test void wrongInstanceOrOutsideCohortAndFreelyChosenActorAreRejected() {
        jdbc.update("UPDATE wa.service_credential SET allowed_instance_id='other-instance' WHERE id=?", worker.credentialId());
        expectCode(() -> commands.context(worker, event, lease), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        jdbc.update("UPDATE wa.service_credential SET allowed_instance_id='instance-test' WHERE id=?", worker.credentialId());
        jdbc.update("UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?", actor);
        expectCode(() -> commands.context(worker, event, lease), WaProblem.Code.ACTION_NOT_ALLOWED);
        var forged = envelope("START_ONBOARDING", 0, Map.of()).put("actorId", UUID.randomUUID().toString());
        expectCode(() -> commands.execute(worker, lease, "forged-actor-01", forged, UUID.randomUUID().toString()), WaProblem.Code.INVALID_PAYLOAD);
    }
    @Test void versionConflictAndFutureCommandDoNotManufactureReceiptsOrSuccess() {
        expectCode(() -> execute("START_ONBOARDING", 9, Map.of(), "bad-version-01"), WaProblem.Code.VERSION_CONFLICT);
        expectCode(() -> execute("FINISH_RUN", 0, Map.of(), "future-finish-01"), WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        expectCode(() -> execute("REQUEST_START", 0, Map.of(), "malformed-start-01"), WaProblem.Code.INVALID_PAYLOAD);
        assertThat(count("SELECT count(*) FROM wa.command_receipt")).isZero();
        assertThat(count("SELECT count(*) FROM wa.message_outbox")).isZero();
        assertThat(count("SELECT count(*) FROM wa.profile")).isZero();
        assertThat(count("SELECT count(*) FROM wa.start_intent")).isZero();
        assertThat(count("SELECT count(*) FROM wa.operation_run")).isZero();
        assertThat(count("SELECT count(*) FROM wa.tracking_session")).isZero();
    }
    @Test void inviteFailuresCommitFiveAttemptLimitButReplayDoesNotConsumeAnotherAttempt() {
        String code = key(5); nextEvent(Map.of("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", code)));
        var first = execute("CLAIM_DRIVER_INVITE", 0, Map.of("inviteCode", code), "failed-invite-01");
        assertThat(first.error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(execute("CLAIM_DRIVER_INVITE", 0, Map.of("inviteCode", code), "failed-invite-01").error()).isEqualTo(first.error());
        assertThat(count("SELECT sum(attempts) FROM wa.actor_attempt_window WHERE identity_id=?", actor)).isEqualTo(1);
        for (int i = 2; i <= 6; i++) {
            nextEvent(Map.of("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", code)));
            var result = execute("CLAIM_DRIVER_INVITE", 0, Map.of("inviteCode", code), "failed-invite-0" + i);
            assertThat(result.error()).isEqualTo(i == 6 ? WaProblem.Code.RATE_LIMITED : WaProblem.Code.INVALID_CREDENTIAL);
        }
        assertThat(count("SELECT sum(attempts) FROM wa.actor_attempt_window WHERE identity_id=?", actor)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM wa.profile")).isZero();
    }
    @Test void driverBootstrapAndHumanConfirmationLeadToKeyBoundSession() throws Exception {
        UUID operator = credential("OPERATOR", null); String invite = key(6);
        jdbc.update("INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,created_at,expires_at,created_by) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(), actor, crypto.secretHash("driver-bootstrap", invite), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(300)), operator);
        nextEvent(Map.of("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", invite)));
        execute("CLAIM_DRIVER_INVITE", 0, Map.of("inviteCode", invite), "driver-claim-01").requireSuccess();
        nextEvent(Map.of()); execute("SET_PROFILE_NAME", 1, Map.of("name", "Motorista sintético"), "driver-name-01").requireSuccess();
        nextEvent(Map.of("text", "ACEITO")); execute("ACCEPT_NOTICE", 2, Map.of("noticeVersion", documentedNotice()), "driver-notice-01").requireSuccess();
        var jwk = jwk(pair()); var proof = proof(WaDpopVerifier.publicMaterial(jwk), null, WaCrypto.randomToken()); String verifier = key(9);
        var beginBody = WaJson.object(WaJson.write(Map.of("publicJwk", jwk, "pkceMethod", "S256", "pkceChallenge",
            WaCrypto.base64(WaCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII))), "platform", "ANDROID", "appVersion", "0.1.0")));
        var begin = pairing.begin(proof, beginBody, "pairing-begin-01").requireSuccess(); String code = begin.path("userCode").asText();
        nextEvent(Map.of("pairingCodeHash", crypto.secretHash("pairing-code", code)));
        execute("CONFIRM_PAIRING", 3, Map.of("userCode", code), "pair-confirm-01").requireSuccess();
        var session = pairing.redeem(proof, WaJson.object(WaJson.write(Map.of("pairingId", begin.path("pairingId").asText(), "pkceVerifier", verifier))), "pairing-redeem-01").requireSuccess();
        var principal = new WaCredentials(jdbc, settings, clock).device(session.path("accessToken").asText(), false);
        assertThat(principal.driverId()).isEqualTo(actor);
        assertThat(pairing.driverStatus(principal).path("readiness").asText()).isEqualTo("READY");
        assertThat(count("SELECT version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo(4);
    }
    @Test void togglingOffPreservesReadOnlyContextAndReceiptsButRejectsNewEffects() {
        var envelope = envelope("START_ONBOARDING", 0, Map.of());
        var first = commands.execute(worker, lease, "before-disable-01", envelope, UUID.randomUUID().toString()).requireSuccess();
        var disabled = commands(new WaSettings(environment().withProperty("vanrides.wa.enabled", "false")));
        assertThat(disabled.context(worker, event, lease).path("allowedCommands")).isEmpty();
        assertThat(disabled.execute(worker, lease, "before-disable-01", envelope, UUID.randomUUID().toString()).requireSuccess()).isEqualTo(first);
        nextEvent(Map.of()); var name = envelope("SET_PROFILE_NAME", 1, Map.of("name", "Must not change"));
        expectCode(() -> disabled.execute(worker, lease, "after-disable-01", name, UUID.randomUUID().toString()), WaProblem.Code.ACTION_NOT_ALLOWED);
        jdbc.update("UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?", actor);
        expectCode(() -> execute("SET_PROFILE_NAME", 1, Map.of("name", "Must not change"), "blocked-profile-01"), WaProblem.Code.ACTION_NOT_ALLOWED);
    }
    @Test void concurrentDuplicateMessageCommitsOneResult() throws Exception {
        var envelope = envelope("START_ONBOARDING", 0, Map.of()); var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
                return commands.execute(worker, lease, "concurrent-start-" + i, envelope, UUID.randomUUID().toString()).requireSuccess();
            })).toList();
            latch.countDown(); var one = tasks.get(0).get(15, TimeUnit.SECONDS); var two = tasks.get(1).get(15, TimeUnit.SECONDS);
            assertThat(one).isEqualTo(two); assertThat(count("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void nonceHasOneWinnerUnderConcurrencyAndIsBoundToEnvironmentAndKey() throws Exception {
        var material = WaDpopVerifier.publicMaterial(jwk(pair())); String nonce = ledger.issue(material.jkt());
        var wrong = WaDpopVerifier.publicMaterial(jwk(pair()));
        assertThat(ledger.consume(proof(wrong, nonce, WaCrypto.randomToken()))).isFalse();
        var other = proxy(new WaProofLedger(jdbc, clock, new WaSettings(environment().withProperty("vanrides.wa.environment", "other"))));
        assertThat(other.consume(proof(material, nonce, WaCrypto.randomToken()))).isFalse();
        var executor = Executors.newFixedThreadPool(2); var latch = new CountDownLatch(1);
        try {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); return ledger.consume(proof(material, nonce, WaCrypto.randomToken()));
            })).toList(); latch.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(count("SELECT count(*) FROM wa.proof_replay")).isEqualTo(1);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void duplicateJtiCannotConsumeFreshNonceAndExpiredNonceIsNeverAccepted() throws Exception {
        var material = WaDpopVerifier.publicMaterial(jwk(pair())); String jti = WaCrypto.randomToken();
        assertThat(ledger.consume(proof(material, ledger.issue(material.jkt()), jti))).isTrue();
        String next = ledger.issue(material.jkt());
        expectCode(() -> ledger.consume(proof(material, next, jti)), WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(ledger.consume(proof(material, next, WaCrypto.randomToken()))).isTrue();
        String expired = ledger.issue(material.jkt());
        jdbc.update("UPDATE wa.proof_nonce SET created_at=?,expires_at=? WHERE nonce_hash=?", Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW), WaCrypto.digest(expired));
        assertThat(ledger.consume(proof(material, expired, WaCrypto.randomToken()))).isFalse();
    }

    @Test void leaseExpiringWhileProfileIsLockedRollsBackCommand() throws Exception {
        expirationWhileWaiting(301, WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
    }
    @Test void serviceCredentialExpiringWhileProfileIsLockedRollsBackCommand() throws Exception {
        jdbc.update("UPDATE wa.integration_inbox SET lease_expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(7200)), event);
        expirationWhileWaiting(3601, WaProblem.Code.INVALID_CREDENTIAL);
    }
    @Test void bootstrapExpiringWhileInviteIsLockedNeverPromotesIdentity() throws Exception {
        String code = key(6); UUID invite = UUID.randomUUID(), operator = credential("OPERATOR", null);
        jdbc.update("INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,created_at,expires_at,created_by) VALUES (?,?,?,?,?,?)",
            invite, actor, crypto.secretHash("driver-bootstrap", code), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(30)), operator);
        nextEvent(Map.of("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", code)));
        var executor = Executors.newSingleThreadExecutor();
        try (var lock = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            lock.setAutoCommit(false);
            try (var statement = lock.prepareStatement("SELECT id FROM wa.driver_bootstrap_invite WHERE id=? FOR UPDATE")) {
                statement.setObject(1, invite); statement.executeQuery().close();
            }
            var pending = executor.submit(() -> execute("CLAIM_DRIVER_INVITE", 0, Map.of("inviteCode", code), "bootstrap-expiry-01"));
            awaitDatabaseLock("SELECT id,consumed_at,expires_at FROM wa.driver_bootstrap_invite%");
            time.advance(31); lock.commit();
            assertThat(pending.get(10, TimeUnit.SECONDS).error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
            assertThat(count("SELECT count(*) FROM wa.profile")).isZero();
            assertThat(count("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE consumed_at IS NOT NULL")).isZero();
            assertThat(count("SELECT sum(attempts) FROM wa.actor_attempt_window")).isEqualTo(1);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
    private void awaitDatabaseLock(String pattern) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (count("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE ?", pattern) > 0) return;
            Thread.sleep(25);
        }
        throw new AssertionError("The expected database lock was not observed");
    }
    private void expirationWhileWaiting(long seconds, WaProblem.Code expected) throws Exception {
        execute("START_ONBOARDING", 0, Map.of(), "setup-for-expiry").requireSuccess();
        nextEvent(Map.of());
        if (seconds > 300) jdbc.update("UPDATE wa.integration_inbox SET lease_expires_at=? WHERE id=?",
            Timestamp.from(NOW.plusSeconds(seconds == 301 ? 300 : 7200)), event);
        var executor = Executors.newSingleThreadExecutor();
        try (var lock = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            lock.setAutoCommit(false);
            try (var statement = lock.prepareStatement("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE")) {
                statement.setObject(1, actor); statement.executeQuery().close();
            }
            var pending = executor.submit(() -> execute("SET_PROFILE_NAME", 1, Map.of("name", "Expired effect"), "expires-in-wait-01"));
            awaitDatabaseLock("SELECT * FROM wa.profile%");
            time.advance(seconds); lock.commit();
            assertThatThrownBy(() -> pending.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(WaProblem.class)
                .satisfies(failure -> assertThat(((WaProblem) failure.getCause()).code()).isEqualTo(expected));
            assertThat(count("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM wa.audit_event")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT display_name FROM wa.profile WHERE identity_id=?", String.class, actor)).isNull();
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
