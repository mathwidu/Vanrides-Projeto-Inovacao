package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaOperatorPostgresIntegrationTest extends WaOperatorPostgresTestSupport {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_operator_test");

    @BeforeAll static void migrate() { migrate(POSTGRES); }
    @Override protected PostgreSQLContainer<?> database() { return POSTGRES; }

    private void cohort() { execute(request("create-test-cohort").put("synthetic", true)); }
    private ObjectNode provision(String principal) {
        return execute(request("provision-service-credential").put("principal", principal)
                .put("allowedInstanceId", INSTANCE).put("expiresAt", now.plusSeconds(600).toString()));
    }
    private ObjectNode seed(UUID identity, UUID worker) {
        return request("seed-test-data").put("identityId", identity.toString()).put("inboxEventId", UUID.randomUUID().toString())
                .put("workerCredentialId", worker.toString()).put("leaseExpiresAt", now.plusSeconds(60).toString());
    }

    @Test void transactionProxyAuthenticatesOperatorAndProvisionsSeparateScopedExpiringCredential() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        cohort(); ObjectNode output = provision("CONVERSATION_WORKER"); String raw = output.get("credential").asText();
        WaDpopVerifier.canonical32(raw);
        assertThat(raw).isNotEqualTo(operator);
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND principal='CONVERSATION_WORKER' AND environment='test' AND token_hash=? AND allowed_instance_id=? AND expires_at=?",
                UUID.fromString(output.get("credentialId").asText()), WaCrypto.digest(raw), INSTANCE, Timestamp.from(now.plusSeconds(600)))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE principal='OPERATOR' AND token_hash=? AND environment='test'", WaCrypto.digest(operator))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.audit_event a JOIN wa.service_credential c ON c.id=a.credential_id WHERE a.resource_id=? AND a.actor_kind='OPERATOR' AND a.actor_identity_id IS NULL AND c.token_hash=?",
                UUID.fromString(output.get("credentialId").asText()), WaCrypto.digest(operator))).isEqualTo(1);
    }

    @Test void rejectsWrongCredentialEnvironmentAndExpiredOperatorWithoutFallback() {
        ObjectNode wrong = request("create-test-cohort").put("synthetic", true).put("operatorCredential", WaCrypto.randomToken());
        assertThatThrownBy(() -> execute(wrong)).isInstanceOf(WaOperatorFailure.class);
        ObjectNode environment = request("create-test-cohort").put("synthetic", true).put("environment", "other_test");
        assertThatThrownBy(() -> execute(environment)).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        context.close(); context = application("test", now.minusSeconds(1)); service = context.getBean(WaOperatorService.class);
        assertThatThrownBy(() -> execute(request("create-test-cohort").put("synthetic", true))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE token_hash=?", WaCrypto.digest(operator))).isZero();
    }

    @Test void rejectsProductionEvenWhenOperatorConfigurationMatches() {
        context.close(); context = application("production", now.plusSeconds(3600)); service = context.getBean(WaOperatorService.class);
        for (String operation : new String[] {"create-test-cohort", "reset-test-data"}) {
            ObjectNode input = request(operation);
            if (operation.equals("create-test-cohort")) input.put("synthetic", true);
            assertThatThrownBy(() -> execute(input)).isInstanceOf(WaOperatorFailure.class);
        }
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE token_hash=?", WaCrypto.digest(operator))).isZero();
    }

    @Test void seedCannotCreateMissingCohortOrUpgradeFalseCohort() {
        UUID identity = UUID.randomUUID(); ObjectNode input = seed(identity, UUID.randomUUID());
        assertThatThrownBy(() -> execute(input)).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',false)", batch);
        assertThatThrownBy(() -> execute(input)).isInstanceOf(WaOperatorFailure.class);
        assertThatThrownBy(() -> execute(request("create-test-cohort").put("synthetic", true))).isInstanceOf(WaOperatorFailure.class);
        assertThat(jdbc.queryForObject("SELECT synthetic FROM wa.test_cohort WHERE id=?", Boolean.class, batch)).isFalse();
        assertThat(count("SELECT count(*) FROM wa.identity WHERE id=?", identity)).isZero();
    }

    @Test void seedUsesExistingWorkerScopeCanonicalLeaseAndOnlyAuthenticatedCodeHashes() {
        cohort(); ObjectNode worker = provision("CONVERSATION_WORKER"); UUID identity = UUID.randomUUID();
        UUID workerId = UUID.fromString(worker.get("credentialId").asText());
        ObjectNode output = execute(seed(identity, workerId).put("pairingCode", "BCDFGHJZ"));
        String lease = output.get("leaseToken").asText(); WaDpopVerifier.canonical32(lease);
        UUID event = UUID.fromString(output.get("inboxEventId").asText());
        ObjectNode payload = WaJson.object(jdbc.queryForObject("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?", String.class, event));
        assertThat(payload.size()).isEqualTo(2);
        assertThat(payload.path("occurredAt").asText()).isEqualTo(now.toString());
        assertThat(payload.get("pairingCodeHash").asText()).isEqualTo(crypto.secretHash("pairing-code", "BCDFGHJZ"));
        assertThat(payload.toString()).doesNotContain("BCDFGHJZ", lease, "actorId", "latitude");
        assertThat(count("SELECT count(*) FROM wa.identity_alias WHERE identity_id=? AND provider='SYNTHETIC' AND instance_id=? AND alias_value=? AND verified_phone IS NULL",
                identity, INSTANCE, "synthetic-" + identity + "@vanrides.invalid")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=?", identity)).isZero();
        assertThat(count("SELECT count(*) FROM wa.integration_inbox WHERE id=? AND state='CLAIMED' AND lease_credential_id=? AND lease_token_hash=? AND lease_generation=1 AND lease_expires_at=?",
                event, workerId, WaCrypto.digest(lease), Timestamp.from(now.plusSeconds(60)))).isEqualTo(1);
        WaInboxAccess access = new WaInboxAccess(jdbc, context.getBean(WaSettings.class), context.getBean(WaClock.class));
        WaPrincipal principal = new WaPrincipal(WaPrincipal.Kind.CONVERSATION_WORKER, workerId, null, null, INSTANCE, null, 0, false);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                assertThat(access.lock(event, lease, principal).identityId()).isEqualTo(identity));
    }

    @Test void seedRejectsOtherBatchWrongPrincipalMissingInstanceAndInvalidLeaseExpiry() {
        cohort(); UUID identity = UUID.randomUUID(); UUID outside = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", outside);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", identity, outside);
        UUID worker = UUID.fromString(provision("CONVERSATION_WORKER").get("credentialId").asText());
        assertThatThrownBy(() -> execute(seed(identity, worker))).isInstanceOf(WaOperatorFailure.class);
        UUID wrong = UUID.fromString(provision("DELIVERY_WORKER").get("credentialId").asText());
        assertThatThrownBy(() -> execute(seed(UUID.randomUUID(), wrong))).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.service_credential SET allowed_instance_id=NULL WHERE id=?", worker);
        assertThatThrownBy(() -> execute(seed(UUID.randomUUID(), worker))).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.service_credential SET allowed_instance_id=? WHERE id=?", INSTANCE, worker);
        assertThatThrownBy(() -> execute(seed(UUID.randomUUID(), worker).put("leaseExpiresAt", now.minusSeconds(1).toString())))
                .isInstanceOf(WaOperatorFailure.class);
        assertThatThrownBy(() -> execute(seed(UUID.randomUUID(), worker).put("leaseExpiresAt", now.plusSeconds(61).toString())))
                .isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?", batch)).isZero();
    }

    @Test void bootstrapIsBoundToIdentityAndSeedStoresDriverInviteHashWithoutGrantingRole() {
        cohort(); UUID worker = UUID.fromString(provision("CONVERSATION_WORKER").get("credentialId").asText());
        UUID identity = UUID.randomUUID(); ObjectNode first = execute(seed(identity, worker));
        ObjectNode bootstrap = execute(request("driver-bootstrap").put("identityId", identity.toString())
                .put("expiresAt", now.plusSeconds(600).toString()));
        String code = bootstrap.get("driverInviteCode").asText(); WaDpopVerifier.canonical32(code);
        assertThat(count("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE identity_id=? AND code_hash=? AND created_by IN (SELECT id FROM wa.service_credential WHERE principal='OPERATOR' AND token_hash=?)",
                identity, crypto.secretHash("driver-bootstrap", code), WaCrypto.digest(operator))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=?", identity)).isZero();
        UUID event = UUID.fromString(first.get("inboxEventId").asText());
        assertThatThrownBy(() -> execute(request("complete-test-inbox").put("inboxEventId", event.toString()))).isInstanceOf(WaOperatorFailure.class);
        receipt(identity, event);
        execute(request("complete-test-inbox").put("inboxEventId", event.toString()));
        ObjectNode second = execute(seed(identity, worker).put("driverInviteCode", code));
        String json = jdbc.queryForObject("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?", String.class,
                UUID.fromString(second.get("inboxEventId").asText()));
        assertThat(WaJson.object(json).get("driverInviteCodeHash").asText()).isEqualTo(crypto.secretHash("driver-bootstrap", code));
        assertThat(json).doesNotContain(code, "pairingCode", "actorId");
    }

    @Test void outputFailureRollsBackOperatorInitializationAndAllEffects() {
        ObjectNode input = request("create-test-cohort").put("synthetic", true);
        assertThatThrownBy(() -> service.execute(parse(input), result -> { throw new WaOperatorFailure(); }))
                .isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE token_hash=?", WaCrypto.digest(operator))).isZero();
    }

    @Test void expiryDuringPrivateWriterRollsBackAndLeavesOnlyPreparedOutput() throws Exception {
        Path output = directory.resolve("expired-operator.json");
        try (WaOperatorFiles files = WaOperatorFiles.create(output)) {
            assertThatThrownBy(() -> service.execute(parse(request("create-test-cohort").put("synthetic", true)), result -> {
                files.prepare(result);
                mutableClock.set(now.plusSeconds(3600));
            })).isInstanceOf(WaOperatorFailure.class);
        }
        assertThat(WaJson.object(Files.readString(output)).path("status").asText()).isEqualTo("PREPARED");
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE token_hash=?", WaCrypto.digest(operator))).isZero();
    }

    @Test void finalWriteFailurePreservesPreparedSecretAfterDatabaseCommit() throws Exception {
        cohort(); Path output = directory.resolve("committed-credential.json");
        try (WaOperatorFiles files = WaOperatorFiles.create(output, (channel, bytes) -> {
            channel.write(ByteBuffer.wrap(bytes, 0, Math.min(8, bytes.length)));
            throw new IOException("injected final write failure");
        })) {
            service.execute(parse(request("provision-service-credential").put("principal", "CONVERSATION_WORKER")
                    .put("allowedInstanceId", INSTANCE).put("expiresAt", now.plusSeconds(600).toString())), files::prepare);
            byte[] prepared = Files.readAllBytes(output);
            ObjectNode result = WaJson.object(prepared);
            UUID credential = UUID.fromString(result.get("credentialId").asText());
            assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND token_hash=?",
                    credential, WaCrypto.digest(result.get("credential").asText()))).isEqualTo(1);
            assertThatThrownBy(files::committed).isInstanceOf(WaOperatorFailure.class);
            assertThat(Files.readAllBytes(output)).isEqualTo(prepared);
            assertThat(WaJson.object(Files.readString(output)).path("status").asText()).isEqualTo("PREPARED");
            assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=?", credential)).isEqualTo(1);
        }
        try (var paths = Files.list(directory)) { assertThat(paths.count()).isEqualTo(1); }
    }

    @Test void databaseCredentialDeadlineIsAlsoRevalidatedAfterWriter() {
        assertThatThrownBy(() -> service.execute(parse(request("create-test-cohort").put("synthetic", true)), result -> {
            jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE token_hash=?",
                    Timestamp.from(now.plusSeconds(1)), WaCrypto.digest(operator));
            mutableClock.set(now.plusSeconds(2));
        })).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isZero();
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE token_hash=?", WaCrypto.digest(operator))).isZero();
    }

    @Test void revokesTechnicalCredentialWithoutAcceptingOperatorAsTarget() {
        cohort(); ObjectNode worker = provision("CONVERSATION_WORKER"); UUID id = UUID.fromString(worker.get("credentialId").asText());
        execute(request("revoke-service-credential").put("credentialId", id.toString()).put("reasonCode", "TEST_REVOKE"));
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND revoked_at IS NOT NULL", id)).isEqualTo(1);
        UUID operatorId = jdbc.queryForObject("SELECT id FROM wa.service_credential WHERE token_hash=?", UUID.class, WaCrypto.digest(operator));
        assertThatThrownBy(() -> execute(request("revoke-service-credential").put("credentialId", operatorId.toString()).put("reasonCode", "TEST_REVOKE")))
                .isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND revoked_at IS NULL", operatorId)).isEqualTo(1);
    }

    @Test void resetPreservesOutsideIdentityCredentialsCohortAndGlobalProofRows() {
        cohort(); UUID worker = UUID.fromString(provision("CONVERSATION_WORKER").get("credentialId").asText());
        UUID identity = UUID.randomUUID(); execute(seed(identity, worker));
        execute(request("driver-bootstrap").put("identityId", identity.toString()).put("expiresAt", now.plusSeconds(600).toString()));
        UUID outsideBatch = UUID.randomUUID(); UUID outside = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", outsideBatch);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", outside, outsideBatch);
        String nonce = WaCrypto.digest(WaCrypto.randomToken()); String jkt = WaCrypto.randomToken(); String jti = "operator-proof-" + UUID.randomUUID();
        jdbc.update("INSERT INTO wa.proof_nonce(nonce_hash,jkt,environment,expires_at,created_at) VALUES (?,?,'test',?,?)",
                nonce, jkt, Timestamp.from(now.plusSeconds(60)), Timestamp.from(now));
        jdbc.update("INSERT INTO wa.proof_replay(jkt,jti,expires_at) VALUES (?,?,?)", jkt, jti, Timestamp.from(now.plusSeconds(60)));
        ObjectNode result = execute(request("reset-test-data"));
        assertThat(result.get("identitiesDeleted").asInt()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?", batch)).isZero();
        assertThat(count("SELECT count(*) FROM wa.identity WHERE id=?", outside)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=? AND synthetic", batch)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=?", worker)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.proof_nonce WHERE nonce_hash=?", nonce)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.proof_replay WHERE jkt=? AND jti=?", jkt, jti)).isEqualTo(1);
    }

    @Test void resetRejectsCrossBatchLinksWithoutRemovingEitherSide() {
        cohort(); UUID driver = UUID.randomUUID(); UUID passenger = UUID.randomUUID(); UUID outsideBatch = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", driver, batch);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,driver_enabled) VALUES (?,'DRIVER','ACTIVE',true)", driver);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", outsideBatch);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", passenger, outsideBatch);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')", passenger);
        UUID link = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id) VALUES (?,?,?)", link, driver, passenger);
        assertThatThrownBy(() -> execute(request("reset-test-data"))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE id IN (?,?)", driver, passenger)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM wa.driver_passenger_link WHERE id=?", link)).isEqualTo(1);
    }

    @Test void operatorRevocationUsesOperatorAuditAndRevokesDeviceTokensAndApprovals() {
        cohort(); UUID driver = UUID.randomUUID(); UUID device = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", driver, batch);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,driver_enabled) VALUES (?,'DRIVER','ACTIVE',true)", driver);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,1)", driver);
        String jkt = WaCrypto.randomToken();
        jdbc.update("INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version) VALUES (?,?,?,'{}'::jsonb,1,'test','ANDROID','synthetic')", device, driver, jkt);
        UUID token = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.device_token(id,device_id,token_hash,kind,family_id,expires_at,created_at) VALUES (?,?,?,'REFRESH',?,?,?)",
                token, device, WaCrypto.digest(WaCrypto.randomToken()), UUID.randomUUID(), Timestamp.from(now.plusSeconds(600)), Timestamp.from(now));
        UUID pairing = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.pairing_challenge(id,public_jwk,jkt,pkce_challenge,platform,app_version,environment,code_hash,state,expires_at,driver_id,approval_generation,created_at) VALUES (?,'{}'::jsonb,?,?,'ANDROID','synthetic','test',?,'APPROVED',?,?,1,?)",
                pairing, jkt, WaCrypto.randomToken(), crypto.secretHash("pairing-code", "BCDFGHJZ"), Timestamp.from(now.plusSeconds(300)), driver, Timestamp.from(now));
        execute(request("revoke-device").put("deviceId", device.toString()).put("reasonCode", "TEST_DEVICE_REVOKE"));
        assertThat(count("SELECT count(*) FROM wa.driver_device WHERE id=? AND status='REVOKED' AND revoked_at IS NOT NULL", device)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.device_token WHERE id=? AND revoked_at IS NOT NULL", token)).isEqualTo(1);
        assertThat(count("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", driver)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT state FROM wa.pairing_challenge WHERE id=?", String.class, pairing)).isEqualTo("REVOKED");
        assertThat(count("SELECT count(*) FROM wa.audit_event WHERE resource_id=? AND actor_kind='OPERATOR' AND actor_identity_id IS NULL AND reason_code='TEST_DEVICE_REVOKE'", device)).isEqualTo(1);
    }

    private void receipt(UUID identity, UUID event) {
        jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,
                request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,'START_ONBOARDING',?,'{}'::jsonb,?,?)
            """, UUID.randomUUID(), identity, event, UUID.randomUUID().toString(), WaCrypto.digest("synthetic-command"),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(86400)));
    }
}
