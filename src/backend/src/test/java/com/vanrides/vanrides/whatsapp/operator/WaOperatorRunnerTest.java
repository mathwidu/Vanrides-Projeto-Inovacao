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
import java.util.function.Consumer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
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

class WaOperatorRunnerTest {
    @TempDir Path directory;
    private Logger logger;
    private ListAppender<ILoggingEvent> events;
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach void captureRunnerEvents() {
        logger = (Logger) LoggerFactory.getLogger(WaOperatorRunner.class);
        previousLevel = logger.getLevel(); previousAdditive = logger.isAdditive();
        events = new ListAppender<>(); events.start();
        logger.addAppender(events); logger.setLevel(Level.INFO); logger.setAdditive(false);
    }
    @AfterEach void restoreLogger() {
        logger.detachAppender(events); events.stop();
        logger.setLevel(previousLevel); logger.setAdditive(previousAdditive);
    }
    private void assertOnlyGenericEvent(Level level, String message) {
        assertThat(events.list).hasSize(1);
        ILoggingEvent event = events.list.get(0);
        assertThat(event.getLevel()).isEqualTo(level);
        assertThat(event.getFormattedMessage()).isEqualTo(message);
        assertThat(event.getArgumentArray()).isNull();
        assertThat(event.getThrowableProxy()).isNull();
    }

