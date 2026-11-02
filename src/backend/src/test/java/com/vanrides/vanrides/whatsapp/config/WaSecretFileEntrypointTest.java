package com.vanrides.vanrides.whatsapp.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs only /bin/sh and a temporary java stub, never Java/Docker/application startup. */
class WaSecretFileEntrypointTest {
    private static final List<String> WHATSAPP = List.of(
            "WA_SECRET_HASH_KEY", "WA_RESPONSE_ENCRYPTION_KEY", "WA_OPERATOR_CREDENTIAL");
    private static final List<String> LEGACY = List.of(
            "SPRING_DATASOURCE_PASSWORD", "GOOGLE_API_KEY", "VANRIDES_JWT_SECRET", "VANRIDES_EMAIL_RESEND_API_KEY");
    private static final SecureRandom RANDOM = new SecureRandom();
    @TempDir Path directory;
    private Path bin;
    private Path expected;
    private Path capture;
    private final List<String> values = new ArrayList<>();

    @BeforeEach
    void prepareStub() throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        bin = Files.createDirectory(directory.resolve("bin"));
        expected = Files.createDirectory(directory.resolve("expected"));
        capture = directory.resolve("java-called");
        Path stub = Files.createFile(bin.resolve("java"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.writeString(stub, """
                #!/bin/sh
                set -eu
                for name in WA_SECRET_HASH_KEY WA_RESPONSE_ENCRYPTION_KEY WA_OPERATOR_CREDENTIAL \
                    SPRING_DATASOURCE_PASSWORD GOOGLE_API_KEY VANRIDES_JWT_SECRET VANRIDES_EMAIL_RESEND_API_KEY
                do
                    expected_file="$EXPECTED_DIRECTORY/$name"
                    [ -f "$expected_file" ] || continue
                    if ! observed="$(printenv "$name")"; then exit 71; fi
                    expected_value="$(tr -d '\\r\\n' < "$expected_file")"
                    [ "$observed" = "$expected_value" ] || exit 72
                    if printenv "${name}_FILE" >/dev/null 2>&1; then exit 73; fi
                done
                [ "$#" -eq 2 ] || exit 74
                [ "$1" = '-jar' ] || exit 75
                [ "$2" = '/app/app.jar' ] || exit 76
                [ "${UNRELATED_FLAG-}" = 'retained' ] || exit 77
                umask 077
                printf 'accepted' > "$ENTRYPOINT_CAPTURE"
                """, StandardCharsets.UTF_8);
    }

    @Test
    void shouldReadWhatsAppSecretsFromPrivateFilesWithoutEmittingTheirValues() throws Exception {
        Map<String, String> variables = new HashMap<>();
        for (String name : WHATSAPP) fileSecret(variables, name);
        Result result = run(variables);
        assertSuccess(result);
    }

    @Test
    void shouldRejectEveryDirectValueAndFileCombinationBeforeCallingJava() throws Exception {
        for (String name : WHATSAPP) {
            Map<String, String> variables = new HashMap<>();
            fileSecret(variables, name); variables.put(name, secret());
            Result result = run(variables);
            assertThat(result.exit()).isEqualTo(1);
            assertThat(result.javaCalled()).isFalse();
            assertThat(result.output().contains("both " + name + " and " + name + "_FILE are set")).isTrue();
        }
    }

    @Test
    void shouldRejectEveryMissingWhatsAppSecretFileBeforeCallingJava() throws Exception {
        for (String name : WHATSAPP) {
            Result result = run(Map.of(name + "_FILE", directory.resolve("missing-" + name).toString(),
                    "VANRIDES_JWT_SECRET", secret()));
            assertThat(result.exit()).isEqualTo(1);
            assertThat(result.javaCalled()).isFalse();
            assertThat(result.output().contains(name + "_FILE points to unreadable file")).isTrue();
        }
    }

    @Test
    void shouldPreserveEveryLegacyFileMappingAlongsideDirectWhatsAppValues() throws Exception {
        Map<String, String> variables = new HashMap<>();
        for (String name : LEGACY) fileSecret(variables, name);
        for (String name : WHATSAPP) directSecret(variables, name);
        assertSuccess(run(variables));
    }

    @Test
    void shouldPreserveDirectLegacyValuesAlongsideWhatsAppSecretFiles() throws Exception {
        Map<String, String> variables = new HashMap<>();
        for (String name : LEGACY) directSecret(variables, name);
        for (String name : WHATSAPP) fileSecret(variables, name);
        assertSuccess(run(variables));
    }

    private Result run(Map<String, String> variables) throws Exception {
        Path script = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .resolve("docker-entrypoint.sh").toAbsolutePath();
        assertThat(Files.isRegularFile(script)).isTrue();
        ProcessBuilder builder = new ProcessBuilder("/bin/sh", script.toString()).redirectErrorStream(true);
        // Do not inherit real secrets, Java flags or a real java executable from the developer/CI environment.
        Map<String, String> environment = builder.environment(); environment.clear();
        environment.put("PATH", bin + ":/usr/bin:/bin"); environment.put("LC_ALL", "C");
        environment.put("EXPECTED_DIRECTORY", expected.toString());
        environment.put("ENTRYPOINT_CAPTURE", capture.toString()); environment.put("UNRELATED_FLAG", "retained");
        environment.putAll(variables);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // Assertions deliberately inspect booleans; regression failures must not dump a secret-bearing output.
            assertThat(values.stream().noneMatch(output::contains)).isTrue();
            return new Result(process.exitValue(), output, Files.exists(capture));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            }
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
        }
    }

    private void fileSecret(Map<String, String> variables, String name) throws Exception {
        String value = secret();
        Path file = privateFile(directory.resolve("secret-" + name), value + "\r\n");
        privateFile(expected.resolve(name), value);
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        variables.put(name + "_FILE", file.toString());
    }

    private void directSecret(Map<String, String> variables, String name) throws Exception {
        String value = secret(); privateFile(expected.resolve(name), value); variables.put(name, value);
    }

    private static Path privateFile(Path path, String value) throws Exception {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        return Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private String secret() {
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random); values.add(value); return value;
    }

    private static void assertSuccess(Result result) {
        assertThat(result.exit()).isZero(); assertThat(result.output().isEmpty()).isTrue();
        assertThat(result.javaCalled()).isTrue();
    }
    private record Result(int exit, String output, boolean javaCalled) { }
}
