package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaOperatorBatchPostgresIntegrationTest extends WaOperatorPostgresTestSupport {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_operator_batch_test");

    @BeforeAll static void migrate() { migrate(POSTGRES); }
    @Override protected PostgreSQLContainer<?> database() { return POSTGRES; }

    private ObjectNode batchPlan(ObjectNode... requests) throws Exception {
        Path base = directory.toRealPath();
        Files.setPosixFilePermissions(base, PosixFilePermissions.fromString("rwx------"));
        ObjectNode plan = WaJson.MAPPER.createObjectNode().put("batchVersion", 1);
        var operations = plan.putArray("operations");
        for (ObjectNode request : requests) operations.addObject()
                .put("outputFile", base.resolve(UUID.randomUUID() + "-result.json").toString()).set("request", request);
        return plan;
    }
    private static Path batchOutput(ObjectNode plan, int index) {
        return Path.of(plan.withArray("operations").get(index).path("outputFile").asText());
    }
    private ObjectNode provisionRequest(String principal) {
        return request("provision-service-credential").put("principal", principal)
                .put("allowedInstanceId", INSTANCE).put("expiresAt", now.plusSeconds(600).toString());
    }
    private void runBatch(ObjectNode plan, Function<Path, WaOperatorFiles> filesFactory) throws Exception {
        Path input = directory.toRealPath().resolve(UUID.randomUUID() + "-batch.json");
        Files.createFile(input, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(input, WaJson.write(plan));
        var environment = new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                .withProperty("vanrides.wa.operator.request-file", input.toString());
        new WaOperatorRunner(environment, context.getBean(WaOperatorMode.class), service, filesFactory)
                .run(new DefaultApplicationArguments());
    }
    private long batchAudits() {
        return count("SELECT count(*) FROM wa.audit_event a JOIN wa.service_credential c ON c.id=a.credential_id WHERE c.token_hash=?",
                WaCrypto.digest(operator));
    }
    private long batchTechnicalCredentials() {
        return count("SELECT count(*) FROM wa.service_credential WHERE allowed_instance_id=?", INSTANCE);
    }
    private static void writeFully(java.nio.channels.FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
    }

    @Test void batchCommitsEachRealTransactionAndItsOwnPrivateResultAndAudit() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true),
                provisionRequest("CHANNEL"), provisionRequest("DELIVERY_WORKER"));
        long credentialsBefore = batchTechnicalCredentials();
        runBatch(plan, WaOperatorFiles::create);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=? AND synthetic", batch)).isEqualTo(1);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore + 2);
        assertThat(batchAudits()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            ObjectNode result = WaJson.object(Files.readString(batchOutput(plan, i)));
            assertThat(result.path("status").asText()).isEqualTo("COMMITTED");
            assertThat(result.path("batchId").asText()).isEqualTo(batch.toString());
            assertThat(Files.getPosixFilePermissions(batchOutput(plan, i))).isEqualTo(PosixFilePermissions.fromString("rw-------"));
            if (i > 0) assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND token_hash=?",
                    UUID.fromString(result.path("credentialId").asText()), WaCrypto.digest(result.path("credential").asText()))).isEqualTo(1);
        }
    }

    @Test void batchIntermediateBusinessFailurePreservesCommittedPrefixAndNeverExecutesSuffix() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true), provisionRequest("CHANNEL"),
                request("revoke-service-credential").put("credentialId", UUID.randomUUID().toString()).put("reasonCode", "TEST_REVOKE"),
                provisionRequest("DELIVERY_WORKER"));
        long credentialsBefore = batchTechnicalCredentials();
        assertThatThrownBy(() -> runBatch(plan, WaOperatorFiles::create)).isInstanceOf(WaOperatorFailure.class);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore + 1);
        assertThat(batchAudits()).isEqualTo(2);
        for (int i = 0; i < 2; i++)
            assertThat(WaJson.object(Files.readString(batchOutput(plan, i))).path("status").asText()).isEqualTo("COMMITTED");
        assertThat(Files.size(batchOutput(plan, 2))).isZero();
        assertThat(Files.size(batchOutput(plan, 3))).isZero();
    }

    @Test void batchPrivateWriterFailureRollsBackOnlyCurrentRealTransactionAndStops() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true),
                provisionRequest("CHANNEL"), provisionRequest("DELIVERY_WORKER"));
        long credentialsBefore = batchTechnicalCredentials();
        assertThatThrownBy(() -> runBatch(plan, path -> WaOperatorFiles.create(path, (channel, bytes) -> {
            writeFully(channel, bytes);
            if (path.equals(batchOutput(plan, 0))) Files.setPosixFilePermissions(batchOutput(plan, 1),
                    PosixFilePermissions.fromString("rw-r--r--"));
        }))).isInstanceOf(WaOperatorFailure.class);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore);
        assertThat(batchAudits()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isEqualTo(1);
        assertThat(WaJson.object(Files.readString(batchOutput(plan, 0))).path("status").asText()).isEqualTo("COMMITTED");
        assertThat(Files.size(batchOutput(plan, 1))).isZero();
        assertThat(Files.size(batchOutput(plan, 2))).isZero();
    }

    @Test void batchReauthenticatesExpiryBetweenRealTransactions() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true), provisionRequest("CHANNEL"));
        long credentialsBefore = batchTechnicalCredentials();
        assertThatThrownBy(() -> runBatch(plan, path -> WaOperatorFiles.create(path, (channel, bytes) -> {
            writeFully(channel, bytes); mutableClock.set(now.plusSeconds(3600));
        }))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isEqualTo(1);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore);
        assertThat(batchAudits()).isEqualTo(1);
        assertThat(WaJson.object(Files.readString(batchOutput(plan, 0))).path("status").asText()).isEqualTo("COMMITTED");
        assertThat(Files.size(batchOutput(plan, 1))).isZero();
    }

    @Test void batchFinalizationFailureKeepsPreparedIndividualResultAfterItsDatabaseCommitAndStops() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true),
                provisionRequest("CHANNEL"), provisionRequest("DELIVERY_WORKER"));
        long credentialsBefore = batchTechnicalCredentials();
        assertThatThrownBy(() -> runBatch(plan, path -> WaOperatorFiles.create(path, (channel, bytes) -> {
            if (path.equals(batchOutput(plan, 1))) throw new IOException("synthetic finalization failure");
            writeFully(channel, bytes);
        }))).isInstanceOf(WaOperatorFailure.class);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore + 1);
        assertThat(batchAudits()).isEqualTo(2);
        assertThat(WaJson.object(Files.readString(batchOutput(plan, 0))).path("status").asText()).isEqualTo("COMMITTED");
        ObjectNode prepared = WaJson.object(Files.readString(batchOutput(plan, 1)));
        assertThat(prepared.path("status").asText()).isEqualTo("PREPARED");
        assertThat(count("SELECT count(*) FROM wa.service_credential WHERE id=? AND token_hash=?",
                UUID.fromString(prepared.path("credentialId").asText()), WaCrypto.digest(prepared.path("credential").asText()))).isEqualTo(1);
        assertThat(Files.size(batchOutput(plan, 2))).isZero();
    }

    @Test void batchCancellationAfterRealCommitPreservesReceiptAndFencesNextTransaction() throws Exception {
        ObjectNode plan = batchPlan(request("create-test-cohort").put("synthetic", true), provisionRequest("CHANNEL"));
        Path marker = directory.toRealPath().resolve("cancel-batch"); plan.put("cancelFile", marker.toString());
        long credentialsBefore = batchTechnicalCredentials();
        assertThatThrownBy(() -> runBatch(plan, path -> WaOperatorFiles.create(path, (channel, bytes) -> {
            writeFully(channel, bytes);
            Files.createFile(marker, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?", batch)).isEqualTo(1);
        assertThat(batchTechnicalCredentials()).isEqualTo(credentialsBefore);
        assertThat(batchAudits()).isEqualTo(1);
        assertThat(WaJson.object(Files.readString(batchOutput(plan, 0))).path("status").asText()).isEqualTo("COMMITTED");
        assertThat(Files.size(batchOutput(plan, 1))).isZero();
    }
}
