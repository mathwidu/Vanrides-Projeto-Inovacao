package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WaOperatorBatchTest {
    @TempDir Path directory;
    private final String operator = WaCrypto.randomToken();
    private final String batch = UUID.randomUUID().toString();

    @BeforeEach void canonicalPrivateDirectory() throws Exception {
        directory = directory.toRealPath();
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    }

    private ObjectNode request() {
        return WaJson.MAPPER.createObjectNode().put("environment", "test").put("operation", "reset-test-data")
                .put("batchId", batch).put("operatorCredential", operator);
    }
    private ObjectNode plan(int count) {
        ObjectNode envelope = WaJson.MAPPER.createObjectNode().put("batchVersion", 1);
        var operations = envelope.putArray("operations");
        for (int i = 0; i < count; i++) {
            var operation = operations.addObject().put("outputFile", directory.resolve(UUID.randomUUID() + ".json").toString());
            operation.set("request", request());
        }
        return envelope;
    }
    private static ObjectNode step(ObjectNode plan, int index) { return (ObjectNode) plan.withArray("operations").get(index); }
    private static ObjectNode requestAt(ObjectNode plan, int index) { return (ObjectNode) step(plan, index).get("request"); }
    private static Path output(ObjectNode plan, int index) { return Path.of(step(plan, index).path("outputFile").asText()); }
    private Path write(String bytes) throws Exception {
        Path input = directory.resolve(UUID.randomUUID() + "-input.json");
        Files.createFile(input, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(input, bytes);
        return input;
    }
    private MockEnvironment environment(Path input) {
        return new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                .withProperty("vanrides.wa.operator.request-file", input.toString());
    }
    private WaOperatorRunner runner(MockEnvironment environment, WaOperatorService service) {
        return new WaOperatorRunner(environment, new WaOperatorMode(new GenericApplicationContext(), environment), service);
    }
    private void rejected(ObjectNode plan) throws Exception {
        WaOperatorService service = mock(WaOperatorService.class);
        assertThatThrownBy(() -> runner(environment(write(WaJson.write(plan))), service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class).hasMessage("WA_OPERATOR_FAILED").hasNoCause();
        verifyNoInteractions(service);
    }
    @SuppressWarnings("unchecked") private void prepareService(WaOperatorService service) {
        doAnswer(call -> {
            Consumer<ObjectNode> writer = call.getArgument(1, Consumer.class);
            writer.accept(WaJson.MAPPER.createObjectNode().put("status", "PREPARED"));
            return null;
        }).when(service).execute(any(), any());
    }

    @Test void commitsIndividualResultsForOneAndSixteenOperationsAndRedactsPlan() throws Exception {
        for (int size : new int[] {1, 16}) {
            ObjectNode plan = plan(size); Path input = write(WaJson.write(plan));
            assertThat(WaOperatorBatch.parse(WaOperatorFiles.read(input), input).toString()).isEqualTo("WaOperatorBatch[redacted]");
            WaOperatorService service = mock(WaOperatorService.class); prepareService(service);
            runner(environment(input), service).run(new DefaultApplicationArguments());
            verify(service, times(size)).execute(any(), any());
            for (int i = 0; i < size; i++) {
                assertThat(WaJson.object(Files.readString(output(plan, i))).path("status").asText()).isEqualTo("COMMITTED");
                assertThat(Files.getPosixFilePermissions(output(plan, i))).isEqualTo(PosixFilePermissions.fromString("rw-------"));
            }
        }
    }

    @Test void rejectsEmptyOversizedVersionedAndUnknownEnvelopeFieldsBeforeEffects() throws Exception {
        rejected(plan(0)); rejected(plan(17));
        for (ObjectNode plan : new ObjectNode[] {plan(1).put("batchVersion", 2), plan(1).put("batchVersion", "1"),
                plan(1).put("batchVersion", 1.0), plan(1).put("extra", true), plan(1).putNull("cancelFile")}) rejected(plan);
        byte[] bytes = (" ".repeat(16385)).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> WaOperatorBatch.parse(bytes, directory.resolve("input.json"))).isInstanceOf(WaOperatorFailure.class);
    }

    @Test void rejectsDuplicateKeysTrailingDocumentsAndMalformedLaterRequestBeforeEffects() throws Exception {
        WaOperatorService service = mock(WaOperatorService.class);
        String valid = WaJson.write(plan(1));
        for (String raw : new String[] {valid + "{}", valid.replace("\"batchVersion\":1", "\"batchVersion\":1,\"batchVersion\":1")})
            assertThatThrownBy(() -> runner(environment(write(raw)), service).run(new DefaultApplicationArguments()))
                    .isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service);
        ObjectNode plan = plan(2); requestAt(plan, 1).put("extra", true); rejected(plan);
        assertThat(output(plan, 0)).doesNotExist();
        plan = plan(2); step(plan, 1).put("extra", true); rejected(plan);
    }

    @Test void rejectsMixedScopeAndOperationsOutsideTheMinimalAllowlist() throws Exception {
        for (String field : new String[] {"environment", "batchId", "operatorCredential"}) {
            ObjectNode plan = plan(2);
            requestAt(plan, 1).put(field, field.equals("environment") ? "other_test"
                    : field.equals("batchId") ? UUID.randomUUID().toString() : WaCrypto.randomToken());
            rejected(plan);
        }
        ObjectNode plan = plan(1);
        requestAt(plan, 0).put("operation", "driver-bootstrap").put("identityId", UUID.randomUUID().toString())
                .put("expiresAt", "2030-01-01T00:00:00Z");
        rejected(plan);
    }

    @Test void rejectsAmbiguousGlobalOutputAndWebOrProductionModes() throws Exception {
        ObjectNode plan = plan(1); Path input = write(WaJson.write(plan));
        WaOperatorService service = mock(WaOperatorService.class);
        MockEnvironment ambiguous = environment(input).withProperty("vanrides.wa.operator.output-file", "");
        assertThatThrownBy(() -> runner(ambiguous, service).run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        MockEnvironment production = environment(input); production.setActiveProfiles("production");
        assertThatThrownBy(() -> runner(production, service).run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        MockEnvironment web = environment(input);
        assertThatThrownBy(() -> new WaOperatorRunner(web, new WaOperatorMode(new StaticWebApplicationContext(), web), service)
                .run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service); assertThat(output(plan, 0)).doesNotExist();
    }

    @Test void rejectsRelativeOutsideNoncanonicalDuplicateExistingAndSymlinkOutputs() throws Exception {
        for (String path : new String[] {"relative.json", directory.getParent().resolve("outside.json").toString(),
                directory + "/../" + directory.getFileName() + "/result.json"}) {
            ObjectNode plan = plan(1); step(plan, 0).put("outputFile", path); rejected(plan);
        }
        ObjectNode duplicate = plan(2); step(duplicate, 1).put("outputFile", output(duplicate, 0).toString()); rejected(duplicate);
        ObjectNode existing = plan(2); Files.writeString(output(existing, 1), "preserve-existing"); rejected(existing);
        assertThat(output(existing, 0)).doesNotExist();
        assertThat(Files.readString(output(existing, 1))).isEqualTo("preserve-existing");
        ObjectNode linked = plan(1); Files.createSymbolicLink(output(linked, 0), directory.resolve("missing.json")); rejected(linked);
    }

    @Test void rejectsInsecureInputDirectoryAndAncestorSymlink() throws Exception {
        ObjectNode plan = plan(1); Path input = write(WaJson.write(plan));
        WaOperatorService service = mock(WaOperatorService.class);
        Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(() -> runner(environment(input), service).run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThatThrownBy(() -> runner(environment(input), service).run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path link = directory.resolve("directory-link"); Files.createSymbolicLink(link, directory);
        assertThatThrownBy(() -> runner(environment(link.resolve(input.getFileName())), service)
                .run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service);
    }

    @Test void reservesEveryOutputBeforeFirstEffectEvenWhenADestinationAppearsAfterParsing() throws Exception {
        ObjectNode plan = plan(2); Path input = write(WaJson.write(plan));
        WaOperatorService service = mock(WaOperatorService.class);
        WaOperatorBatch parsed = WaOperatorBatch.parse(WaOperatorFiles.read(input), input);
        Files.writeString(output(plan, 1), "preserve-raced-file");
        assertThatThrownBy(() -> parsed.execute(service, WaOperatorFiles::create)).isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service); assertThat(Files.size(output(plan, 0))).isZero();
        assertThat(Files.readString(output(plan, 1))).isEqualTo("preserve-raced-file");
    }

    @Test void cancellationBeforeFirstOperationAndAfterFirstCommitNeverRunsTheNextOperation() throws Exception {
        for (boolean beforeFirst : new boolean[] {true, false}) {
            ObjectNode plan = plan(2); Path cancel = directory.resolve(UUID.randomUUID() + "-cancel");
            plan.put("cancelFile", cancel.toString()); Path input = write(WaJson.write(plan));
            WaOperatorBatch parsed = WaOperatorBatch.parse(WaOperatorFiles.read(input), input);
            WaOperatorService service = mock(WaOperatorService.class); prepareService(service);
            if (beforeFirst) Files.createFile(cancel, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            assertThatThrownBy(() -> parsed.execute(service, path -> WaOperatorFiles.create(path, (channel, bytes) -> {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
                Files.createFile(cancel, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            }))).isInstanceOf(WaOperatorFailure.class);
            verify(service, times(beforeFirst ? 0 : 1)).execute(any(), any());
            assertThat(Files.size(output(plan, 1))).isZero();
            if (!beforeFirst) assertThat(WaJson.object(Files.readString(output(plan, 0))).path("status").asText()).isEqualTo("COMMITTED");
        }
    }

    @Test void rejectsPreexistingOutsideAndOutputAliasedCancellationMarkers() throws Exception {
        ObjectNode existing = plan(1); Path marker = directory.resolve("already-cancelled"); Files.writeString(marker, "cancel");
        rejected(existing.put("cancelFile", marker.toString()));
        ObjectNode alias = plan(1); rejected(alias.put("cancelFile", output(alias, 0).toString()));
        rejected(plan(1).put("cancelFile", directory.getParent().resolve("outside-cancel").toString()));
    }
}