    private Path input() throws Exception {
        Path path = directory.resolve(UUID.randomUUID() + ".json");
        ObjectNode request = WaJson.MAPPER.createObjectNode().put("environment", "test")
                .put("operation", "reset-test-data").put("batchId", UUID.randomUUID().toString())
                .put("operatorCredential", WaCrypto.randomToken());
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path, WaJson.write(request));
        return path;
    }
    private MockEnvironment environment(Path input, Path output) {
        return new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                .withProperty("vanrides.wa.operator.request-file", input.toString())
                .withProperty("vanrides.wa.operator.output-file", output.toString());
    }
    private static WaOperatorRunner runner(MockEnvironment environment, WaOperatorService service) {
        return new WaOperatorRunner(environment, new WaOperatorMode(new GenericApplicationContext(), environment), service);
    }

    @Test void remainsInactiveWithoutExplicitRequestFile() {
        WaOperatorService service = mock(WaOperatorService.class);
        runner(new MockEnvironment(), service).run(new DefaultApplicationArguments());
        verifyNoInteractions(service);
    }

    @Test void refusesWebContextEvenWhenPropertySaysNone() throws Exception {
        MockEnvironment environment = environment(input(), directory.resolve("unused.json"));
        WaOperatorService service = mock(WaOperatorService.class);
        WaOperatorMode mode = new WaOperatorMode(new StaticWebApplicationContext(), environment);
        assertThatThrownBy(() -> new WaOperatorRunner(environment, mode, service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class).hasMessage("WA_OPERATOR_FAILED");
        verifyNoInteractions(service);
    }

    @Test void refusesProductionProfileEvenWhenRequestEnvironmentWouldSayTest() throws Exception {
        MockEnvironment environment = environment(input(), directory.resolve("unused-production.json"));
        environment.setActiveProfiles("prod");
        WaOperatorService service = mock(WaOperatorService.class);
        assertThatThrownBy(() -> runner(environment, service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service);
    }

    @Test void refusesWorldReadableInputAndNeverOverwritesAnExistingOutput() throws Exception {
        Path input = input(); Path output = directory.resolve("existing.json");
        Files.writeString(output, "preserve-existing");
        WaOperatorService service = mock(WaOperatorService.class);
        assertThatThrownBy(() -> runner(environment(input, output), service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class);
        assertThat(Files.readString(output)).isEqualTo("preserve-existing");
        Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rw-r--r--"));
        Path fresh = directory.resolve("not-created.json");
        assertThatThrownBy(() -> runner(environment(input, fresh), service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class);
        assertThat(fresh).doesNotExist();
        verifyNoInteractions(service);
    }

    @Test void refusesSymlinkInputAndOutput() throws Exception {
        Path input = input(); Path link = directory.resolve("link.json");
        Files.createSymbolicLink(link, input);
        WaOperatorService service = mock(WaOperatorService.class);
        assertThatThrownBy(() -> runner(environment(link, directory.resolve("fresh.json")), service)
                .run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        assertThatThrownBy(() -> WaOperatorFiles.create(link)).isInstanceOf(WaOperatorFailure.class);
        verifyNoInteractions(service);
    }

    @Test @SuppressWarnings("unchecked")
    void writesSecretOnlyToPrivateFileAndReportsGenericSuccess() throws Exception {
        Path input = input(); Path output = directory.resolve("result.json");
        WaOperatorService service = mock(WaOperatorService.class);
        String secret = WaCrypto.randomToken();
        doAnswer(call -> {
            assertThat(Files.getPosixFilePermissions(output)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
            Consumer<ObjectNode> writer = call.getArgument(1, Consumer.class);
            writer.accept(WaJson.MAPPER.createObjectNode().put("status", "PREPARED").put("credential", secret));
            return null;
        }).when(service).execute(any(), any());
        runner(environment(input, output), service).run(new DefaultApplicationArguments());
        assertOnlyGenericEvent(Level.INFO, "WA_OPERATOR_OK");
        assertThat(Files.getPosixFilePermissions(output)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        ObjectNode result = WaJson.object(Files.readString(output));
        assertThat(result.path("status").asText()).isEqualTo("COMMITTED");
        assertThat(result.path("credential").asText()).isEqualTo(secret);
    }

    @Test @SuppressWarnings("unchecked")
    void leavesPreparedResultOnCommitFailureAndSanitizesException() throws Exception {
        Path input = input(); Path output = directory.resolve("prepared.json");
        WaOperatorService service = mock(WaOperatorService.class); String secret = WaCrypto.randomToken();
        doAnswer(call -> {
            Consumer<ObjectNode> writer = call.getArgument(1, Consumer.class);
            writer.accept(WaJson.MAPPER.createObjectNode().put("status", "PREPARED").put("credential", secret));
            throw new IllegalStateException(secret);
        }).when(service).execute(any(), any());
        assertThatThrownBy(() -> runner(environment(input, output), service).run(new DefaultApplicationArguments()))
                .isInstanceOf(WaOperatorFailure.class).hasMessage("WA_OPERATOR_FAILED").hasNoCause();
        assertOnlyGenericEvent(Level.WARN, "WA_OPERATOR_FAILED");
        assertThat(WaJson.object(Files.readString(output)).path("status").asText()).isEqualTo("PREPARED");
    }

    @Test @SuppressWarnings("unchecked")
    void finalWriteFailureKeepsPreparedSecretAndNeverReportsSuccessAfterServiceCommit() throws Exception {
        Path input = input(); Path output = directory.resolve("failed-finalization.json");
        WaOperatorService service = mock(WaOperatorService.class);
        String secret = WaCrypto.randomToken();
        boolean[] committed = {false};
        doAnswer(call -> {
            Consumer<ObjectNode> writer = call.getArgument(1, Consumer.class);
            writer.accept(WaJson.MAPPER.createObjectNode().put("status", "PREPARED").put("credential", secret));
            committed[0] = true;
            return null;
        }).when(service).execute(any(), any());
        MockEnvironment environment = environment(input, output);
        WaOperatorRunner runner = new WaOperatorRunner(environment,
                new WaOperatorMode(new GenericApplicationContext(), environment), service,
                path -> WaOperatorFiles.create(path, (channel, bytes) -> {
                    channel.write(ByteBuffer.wrap(bytes, 0, Math.min(8, bytes.length)));
                    throw new IOException("injected final write failure");
                }));
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isInstanceOf(WaOperatorFailure.class);
        assertThat(committed[0]).isTrue();
        assertOnlyGenericEvent(Level.WARN, "WA_OPERATOR_FAILED");
        ObjectNode prepared = WaJson.object(Files.readString(output));
        assertThat(prepared.path("status").asText()).isEqualTo("PREPARED");
        assertThat(prepared.path("credential").asText()).isEqualTo(secret);
        try (var paths = Files.list(directory)) { assertThat(paths.count()).isEqualTo(2); }
    }

    @Test void permissionChangeBeforeWriteFailsWithoutWritingSecret() throws Exception {
        Path output = directory.resolve("changed.json");
        try (WaOperatorFiles files = WaOperatorFiles.create(output)) {
            Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-r--r--"));
            assertThatThrownBy(() -> files.prepare(WaJson.MAPPER.createObjectNode().put("status", "PREPARED")
                    .put("credential", WaCrypto.randomToken()))).isInstanceOf(WaOperatorFailure.class);
            assertThat(Files.size(output)).isZero();
        }
    }
}
